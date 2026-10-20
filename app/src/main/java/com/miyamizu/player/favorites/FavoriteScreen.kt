package com.miyamizu.player.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miyamizu.player.data.Track

/**
 * お気に入り画面。
 * お気に入り登録したトラックの一覧を表示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteScreen(
    onNavigateBack: () -> Unit,
    onTrackClick: (Track) -> Unit,
    viewModel: FavoriteViewModel = viewModel()
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favorites") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (favorites.isEmpty()) {
                EmptyFavoritesContent(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // FavoriteEntityからTrackへの変換は実際にはMusicLibrary経由で行う
                // フェーズBでは簡易表示
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(favorites, key = { it.trackId }) { favorite ->
                        FavoriteItem(
                            favorite = favorite,
                            onClick = {
                                // TODO: MusicLibraryからTrackを引いて再生
                            },
                            onRemove = {
                                viewModel.removeFavorite(favorite.trackId)
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFavoritesContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No favorites yet",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Tap the heart icon on any track to add it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FavoriteItem(
    favorite: com.miyamizu.player.db.FavoriteEntity,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            // ファイルパスから曲名を抽出して表示
            val fileName = java.io.File(favorite.filePath).nameWithoutExtension
            Text(
                text = fileName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = java.io.File(favorite.filePath).parent ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Remove from favorites") },
                        onClick = {
                            showMenu = false
                            onRemove()
                        }
                    )
                }
            }
        },
        modifier = Modifier.clickable { onClick() }
    )
}
