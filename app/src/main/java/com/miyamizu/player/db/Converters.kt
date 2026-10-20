package com.miyamizu.player.db

import androidx.room.TypeConverter
import java.time.Instant

/**
 * Room用型コンバーター。
 */
class Converters {

    @TypeConverter
    fun fromTimestamp(value: Long?): java.time.Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun instantToTimestamp(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }
}
