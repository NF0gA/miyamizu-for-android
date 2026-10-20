package com.miyamizu.player.playlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miyamizu.player.data.Track
import com.miyamizu.player.db.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * プレイリスト画面のViewModel。
 */
class PlaylistViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlaylistRepository(application)

    // ===== プレイリスト一覧 =====
    val playlists: StateFlow<List<PlaylistEntity>> = repository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ===== 選択中のプレイリスト =====
    private val _selectedPlaylist = MutableStateFlow<PlaylistEntity?>(null)
    val selectedPlaylist: StateFlow<PlaylistEntity?> = _selectedPlaylist

    // ===== プレイリスト内のトラック（Track型に変換） =====
    // 実際にはPlaylistTrackEntityからMusicLibraryのTrackを引く必要がある
    // フェーズBでは簡易実装
    private val _playlistTracks = MutableStateFlow<List<Track>>(emptyList())
    val playlistTracks: StateFlow<List<Track>> = _playlistTracks

    // ===== ダイアログ状態 =====
    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog

    private val _showRenameDialog = MutableStateFlow(false)
    val showRenameDialog: StateFlow<Boolean> = _showRenameDialog

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // ===== 操作 =====

    fun createPlaylist(name: String) {
        if (name.isBlank()) {
            _errorMessage.value = "Playlist name cannot be empty"
            return
        }
        viewModelScope.launch {
            repository.createPlaylist(name)
            _showCreateDialog.value = false
        }
    }

    fun renamePlaylist(id: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renamePlaylist(id, newName)
            _showRenameDialog.value = false
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
            if (_selectedPlaylist.value?.id == id) {
                _selectedPlaylist.value = null
            }
        }
    }

    fun selectPlaylist(playlist: PlaylistEntity) {
        _selectedPlaylist.value = playlist
        // TODO: プレイリスト内トラックをロード
        _playlistTracks.value = emptyList()
    }

    fun clearSelection() {
        _selectedPlaylist.value = null
        _playlistTracks.value = emptyList()
    }

    // ===== ダイアログ制御 =====

    fun showCreateDialog() { _showCreateDialog.value = true }
    fun hideCreateDialog() { _showCreateDialog.value = false }

    fun showRenameDialog() { _showRenameDialog.value = true }
    fun hideRenameDialog() { _showRenameDialog.value = false }

    fun clearError() { _errorMessage.value = null }
}
