package com.miyamizu.player

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.miyamizu.player.favorites.FavoriteScreen
import com.miyamizu.player.history.HistoryScreen
import com.miyamizu.player.playlist.PlaylistScreen
import com.miyamizu.player.settings.SettingsScreen
import com.miyamizu.player.ui.library.LibraryScreen
import com.miyamizu.player.ui.player.PlayerScreen
import com.miyamizu.player.ui.queue.QueueScreen

/**
 * Miyamizuアプリのナビゲーション構成。
 * BottomNavigation + NavHostで各画面を管理。
 *
 * 画面一覧:
 * - library: ライブラリ（アルバム一覧・トラック一覧）
 * - playlists: プレイリスト
 * - favorites: お気に入り
 * - history: 再生履歴
 * - player: プレーヤー画面（フルスクリーン）
 * - queue: 再生キュー（Playerから遷移）
 * - settings: 設定画面（Playerから遷移）
 */
@Composable
fun MiyamizuApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { innerPadding ->
        MiyamizuNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

/**
 * ナビゲーション先定義。
 */
sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null
) {
    object Library : Screen("library", "Library", Icons.Default.LibraryMusic)
    object Playlists : Screen("playlists", "Playlists", Icons.Default.PlaylistPlay)
    object Favorites : Screen("favorites", "Favorites", Icons.Default.Favorite)
    object History : Screen("history", "History", Icons.Default.History)
    object Player : Screen("player", "Now Playing")
    object Queue : Screen("queue", "Queue")
    object Settings : Screen("settings", "Settings")

    companion object {
        val bottomNavItems = listOf(Library, Playlists, Favorites, History)
    }
}

@Composable
private fun BottomNavigationBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Player/Queue/Settings画面ではBottomNavを非表示
    val hideBottomNavRoutes = listOf(Screen.Player.route, Screen.Queue.route, Screen.Settings.route)
    val shouldShow = currentDestination?.route !in hideBottomNavRoutes

    if (shouldShow) {
        NavigationBar {
            Screen.bottomNavItems.forEach { screen ->
                NavigationBarItem(
                    icon = { screen.icon?.let { Icon(it, contentDescription = screen.title) } },
                    label = { Text(screen.title) },
                    selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                    onClick = {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MiyamizuNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Library.route,
        modifier = modifier
    ) {
        // ===== ライブラリ画面 =====
        composable(Screen.Library.route) {
            LibraryScreen(
                onNavigateToPlayer = {
                    navController.navigate(Screen.Player.route)
                },
                onPlayTrack = { track, trackList ->
                    // TODO: Playerにトラックとキューを設定して再生
                    navController.navigate(Screen.Player.route)
                },
                onPlayAlbum = { tracks ->
                    // TODO: Playerにアルバムキューを設定して再生
                    navController.navigate(Screen.Player.route)
                }
            )
        }

        // ===== プレイリスト画面 =====
        composable(Screen.Playlists.route) {
            PlaylistScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPlayPlaylist = { tracks ->
                    // TODO: Playerにキューを設定して再生開始
                    navController.navigate(Screen.Player.route)
                }
            )
        }

        // ===== お気に入り画面 =====
        composable(Screen.Favorites.route) {
            FavoriteScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onTrackClick = { track ->
                    // TODO: Playerにトラックを設定して再生
                    navController.navigate(Screen.Player.route)
                }
            )
        }

        // ===== 再生履歴画面 =====
        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onTrackClick = { filePath ->
                    // TODO: filePathからTrackを引いて再生
                    navController.navigate(Screen.Player.route)
                }
            )
        }

        // ===== プレーヤー画面 =====
        composable(Screen.Player.route) {
            PlayerScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToQueue = {
                    navController.navigate(Screen.Queue.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        // ===== キュー画面 =====
        composable(Screen.Queue.route) {
            QueueScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onTrackClick = { track ->
                    // トラックをタップして再生位置にジャンプ
                    // Player画面に戻る
                    navController.popBackStack()
                }
            )
        }

        // ===== 設定画面 =====
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

/**
 * ライブラリ画面の仮実装（フェーズCで本実装）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryPlaceholderScreen(
    onNavigateToPlayer: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(
                    "Library Screen",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Album and track listing will be implemented in Phase C.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onNavigateToPlayer) {
                    Text("Open Player")
                }
            }
        }
    }
}
