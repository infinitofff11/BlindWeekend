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
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 盲盒详情页 Fragment
 *
 * 展示盲盒的完整模糊信息，支持"想组队"参与操作
 */
class BlindBoxDetailFragment : Fragment() {

    private var blindBox: BlindBox? = null

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

        // ✅ 从服务端拉取最新数据（触发浏览量+1 + 获取最新人数/状态）
        fetchLatestData(box.id)

        // 先用本地数据显示界面（快速响应），数据回来后覆盖
        renderUI(box)
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
                    val latestBox = response.body()?.data
                    if (latestBox != null) {
                        blindBox = latestBox
                        // 用最新数据重新渲染
                        renderUI(latestBox)
                    }
                }
            } catch (_: Exception) {
                // 网络异常时静默失败，已用本地数据渲染
            }
        }
    }

    /**
     * 渲染盲盒详情UI
     */
    private fun renderUI(box: BlindBox) {
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

        // 发布者信息（匿名处理）
        view.findViewById<TextView>(R.id.tv_publisher_name).text =
            "用户${box.publisherId % 1000}"

        // 参与按钮
        val btnJoinAction = view.findViewById<MaterialButton>(R.id.btn_join_action)
        val isOpen = box.isOpen()

        if (isOpen && AuthManager.currentUser != null) {
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

                    // ✅ 重新拉取最新数据（刷新人数和状态）
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
