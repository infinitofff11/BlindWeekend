package com.example.blindweekend.data.model

import com.google.gson.annotations.SerializedName

/**
 * 用户数据模型
 */
data class User(
    val id: Long,
    val phone: String?,
    val nickname: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    val city: String?,
    val status: Int?
)

/**
 * 登录/注册响应 — 包含 JWT Token 和用户信息
 */
data class LoginResponse(
    val token: String,
    val user: User
)

/**
 * 用户偏好
 */
data class UserPreference(
    val id: Long? = null,
    val userId: Long,
    @SerializedName("consume_level") val consumeLevel: String = "low",
    @SerializedName("activity_radius") val activityRadius: Int = 5,
    @SerializedName("default_group_size") val defaultGroupSize: String = "1"
)

/**
 * 方案模板数据模型
 */
data class PlanTemplate(
    val id: Long,
    val name: String,
    val description: String?,
    @SerializedName("total_duration") val totalDuration: Int?,
    @SerializedName("consume_level") val consumeLevel: String?,
    @SerializedName("theme_type") val themeType: String?,
    @SerializedName("sort_order") val sortOrder: Int?,
    val status: Int?
)

/**
 * 模板时段配置
 */
data class TemplateSegment(
    val id: Long,
    @SerializedName("template_id") val templateId: Long,
    @SerializedName("segment_order") val segmentOrder: Int,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("activity_types") val activityTypes: String?,
    @SerializedName("segment_name") val segmentName: String
) {
    fun getActivityTypeList(): List<String> {
        return activityTypes?.let { tags ->
            try {
                com.google.gson.Gson().fromJson(tags, Array<String>::class.java).toList()
            } catch (e: Exception) {
                tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        } ?: emptyList()
    }
}

// ==================== 方案生成相关 ====================

/**
 * 生成的完整方案
 */
data class GeneratedPlan(
    val templateName: String,
    val templateDescription: String? = null,
    val items: List<GeneratedPlanItem>
)

/**
 * 方案中的单个环节
 */
data class GeneratedPlanItem(
    val order: Int,
    val startTime: String,
    val endTime: String,
    val segmentName: String,
    val spot: ActivitySpot
)
