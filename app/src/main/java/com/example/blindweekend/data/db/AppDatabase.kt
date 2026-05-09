package com.example.blindweekend.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room 数据库定义
 */
@Database(
    entities = [
        UserPreferenceEntity::class,
        CachedPlanEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun preferenceDao(): PreferenceDao
    abstract fun cachedPlanDao(): CachedPlanDao

    companion object {
        const val DATABASE_NAME = "blind_weekend_db"
    }
}
