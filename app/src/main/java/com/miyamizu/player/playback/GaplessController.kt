package com.miyamizu.player.playback

import android.util.Log
import com.miyamizu.player.data.Track
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * ギャップレス再生コントローラー。
 * 次曲を事前にデコードしてバッファしておき、現在曲の終了と同時に無縫継で切り替える。
 *
 * Miyamizuの設計理念に基づき、出力スレッドを止めずにPCMバッファの切り替えのみで
 * 曲間のギャップをゼロにする。
 */
class GaplessController(private val engine: PlaybackEngine) {

    companion object {
        private const val TAG = "GaplessController"
        // 次曲を何フレーム前からプリバッファ開始するか（44.1kHzで約1秒）
        private const val PRELOAD_THRESHOLD_FRAMES = 44100
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 再生ュー
    private val trackQueue = ArrayList<Track>()
    private var currentQueueIndex = 0

    // 次曲のプリバッファ済みPCM
    private var nextPcmBuffer: FloatArray? = null
    private var nextTrack: Track? = null
    private var nextTrackInfo: FlacInfoData? = null

    // プリロードジョブ
    private var preloadJob: Job? = null

    // リスナー
    interface Listener {
        fun onTrackChanged(track: Track, index: Int)
        fun onQueueEnded()
    }

    private var listener: Listener? = null

    fun setListener(l: Listener) {
        listener = l
    }

    // ===== キュー管理 =====

    /**
     * 再生キューを設定して最初のトラックをロード。
     */
    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        trackQueue.clear()
        trackQueue.addAll(tracks)
        currentQueueIndex = startIndex.coerceIn(0, trackQueue.size - 1)

        // 最初のトラックをロード
        val firstTrack = trackQueue.getOrNull(currentQueueIndex)
        if (firstTrack != null) {
            engine.loadTrack(firstTrack) {
                // ロード完了後、次曲をプリバッファ
                preloadNextTrack()
            }
        }
    }

    /**
     * キューに追加。
     */
    fun appendToQueue(track: Track) {
        trackQueue.add(track)
        // キューが1曲だけだった場合、プリバッファ開始
        if (trackQueue.size == 1) {
            currentQueueIndex = 0
            engine.loadTrack(track) { preloadNextTrack() }
        }
    }

    /**
     * キューをクリア。
     */
    fun clearQueue() {
        trackQueue.clear()
        currentQueueIndex = 0
        cancelPreload()
        nextPcmBuffer = null
        nextTrack = null
    }

    // ===== ナビゲーション =====

    /**
     * 次のトラックへ。
     */
    fun nextTrack() {
        val nextIndex = currentQueueIndex + 1
        if (nextIndex >= trackQueue.size) {
            listener?.onQueueEnded()
            return
        }

        // プリバッファ済みがあれば即切り替え
        if (nextPcmBuffer != null && nextTrack == trackQueue[nextIndex]) {
            switchToPreloadedTrack()
        } else {
            // プリバッファなし: 通常ロード
            currentQueueIndex = nextIndex
            val track = trackQueue[currentQueueIndex]
            engine.unload()
            engine.loadTrack(track) {
                startPlaybackIfReady()
                preloadNextTrack()
            }
        }
    }

    /**
     * 前のトラックへ。
     */
    fun previousTrack() {
        val prevIndex = currentQueueIndex - 1
        if (prevIndex < 0) {
            // 最初のトラックなら先頭にシーク
            engine.seekToFrame(0)
            return
        }

        cancelPreload()
        nextPcmBuffer = null
        currentQueueIndex = prevIndex
        val track = trackQueue[currentQueueIndex]
        engine.unload()
        engine.loadTrack(track) {
            startPlaybackIfReady()
            preloadNextTrack()
        }
    }

    /**
     * キュー内の特定インデックスジャンプ。
     */
    fun jumpToIndex(index: Int) {
        if (index !in trackQueue.indices) return

        cancelPreload()
        nextPcmBuffer = null
        currentQueueIndex = index
        val track = trackQueue[currentQueueIndex]
        engine.unload()
        engine.loadTrack(track) {
            startPlaybackIfReady()
            preloadNextTrack()
        }
    }

    // ===== ギャップレス制御 =====

    /**
     * 現在の再生位置を監視し、閾値を下回ったら次曲をプリバッファ。
     * PlaybackEngineの再生ループから呼ばれる。
     */
    fun onPlaybackPositionChanged(remainingFrames: Int) {
        if (remainingFrames <= PRELOAD_THRESHOLD_FRAMES && nextPcmBuffer == null) {
            preloadNextTrack()
        }
    }

    /**
     * 現在曲の終了を検出したときに呼ばれる。
     * PlaybackEngineの再生ループから呼ばれる。
     * @return true = 次曲に切り替えた、false = キュー終了
     */
    fun onCurrentTrackFinished(): Boolean {
        return if (nextPcmBuffer != null) {
            switchToPreloadedTrack()
            true
        } else {
            // プリバッファなし: 次曲をロード
            val nextIndex = currentQueueIndex + 1
            if (nextIndex < trackQueue.size) {
                currentQueueIndex = nextIndex
                val track = trackQueue[currentQueueIndex]
                engine.loadTrack(track) {
                    startPlaybackIfReady()
                    preloadNextTrack()
                }
                true
            } else {
                listener?.onQueueEnded()
                false
            }
        }
    }

    // ===== 内部 =====

    private fun preloadNextTrack() {
        val nextIndex = currentQueueIndex + 1
        if (nextIndex >= trackQueue.size) return

        // 既にプリバッファ済みならスキップ
        if (nextPcmBuffer != null && nextTrack == trackQueue[nextIndex]) return

        cancelPreload()

        val track = trackQueue[nextIndex]
        nextTrack = track

        preloadJob = scope.launch {
            Log.i(TAG, "Preloading next track: ${track.title}")

            // FLACメタデータ取得
            val info = NativeEngine.getFlacInfo(track.filePath)
            nextTrackInfo = info

            if (info == null) {
                Log.e(TAG, "Failed to get FLAC info for preload: ${track.filePath}")
                return@launch
            }

            // デコード
            val pcm = NativeEngine.decodeFlacToRam(
                track.filePath,
                track.cueStartSample,
                track.cueEndSample
            )

            if (pcm.isEmpty()) {
                Log.e(TAG, "Failed to decode next track: ${track.title}")
                return@launch
            }

            nextPcmBuffer = pcm
            Log.i(TAG, "Preloaded next track: ${track.title}, ${pcm.size} samples")
        }
    }

    private fun switchToPreloadedTrack() {
        val pcm = nextPcmBuffer ?: return
        val track = nextTrack ?: return
        val info = nextTrackInfo

        currentQueueIndex++

        // PlaybackEngineのPCMバッファを差し替え
        // 出力スレッドを止めに無縫継で切り替え
        engine.switchToPreloadedBuffer(pcm, info?.sampleRate ?: 44100,
            info?.bitsPerSample ?: 16, info?.channels ?: 2)

        listener?.onTrackChanged(track, currentQueueIndex)

        Log.i(TAG, "Gapless switch to: ${track.title}")

        // 次の次曲をプリバッファ
        nextPcmBuffer = null
        nextTrack = null
        preloadNextTrack()
    }

    private fun startPlaybackIfReady() {
        val state = engine.state.value
        if (state == PlaybackEngine.State.Ready || state == PlaybackEngine.State.Paused) {
            // 出力モードに応じて再生開始
            // 実際にはUIから呼ばれるため、ここでは準備完了を通知するだけでも可
        }
    }

    private fun cancelPreload() {
        preloadJob?.cancel()
        preloadJob = null
    }

    /**
     * 現在のキューインデックス。
     */
    fun getCurrentIndex(): Int = currentQueueIndex

    /**
     * キューのサイズ。
     */
    fun getQueueSize(): Int = trackQueue.size

    /**
     * 現在のトラク。
     */
    fun getCurrentTrack(): Track? = trackQueue.getOrNull(currentQueueIndex)

    /**
     * キュー全体。
     */
    fun getQueue(): List<Track> = trackQueue.toList()

    /**
     * クリーンアップ。
     */
    fun cleanup() {
        cancelPreload()
        scope.cancel()
        nextPcmBuffer = null
    }
}
