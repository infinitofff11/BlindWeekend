package com.example.blindweekend.data.repository

import com.example.blindweekend.data.db.*
import com.example.blindweekend.data.model.*
import com.example.blindweekend.network.BlindWeekendApi
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 活动点仓库 - 管理活动点数据的获取和缓存
 */
class SpotRepository(
    private val api: BlindWeekendApi,
    private val gson: Gson = Gson()
) {
    /**
     * 获取所有启用的活动点（优先网络，失败返回缓存）
     */
    suspend fun getActiveSpots(city: String? = null): Result<List<ActivitySpot>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getActiveSpots(city)
                if (response.isSuccessful && response.body()?.code == 200) {
                    val spots = response.body()?.data ?: emptyList()
                    Result.success(spots)
                } else {
                    Result.failure(Exception("获取活动点失败: ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 根据标签和消费水平筛选活动点
     */
    suspend fun filterSpots(
        spots: List<ActivitySpot>,
        tags: List<String>? = null,
        consumeLevel: String? = null
    ): List<ActivitySpot> {
        return withContext(Dispatchers.Default) {
            var result = spots

            // 按类型标签筛选（交集：包含任一指定标签即可）
            if (!tags.isNullOrEmpty()) {
                result = result.filter { spot ->
                    spot.getTypeTagList().any { it in tags }
                }
            }

            // 按消费水平筛选
            if (!consumeLevel.isNullOrEmpty()) {
                result = result.filter { it.consumeLevel == consumeLevel }
            }

            result
        }
    }

    /**
     * 随机选取N个活动点
     */
    fun pickRandom(spots: List<ActivitySpot>, count: Int): List<ActivitySpot> {
        return if (spots.size <= count) {
            spots.toList()
        } else {
            spots.shuffled().take(count)
        }
    }
}
