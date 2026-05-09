package com.example.blindweekend.auth

import android.content.Context
import android.content.SharedPreferences
import com.example.blindweekend.BlindWeekendApplication
import com.example.blindweekend.data.model.User
import com.google.gson.Gson

/**
 * 认证管理器 - 管理登录状态、Token、用户信息
 *
 * 使用 SharedPreferences 持久化存储：
 * - token: 登录凭证
 * - user_json: 用户信息JSON
 * - phone: 手机号（用于自动填充）
 */
object AuthManager {

    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_JSON = "user_json"
    private const val KEY_PHONE = "phone"

    private val gson = Gson()

    /** 获取 SharedPreferences 实例 */
    private fun getPrefs(): SharedPreferences {
        return BlindWeekendApplication.instance.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ==================== 公开状态查询 ====================

    /** 是否已登录 */
    val isLoggedIn: Boolean
        get() = getToken().isNotEmpty()

    /** 当前用户（未登录返回null） */
    val currentUser: User?
        get() {
            val json = getPrefs().getString(KEY_USER_JSON, null) ?: return null
            return try { gson.fromJson(json, User::class.java) } catch (_: Exception) { null }
        }

    /** 当前Token */
    fun getToken(): String = getPrefs().getString(KEY_TOKEN, "").orEmpty()

    /** 当前用户ID（未登录返回-1） */
    val currentUserId: Long
        get() = currentUser?.id ?: -1L

    /** 保存的手机号 */
    var savedPhone: String
        get() = getPrefs().getString(KEY_PHONE, "").orEmpty()
        set(value) { getPrefs().edit().putString(KEY_PHONE, value).apply() }

    // ==================== 登录/注册操作 ====================

    /**
     * 保存登录成功后的信息
     */
    fun saveLoginInfo(token: String, user: User) {
        getPrefs().edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_JSON, gson.toJson(user))
            .apply()
    }

    /**
     * 更新当前用户信息
     */
    fun updateUser(user: User) {
        getPrefs().edit()
            .putString(KEY_USER_JSON, gson.toJson(user))
            .apply()
    }

    /**
     * 退出登录 - 清除所有认证信息
     */
    fun logout() {
        getPrefs().edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_JSON)
            // 保留手机号方便下次登录
            .apply()
    }
}
