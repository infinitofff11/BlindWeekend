package com.example.blindweekend.data.model

import com.google.gson.annotations.SerializedName

/**
 * 发布盲盒请求体（对齐后端 BlindBoxCreateDTO）
 */
data class BlindBoxCreateRequest(
    @SerializedName("publisher_id") val publisherId: Long,
    @SerializedName("title") val title: String,
    @SerializedName("required_count") val requiredCount: Int,
    @SerializedName("plan_id") val planId: Long? = null,
    @SerializedName("summary_text") val summaryText: String? = null,
    @SerializedName("mood_text") val moodText: String? = null,
    @SerializedName("district") val district: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("activity_date") val activityDate: String? = null,
    @SerializedName("activity_time_period") val activityTimePeriod: String? = null,
    @SerializedName("activity_type_tags") val activityTypeTags: String? = null
)
