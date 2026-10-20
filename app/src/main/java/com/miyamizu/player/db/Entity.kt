package com.miyamizu.player.db

import androidx.room.*
import java.time.Instant

/**
 * プレイリストエンティティ。
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now(),

    @ColumnInfo(name = "track_count")
    val trackCount: Int = 0,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0  // 0=custom, 1=title, 2=artist, 3=album, 4=track_number
)

/**
 * プレイリスト内のトラック（多対多の中間テーブル）。
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlist_id", "track_id"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaylistTrackEntity(
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,

    @ColumnInfo(name = "track_id")
    val trackId: Long,  // Track.id (filePath.hashCode().toLong())

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "position")
    val position: Int,  // プレイリスト内の順序

    @ColumnInfo(name = "added_at")
    val addedAt: Instant = Instant.now()
)

/**
 * お気に入りエンティティ。
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    @ColumnInfo(name = "track_id")
    val trackId: Long,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "added_at")
    val addedAt: Instant = Instant.now()
)

/**
 * 再生履歴エンティティ。
 */
@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "track_id")
    val trackId: Long,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "played_at")
    val playedAt: Instant = Instant.now(),

    @ColumnInfo(name = "duration_played_sec")
    val durationPlayedSec: Double = 0.0,

    @ColumnInfo(name = "completed")
    val completed: Boolean = false
)
