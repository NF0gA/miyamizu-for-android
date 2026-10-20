package com.miyamizu.player.playback

import android.util.Log
import com.miyamizu.player.data.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * RAM再生キュー。
 * 再生予定のトラックを事前にデコードしてメモリに展開し、
 * 再生時にはファイルI/Oなしで即座に出力できるようにする。
 *
 * Miyamizuの設計理念に基づき、再生中のCPU負荷を最小化し、
 * 出力スレッドをデコードから完全に分離する。
 */
class RamPlaybackQueue(private val engine: PlaybackEngine) {

    companion object {
        private const val TAG = "RamPlaybackQueue"
        // 最大RAMキューサイズ（トラック数）
        private const val MAX_QUEUE_SIZE = 10
        // 同時デコード最大数
        private const val MAX_CONCURRENT_DECODE = 2
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * RAM上のデコード済みトラック。
     */
    data class RamTrack(
        val track: Track,
        val pcmBuffer: FloatArray,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val channels: Int,
        val decodeTimeMs: Long
    ) {
        val totalFrames: Int get() = pcmBuffer.size / channels
        val durationSec: Double get() = if (sampleRate > 0) totalFrames.toDouble() / sampleRate else 0.0

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as RamTrack
            return track.id == other.track.id
        }

        override fun hashCode(): Int = track.id.hashCode()
    }

    // ===== キュー =====
    private val ramQueue = ConcurrentLinkedQueue<RamTrack>()
    private val pendingTracks = ConcurrentLinkedQueue<Track>()

    // ===== 状態 =====
    sealed class State {
        object Idle : State()
        object Processing : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    // ===== 統計 =====
    data class Stats(
        val queuedTracks: Int = 0,
        val decodedTracks: Int = 0,
        val totalDecodeTimeMs: Long = 0,
        val totalPcmSamples: Long = 0,
        val memoryEstimateMb: Double = 0.0
    )

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats

    private val decodeCounter = AtomicInteger(0)
    private val totalDecodeTime = AtomicInteger(0)

    // ===== 制御 =====
    @Volatile
    private var isEnabled = false

    private var processJob: Job? = null

    // ===== リスナー =====
    interface Listener {
        fun onTrackDecoded(track: Track, ramTrack: RamTrack)
        fun onQueueFull()
        fun onError(track: Track, error: String)
    }

    private var listener: Listener? = null

    fun setListener(l: Listener) {
        listener = l
    }

    // ========================================================================
    // 公開API
    // ========================================================================

    /**
     * RAM再生を有効化。
     */
    fun enable() {
        isEnabled = true
        Log.i(TAG, "RAM playback enabled")
    }

    /**
     * RAM再生を無効化。
     */
    fun disable() {
        isEnabled = false
        clear()
        Log.i(TAG, "RAM playback disabled")
    }

    fun isEnabled(): Boolean = isEnabled

    /**
     * トラックをキューに追加して事前デコード。
     */
    fun enqueue(track: Track) {
        if (!isEnabled) return

        // 既にRAMにある場合はスキップ
        if (ramQueue.any { it.track.id == track.id }) {
            Log.d(TAG, "Track already in RAM queue: ${track.title}")
            return
        }

        // キューが満杯なら先頭を削除
        if (ramQueue.size >= MAX_QUEUE_SIZE) {
            val removed = ramQueue.poll()
            removed?.let {
                Log.d(TAG, "Removed from RAM queue: ${it.track.title}")
            }
        }

        pendingTracks.add(track)
        startProcessing()
    }

    /**
     * 複数トラックをキューに追加。
     */
    fun enqueueAll(tracks: List<Track>) {
        if (!isEnabled) return
        tracks.forEach { enqueue(it) }
    }

    /**
     * RAMキューから次のトラックを取得。
     * まだデコードされていない場合はnull。
     */
    fun dequeue(): RamTrack? {
        return ramQueue.poll()
    }

    /**
     * 指定トラックがRAM上にあるか確認。
     */
    fun isInRam(track: Track): Boolean {
        return ramQueue.any { it.track.id == track.id }
    }

    /**
     * 指定トラックのRAMデータを取得。
     */
    fun getRamTrack(track: Track): RamTrack? {
        return ramQueue.find { it.track.id == track.id }
    }

    /**
     * RAMキューをクリア。
     */
    fun clear() {
        pendingTracks.clear()
        ramQueue.clear()
        decodeCounter.set(0)
        totalDecodeTime.set(0)
        updateStats()
        _state.value = State.Idle
    }

    /**
     * キューの先頭を覗く（削除しない）。
     */
    fun peek(): RamTrack? = ramQueue.peek()

    /**
     * RAMキューのサイズ。
     */
    fun size(): Int = ramQueue.size

    /**
     * クリーンアップ。
     */
    fun cleanup() {
        clear()
        processJob?.cancel()
        scope.cancel()
    }

    // ========================================================================
    // 内部
    // ========================================================================

    private fun startProcessing() {
        if (processJob?.isActive == true) return

        processJob = scope.launch {
            _state.value = State.Processing

            while (isActive && pendingTracks.isNotEmpty()) {
                // 同時デコード数を制限
                val activeDecodes = scope.coroutineContext[Job]?.children?.count { it.isActive } ?: 0
                if (activeDecodes >= MAX_CONCURRENT_DECODE) {
                    delay(100)
                    continue
                }

                val track = pendingTracks.poll() ?: break

                launch {
                    decodeTrack(track)
                }
            }

            _state.value = if (pendingTracks.isEmpty()) State.Idle else State.Processing
        }
    }

    private suspend fun decodeTrack(track: Track) = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            Log.i(TAG, "Decoding to RAM: ${track.title}")

            // FLACメタデータ取得
            val info = NativeEngine.getFlacInfo(track.filePath)
            if (info == null) {
                Log.e(TAG, "Failed to get FLAC info: ${track.filePath}")
                listener?.onError(track, "Failed to read FLAC info")
                return@withContext
            }

            // デコード
            val pcm = NativeEngine.decodeFlacToRam(
                track.filePath,
                track.cueStartSample,
                track.cueEndSample
            )

            if (pcm.isEmpty()) {
                Log.e(TAG, "Failed to decode FLAC: ${track.filePath}")
                listener?.onError(track, "Decode failed")
                return@withContext
            }

            val decodeTime = System.currentTimeMillis() - startTime

            val ramTrack = RamTrack(
                track = track,
                pcmBuffer = pcm,
                sampleRate = info.sampleRate,
                bitsPerSample = info.bitsPerSample,
                channels = info.channels,
                decodeTimeMs = decodeTime
            )

            ramQueue.add(ramTrack)
            decodeCounter.incrementAndGet()
            totalDecodeTime.addAndGet(decodeTime.toInt())
            updateStats()

            Log.i(TAG, "Decoded to RAM: ${track.title}, " +
                    "${pcm.size} samples, ${decodeTime}ms")

            listener?.onTrackDecoded(track, ramTrack)

        } catch (e: Exception) {
            Log.e(TAG, "Decode error for ${track.title}", e)
            listener?.onError(track, e.message ?: "Unknown error")
        }
    }

    private fun updateStats() {
        val decoded = decodeCounter.get()
        val totalTime = totalDecodeTime.get()
        val totalSamples = ramQueue.sumOf { it.pcmBuffer.size.toLong() }
        // float = 4 bytes
        val memoryMb = totalSamples * 4.0 / (1024 * 1024)

        _stats.value = Stats(
            queuedTracks = pendingTracks.size,
            decodedTracks = decoded,
            totalDecodeTimeMs = totalTime.toLong(),
            totalPcmSamples = totalSamples,
            memoryEstimateMb = memoryMb
        )
    }
}
