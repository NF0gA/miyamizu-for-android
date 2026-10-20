package com.miyamizu.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.miyamizu.player.data.Track

/**
 * ライブラリ画面。
 * アルバム一覧（グリッド表示）またはトラック検索結果を表示。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun LibraryScreen(
    onNavigateToPlayer: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAlbum: (List<Track>) -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val selectedAlbum by viewModel.selectedAlbum.collectAsStateWithLifecycle()
    val albumTracks by viewModel.albumTracks.collectAsStateWithLifecycle()
    val albumCoverPath by viewModel.albumCoverPath.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isScanned by viewModel.isScanned.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            if (selectedAlbum != null) {
                // アルバム詳細画面のTopBar
                TopAppBar(
                    title = {
                        Text(
                            selectedAlbum ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearAlbumSelection() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { onPlayAlbum(albumTracks) }) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play album"
                            )
                        }
                    }
                )
            } else {
                // ライブラリ一覧画面のTopBar
                TopAppBar(
                    title = { Text("Library") },
                    actions = {
                        IconButton(onClick = { viewModel.scanLibrary() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                selectedAlbum != null -> {
                    AlbumDetailContent(
                        tracks = albumTracks,
                        coverPath = albumCoverPath,
                        onTrackClick = { track ->
                            onPlayTrack(track, albumTracks)
                        }
                    )
                }
                !isScanned -> {
                    EmptyLibraryContent(
                        onScan = { viewModel.scanLibrary() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    if (isSearching) {
                        SearchResultsContent(
                            results = searchResults,
                            onTrackClick = { track ->
                                onPlayTrack(track, searchResults)
                            }
                        )
                    } else {
                        AlbumGridContent(
                            albums = albums,
                            onAlbumClick = { viewModel.selectAlbum(it) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AlbumGridContent(
    albums: List<String>,
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (albums.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No albums found.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(albums, key = { it }) { album ->
                AlbumCard(
                    album = album,
                    onClick = { onAlbumClick(album) }
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AlbumCard(
    album: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverPath = MusicLibrary.getAlbumCoverPath(album)

    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        // アルバムカバー
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (coverPath != null) {
                GlideImage(
                    model = coverPath,
                    contentDescription = album,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // アルバム名
        Text(
            text = album,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AlbumDetailContent(
    tracks: List<Track>,
    coverPath: String?,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        // カバーアートヘッダー
        item {
            if (coverPath != null) {
                com.bumptech.glide.integration.compose.GlideImage(
                    model = coverPath,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .aspectRatio(1f)
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // トラック一覧
        items(tracks, key = { it.id }) { track ->
            TrackListItem(
                track = track,
                onClick = { onTrackClick(track) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun TrackListItem(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = {
            Text(
                text = "${track.trackNumber}. ${track.title}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = track.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Text(
                text = formatDuration(track.durationSec),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = modifier.clickable { onClick() }
    )
}

@Composable
private fun SearchResultsContent(
    results: List<Track>,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No results found.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize()) {
            items(results, key = { it.id }) { track ->
                TrackListItem(
                    track = track,
                    onClick = { onTrackClick(track) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EmptyLibraryContent(
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.LibraryMusic,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Library not scanned",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Tap the button below to scan your music folder for FLAC files.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onScan) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan Library")
        }
    }
}

private fun formatDuration(seconds: Double): String {
    val totalSec = seconds.toInt()
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%d:%02d", min, sec)
}
