package com.miyamizu.player.playback

import android.os.Looper
import androidx.media3.common.*
import androidx.media3.common.util.Clock
import androidx.media3.common.util.ListenerSet
import com.miyamizu.player.data.Track
import com.miyamizu.player.db.PlaylistRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Miyamizu専用のPlayer実装。
 * MediaSession互換だが、内部は自前のPlaybackEngine + GaplessController + RamPlaybackQueueを使う。
 *
 * フェーズB統合:
 * - GaplessController: 次曲プリバッファ・無縫継切り替え
 * - RamPlaybackQueue: トラック事前デコード
 * - PlaybackHistory: 再生履歴自動記録
 */
class MiyamizuPlayer(
    private val engine: PlaybackEngine,
    private val gaplessController: GaplessController,
    private val ramQueue: RamPlaybackQueue,
    private val playlistRepository: PlaylistRepository
) : Player {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val listeners = ListenerSet<Player.Listener>(Looper.getMainLooper(), Clock.DEFAULT) { _, _ -> }

    // ===== 状態Flow =====
    private val _isPlaying = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlaying

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrackFlow: StateFlow<Track?> = _currentTrack

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)

    // ===== 再生履 =====
    private var trackStartTime: Long = 0
    private var trackDurationPlayed: Double = 0.0

    // ===== テレメトリ =====
    val telemetry: StateFlow<PlaybackEngine.Telemetry> = engine.telemetry

    init {
        // PlaybackEngineの状態を監視
        scope.launch {
            engine.state.collect { state ->
                val isPlayingNow = state is PlaybackEngine.State.Playing
                _isPlaying.value = isPlayingNow

                when (state) {
                    is PlaybackEngine.State.Playing -> {
                        _playbackState.value = Player.STATE_READY
                        if (trackStartTime == 0L) {
                            trackStartTime = System.currentTimeMillis()
                        }
                    }
                    is PlaybackEngine.State.Ready -> {
                        _playbackState.value = Player.STATE_READY
                    }
                    is PlaybackEngine.State.Idle -> {
                        _playbackState.value = Player.STATE_IDLE
                        recordHistory(completed = false)
                    }
                    is PlaybackEngine.State.Error -> {
                        _playbackState.value = Player.STATE_IDLE
                        recordHistory(completed = false)
                    }
                    else -> {}
                }

                listeners.sendEvent(Player.EVENT_PLAYBACK_STATE_CHANGED) { listener ->
                    listener.onPlaybackStateChanged(_playbackState.value)
                }
                listeners.sendEvent(Player.EVENT_IS_PLAYING_CHANGED) { listener ->
                    listener.onIsPlayingChanged(isPlayingNow)
                }
            }
        }

        // 在トラック監視
        scope.launch {
            _currentTrack.filterNotNull().collect { track ->
                trackStartTime = System.currentTimeMillis()
                trackDurationPlayed = 0.0
            }
        }

        // テレメトリ監視（xrun検出時のログ）
        scope.launch {
            telemetry.collect { t ->
                if (t.xrunCount > 0) {
                    android.util.Log.w("MiyamizuPlayer", "xrun detected: count=${t.xrunCount}, jitter=${t.jitterRmsUs}us")
                }
            }
        }

        // GaplessControllerリスナー
        gaplessController.setListener(object : GaplessController.Listener {
            override fun onTrackChanged(track: Track, index: Int) {
                _currentTrack.value = track
                trackStartTime = System.currentTimeMillis()
                trackDurationPlayed = 0.0
                listeners.sendEvent(Player.EVENT_MEDIA_ITEM_TRANSITION) { listener ->
                    listener.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
                }
            }

            override fun onQueueEnded() {
                _isPlaying.value = false
                _playbackState.value = Player.STATE_ENDED
                recordHistory(completed = true)
                listeners.sendEvent(Player.EVENT_PLAYBACK_STATE_CHANGED) { listener ->
                    listener.onPlaybackStateChanged(Player.STATE_ENDED)
                }
            }
        })
    }

    // ===== 再生制御 =====

    /**
     * トラックをロードして再生開始。
     */
    fun playTrack(track: Track, queue: List<Track> = listOf(track)) {
        _currentTrack.value = track

        // RAM再生が有効でRAMキューにあれば使用
        val ramTrack = ramQueue.getRamTrack(track)
        if (ramQueue.isEnabled() && ramTrack != null) {
            // RAMから直接再生
            engine.switchToPreloadedBuffer(
                ramTrack.pcmBuffer,
                ramTrack.sampleRate,
                ramTrack.bitsPerSample,
                ramTrack.channels
            )
            _playbackState.value = Player.STATE_READY
            play()
        } else {
            // 通常ロード
            engine.loadTrack(track) {
                _playbackState.value = Player.STATE_READY
                play()
            }
        }

        // キューを設定
        val startIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        gaplessController.setQueue(queue, startIndex)

        // RAM再生が有効なら次曲をプリバッファ
        if (ramQueue.isEnabled()) {
            val nextIndex = startIndex + 1
            if (nextIndex < queue.size) {
                ramQueue.enqueue(queue[nextIndex])
            }
        }
    }

    /**
     * キューを設定して再生。
     */
    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        playTrack(tracks[startIndex], tracks)
    }

    // ===== Playerインターフェース実装 =====

    override fun getApplicationLooper(): Looper = Looper.getMainLooper()
    override fun getPlaybackState(): Int = _playbackState.value
    override fun getPlayWhenReady(): Boolean = _isPlaying.value

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) play() else pause()
    }

    override fun play() {
        if (_playbackState.value == Player.STATE_IDLE) return
        engine.resume()
        _isPlaying.value = true
    }

    override fun pause() {
        engine.pause()
        _isPlaying.value = false
        // 一時停止時に再生時間を記録
        if (trackStartTime > 0) {
            trackDurationPlayed += (System.currentTimeMillis() - trackStartTime) / 1000.0
            trackStartTime = 0
        }
    }

    override fun stop() {
        recordHistory(completed = false)
        engine.stop()
        _isPlaying.value = false
        _playbackState.value = Player.STATE_IDLE
    }

    override fun release() {
        recordHistory(completed = false)
        scope.cancel()
        engine.unload()
        gaplessController.cleanup()
        ramQueue.cleanup()
    }

    override fun isPlaying(): Boolean = _isPlaying.value
    override fun isLoading(): Boolean = engine.state.value is PlaybackEngine.State.Decoding
    override fun isCommandAvailable(command: Int): Boolean = true
    override fun availableCommands(): Player.Commands = Player.Commands.Builder().addAll(
        Player.COMMAND_PLAY_PAUSE,
        Player.COMMAND_PREPARE,
        Player.COMMAND_STOP,
        Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SET_SHUFFLE_MODE,
        Player.COMMAND_SET_REPEAT_MODE
    ).build()

    override fun prepare() {}
    override fun getCurrentManifest(): Any? = null
    override fun getCurrentTimeline(): Timeline = Timeline.EMPTY
    override fun getMediaItemCount(): Int = gaplessController.getQueueSize()
    override fun getCurrentMediaItemIndex(): Int = gaplessController.getCurrentIndex()
    override fun getDuration(): Long = (_currentTrack.value?.durationSec?.times(1000))?.toLong() ?: C.TIME_UNSET
    override fun getCurrentPosition(): Long = (engine.currentPositionSec() * 1000).toLong()
    override fun getBufferedPosition(): Long = getCurrentPosition()
    override fun getBufferedPercentage(): Int = 100
    override fun getTotalBufferedDuration(): Long = 0
    override fun isCurrentWindowDynamic(): Boolean = false
    override fun isCurrentWindowLive(): Boolean = false
    override fun isCurrentWindowSeekable(): Boolean = true
    override fun getCurrentLiveOffset(): Long = C.TIME_UNSET
    override fun getContentDuration(): Long = getDuration()
    override fun getContentPosition(): Long = getCurrentPosition()
    override fun getContentBufferedPosition(): Long = getBufferedPosition()
    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {}
    override fun setMediaItem(mediaItem: MediaItem) {}
    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {}
    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {}
    override fun addMediaItem(mediaItem: MediaItem) {}
    override fun addMediaItem(index: Int, mediaItem: MediaItem) {}
    override fun addMediaItems(mediaItems: List<MediaItem>) {}
    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {}
    override fun removeMediaItem(index: Int) {}
    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {}
    override fun moveMediaItem(currentIndex: Int, newIndex: Int) {}
    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {}
    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {}
    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>) {}
    override fun clearMediaItems() {}
    override fun getCurrentMediaItem(): MediaItem? = null
    override fun getMediaItemAt(index: Int): MediaItem? = null
    override fun getMediaItemTransitionReason(): Int = Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
    override fun getCurrentCues(): CueGroup = CueGroup(emptyList(), 0)
    override fun getDeviceInfo(): DeviceInfo = DeviceInfo.EMPTY
    override fun getDeviceVolume(): Int = 100
    override fun isDeviceMuted(): Boolean = false
    override fun setDeviceVolume(volume: Int) {}
    override fun setDeviceVolume(volume: Int, flags: Int) {}
    override fun decreaseDeviceVolume() {}
    override fun decreaseDeviceVolume(flags: Int) {}
    override fun increaseDeviceVolume() {}
    override fun increaseDeviceVolume(flags: Int) {}
    override fun setDeviceMuted(muted: Boolean) {}
    override fun setDeviceMuted(muted: Boolean, flags: Int) {}
    override fun setVideoSurface(surface: android.view.Surface?) {}
    override fun setVideoSurfaceHolder(surfaceHolder: android.view.SurfaceHolder?) {}
    override fun clearVideoSurfaceHolder(surfaceHolder: android.view.SurfaceHolder?) {}
    override fun setVideoSurfaceView(surfaceView: android.view.SurfaceView?) {}
    override fun clearVideoSurfaceView(surfaceView: android.view.SurfaceView?) {}
    override fun setVideoTextureView(textureView: android.view.TextureView?) {}
    override fun clearVideoTextureView(textureView: android.view.TextureView?) {}
    override fun getVideoSize(): VideoSize = VideoSize.UNKNOWN
    override fun getSurfaceSize(): Size = Size.UNKNOWN
    override fun getCurrentTracks(): Tracks = Tracks.EMPTY
    override fun getTrackSelectionParameters(): TrackSelectionParameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {}
    override fun getMediaMetadata(): MediaMetadata = _currentTrack.value?.let {
        MediaMetadata.Builder()
            .setTitle(it.title)
            .setArtist(it.artist)
            .setAlbumTitle(it.album)
            .build()
    } ?: MediaMetadata.EMPTY
    override fun getPlaylistMetadata(): MediaMetadata = MediaMetadata.EMPTY
    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {}
    override fun getRepeatMode(): Int = Player.REPEAT_MODE_OFF
    override fun setRepeatMode(repeatMode: Int) {}
    override fun getShuffleModeEnabled(): Boolean = false
    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {}
    override fun seekTo(positionMs: Long) {
        val frame = ((positionMs / 1000.0) * (_currentTrack.value?.sampleRate ?: 44100)).toInt()
        engine.seekToFrame(frame)
    }
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {}
    override fun seekBack() {
        val current = engine.currentPositionSec()
        val newPos = (current - 10).coerceAtLeast(0.0)
        engine.seekToFrame((newPos * (_currentTrack.value?.sampleRate ?: 44100)).toInt())
    }
    override fun seekForward() {
        val current = engine.currentPositionSec()
        val duration = _currentTrack.value?.durationSec ?: 0.0
        val newPos = (current + 10).coerceAtMost(duration)
        engine.seekToFrame((newPos * (_currentTrack.value?.sampleRate ?: 44100)).toInt())
    }
    override fun hasPreviousMediaItem(): Boolean = gaplessController.getCurrentIndex() > 0
    override fun seekToPrevious() {
        gaplessController.previousTrack()
    }
    override fun seekToPreviousMediaItem() {}
    override fun hasNextMediaItem(): Boolean = gaplessController.getCurrentIndex() < gaplessController.getQueueSize() - 1
    override fun seekToNext() {
        gaplessController.nextTrack()
    }
    override fun seekToNextMediaItem() {}
    override fun getMaxSeekToPreviousPosition(): Long = 3000
    override fun setPlaybackSpeed(speed: Float) {}
    override fun setPlaybackSpeed(speed: Float, pitch: Float) {}
    override fun getAudioComponent(): Player.AudioComponent? = null
    override fun getVideoComponent(): Player.VideoComponent? = null
    override fun getTextComponent(): Player.TextComponent? = null
    override fun getDeviceComponent(): Player.DeviceComponent? = null
    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
    }
    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
    }
    override fun setMediaItems(mediaItems: List<MediaItem>) {}
    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {}
    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {}
    override fun clearVideoSurface() {}
    override fun getPlayerError(): PlaybackException? = null
    override fun getRendererCount(): Int = 1
    override fun getRendererType(index: Int): Int = Player.RENDERER_TYPE_AUDIO
    override fun getVolume(): Float = 1.0f
    override fun setVolume(volume: Float) {}

    // ===== 内部 =====

    private fun recordHistory(completed: Boolean) {
        val track = _currentTrack.value ?: return
        if (trackStartTime > 0) {
            trackDurationPlayed += (System.currentTimeMillis() - trackStartTime) / 1000.0
            trackStartTime = 0
        }
        // 1秒以上再生した場合のみ記録
        if (trackDurationPlayed >= 1.0) {
            scope.launch(Dispatchers.IO) {
                playlistRepository.addHistoryEntry(track, trackDurationPlayed, completed)
            }
        }
    }
}
