package com.example.blindweekend

import android.app.Application
import com.example.blindweekend.data.db.AppDatabase

/**
 * Application类 - 全局初始化
 */
class BlindWeekendApplication : Application() {

    companion object {
        @Volatile
        private var _instance: BlindWeekendApplication? = null

        @Volatile
        private var database: AppDatabase? = null

        /** Application单例 */
        val instance: BlindWeekendApplication
            get() = _instance ?: throw IllegalStateException("Application未初始化")

        /** 获取Room数据库实例 */
        fun getDatabase(): AppDatabase {
            return database ?: synchronized(this) {
                database ?: androidx.room.Room.databaseBuilder(
                    instance.applicationContext,
                    AppDatabase::class.java,
                    AppDatabase.DATABASE_NAME
                ).build().also { database = it }
            }
        }

        internal fun init(instance: BlindWeekendApplication) {
            _instance = instance
        }
    }

    override fun onCreate() {
        super.onCreate()
        init(this)
    }
}
