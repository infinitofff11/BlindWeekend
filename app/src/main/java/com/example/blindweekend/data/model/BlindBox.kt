package com.example.blindweekend.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/**
 * 盲盒数据模型
 */
@Parcelize
data class BlindBox(
    val id: Long,
    @SerializedName("publisher_id") val publisherId: Long,
    @SerializedName("plan_id") val planId: Long?,
    val title: String,
    @SerializedName("summary_text") val summaryText: String?,
    @SerializedName("mood_text") val moodText: String?,
    val district: String?,
    val city: String?,
    @SerializedName("activity_date") val activityDate: String?,
    @SerializedName("activity_time_period") val activityTimePeriod: String?,
    @SerializedName("required_count") val requiredCount: Int,
    @SerializedName("current_count") val currentCount: Int = 0,
    @SerializedName("activity_type_tags") val activityTypeTags: String?,
    val status: String,
    @SerializedName("view_count") val viewCount: Int?
) : Parcelable {
    fun getTypeTagList(): List<String> {
        return activityTypeTags?.let { tags ->
            try {
                com.google.gson.Gson().fromJson(tags, Array<String>::class.java).toList()
            } catch (e: Exception) {
                tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        } ?: emptyList()
    }

    /** 是否还可以申请 */
    fun isOpen(): Boolean = status == "open" && currentCount < requiredCount

    /** 获取剩余名额 */
    fun getRemainingSlots(): Int = (requiredCount - currentCount).coerceAtLeast(0)
}

/**
 * 方案环节详情（含活动点信息，用于盲盒详情展示）
 */
@Parcelize
data class PlanItemDetail(
    @SerializedName("item_order") val itemOrder: Int,
    @SerializedName("start_time") val startTime: String?,
    @SerializedName("end_time") val endTime: String?,
    @SerializedName("spot_name") val spotName: String,
    @SerializedName("spot_address") val spotAddress: String?,
    @SerializedName("spot_cover_image") val spotCoverImage: String?,
    @SerializedName("recommend_duration") val recommendDuration: Int?,
    val note: String?
) : Parcelable

/**
 * 盲盒详情响应（后端 BlindBoxDetailDTO）
 */
data class BlindBoxDetailResponse(
    val blindBox: BlindBox,
    @SerializedName("plan_items") val planItems: List<PlanItemDetail>?,
    val publisher: Boolean,
    val participated: Boolean
)

/**
 * 用户生成的方案
 */
data class UserPlan(
    val id: Long,
    val userId: Long,
    @SerializedName("template_id") val templateId: Long?,
    val planName: String?,
    @SerializedName("plan_date") val planDate: String?,
    @SerializedName("is_favorited") val isFavorited: Boolean = false,
    val status: String = "draft"
)

/**
 * 方案环节（某个活动点在方案中的安排）
 */
data class PlanItem(
    val id: Long,
    @SerializedName("plan_id") val planId: Long,
    @SerializedName("spot_id") val spotId: Long,
    @SerializedName("item_order") val itemOrder: Int,
    @SerializedName("start_time") val startTime: String?,
    @SerializedName("end_time") val endTime: String?,
    val note: String?,
    /** 关联的活动点详情 */
    var spot: ActivitySpot? = null
)
