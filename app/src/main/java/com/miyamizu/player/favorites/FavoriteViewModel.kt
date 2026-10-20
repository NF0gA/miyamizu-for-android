package com.miyamizu.player.favorites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miyamizu.player.data.Track
import com.miyamizu.player.db.FavoriteEntity
import com.miyamizu.player.db.PlaylistRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * お気に入り画面のViewModel。
 */
class FavoriteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlaylistRepository(application)

    val favorites: StateFlow<List<FavoriteEntity>> = repository.getAllFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /**
     * トラックがお気に入りかどうかを確認。
     */
    suspend fun isFavorite(trackId: Long): Boolean {
        return repository.isFavorite(trackId)
    }

    /**
     * お気に入りに追加。
     */
    fun addFavorite(track: Track) {
        viewModelScope.launch {
            repository.addFavorite(track)
        }
    }

    /**
     * お気に入りから削除。
     */
    fun removeFavorite(trackId: Long) {
        viewModelScope.launch {
            repository.removeFavorite(trackId)
        }
    }

    /**
     * お気に入り状態をトグル。
     */
    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            if (isFavorite(track.id)) {
                removeFavorite(track.id)
            } else {
                addFavorite(track)
            }
        }
    }
}
