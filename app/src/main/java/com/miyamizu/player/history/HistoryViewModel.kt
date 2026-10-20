package com.miyamizu.player.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miyamizu.player.db.PlaybackHistoryEntity
import com.miyamizu.player.db.PlaylistRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 再生履歴画面のViewModel。
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlaylistRepository(application)

    // ===== 再生履歴（最近100件） =====
    val recentHistory: StateFlow<List<PlaybackHistoryEntity>> =
        repository.getRecentHistory(100)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _showClearConfirm = MutableStateFlow(false)
    val showClearConfirm: StateFlow<Boolean> = _showClearConfirm

    // ===== 操作 =====

    fun clearOldHistory(days: Int = 30) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.clearOldHistory(days)
            _isLoading.value = false
            _showClearConfirm.value = false
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.clearAllHistory()
            _isLoading.value = false
            _showClearConfirm.value = false
        }
    }

    fun showClearConfirm() { _showClearConfirm.value = true }
    fun hideClearConfirm() { _showClearConfirm.value = false }
}
