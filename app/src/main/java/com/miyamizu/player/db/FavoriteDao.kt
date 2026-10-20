package com.miyamizu.player.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * お気に入りDAO。
 */
@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY added_at DESC")
    fun getAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE track_id = :trackId)")
    suspend fun isFavorite(trackId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(favorite: FavoriteEntity)

    @Delete
    suspend fun remove(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE track_id = :trackId")
    suspend fun removeByTrackId(trackId: Long)

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int
}
