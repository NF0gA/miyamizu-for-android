package com.miyamizu.player.playback

import android.util.Log
import com.miyamizu.player.data.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.min

/**
 * Miyamizuのコア再生エンジン。
 * FLACデコード → リサンプル → 出力(AAudio/USB) のパイプラインを管理。
 *
 * 設計方針:
 * - デコード・リサンプルはIOディスパッチャーで非同期実行
 * - 出力書き込みは専用コルーチンで継続的実行
 * - USB出力時はSPSCリングバッファに書き込み、ネイティブRTスレッドが読む
 * - AAudio出力時はブロッキングwriteで直接書き込む
 * - ギャップレス再生対応: PCMバッファを無縫継で切り替え
 */
class PlaybackEngine {

    companion object {
        private const val TAG = "PlaybackEngine"
        private const val RING_BUFFER_FRAMES = 65536
        private const val WRITE_CHUNK_FRAMES = 2048

        @Volatile
        private var instance: PlaybackEngine? = null

        fun getInstance(): PlaybackEngine {
            return instance ?: synchronized(this) {
                instance ?: PlaybackEngine().also { instance = it }
            }
        }
    }

    // ===== 状態 =====
    sealed class State {
        object Idle : State()
        object Decoding : State()
        object Ready : State()
        object Playing : State()
        object Paused : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    // ===== 現在のトラック =====
    private var currentTrack: Track? = null

    // ===== PCMバッファ（RAM再生） =====
    private var pcmBuffer: FloatArray = floatArrayOf()
    private var pcmChannels: Int = 2
    private var pcmSampleRate: Int = 44100
    private var pcmBitsPerSample: Int = 16
    private var playbackFramePosition: Int = 0

    // ===== ギャップレス =====
    private var gaplessController: GaplessController? = null

    // ===== リサンプラー =====
    data class ResamplerConfig(
        val enabled: Boolean = false,
        val engine: ResamplerEngine = ResamplerEngine.SOXR,
        val targetSampleRate: Int = 48000,
        val soxrPhase: Int = 1,
        val soxrBandwidth: Int = 0,
        val soxrQuality: Double = 10.0,
        val r8brainPreset: Int = 1
    )

    enum class ResamplerEngine { SOXR, R8BRAIN }
    private var resamplerConfig = ResamplerConfig()
    private var resamplerHandle: Long = 0

    // ===== 出力設定 =====
    enum class OutputMode { NONE, AAUDIO, USB }
    private var outputMode: OutputMode = OutputMode.NONE

    // ===== コルーチン =====
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var playbackJob: Job? = null

    // ===== テレメトリ =====
    data class Telemetry(
        val jitterRmsUs: Double = 0.0,
        val xrunCount: Int = 0,
        val isBitPerfect: Boolean = false,
        val isMmap: Boolean = false,
        val actualSampleRate: Int = 0
    )

    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry: StateFlow<Telemetry> = _telemetry
    private var telemetryJob: Job? = null

    // ========================================================================
    // 公開API
    // ========================================================================

    fun setGaplessController(controller: GaplessController) {
        gaplessController = controller
    }

    fun loadTrack(track: Track, onReady: (() -> Unit)? = null) {
        engineScope.launch {
            _state.value = State.Decoding
            currentTrack = track

            val path = track.filePath
            val startSample = track.cueStartSample
            val endSample = track.cueEndSample

            Log.i(TAG, "Loading track: $path, start=$startSample, end=$endSample")

            val info = NativeEngine.getFlacInfo(path)
            if (info == null) {
                _state.value = State.Error("Failed to read FLAC info: $path")
                return@launch
            }

            pcmChannels = info.channels
            pcmSampleRate = info.sampleRate
            pcmBitsPerSample = info.bitsPerSample

            val pcm = NativeEngine.decodeFlacToRam(path, startSample, endSample)
            if (pcm.isEmpty()) {
                _state.value = State.Error("Failed to decode FLAC: $path")
                return@launch
            }

            pcmBuffer = pcm
            playbackFramePosition = 0

            Log.i(TAG, "Decoded ${pcm.size} samples, " +
                    "${info.totalSamples} frames, " +
                    "${info.durationSec}s, " +
                    "${info.sampleRate}Hz/${info.channels}ch/${info.bitsPerSample}bit")

            _state.value = State.Ready
            onReady?.invoke()
        }
    }

    /**
     * ギャップレス用: プリバッファ済みPCMに即座に切り替え。
     * 出力スレッドを止めずにバッファを差し替える。
     */
    fun switchToPreloadedBuffer(
        pcm: FloatArray,
        sampleRate: Int,
        bitsPerSample: Int,
        channels: Int
    ) {
        // 現在の再生を一時停止せずにバッファを差し替え
        pcmBuffer = pcm
        pcmSampleRate = sampleRate
        pcmBitsPerSample = bitsPerSample
        pcmChannels = channels
        playbackFramePosition = 0

        // リサンプラーが有効でレートが変わった場合、再初期化
        if (resamplerConfig.enabled && resamplerConfig.targetSampleRate != sampleRate) {
            initResampler(sampleRate.toDouble(), resamplerConfig.targetSampleRate.toDouble(), channels)
        }

        Log.i(TAG, "Switched to preloaded buffer: ${pcm.size} samples, " +
                "${sampleRate}Hz/${channels}ch/${bitsPerSample}bit")
    }

    fun playAaudio(
        sampleRate: Int = pcmSampleRate,
        bitDepth: Int = pcmBitsPerSample,
        channels: Int = pcmChannels
    ) {
        if (_state.value != State.Ready && _state.value != State.Paused) {
            Log.w(TAG, "Cannot play in state: ${_state.value}")
            return
        }

        outputMode = OutputMode.AAUDIO

        val opened = NativeEngine.aaudioOpen(sampleRate, bitDepth, channels)
        if (!opened) {
            _state.value = State.Error("Failed to open AAudio")
            return
        }

        if (resamplerConfig.enabled && sampleRate != pcmSampleRate) {
            initResampler(pcmSampleRate.toDouble(), sampleRate.toDouble(), channels)
        }

        NativeEngine.aaudioStart()
        _state.value = State.Playing

        startPlaybackLoop()
        startTelemetryCollection()
    }

    fun playUsb(config: UsbOutputConfig) {
        if (_state.value != State.Ready && _state.value != State.Paused) {
            Log.w(TAG, "Cannot play USB in state: ${_state.value}")
            return
        }

        outputMode = OutputMode.USB

        val opened = NativeEngine.usbOpen(
            config.fd,
            config.interfaceNum,
            config.altSetting,
            config.endpointAddress,
            config.frameBytes,
            config.sampleRate,
            config.bitDepth,
            config.channels
        )
        if (!opened) {
            _state.value = State.Error("Failed to open USB audio")
            return
        }

        NativeEngine.usbSetupRingBuffer(RING_BUFFER_FRAMES, config.channels)

        if (resamplerConfig.enabled && config.sampleRate != pcmSampleRate) {
            initResampler(pcmSampleRate.toDouble(), config.sampleRate.toDouble(), config.channels)
        }

        NativeEngine.usbStart()
        _state.value = State.Playing

        startPlaybackLoop()
        startTelemetryCollection()
    }

    fun pause() {
        if (_state.value != State.Playing) return

        playbackJob?.cancel()
        playbackJob = null

        when (outputMode) {
            OutputMode.AAUDIO -> NativeEngine.aaudioStop()
            OutputMode.USB -> NativeEngine.usbStop()
            else -> {}
        }

        _state.value = State.Paused
    }

    fun resume() {
        if (_state.value != State.Paused) return

        when (outputMode) {
            OutputMode.AAUDIO -> {
                NativeEngine.aaudioStart()
                startPlaybackLoop()
                startTelemetryCollection()
            }
            OutputMode.USB -> {
                NativeEngine.usbStart()
                startPlaybackLoop()
                startTelemetryCollection()
            }
            else -> {}
        }

        _state.value = State.Playing
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        telemetryJob?.cancel()
        telemetryJob = null

        when (outputMode) {
            OutputMode.AAUDIO -> {
                NativeEngine.aaudioStop()
                NativeEngine.aaudioClose()
            }
            OutputMode.USB -> {
                NativeEngine.usbStop()
                NativeEngine.usbClose()
            }
            else -> {}
        }

        releaseResampler()
        playbackFramePosition = 0
        outputMode = OutputMode.NONE
        _state.value = State.Ready
    }

    fun unload() {
        stop()
        pcmBuffer = floatArrayOf()
        currentTrack = null
        _state.value = State.Idle
    }

    fun seekToFrame(frame: Int) {
        val maxFrame = pcmBuffer.size / pcmChannels
        playbackFramePosition = frame.coerceIn(0, maxFrame)
    }

    fun seekToPercent(percent: Float) {
        val maxFrame = pcmBuffer.size / pcmChannels
        playbackFramePosition = ((percent / 100f) * maxFrame).toInt()
    }

    fun currentPositionSec(): Double {
        return if (pcmSampleRate > 0) playbackFramePosition.toDouble() / pcmSampleRate else 0.0
    }

    fun durationSec(): Double {
        val totalFrames = pcmBuffer.size / pcmChannels
        return if (pcmSampleRate > 0) totalFrames.toDouble() / pcmSampleRate else 0.0
    }

    fun setResamplerConfig(config: ResamplerConfig) {
        resamplerConfig = config
    }

    fun getCurrentTrack(): Track? = currentTrack

    // ========================================================================
    // 内部
    // ========================================================================

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = engineScope.launch {
            playbackLoop()
        }
    }

    private suspend fun playbackLoop() {
        val channels = pcmChannels
        val totalFrames = pcmBuffer.size / channels

        while (isActive && playbackFramePosition < totalFrames) {
            val remainingFrames = totalFrames - playbackFramePosition
            val chunkFrames = min(WRITE_CHUNK_FRAMES, remainingFrames)

            // ギャップレス: 残りフレームが閾値を下回ったらプリバッファ通知
            if (remainingFrames <= GaplessController.PRELOAD_THRESHOLD_FRAMES) {
                gaplessController?.onPlaybackPositionChanged(remainingFrames)
            }

            val chunkOffset = playbackFramePosition * channels
            val chunkSize = chunkFrames * channels
            val chunk = pcmBuffer.copyOfRange(chunkOffset, chunkOffset + chunkSize)

            val outputChunk = if (resamplerConfig.enabled && resamplerHandle != 0L) {
                resampleChunk(chunk, chunkFrames, channels)
            } else {
                chunk
            }

            when (outputMode) {
                OutputMode.AAUDIO -> {
                    NativeEngine.aaudioWrite(outputChunk, 0, chunkFrames, channels)
                }
                OutputMode.USB -> {
                    NativeEngine.usbWriteRing(outputChunk, 0, chunkFrames, channels)
                }
                else -> {}
            }

            playbackFramePosition += chunkFrames
            yield()
        }

        // トラック終了
        if (playbackFramePosition >= totalFrames) {
            Log.i(TAG, "Track finished, checking gapless")
            val continued = gaplessController?.onCurrentTrackFinished() ?: false
            if (!continued) {
                Log.i(TAG, "Queue ended, stopping")
                stop()
            }
        }
    }

    private fun initResampler(inRate: Double, outRate: Double, channels: Int) {
        releaseResampler()

        resamplerHandle = when (resamplerConfig.engine) {
            ResamplerEngine.SOXR -> {
                NativeEngine.soxrCreate(
                    inRate, outRate, channels,
                    resamplerConfig.soxrPhase,
                    resamplerConfig.soxrBandwidth,
                    resamplerConfig.soxrQuality
                )
            }
            ResamplerEngine.R8BRAIN -> {
                NativeEngine.r8brainCreate(
                    inRate, outRate, channels,
                    resamplerConfig.r8brainPreset,
                    0.0, 0.0, true, 0.0
                )
            }
        }

        Log.i(TAG, "Resampler initialized: $inRate -> $outRate, engine=${resamplerConfig.engine}")
    }

    private fun releaseResampler() {
        if (resamplerHandle != 0L) {
            when (resamplerConfig.engine) {
                ResamplerEngine.SOXR -> NativeEngine.soxrDestroy(resamplerHandle)
                ResamplerEngine.R8BRAIN -> NativeEngine.r8brainDestroy(resamplerHandle)
            }
            resamplerHandle = 0
        }
    }

    private fun resampleChunk(
        input: FloatArray,
        inputFrames: Int,
        channels: Int
    ): FloatArray {
        if (resamplerHandle == 0L) return input

        val rateRatio = resamplerConfig.targetSampleRate.toDouble() / pcmSampleRate
        val maxOutputFrames = (inputFrames * rateRatio * 1.2).toInt() + 1024
        val output = FloatArray(maxOutputFrames * channels)

        val done = when (resamplerConfig.engine) {
            ResamplerEngine.SOXR -> {
                NativeEngine.soxrProcess(
                    resamplerHandle,
                    input, 0, inputFrames,
                    output, maxOutputFrames
                )
            }
            ResamplerEngine.R8BRAIN -> {
                NativeEngine.r8brainProcess(
                    resamplerHandle,
                    input, 0, inputFrames,
                    output, maxOutputFrames
                )
            }
        }

        return if (done > 0) {
            output.copyOf(done * channels)
        } else {
            input
        }
    }

    private fun startTelemetryCollection() {
        telemetryJob?.cancel()
        telemetryJob = engineScope.launch {
            while (isActive && _state.value == State.Playing) {
                val t = when (outputMode) {
                    OutputMode.AAUDIO -> {
                        val arr = NativeEngine.aaudioGetTelemetry()
                        if (arr.size >= 5) {
                            Telemetry(
                                jitterRmsUs = arr[0].toDouble(),
                                xrunCount = arr[1].toInt(),
                                isBitPerfect = arr[2] == 1.0f,
                                isMmap = arr[3] == 1.0f,
                                actualSampleRate = arr[4].toInt()
                            )
                        } else Telemetry()
                    }
                    OutputMode.USB -> {
                        val arr = NativeEngine.usbGetTelemetry()
                        if (arr.size >= 4) {
                            Telemetry(
                                jitterRmsUs = arr[0].toDouble(),
                                xrunCount = arr[1].toInt(),
                                isBitPerfect = arr[2] == 1.0f,
                                actualSampleRate = arr[3].toInt()
                            )
                        } else Telemetry()
                    }
                    else -> Telemetry()
                }
                _telemetry.value = t
                delay(500)
            }
        }
    }

    data class UsbOutputConfig(
        val fd: Int,
        val interfaceNum: Int,
        val altSetting: Int,
        val endpointAddress: Int,
        val frameBytes: Int,
        val sampleRate: Int,
        val bitDepth: Int,
        val channels: Int
    )
}
