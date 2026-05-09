package com.example.blindweekend.data.model

import com.google.gson.annotations.SerializedName

/**
 * 统一API响应
 */
data class ApiResponse<T>(
    val code: Int = 200,
    val message: String? = null,
    val data: T? = null,
    val timestamp: Long? = null
)

/**
 * 分页响应
 */
data class PageResponse<T>(
    @SerializedName("page_num") val pageNum: Long,
    @SerializedName("page_size") val pageSize: Long,
    @SerializedName("total") val total: Long,
    @SerializedName("pages") val pages: Long,
    @SerializedName("list") val list: List<T>
)

/**
 * 活动点数据模型
 */
data class ActivitySpot(
    val id: Long,
    val name: String,
    @SerializedName("type_tags") val typeTags: String?,
    @SerializedName("consume_per_person") val consumePerPerson: Double?,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val city: String?,
    val district: String?,
    @SerializedName("suggest_time_period") val suggestTimePeriod: String?,
    @SerializedName("suitable_capacity") val suitableCapacity: String?,
    @SerializedName("cover_image_url") val coverImageUrl: String?,
    val description: String?,
    @SerializedName("recommend_duration") val recommendDuration: Int?,
    @SerializedName("consume_level") val consumeLevel: String?,
    val status: Int?
) {
    /** 解析类型标签为列表 */
    fun getTypeTagList(): List<String> {
        return typeTags?.let { tags ->
            try {
                // 尝试解析JSON数组
                com.google.gson.Gson().fromJson(tags, Array<String>::class.java).toList()
            } catch (e: Exception) {
                // 降级：按逗号分割
                tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        } ?: emptyList()
    }

    /** 格式化消费金额 */
    fun getFormattedPrice(): String {
        return consumePerPerson?.let { "¥${String.format("%.0f", it)}" } ?: "免费"
    }
}
