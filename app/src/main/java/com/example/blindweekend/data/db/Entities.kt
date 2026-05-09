package com.example.blindweekend.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户偏好 - Room实体
 */
@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey val userId: Long,
    val consumeLevel: String = "low",
    val activityRadius: Int = 5,
    val defaultGroupSize: String = "1",
    val interestTags: String = "", // JSON数组字符串
    val city: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 缓存的方案 - 用于离线查看
 */
@Entity(tableName = "cached_plans")
data class CachedPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val planName: String?,
    val planData: String, // JSON格式的完整方案数据
    val isFavorited: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
