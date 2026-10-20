package com.miyamizu.player.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miyamizu.player.db.PlaybackHistoryEntity
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 再生履歴画面。
 * 最再生したトラックの一覧を表示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onTrackClick: (String) -> Unit,  // filePath
    viewModel: HistoryViewModel = viewModel()
) {
    val history by viewModel.recentHistory.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val showClearConfirm by viewModel.showClearConfirm.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playback History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { viewModel.showClearConfirm() }) {
                            Icon(
                                imageVector = Icons.Default.ClearAll,
                                contentDescription = "Clear history"
                            )
                        }
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
            } else if (history.isEmpty()) {
                EmptyHistoryContent(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(history, key = { it.id }) { entry ->
                        HistoryItem(
                            entry = entry,
                            onClick = { onTrackClick(entry.filePath) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // クリア確認ダイアログ
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.hideClearConfirm() },
            title = { Text("Clear History") },
            text = { Text("Delete all playback history? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearAllHistory() }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideClearConfirm() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyHistoryContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No playback history",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Tracks you play will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HistoryItem(
    entry: PlaybackHistoryEntity,
    onClick: () -> Unit
) {
    val fileName = File(entry.filePath).nameWithoutExtension
    val formatter = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
    }
    val dateStr = formatter.format(entry.playedAt)

    val durationStr = if (entry.durationPlayedSec > 0) {
        val min = (entry.durationPlayedSec / 60).toInt()
        val sec = (entry.durationPlayedSec % 60).toInt()
        String.format("%d:%02d", min, sec)
    } else ""

    ListItem(
        headlineContent = {
            Text(
                text = fileName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Row {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (durationStr.isNotEmpty()) {
                    Text(
                        text = " - $durationStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (entry.completed) {
                    Text(
                        text = " - completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier.clickable { onClick() }
    )
}
