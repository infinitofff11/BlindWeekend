package com.example.blindweekend.network

import com.example.blindweekend.data.model.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/**
 * API接口定义
 */
interface BlindWeekendApi {

    // ==================== 活动点相关 ====================

    /**
     * 获取所有启用的活动点
     */
    @GET("admin/spots/active")
    suspend fun getActiveSpots(@Query("city") city: String? = null): Response<ApiResponse<List<ActivitySpot>>>

    /**
     * 获取活动点详情
     */
    @GET("admin/spots/{id}")
    suspend fun getSpotDetail(@Path("id") id: Long): Response<ApiResponse<ActivitySpot>>

    // ==================== 方案模板相关 ====================

    /**
     * 获取所有启用的模板
     */
    @GET("admin/templates/active")
    suspend fun getActiveTemplates(
        @Query("consumeLevel") consumeLevel: String? = null,
        @Query("themeType") themeType: String? = null
    ): Response<ApiResponse<List<PlanTemplate>>>

    /**
     * 获取模板详情（含时段）
     */
    @GET("admin/templates/{id}")
    suspend fun getTemplateDetail(@Path("id") id: Long): Response<ApiResponse<Map<String, Any?>>>

    /**
     * 获取模板时段配置
     */
    @GET("admin/templates/{id}/segments")
    suspend fun getTemplateSegments(@Path("id") id: Long): Response<ApiResponse<List<TemplateSegment>>>

    // ==================== 盲盒相关 ====================

    /**
     * 盲盒广场列表
     */
    @GET("admin/blindboxes")
    suspend fun getBlindBoxList(
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("status") status: String = "open",
        @Query("city") city: String? = null,
        @Query("tags") tags: String? = null
    ): Response<ApiResponse<PageResponse<BlindBox>>>

    /**
     * 盲盒详情（含活动地点信息）
     */
    @GET("admin/blindboxes/{id}")
    suspend fun getBlindBoxDetail(@Path("id") id: Long): Response<ApiResponse<BlindBoxDetailResponse>>

    /**
     * 发布盲盒
     */
    @POST("admin/blindboxes")
    suspend fun createBlindBox(@Body request: BlindBoxCreateRequest): Response<ApiResponse<BlindBox>>

    /**
     * 参与盲盒（意向响应）
     */
    @POST("admin/blindboxes/{id}/join")
    suspend fun joinBlindBox(
        @Path("id") id: Long,
        @Query("userId") userId: Long
    ): Response<ApiResponse<Void>>

    // ==================== 用户相关 ====================

    /**
     * 登录（手机号+密码）→ 返回 JWT Token + 用户信息
     */
    @POST("auth/login")
    suspend fun login(@Body loginReq: Map<String, String>): Response<ApiResponse<LoginResponse>>

    /**
     * 用户注册（手机号+昵称+密码）→ 返回 JWT Token + 用户信息
     */
    @POST("auth/register")
    suspend fun register(@Body registerReq: Map<String, String>): Response<ApiResponse<LoginResponse>>

    /**
     * 获取用户信息
     */
    @GET("admin/users/{id}")
    suspend fun getUserInfo(@Path("id") id: Long): Response<ApiResponse<User>>

    // ==================== 用户盲盒记录相关 ====================

    /**
     * 获取用户参与的盲盒列表
     */
    @GET("admin/blindboxes/user/{userId}/participated")
    suspend fun getParticipatedBlindBoxes(@Path("userId") userId: Long): Response<ApiResponse<List<BlindBox>>>

    /**
     * 获取用户发布的盲盒列表
     */
    @GET("admin/blindboxes/user/{userId}/published")
    suspend fun getPublishedBlindBoxes(@Path("userId") userId: Long): Response<ApiResponse<List<BlindBox>>>

    /**
     * 获取用户盲盒统计（参与数 + 发布数）
     */
    @GET("admin/blindboxes/user/{userId}/stats")
    suspend fun getUserBlindBoxStats(@Path("userId") userId: Long): Response<ApiResponse<Map<String, Any>>>

    // ==================== 用户资料相关 ====================

    /**
     * 更新用户资料（昵称/城市）
     */
    @PUT("auth/profile")
    suspend fun updateProfile(@Body body: Map<String, @JvmSuppressWildcards String>): Response<ApiResponse<User>>

    /**
     * 上传头像
     */
    @Multipart
    @POST("auth/profile/avatar")
    suspend fun uploadAvatar(@Part avatar: MultipartBody.Part): Response<ApiResponse<User>>
}
