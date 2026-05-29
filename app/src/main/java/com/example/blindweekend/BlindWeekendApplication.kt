package com.example.blindweekend

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.blindweekend.data.db.AppDatabase

/**
 * Application类 - 全局初始化
 */
class BlindWeekendApplication : Application(), ImageLoaderFactory {

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

    /**
     * 创建 Coil ImageLoader 单例，配置内存缓存和磁盘缓存
     * 这样头像加载后会被缓存到磁盘，切换页面/重启应用后仍能显示
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // 使用 25% 可用内存
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50 * 1024 * 1024) // 50MB 磁盘缓存
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
