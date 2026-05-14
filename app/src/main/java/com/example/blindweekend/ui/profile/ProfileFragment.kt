package com.example.blindweekend.ui.profile

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.blindweekend.R
import com.example.blindweekend.BlindWeekendApplication
import com.example.blindweekend.auth.AuthManager
import com.example.blindweekend.auth.LoginActivity
import kotlinx.coroutines.launch

/**
 * 个人中心页面
 *
 * 功能：
 * - 展示用户基本信息（头像、昵称）
 *   - 未登录：显示"点击登录"，点击跳转登录页
 *   - 已登录：显示用户昵称和手机号
 * - 统计数据（已生成方案数、收藏数）
 * - 偏好设置（城市/消费水平/兴趣标签）— 保存到Room
 * - 清除缓存
 * - 关于我们
 * - 退出登录（仅登录后显示）
 */
class ProfileFragment : Fragment() {

    private lateinit var tvNickname: TextView
    private lateinit var tvUserDesc: TextView
    private lateinit var tvPlanCount: TextView
    private lateinit var tvBlindboxCount: TextView
    private lateinit var tvFavoriteCount: TextView
    private lateinit var tvCacheSize: TextView
    private lateinit var btnLogout: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 绑定视图
        tvNickname = view.findViewById(R.id.tv_nickname)
        tvUserDesc = view.findViewById(R.id.tv_user_desc)
        tvPlanCount = view.findViewById(R.id.tv_plan_count)
        tvBlindboxCount = view.findViewById(R.id.tv_blindbox_count)
        tvFavoriteCount = view.findViewById(R.id.tv_favorite_count)
        tvCacheSize = view.findViewById(R.id.tv_cache_size)
        btnLogout = view.findViewById(R.id.btn_logout)

        // 点击头像区域 → 登录/查看个人信息
        view.findViewById<View>(R.id.iv_avatar).setOnClickListener {
            if (AuthManager.isLoggedIn) {
                showUserInfoDialog()
            } else {
                goToLogin()
            }
        }

        // 加载统计数据
        loadStats()

        // 设置按钮点击
        view.findViewById<View>(R.id.item_preference).setOnClickListener {
            showPreferenceDialog()
        }

        view.findViewById<View>(R.id.item_clear_cache).setOnClickListener {
            confirmClearCache()
        }

        view.findViewById<View>(R.id.item_about).setOnClickListener {
            showAboutDialog()
        }

        btnLogout.setOnClickListener {
            confirmLogout()
        }

        // 设置按钮入口（右上角齿轮图标）
        view.findViewById<View>(R.id.btn_settings).setOnClickListener {
            Toast.makeText(requireContext(), "设置功能开发中~", Toast.LENGTH_SHORT).show()
        }

        // 我参与的盲盒
        view.findViewById<View>(R.id.item_my_participated).setOnClickListener {
            if (!AuthManager.isLoggedIn) {
                Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showBlindBoxListDialog("🎯 我参与的盲盒", true)
        }

        // 我发布的盲盒
        view.findViewById<View>(R.id.item_my_published).setOnClickListener {
            if (!AuthManager.isLoggedIn) {
                Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showBlindBoxListDialog("✨ 我发布的盲盒", false)
        }

        refreshUserUI()
    }

    /**
     * 根据登录状态刷新用户信息展示
     */
    private fun refreshUserUI() {
        if (AuthManager.isLoggedIn) {
            val user = AuthManager.currentUser
            tvNickname.text = user?.nickname ?: "探索者"
            val maskedPhone = user?.phone?.let { maskPhone(it) } ?: ""
            tvUserDesc.text = if (maskedPhone.isNotEmpty()) "已登录 · $maskedPhone" else "已登录"
            btnLogout.visibility = View.VISIBLE
        } else {
            tvNickname.text = "点击登录"
            tvUserDesc.text = "发现更多精彩内容"
            btnLogout.visibility = View.GONE
        }
    }

    /** 手机号脱敏 */
    private fun maskPhone(phone: String): String {
        return if (phone.length >= 7) {
            "${phone.substring(0, 3)}****${phone.substring(phone.length - 4)}"
        } else phone
    }

    /**
     * 跳转到登录页面
     */
    private fun goToLogin() {
        startActivityForResult(
            Intent(requireContext(), LoginActivity::class.java),
            LoginActivity.REQUEST_CODE_LOGIN
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LoginActivity.REQUEST_CODE_LOGIN && resultCode == AppCompatActivity.RESULT_OK) {
            // 登录成功，刷新UI
            refreshUserUI()
            loadStats()
            Toast.makeText(requireContext(), "欢迎回来！", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 从本地数据库 + API 加载统计信息
     */
    private fun loadStats() {
        lifecycleScope.launch {
            try {
                val dao = BlindWeekendApplication.getDatabase().cachedPlanDao()
                val allPlans = dao.getAll()
                val totalPlans = allPlans.size
                val favoriteCount = allPlans.count { it.isFavorited }

                // 盲盒参与数：优先从 API 获取，未登录或失败时为 0
                var blindboxCount = 0
                if (AuthManager.isLoggedIn) {
                    val api = com.example.blindweekend.network.RetrofitClient.api
                    val userId = AuthManager.currentUserId
                    if (userId > 0L) {  // 有效userId才请求统计接口
                        try {
                            val statsResponse = api.getUserBlindBoxStats(userId)
                            if (statsResponse.isSuccessful && statsResponse.body()?.code == 200) {
                            val statsData = statsResponse.body()?.data
                            @Suppress("UNCHECKED_CAST")
                            val statsMap = statsData as? Map<String, Any>
                            if (statsMap != null) {
                                // 参与数 + 发布数 的总和
                                val participated = (statsMap["participatedCount"] as? Number)?.toInt() ?: 0
                                val published = (statsMap["publishedCount"] as? Number)?.toInt() ?: 0
                                blindboxCount = participated + published
                                android.util.Log.d("ProfileFragment", "盲盒统计: participated=$participated, published=$published")
                            }
                        } else {
                            android.util.Log.w("ProfileFragment", "盲盒统计请求失败: code=${statsResponse.code()}, body=${statsResponse.body()}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("ProfileFragment", "盲盒统计API调用异常", e)
                        // API 调用失败时保持默认值 0
                    }
                    }  // end if (userId > 0L)
                }

                requireActivity().runOnUiThread {
                    tvPlanCount.text = totalPlans.toString()
                    tvBlindboxCount.text = blindboxCount.toString()
                    tvFavoriteCount.text = favoriteCount.toString()

                    // 计算缓存大小
                    val dbFile = requireContext().getDatabasePath("blind_weekend_db")
                    val sizeKB = if (dbFile.exists()) (dbFile.length() / 1024).toInt() else 0
                    if (sizeKB > 1024) {
                        tvCacheSize.text = "${sizeKB / 1024} MB"
                    } else {
                        tvCacheSize.text = "$sizeKB KB"
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * 显示用户详细信息对话框（已登录时）
     */
    private fun showUserInfoDialog() {
        val user = AuthManager.currentUser ?: return
        AlertDialog.Builder(requireContext())
            .setTitle("个人信息")
            .setMessage(
                "昵称：${user.nickname ?: "未设置"}\n" +
                        "手机号：${user.phone ?: "未绑定"}\n" +
                        "所在城市：${user.city ?: "未设置"}\n" +
                        "账号ID：${user.id}"
            )
            .setPositiveButton("确定", null)
            .setNegativeButton("修改昵称") { _, _ ->
                showEditNicknameDialog(user)
            }
            .show()
    }

    /**
     * 修改昵称对话框
     */
    private fun showEditNicknameDialog(user: com.example.blindweekend.data.model.User) {
        val input = android.widget.EditText(requireContext()).apply {
            setText(user.nickname)
            hint = "输入新昵称"
            maxLines = 1
            setSelection(text.length)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("修改昵称")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.length in 2..12) {
                    val updated = user.copy(nickname = newName)
                    AuthManager.updateUser(updated)
                    refreshUserUI()
                    Toast.makeText(requireContext(), "昵称已更新", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "昵称需要2-12个字符", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示偏好设置对话框 — 完整编辑功能
     */
    private fun showPreferenceDialog() {
        val context = requireContext()

        // 读取当前偏好（从Room或默认值）
        lifecycleScope.launch {
            try {
                val prefDao = BlindWeekendApplication.getDatabase().preferenceDao()
                val userId = if (AuthManager.isLoggedIn) AuthManager.currentUserId else 0L
                val savedPref = if (userId > 0) prefDao.getByUserId(userId) else null

                val currentCity = savedPref?.city ?: "北京"
                val currentConsumeLevel = savedPref?.consumeLevel ?: "low"
                val currentTags = savedPref?.interestTags ?: ""

                requireActivity().runOnUiThread {
                    showPreferenceEditor(currentCity, currentConsumeLevel, currentTags)
                }
            } catch (_: Exception) {
                requireActivity().runOnUiThread {
                    showPreferenceEditor("北京", "low", "")
                }
            }
        }
    }

    /**
     * 偏好设置编辑器（城市 + 消费水平 + 兴趣标签）
     */
    private fun showPreferenceEditor(
        initialCity: String,
        initialConsumeLevel: String,
        initialTags: String
    ) {
        val context = requireContext()

        // 城市选项
        val cities = arrayOf("北京", "上海", "广州", "深圳", "杭州", "成都", "武汉", "南京",
                             "西安", "重庆", "天津", "苏州")
        var selectedCity = initialCity

        // 消费水平选项
        val consumeOptions = arrayOf("经济实惠", "中等消费", "高端享受")
        val consumeValues = arrayOf("low", "medium", "high")
        var selectedConsumeIndex =
            consumeValues.indexOf(initialConsumeLevel).coerceAtLeast(0)

        // 兴趣标签选项
        val allTags = listOf(
            "文艺", "美食", "户外", "运动", "亲子", "拍照",
            "展览", "音乐", "咖啡", "购物", "自然", "探险"
        )
        val savedTagList = try {
            if (initialTags.isEmpty()) emptyList()
            else com.google.gson.Gson().fromJson(
                initialTags,
                Array<String>::class.java
            ).toList()
        } catch (_: Exception) { emptyList() }
        var selectedTags = savedTagList.toMutableSet()

        // 构建自定义视图
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }

        // 1. 城市选择
        val cityLabel = TextView(context).apply { text = "所在城市：" }
        val citySpinnerView = TextView(context).apply {
            text = selectedCity
            textSize = 15f
            setTextColor(0xFF1B3A5C.toInt())
            setPadding(16, 8, 16, 8)
            isFocusable = true
            isClickable = true
            // 解析 attr 为实际 drawable
            val tv = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
            foreground = context.getDrawable(tv.resourceId)
            setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        }
        container.addView(cityLabel)
        container.addView(citySpinnerView)
        citySpinnerView.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("选择城市")
                .setItems(cities) { _, which ->
                    selectedCity = cities[which]
                    citySpinnerView.text = selectedCity
                }
                .show()
        }

        // 分隔线
        val divider = View(context).apply {
            layoutParams = (LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ) as ViewGroup.MarginLayoutParams).apply {
                topMargin = 12.dpToPx(); bottomMargin = 12.dpToPx()
            }
            setBackgroundColor(0xFFEEEEEE.toInt())
        }
        container.addView(divider)

        // 2. 消费水平选择（Radio-like buttons）
        val consumeLabel = TextView(context).apply { text = "消费偏好：" }
        container.addView(consumeLabel)

        val consumeContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for ((i, option) in consumeOptions.withIndex()) {
            val btn = com.google.android.material.button.MaterialButton(context).apply {
                text = option
                textSize = 13f
                if (i == selectedConsumeIndex) {
                    backgroundTintList = context.getColorStateList(R.color.primary_color)
                    setTextColor(context.getColor(R.color.white))
                } else {
                    backgroundTintList = context.getColorStateList(R.color.gray_200)
                    setTextColor(context.getColorStateList(R.color.text_secondary))
                }
                cornerRadius = 18.dpToPx()
                isAllCaps = false
            }
            btn.setOnClickListener {
                selectedConsumeIndex = i
                // 刷新按钮样式
                for (j in 0 until consumeContainer.childCount) {
                    val child = consumeContainer.getChildAt(j)
                            as? com.google.android.material.button.MaterialButton
                    if (child != null) {
                        if (j == i) {
                            child.backgroundTintList = context.getColorStateList(R.color.primary_color)
                            child.setTextColor(context.getColor(R.color.white))
                        } else {
                            child.backgroundTintList = context.getColorStateList(R.color.gray_200)
                            child.setTextColor(context.getColorStateList(R.color.text_secondary))
                        }
                    }
                }
            }
            consumeContainer.addView(btn,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ).apply { marginEnd = 6.dpToPx() })
        }
        container.addView(consumeContainer)

        // 分隔线
        val divider2 = View(context).apply {
            layoutParams = (LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ) as ViewGroup.MarginLayoutParams).apply {
                topMargin = 16.dpToPx(); bottomMargin = 8.dpToPx()
            }
            setBackgroundColor(0xFFEEEEEE.toInt())
        }
        container.addView(divider2)

        // 3. 兴趣标签选择（多选Chip-like）
        val tagLabel = TextView(context).apply {
            text = "兴趣标签（可多选）："
            layoutParams = (LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ) as ViewGroup.MarginLayoutParams).apply { topMargin = 4.dpToPx() }
        }
        container.addView(tagLabel)

        val tagContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for (tag in allTags) {
            val isSelected = selectedTags.contains(tag)
            val chip = TextView(context).apply {
                text = tag
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setPadding(14.dpToPx(), 6.dpToPx(), 14.dpToPx(), 6.dpToPx())
                if (isSelected) {
                    setTextColor(context.getColor(R.color.white))
                    background = context.getDrawable(R.drawable.bg_tag_chip)
                } else {
                    setTextColor(context.getColorStateList(R.color.text_secondary))
                    background = context.getDrawable(R.drawable.bg_tag_unselected)
                }
                isFocusable = true
                isClickable = true
                val fgTv = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, fgTv, true)
                foreground = context.getDrawable(fgTv.resourceId)
            }
            chip.setOnClickListener {
                if (selectedTags.contains(tag)) {
                    selectedTags.remove(tag)
                    chip.setTextColor(context.getColorStateList(R.color.text_secondary))
                    chip.background = context.getDrawable(R.drawable.bg_tag_unselected)
                } else {
                    selectedTags.add(tag)
                    chip.setTextColor(context.getColor(R.color.white))
                    chip.background = context.getDrawable(R.drawable.bg_tag_chip)
                }
            }
            tagContainer.addView(chip,
                (LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ) as ViewGroup.MarginLayoutParams).apply {
                    topMargin = 4.dpToPx()
                    rightMargin = 6.dpToPx()
                    bottomMargin = 4.dpToPx()
                })
        }
        container.addView(tagContainer)

        // 显示对话框
        AlertDialog.Builder(context)
            .setTitle("偏好设置")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                savePreferences(selectedCity, consumeValues[selectedConsumeIndex], selectedTags.toList())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 保存偏好到 Room 数据库
     */
    private fun savePreferences(city: String, consumeLevel: String, tags: List<String>) {
        lifecycleScope.launch {
            try {
                val db = BlindWeekendApplication.getDatabase()
                val prefDao = db.preferenceDao()
                val userId = if (AuthManager.isLoggedIn) AuthManager.currentUserId else 0L
                val tagsJson = com.google.gson.Gson().toJson(tags)

                val entity = com.example.blindweekend.data.db.UserPreferenceEntity(
                    userId = if (userId > 0) userId else System.currentTimeMillis(),
                    consumeLevel = consumeLevel,
                    interestTags = tagsJson,
                    city = city,
                    updatedAt = System.currentTimeMillis()
                )
                prefDao.insertOrUpdate(entity)
                Toast.makeText(requireContext(), "偏好设置已保存！", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        }
    }

    /** dp 转 px 扩展函数 */
    private fun Int.dpToPx(): Int {
        val density = resources.displayMetrics.density
        return (this * density).toInt()
    }

    /**
     * 确认清除缓存
     */
    private fun confirmClearCache() {
        AlertDialog.Builder(requireContext())
            .setTitle("清除缓存")
            .setMessage("将清除所有已保存的本地方案数据，确定继续？")
            .setPositiveButton("清除") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val dao = BlindWeekendApplication.getDatabase().cachedPlanDao()
                        val all = dao.getAll()
                        all.forEach { dao.deleteById(it.id) }
                        loadStats()
                        Toast.makeText(requireContext(), "缓存已清除", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {}
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 关于我们对话框
     */
    private fun showAboutDialog() {
        val msg = "版本：v1.0.0\n\n" +
            "不期周末 \u2014 让每个周末都不期而遇的精彩。\n\n" +
            "基于你的兴趣偏好，生成专属周末出行方案，\n" +
            "还能发布为盲盒，找到志同道合的伙伴一起出发！\n\n"

        AlertDialog.Builder(requireContext())
            .setTitle("关于不期周末")
            .setMessage(msg)
            .setPositiveButton("知道了", null)
            .show()
    }

    /**
     * 显示盲盒列表对话框（我参与的 / 我发布的）
     * 深色背景 + 白色字体
     * @param title 对话框标题
     * @param isParticipated true=参与的盲盒, false=发布的盲盒
     */
    private fun showBlindBoxListDialog(title: String, isParticipated: Boolean) {
        val userId = AuthManager.currentUserId
        val context = requireContext()

        // 用户ID有效性校验
        if (userId <= 0L) {
            Toast.makeText(context, "用户信息异常，请重新登录", Toast.LENGTH_SHORT).show()
            return
        }

        android.util.Log.d("ProfileFragment", "加载盲盒列表: isParticipated=$isParticipated, userId=$userId")

        // 先显示一个加载中的对话框
        val loadingDialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage("正在加载...")
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            try {
                val api = com.example.blindweekend.network.RetrofitClient.api
                val boxes: List<com.example.blindweekend.data.model.BlindBox> = if (isParticipated) {
                    val response = api.getParticipatedBlindBoxes(userId)
                    android.util.Log.d("ProfileFragment", "参与的盲盒响应: http=${response.isSuccessful}, code=${response.body()?.code}")
                    if (response.isSuccessful && response.body()?.code == 200) {
                        response.body()?.data ?: emptyList()
                    } else {
                        android.util.Log.w("ProfileFragment", "参与的盲盒请求失败: httpCode=${response.code()}, body=${response.body()}")
                        null  // 用null标记请求失败，区分"真的没数据"
                    }
                } else {
                    val response = api.getPublishedBlindBoxes(userId)
                    android.util.Log.d("ProfileFragment", "发布的盲盒响应: http=${response.isSuccessful}, code=${response.body()?.code}")
                    if (response.isSuccessful && response.body()?.code == 200) {
                        response.body()?.data ?: emptyList()
                    } else {
                        android.util.Log.w("ProfileFragment", "发布的盲盒请求失败: httpCode=${response.code()}, body=${response.body()}")
                        null
                    }
                } ?: emptyList()  // null（请求失败）时返回空列表

                loadingDialog.dismiss()

                android.util.Log.d("ProfileFragment", "盲盒列表加载完成: size=${boxes.size}")

                requireActivity().runOnUiThread {
                    // 深色背景容器
                    val container = android.widget.LinearLayout(context).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        setPadding(32, 20, 32, 20)
                        setBackgroundColor(context.getColor(R.color.nav_bg))
                    }

                    if (boxes.isEmpty()) {
                        val tvEmpty = TextView(context).apply {
                            text = if (isParticipated) "还没有参与过任何盲盒\n\n去广场看看吧~" else "还没有发布过盲盒\n\n生成方案后试试发布吧~"
                            textSize = 15f
                            setTextColor(0xFFFFFFFF.toInt())
                            gravity = android.view.Gravity.CENTER
                            setPadding(0, 40, 0, 40)
                        }
                        container.addView(tvEmpty)

                        AlertDialog.Builder(context)
                            .setTitle(title)
                            .setView(container)
                            .setPositiveButton("去广场", { _, _ ->
                                try {
                                    val bottomNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.nav_view)
                                    bottomNav?.selectedItemId = R.id.navigation_blindbox
                                } catch (_: Exception) {}
                            })
                            .setNegativeButton("取消", null)
                            .show()
                    } else {
                        // 构建盲盒列表文本（深色背景+白色字体）
                        val sb = StringBuilder()
                        for ((index, box) in boxes.withIndex()) {
                            val statusLabel = when (box.status) {
                                "open" -> "🟢 招募中"
                                "full" -> "🔴 已满员"
                                "closed" -> "⚫ 已结束"
                                else -> box.status
                            }
                            val dateStr = box.activityDate ?: "日期待定"
                            sb.append("${index + 1}. ${box.title}\n")
                            sb.append("   📅 $dateStr  |  👥 ${box.currentCount}/${box.requiredCount}人\n")
                            sb.append("   状态: $statusLabel")
                            if (box.district != null) {
                                sb.append("  |  📍 ${box.district}")
                            }
                            sb.append("\n")
                            if (index < boxes.size - 1) {
                                sb.append("   ──────────────────────\n")
                            }
                        }

                        // 白色字体 TextView 放在深色 ScrollView 中
                        val tvContent = TextView(context).apply {
                            text = sb.toString().trim()
                            textSize = 14f
                            setTextColor(0xFFFFFFFF.toInt())
                            typeface = android.graphics.Typeface.MONOSPACE
                            setLineSpacing(1.2f, 1f)
                        }
                        val scrollView = android.widget.ScrollView(context).apply {
                            setBackgroundColor(context.getColor(R.color.nav_bg))
                            addView(tvContent)
                        }

                        container.addView(scrollView)

                        AlertDialog.Builder(context)
                            .setTitle("$title (${boxes.size})")
                            .setView(container)
                            .setPositiveButton("知道了", null)
                            .setNeutralButton("去广场") { _, _ ->
                                try {
                                    val bottomNav = requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.nav_view)
                                    bottomNav?.selectedItemId = R.id.navigation_blindbox
                                } catch (_: Exception) {}
                            }
                            .show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileFragment", "盲盒列表加载异常", e)
                loadingDialog.dismiss()
                requireActivity().runOnUiThread {
                    Toast.makeText(context, "加载失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 确认退出登录
     */
    private fun confirmLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle("确认退出")
            .setMessage("确定要退出当前账号吗？")
            .setPositiveButton("退出") { _, _ ->
                AuthManager.logout()
                refreshUserUI()
                loadStats()
                Toast.makeText(requireContext(), "已退出登录", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // 每次回到页面刷新数据和UI状态
        loadStats()
        refreshUserUI()
    }
}
