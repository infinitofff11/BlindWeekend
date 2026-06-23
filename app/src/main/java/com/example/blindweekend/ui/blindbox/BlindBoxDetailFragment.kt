package com.example.blindweekend.ui.blindbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.blindweekend.R
import com.example.blindweekend.auth.AuthManager
import com.example.blindweekend.data.model.BlindBox
import com.example.blindweekend.data.model.BlindBoxDetailResponse
import com.example.blindweekend.data.model.PlanItemDetail
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 盲盒详情页 Fragment
 *
 * 展示盲盒的完整模糊信息，支持"想组队"参与操作
 * 已参与/发布者可查看具体活动地点
 */
class BlindBoxDetailFragment : Fragment() {

    private var blindBox: BlindBox? = null
    private var detailResponse: BlindBoxDetailResponse? = null

    companion object {
        private const val ARG_BLIND_BOX = "blind_box"

        fun newInstance(blindBox: BlindBox): BlindBoxDetailFragment {
            return BlindBoxDetailFragment().apply {
                arguments = Bundle().also { it.putParcelable(ARG_BLIND_BOX, blindBox) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blindBox = arguments?.getParcelable(ARG_BLIND_BOX)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_blind_box_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val box = blindBox ?: return

        // 返回按钮
        view.findViewById<View>(R.id.btn_back).setOnClickListener {
            findNavController().navigateUp()
        }

        // 从服务端拉取最新数据（触发浏览量+1 + 获取最新人数/状态 + 活动地点）
        fetchLatestData(box.id)

        // 先用本地数据显示界面（快速响应），数据回来后覆盖
        renderUI(box, null)
    }

    /**
     * 从服务端获取最新盲盒数据（同时触发浏览量计数）
     */
    private fun fetchLatestData(boxId: Long) {
        lifecycleScope.launch {
            try {
                val api = com.example.blindweekend.network.RetrofitClient.api
                val response = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    api.getBlindBoxDetail(boxId)
                }
                if (response.isSuccessful && response.body()?.code == 200) {
                    val detailData = response.body()?.data
                    if (detailData != null) {
                        detailResponse = detailData
                        blindBox = detailData.blindBox
                        // 用最新数据重新渲染
                        renderUI(detailData.blindBox, detailData)
                    }
                }
            } catch (_: Exception) {
                // 网络异常时静默失败，已用本地数据渲染
            }
        }
    }

    /**
     * 渲染盲盒详情UI
     * @param box 盲盒基本信息
     * @param detail 详情响应（含活动地点），首次渲染时可能为null
     */
    private fun renderUI(box: BlindBox, detail: BlindBoxDetailResponse?) {
        val view = requireView()

        // 状态标签
        val tvStatusBadge = view.findViewById<TextView>(R.id.tv_status_badge)
        when (box.status) {
            "open" -> {
                tvStatusBadge.text = "🔥 招募中"
                tvStatusBadge.setBackgroundResource(R.drawable.bg_tag_primary)
            }
            "full" -> {
                tvStatusBadge.text = "✅ 已满员"
                tvStatusBadge.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.success_color)
                )
            }
            "closed", "cancelled" -> {
                tvStatusBadge.text = "🚫 已结束"
                tvStatusBadge.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.gray_300)
                )
            }
        }

        // 标题
        view.findViewById<TextView>(R.id.tv_title).text = box.title

        // 心情文案
        box.moodText?.let { mood ->
            view.findViewById<TextView>(R.id.tv_mood_text).text = mood
        }

        // 基本信息
        val locationText = buildString {
            if (!box.city.isNullOrBlank()) append(box.city)
            if (!box.city.isNullOrBlank() && !box.district.isNullOrBlank()) append("·")
            if (!box.district.isNullOrBlank()) append(box.district)
        }.ifEmpty { "未指定" }
        view.findViewById<TextView>(R.id.tv_district).text = locationText
        view.findViewById<TextView>(R.id.tv_activity_date).text =
            box.activityDate ?: "待定"
        view.findViewById<TextView>(R.id.tv_time_period).text =
            formatTimePeriod(box.activityTimePeriod)

        // 人数信息
        val countInfo = if (box.currentCount >= box.requiredCount) {
            "已满员 (${box.currentCount}/${box.requiredCount})"
        } else {
            "已招募 ${box.currentCount}/${box.requiredCount} 人"
        }
        view.findViewById<TextView>(R.id.tv_count_info).text = countInfo

        // 类型标签
        val tagContainer = view.findViewById<LinearLayout>(R.id.layout_tags)
        tagContainer.removeAllViews()
        box.getTypeTagList().forEach { tag ->
            val tagView = TextView(context).apply {
                text = tag
                textSize = 12f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_color))
                setPadding(20, 6, 20, 6)
                setBackgroundResource(R.drawable.bg_tag_unselected)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = 8
                topMargin = 4
                bottomMargin = 4
            }
            tagContainer.addView(tagView, params)
        }

        // 描述（如果有）
        box.summaryText?.takeIf { it.isNotEmpty() }?.let { summary ->
            view.findViewById<View>(R.id.card_summary).visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tv_summary_text).text = summary
        }

        // ==================== 活动地点渲染 ====================
        val cardSpots = view.findViewById<View>(R.id.card_spots)
        val cardSpotsHint = view.findViewById<View>(R.id.card_spots_hint)
        val layoutSpots = view.findViewById<LinearLayout>(R.id.layout_spots)

        if (detail != null) {
            val planItems = detail.planItems
            val isPublisher = detail.publisher
            val isParticipated = detail.participated

            if (isPublisher || isParticipated) {
                // 已参与或发布者：显示具体活动地点
                if (planItems != null && planItems.isNotEmpty()) {
                    cardSpots.visibility = View.VISIBLE
                    cardSpotsHint.visibility = View.GONE
                    renderSpotItems(layoutSpots, planItems)
                } else if (box.planId != null) {
                    // 有方案但无环节数据，显示提示
                    cardSpotsHint.visibility = View.VISIBLE
                    cardSpots.visibility = View.GONE
                } else {
                    // 无关联方案
                    cardSpotsHint.visibility = View.GONE
                    cardSpots.visibility = View.GONE
                }
            } else {
                // 未参与：显示提示
                cardSpotsHint.visibility = View.VISIBLE
                cardSpots.visibility = View.GONE
            }
        }
        // detail为null时（首次本地渲染），不显示活动地点相关卡片，等API数据回来后再渲染

        // 发布者信息（匿名处理）
        view.findViewById<TextView>(R.id.tv_publisher_name).text =
            "用户${box.publisherId % 1000}"

        // 参与按钮
        val btnJoinAction = view.findViewById<MaterialButton>(R.id.btn_join_action)
        val isOpen = box.isOpen()

        // 如果当前用户是发布者，按钮显示"我发布的"
        val isPublisher = detail?.publisher == true || (AuthManager.isLoggedIn && AuthManager.currentUserId == box.publisherId)

        if (isPublisher) {
            btnJoinAction.text = "✨ 我发布的"
            btnJoinAction.isEnabled = false
            btnJoinAction.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.primary_color)
        } else if (isOpen && AuthManager.currentUser != null) {
            btnJoinAction.text = "💝 想组队"
            btnJoinAction.setOnClickListener { performJoin(box) }
        } else if (!isOpen) {
            btnJoinAction.text = when (box.status) {
                "full" -> "😢 已满员"
                else -> "🚫 已结束"
            }
            btnJoinAction.isEnabled = false
            btnJoinAction.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.gray_300)
        } else {
            btnJoinAction.text = "请先登录"
            btnJoinAction.isEnabled = false
        }
    }

    /**
     * 渲染活动地点列表
     */
    private fun renderSpotItems(container: LinearLayout, items: List<PlanItemDetail>) {
        container.removeAllViews()
        val context = requireContext()

        for ((index, item) in items.withIndex()) {
            val itemView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12, 0, 12)
            }

            // 环节标题：第N站 + 时段
            val headerText = buildString {
                append("第${item.itemOrder}站")
                if (!item.startTime.isNullOrBlank() || !item.endTime.isNullOrBlank()) {
                    append("  ${item.startTime ?: ""}-${item.endTime ?: ""}")
                }
            }
            val tvHeader = TextView(context).apply {
                text = headerText
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.hint_text))
            }
            itemView.addView(tvHeader)

            // 活动点名称
            val tvSpotName = TextView(context).apply {
                text = item.spotName
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 4, 0, 0)
            }
            itemView.addView(tvSpotName)

            // 详细地址
            if (!item.spotAddress.isNullOrBlank()) {
                val tvAddress = TextView(context).apply {
                    text = "📍 ${item.spotAddress}"
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    setPadding(0, 2, 0, 0)
                }
                itemView.addView(tvAddress)
            }

            // 建议停留时长
            if (item.recommendDuration != null && item.recommendDuration > 0) {
                val hours = item.recommendDuration / 60
                val minutes = item.recommendDuration % 60
                val durationText = if (hours > 0) {
                    if (minutes > 0) "建议停留 ${hours}小时${minutes}分钟" else "建议停留 ${hours}小时"
                } else {
                    "建议停留 ${minutes}分钟"
                }
                val tvDuration = TextView(context).apply {
                    text = "⏱ $durationText"
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(context, R.color.hint_text))
                    setPadding(0, 2, 0, 0)
                }
                itemView.addView(tvDuration)
            }

            // 备注
            if (!item.note.isNullOrBlank()) {
                val tvNote = TextView(context).apply {
                    text = "💬 ${item.note}"
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    setPadding(0, 2, 0, 0)
                }
                itemView.addView(tvNote)
            }

            // 分隔线（非最后一项）
            if (index < items.size - 1) {
                val divider = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).apply { topMargin = 12 }
                    setBackgroundColor(0xFFEEEEEE.toInt())
                }
                itemView.addView(divider)
            }

            container.addView(itemView)
        }
    }

    /**
     * 执行参与盲盒操作
     */
    private fun performJoin(blindBox: BlindBox) {
        val userId = AuthManager.currentUser?.id ?: run {
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show()
            return
        }

        val btnJoin = requireView()?.findViewById<MaterialButton>(R.id.btn_join_action)
        btnJoin?.isEnabled = false
        btnJoin?.text = "⏳ 提交中..."

        lifecycleScope.launch {
            try {
                val api = com.example.blindweekend.network.RetrofitClient.api
                val response = api.joinBlindBox(blindBox.id, userId)

                if (response.isSuccessful && response.body()?.code == 200) {
                    Toast.makeText(
                        context,
                        "✅ 已向发起者表达组队意向！",
                        Toast.LENGTH_SHORT
                    ).show()

                    // 重新拉取最新数据（刷新人数、状态和活动地点）
                    fetchLatestData(blindBox.id)

                    // 刷新列表数据（通知广场页面）
                    parentFragmentManager.setFragmentResult("blind_box_joined", Bundle())
                } else {
                    Toast.makeText(
                        context,
                        response.body()?.message ?: "操作失败",
                        Toast.LENGTH_SHORT
                    ).show()
                    resetJoinButton(btnJoin)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "操作失败：${e.message}", Toast.LENGTH_SHORT).show()
                resetJoinButton(btnJoin)
            }
        }
    }

    private fun resetJoinButton(btn: MaterialButton?) {
        btn?.isEnabled = true
        btn?.text = "💝 想组队"
    }

    /**
     * 格式化时间段显示文本
     */
    private fun formatTimePeriod(period: String?): String {
        return when (period) {
            "morning" -> "上午"
            "afternoon" -> "下午"
            "evening" -> "晚上"
            "all_day" -> "全天"
            null, "" -> "待定"
            else -> period
        }
    }
}
