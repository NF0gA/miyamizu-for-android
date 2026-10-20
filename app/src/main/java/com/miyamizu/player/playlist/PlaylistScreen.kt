package com.miyamizu.player.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miyamizu.player.db.PlaylistEntity

/**
 * プレイリスト一覧・詳細画面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    onNavigateBack: () -> Unit,
    onPlayPlaylist: (List<com.miyamizu.player.data.Track>) -> Unit,
    viewModel: PlaylistViewModel = viewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
    val playlistTracks by viewModel.playlistTracks.collectAsStateWithLifecycle()
    val showCreate by viewModel.showCreateDialog.collectAsStateWithLifecycle()
    val showRename by viewModel.showRenameDialog.collectAsStateWithLifecycle()
    val error by viewModel.errorMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedPlaylist != null) selectedPlaylist!!.name
                        else "Playlists"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedPlaylist != null) viewModel.clearSelection()
                        else onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (selectedPlaylist != null) {
                        // プレイリスト詳細画面のアクション
                        IconButton(onClick = {
                            onPlayPlaylist(playlistTracks)
                        }) {
                            Icon(Icons.Default.PlaylistPlay, contentDescription = "Play")
                        }
                        var menuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.showRenameDialog()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = {
                                    menuExpanded = false
                                    selectedPlaylist?.let {
                                        viewModel.deletePlaylist(it.id)
                                        viewModel.clearSelection()
                                    }
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedPlaylist == null) {
                FloatingActionButton(onClick = { viewModel.showCreateDialog() }) {
                    Icon(Icons.Default.Add, contentDescription = "Create Playlist")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (selectedPlaylist == null) {
            // プレイリスト一覧
            PlaylistListContent(
                playlists = playlists,
                onPlaylistClick = { viewModel.selectPlaylist(it) },
                modifier = Modifier.padding(padding)
            )
        } else {
            // プレイリスト詳細（トラック一覧）
            PlaylistDetailContent(
                tracks = playlistTracks,
                onTrackClick = { /* TODO: 再生 */ },
                modifier = Modifier.padding(padding)
            )
        }
    }

    // 作成ダイアログ
    if (showCreate) {
        PlaylistNameDialog(
            title = "New Playlist",
            onConfirm = { viewModel.createPlaylist(it) },
            onDismiss = { viewModel.hideCreateDialog() }
        )
    }

    // リネームダイアログ
    if (showRename) {
        PlaylistNameDialog(
            title = "Rename Playlist",
            initialName = selectedPlaylist?.name ?: "",
            onConfirm = { name ->
                selectedPlaylist?.let { viewModel.renamePlaylist(it.id, name) }
            },
            onDismiss = { viewModel.hideRenameDialog() }
        )
    }
}

@Composable
private fun PlaylistListContent(
    playlists: List<PlaylistEntity>,
    onPlaylistClick: (PlaylistEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (playlists.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No playlists yet.\nTap + to create one.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize()) {
            items(playlists, key = { it.id }) { playlist ->
                ListItem(
                    headlineContent = {
                        Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text("${playlist.trackCount} tracks")
                    },
                    leadingContent = {
                        Icon(Icons.Default.PlaylistPlay, contentDescription = null)
                    },
                    modifier = Modifier.clickable { onPlaylistClick(playlist) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PlaylistDetailContent(
    tracks: List<com.miyamizu.player.data.Track>,
    onTrackClick: (com.miyamizu.player.data.Track) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tracks.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "This playlist is empty.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize()) {
            items(tracks, key = { it.id }) { track ->
                ListItem(
                    headlineContent = {
                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text("${track.artist} - ${track.album}")
                    },
                    modifier = Modifier.clickable { onTrackClick(track) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    initialName: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
