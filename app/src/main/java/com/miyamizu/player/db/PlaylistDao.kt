package com.miyamizu.player.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * プレイリストDAO。
 */
@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY sort_order, name")
    fun getAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET name = :name, updated_at = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, name: String, updatedAt: java.time.Instant)

    @Delete
    suspend fun delete(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun count(): Int

    // ===== プレイリスト内トラック操作 =====

    @Query("SELECT * FROM playlist_tracks WHERE playlist_id = :playlistId ORDER BY position")
    suspend fun getTracksForPlaylist(playlistId: Long): List<PlaylistTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: PlaylistTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId AND track_id = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Long)

    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int?

    @Query("UPDATE playlists SET track_count = :count, updated_at = :updatedAt WHERE id = :playlistId")
    suspend fun updateTrackCount(playlistId: Long, count: Int, updatedAt: java.time.Instant)

    /**
     * トラックをプレイリストに追加してトラック数を更新（トランザクション）。
     */
    @Transaction
    suspend fun addTrackToPlaylist(playlistId: Long, track: com.miyamizu.player.data.Track) {
        val maxPos = getMaxPosition(playlistId) ?: -1
        val entity = PlaylistTrackEntity(
            playlistId = playlistId,
            trackId = track.id,
            filePath = track.filePath,
            position = maxPos + 1
        )
        insertTrack(entity)

        // トラック数を更新
        val currentTracks = getTracksForPlaylist(playlistId)
        updateTrackCount(playlistId, currentTracks.size, java.time.Instant.now())
    }

    /**
     * 複数トラックをプレイリストに追加（トランザクション）。
     */
    @Transaction
    suspend fun addTracksToPlaylist(playlistId: Long, tracks: List<com.miyamizu.player.data.Track>) {
        var position = (getMaxPosition(playlistId) ?: -1) + 1
        val entities = tracks.map { track ->
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = track.id,
                filePath = track.filePath,
                position = position++
            )
        }
        insertTracks(entities)

        val currentTracks = getTracksForPlaylist(playlistId)
        updateTrackCount(playlistId, currentTracks.size, java.time.Instant.now())
    }

    /**
     * トラックを削除してトラック数を更新（トランザクション）。
     */
    @Transaction
    suspend fun removeTrackAndUpdateCount(playlistId: Long, trackId: Long) {
        removeTrackFromPlaylist(playlistId, trackId)
        val currentTracks = getTracksForPlaylist(playlistId)
        updateTrackCount(playlistId, currentTracks.size, java.time.Instant.now())
    }
}
