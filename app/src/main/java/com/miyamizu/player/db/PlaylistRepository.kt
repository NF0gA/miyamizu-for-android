package com.miyamizu.player.db

import android.content.Context
import com.miyamizu.player.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * プレイリスト・お気に入り・再生履歴のリポジトリ。
 * Room DAOをラップしてアプリ層に提供する。
 */
class PlaylistRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val playlistDao = db.playlistDao()
    private val favoriteDao = db.favoriteDao()
    private val historyDao = db.playbackHistoryDao()

    // ===== プレイリスト =====

    fun getAllPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.getAll()

    suspend fun createPlaylist(name: String): Long {
        val playlist = PlaylistEntity(name = name)
        return playlistDao.insert(playlist)
    }

    suspend fun renamePlaylist(id: Long, newName: String) {
        playlistDao.rename(id, newName, Instant.now())
    }

    suspend fun deletePlaylist(id: Long) {
        playlistDao.deleteById(id)
    }

    /**
     * トラックをプレイリストに追加（トランザクション）。
     */
    suspend fun addTrackToPlaylist(playlistId: Long, track: Track) {
        playlistDao.addTrackToPlaylist(playlistId, track)
    }

    /**
     * 複数トラックをプレイリストに追加（トランザクション）。
     */
    suspend fun addTracksToPlaylist(playlistId: Long, tracks: List<Track>) {
        playlistDao.addTracksToPlaylist(playlistId, tracks)
    }

    /**
     * プレイリスト内のトラックを取得。
     */
    suspend fun getTracksForPlaylist(playlistId: Long): List<PlaylistTrackEntity> {
        return playlistDao.getTracksForPlaylist(playlistId)
    }

    /**
     * トラックをプレイリストから削除。
     */
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        playlistDao.removeTrackAndUpdateCount(playlistId, trackId)
    }

    /**
     * プレイリスをクリア。
     */
    suspend fun clearPlaylist(playlistId: Long) {
        playlistDao.clearPlaylistTracks(playlistId)
        playlistDao.updateTrackCount(playlistId, 0, Instant.now())
    }

    // ===== お気に入り =====

    fun getAllFavorites(): Flow<List<FavoriteEntity>> = favoriteDao.getAll()

    suspend fun addFavorite(track: Track) {
        val entity = FavoriteEntity(
            trackId = track.id,
            filePath = track.filePath
        )
        favoriteDao.add(entity)
    }

    suspend fun removeFavorite(trackId: Long) {
        favoriteDao.removeByTrackId(trackId)
    }

    suspend fun isFavorite(trackId: Long): Boolean {
        return favoriteDao.isFavorite(trackId)
    }

    // ===== 再生履歴 =====

    fun getRecentHistory(limit: Int = 100): Flow<List<PlaybackHistoryEntity>> =
        historyDao.getRecent(limit)

    suspend fun addHistoryEntry(track: Track, durationPlayedSec: Double, completed: Boolean) {
        val entity = PlaybackHistoryEntity(
            trackId = track.id,
            filePath = track.filePath,
            durationPlayedSec = durationPlayedSec,
            completed = completed
        )
        historyDao.insert(entity)
    }

    suspend fun clearOldHistory(olderThanDays: Int = 30) {
        val cutoff = Instant.now().minusSeconds(olderThanDays * 86400L)
        historyDao.deleteOlderThan(cutoff)
    }

    suspend fun clearAllHistory() {
        historyDao.clearAll()
    }
}
