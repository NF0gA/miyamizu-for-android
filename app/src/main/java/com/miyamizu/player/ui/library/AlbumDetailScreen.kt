package com.miyamizu.player.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miyamizu.player.data.Track

/**
 * アルバム詳細画面。
 * アルバムカバー・メタデータ・トラック一覧を表示し、再生キューとして設定可能。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumName: String,
    onNavigateBack: () -> Unit,
    onPlayAlbum: (List<Track>, Int) -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val selectedAlbum by viewModel.selectedAlbum.collectAsStateWithLifecycle()
    val tracks by viewModel.albumTracks.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()

    // アルバム情報を取得
    val albumItem = remember(albumName, albums) {
        albums.find { it.name == albumName }
    }

    // アルバムが選択されていなければロード
    LaunchedEffect(albumName) {
        if (selectedAlbum != albumName) {
            viewModel.selectAlbum(albumName)
        }
    }

    val coverBitmap = remember(albumItem) {
        albumItem?.let { viewModel.getCoverBitmap(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = albumName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearAlbumSelection()
                        onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // アルバムヘッダー
            item {
                AlbumHeader(
                    albumItem = albumItem,
                    coverBitmap = coverBitmap,
                    trackCount = tracks.size,
                    totalDuration = calculateTotalDuration(tracks),
                    onPlayAll = {
                        if (tracks.isNotEmpty()) {
                            onPlayAlbum(tracks, 0)
                        }
                    },
                    onShufflePlay = {
                        if (tracks.isNotEmpty()) {
                            val shuffled = tracks.shuffled()
                            onPlayAlbum(shuffled, 0)
                        }
                    }
                )
            }

            // トラック一覧
            itemsIndexed(
                items = tracks,
                key = { _, track -> track.id }
            ) { index, track ->
                TrackListItem(
                    track = track,
                    trackNumber = index + 1,
                    onClick = {
                        onPlayAlbum(tracks, index)
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AlbumHeader(
    albumItem: LibraryViewModel.AlbumItem?,
    coverBitmap: android.graphics.Bitmap?,
    trackCount: Int,
    totalDuration: String,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // カバーアート
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap.asImageBitmap(),
                    contentDescription = albumItem?.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // アルバム名
        Text(
            text = albumItem?.name ?: "",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        // アーティスト
        Text(
            text = albumItem?.artist ?: "Unknown Artist",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        // メタ情報
        Text(
            text = "$trackCount tracks ・ $totalDuration",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 再生ボタン
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onPlayAll,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play")
            }

            OutlinedButton(
                onClick = onShufflePlay,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Shuffle")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun TrackListItem(
    track: Track,
    trackNumber: Int,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Row {
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = " ・ ${formatDuration(track.durationSec)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        },
        leadingContent = {
            Text(
                text = "$trackNumber",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp)
            )
        },
        modifier = Modifier.clickable { onClick() }
    )
}

private fun calculateTotalDuration(tracks: List<Track>): String {
    val totalSec = tracks.sumOf { it.durationSec.toInt() }
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    return if (hours > 0) {
        String.format("%dh %02dm", hours, minutes)
    } else {
        String.format("%dm", minutes)
    }
}

private fun formatDuration(seconds: Double): String {
    val min = (seconds / 60).toInt()
    val sec = (seconds % 60).toInt()
    return String.format("%d:%02d", min, sec)
}
