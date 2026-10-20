package com.miyamizu.player.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miyamizu.player.data.MusicLibrary
import com.miyamizu.player.data.Track
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ライブラリ画面のViewModel。
 * アルバム一覧・トラック一覧を管理する。
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // ===== アルバム一覧 =====
    private val _albums = MutableStateFlow<List<String>>(emptyList())
    val albums: StateFlow<List<String>> = _albums

    // ===== 選択中のアルバム =====
    private val _selectedAlbum = MutableStateFlow<String?>(null)
    val selectedAlbum: StateFlow<String?> = _selectedAlbum

    // ===== アルバム内トラック一覧 =====
    private val _albumTracks = MutableStateFlow<List<Track>>(emptyList())
    val albumTracks: StateFlow<List<Track>> = _albumTracks

    // ===== カバーアートパス =====
    private val _albumCoverPath = MutableStateFlow<String?>(null)
    val albumCoverPath: StateFlow<String?> = _albumCoverPath

    // ===== 検索 =====
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults: StateFlow<List<Track>> = _searchResults

    val isSearching: StateFlow<Boolean> = _searchQuery
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ===== スキャン状態 =====
    val isScanned: StateFlow<Boolean> = flow {
        emit(MusicLibrary.isLibraryScanned())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        // 既にスキャン済みならアルバム一覧を読み込む
        if (MusicLibrary.isLibraryScanned()) {
            loadAlbums()
        }
    }

    // ===== アルバム一覧 =====

    fun loadAlbums() {
        _albums.value = MusicLibrary.getAlbums()
    }

    // ===== アルバム選択 =====

    fun selectAlbum(album: String) {
        _selectedAlbum.value = album
        _albumTracks.value = MusicLibrary.getTracksByAlbum(album)
        _albumCoverPath.value = MusicLibrary.getAlbumCoverPath(album)
    }

    fun clearAlbumSelection() {
        _selectedAlbum.value = null
        _albumTracks.value = emptyList()
        _albumCoverPath.value = null
    }

    // ===== 検索 =====

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
        } else {
            _searchResults.value = MusicLibrary.search(query)
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    // ===== スキャン =====

    fun scanLibrary() {
        viewModelScope.launch {
            _isLoading.value = true
            MusicLibrary.scanDirectory(getApplication())
            loadAlbums()
            _isLoading.value = false
        }
    }
}
