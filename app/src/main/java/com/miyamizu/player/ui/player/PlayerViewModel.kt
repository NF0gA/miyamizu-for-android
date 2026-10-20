package com.miyamizu.player.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miyamizu.player.data.Track
import com.miyamizu.player.db.PlaylistRepository
import com.miyamizu.player.playback.MiyamizuPlayer
import com.miyamizu.player.playback.PlaybackEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * プレーヤー画面のViewModel。
 * MiyamizuPlayerの状態を監視し、UIに提供する。
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val player: MiyamizuPlayer = TODO("Inject or create MiyamizuPlayer instance")
    private val playlistRepository = PlaylistRepository(application)

    // ===== 再生状態 =====
    val currentTrack: StateFlow<Track?> = player.currentTrackFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isPlaying: StateFlow<Boolean> = player.isPlayingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ===== 再生位置（1秒間隔で更新） =====
    private val _positionSec = MutableStateFlow(0.0)
    val positionSec: StateFlow<Double> = _positionSec

    private val _durationSec = MutableStateFlow(0.0)
    val durationSec: StateFlow<Double> = _durationSec

    // ===== テレメトリ =====
    val telemetry: StateFlow<PlaybackEngine.Telemetry> = player.telemetry
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackEngine.Telemetry())

    // ===== カバーアート =====
    private val _coverArt = MutableStateFlow<android.graphics.Bitmap?>(null)
    val coverArt: StateFlow<android.graphics.Bitmap?> = _coverArt

    // ===== お気に入り =====
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

    // ===== テレメトリ表示設定 =====
    private val settingsRepo = com.miyamizu.player.settings.SettingsRepository(application)
    val telemetryEnabled: StateFlow<Boolean> = settingsRepo.telemetryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // ===== 位置更新ジョブ =====
    private var positionUpdateJob: Job? = null

    init {
        // トラック変更時にお気に入り状態を確認
        viewModelScope.launch {
            currentTrack.filterNotNull().collect { track ->
                _isFavorite.value = playlistRepository.isFavorite(track.id)
                loadCoverArt(track)
                _durationSec.value = track.durationSec
            }
        }

        // 再生状態に応じて位置更新を開始/停止
        viewModelScope.launch {
            isPlaying.collect { playing ->
                if (playing) {
                    startPositionUpdates()
                } else {
                    stopPositionUpdates()
                }
            }
        }
    }

    // ===== 再生制御 =====

    fun togglePlayPause() {
        if (isPlaying.value) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun previous() {
        player.seekToPrevious()
    }

    fun next() {
        player.seekToNext()
    }

    fun hasPrevious(): Boolean = player.hasPreviousMediaItem()
    fun hasNext(): Boolean = player.hasNextMediaItem()

    // ===== シーク =====

    fun seekToPercent(percent: Float) {
        player.seekTo((percent * _durationSec.value * 10).toLong())
    }

    // ===== お気に入り =====

    fun toggleFavorite() {
        val track = currentTrack.value ?: return
        viewModelScope.launch {
            if (_isFavorite.value) {
                playlistRepository.removeFavorite(track.id)
                _isFavorite.value = false
            } else {
                playlistRepository.addFavorite(track)
                _isFavorite.value = true
            }
        }
    }

    // ===== カバーアート =====

    private fun loadCoverArt(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            _coverArt.value = com.miyamizu.player.playback.NativeEngine.getCoverArt(track.filePath)
        }
    }

    // ===== 位置更新 =====

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                _positionSec.value = player.currentPosition / 1000.0
                delay(1000)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPositionUpdates()
    }
}
