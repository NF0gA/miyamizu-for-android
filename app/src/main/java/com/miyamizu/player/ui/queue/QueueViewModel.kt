package com.miyamizu.player.ui.queue

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miyamizu.player.data.Track
import com.miyamizu.player.playback.GaplessController
import com.miyamizu.player.playback.MiyamizuPlayer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 再生キュー画面のViewModel。
 * 現在の再生キューと再生中トラックを監視する。
 */
class QueueViewModel(application: Application) : AndroidViewModel(application) {

    private val player: MiyamizuPlayer = com.miyamizu.player.playback.MiyamizuPlayerProvider.getInstance(application)

    // ===== キュー状態 =====
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack

    init {
        // GaplessControllerからキュー情報を取得
        // 実際にはplayer経由でgaplessControllerにアクセスする
        // 現状は簡易実装
    }

    /**
     * キューを更新（外部から呼ばれる）。
     */
    fun updateQueue(tracks: List<Track>, currentIndex: Int) {
        _queue.value = tracks
        _currentIndex.value = currentIndex
        _currentTrack.value = tracks.getOrNull(currentIndex)
    }

    /**
     * 指定インデックスにジャンプ。
     */
    fun jumpToIndex(index: Int) {
        // player経由でジャンプ
        // player.jumpToQueueIndex(index)
    }

    /**
     * キューからトラックを削除。
     */
    fun removeFromQueue(index: Int) {
        val current = _queue.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _queue.value = current
            if (index < _currentIndex.value) {
                _currentIndex.value = _currentIndex.value - 1
            }
        }
    }

    /**
     * キューをクリア。
     */
    fun clearQueue() {
        _queue.value = emptyList()
        _currentIndex.value = 0
        _currentTrack.value = null
    }

    /**
     * キューをシャッフル。
     */
    fun shuffleQueue() {
        val current = _queue.value
        if (current.size < 2) return

        val currentTrack = current.getOrNull(_currentIndex.value)
        val remaining = current.toMutableList()
        remaining.removeAt(_currentIndex.value)
        remaining.shuffle()

        // 現在曲を先頭にして残りをシャッフル
        val newQueue = if (currentTrack != null) {
            listOf(currentTrack) + remaining
        } else remaining

        _queue.value = newQueue
        _currentIndex.value = 0
    }

    /**
     * キュー内のトラックを移動。
     */
    fun moveTrack(fromIndex: Int, toIndex: Int) {
        val current = _queue.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val track = current.removeAt(fromIndex)
            current.add(toIndex, track)
            _queue.value = current

            // currentIndexを更新
            when {
                fromIndex == _currentIndex.value -> _currentIndex.value = toIndex
                fromIndex < _currentIndex.value && toIndex >= _currentIndex.value ->
                    _currentIndex.value = _currentIndex.value - 1
                fromIndex > _currentIndex.value && toIndex <= _currentIndex.value ->
                    _currentIndex.value = _currentIndex.value + 1
            }
        }
    }
}
