package com.miyamizu.player.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 再生履歴DAO。
 */
@Dao
interface PlaybackHistoryDao {

    @Query("SELECT * FROM playback_history ORDER BY played_at DESC")
    fun getAll(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY played_at DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE track_id = :trackId ORDER BY played_at DESC LIMIT 1")
    suspend fun getLastPlay(trackId: Long): PlaybackHistoryEntity?

    @Insert
    suspend fun insert(history: PlaybackHistoryEntity): Long

    @Query("DELETE FROM playback_history WHERE played_at < :before")
    suspend fun deleteOlderThan(before: java.time.Instant)

    @Query("DELETE FROM playback_history")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM playback_history")
    suspend fun count(): Int
}
