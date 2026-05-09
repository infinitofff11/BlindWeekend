package com.example.blindweekend.ui.plan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.blindweekend.R
import com.example.blindweekend.BlindWeekendApplication
import com.example.blindweekend.data.db.CachedPlanEntity
import com.example.blindweekend.data.model.GeneratedPlan
import com.example.blindweekend.data.model.GeneratedPlanItem
import com.example.blindweekend.ui.blindbox.PublishBlindBoxDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

/**
 * 周末玩法生成器 - 首页Fragment
 *
 * 核心功能：
 * - 一键生成半日/一日行程方案
 * - 时间轴卡片流展示
 * - 换一批 / 换个感觉
 * - 保存 / 发布盲盒
 */
class PlanGeneratorFragment : Fragment() {

    private lateinit var viewModel: PlanGeneratorViewModel
    private lateinit var layoutPlanItems: LinearLayout

    // 默认配置（实际应从用户偏好中读取）
    private var currentCity = "北京"
    private var currentConsumeLevel = "low"
    private var currentThemeTypes: List<String>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_plan_generator, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化ViewModel
        viewModel = PlanGeneratorViewModel()

        // 绑定视图
        val btnGenerate: MaterialCardView = view.findViewById(R.id.btn_generate_plan)
        val progressBar: ProgressBar = view.findViewById(R.id.progress_bar)
        val tvError: TextView = view.findViewById(R.id.tv_error)
        val tvPlanTitle: TextView = view.findViewById(R.id.tv_plan_title)
        layoutPlanItems = view.findViewById(R.id.layout_plan_items)
        val layoutActions: LinearLayout = view.findViewById(R.id.layout_actions)
        val btnRegenerate: MaterialButton = view.findViewById(R.id.btn_regenerate)
        val btnSave: MaterialButton = view.findViewById(R.id.btn_save)
        val btnPublishBlindbox: MaterialButton = view.findViewById(R.id.btn_publish_blindbox)

        // 一键生成按钮点击
        btnGenerate.setOnClickListener {
            viewModel.generatePlan(currentCity, currentConsumeLevel, currentThemeTypes)
        }

        // 观察加载状态
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnGenerate.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
        }

        // 观察错误信息
        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                tvError.text = msg
                tvError.visibility = View.VISIBLE
                tvError.setOnClickListener { viewModel.clearError() }
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            } else {
                tvError.visibility = View.GONE
            }
        }

        // 观察生成的方案
        viewModel.generatedPlan.observe(viewLifecycleOwner) { plan ->
            if (plan != null) {
                displayPlan(plan, layoutPlanItems)
                tvPlanTitle.text = "📋 ${plan.templateName}"
                tvPlanTitle.visibility = View.VISIBLE
                layoutPlanItems.visibility = View.VISIBLE
                layoutActions.visibility = View.VISIBLE
                btnPublishBlindbox.visibility = View.VISIBLE
            }
        }

        // "换个感觉" - 重新生成全部
        btnRegenerate.setOnClickListener {
            viewModel.regeneratePlan(currentCity, currentConsumeLevel, currentThemeTypes)
        }

        // "保存方案"
        btnSave.setOnClickListener {
            val plan = viewModel.generatedPlan.value
            if (plan != null) {
                savePlanToDatabase(plan)
            }
        }

        // "发布为盲盒"
        btnPublishBlindbox.setOnClickListener {
            val plan = viewModel.generatedPlan.value
            if (plan != null) {
                val dialog = PublishBlindBoxDialog(
                    context = requireContext(),
                    plan = plan
                ) {
                    Toast.makeText(requireContext(), "盲盒已发布到广场！", Toast.LENGTH_SHORT).show()
                }
                dialog.show()
            }
        }
    }

    /**
     * 展示生成的方案（时间轴卡片流）
     */
    private fun displayPlan(
        plan: GeneratedPlan,
        container: LinearLayout
    ) {
        container.removeAllViews()

        plan.items.forEachIndexed { index, item ->
            val itemView = createSpotCard(item, index == plan.items.lastIndex)
            container.addView(itemView)
        }
    }

    /**
     * 创建单个活动点卡片
     */
    private fun createSpotCard(item: GeneratedPlanItem, isLast: Boolean): View {
        val cardView = layoutInflater.inflate(R.layout.item_plan_spot, layoutPlanItems, false)

        // 绑定视图元素
        val tvStartTime = cardView.findViewById<TextView>(R.id.tv_item_start_time)
        val tvEndTime = cardView.findViewById<TextView>(R.id.tv_item_end_time)
        val tvSpotName = cardView.findViewById<TextView>(R.id.tv_spot_name)
        val chipTypeTag = cardView.findViewById<TextView>(R.id.chip_type_tag)
        val tvConsume = cardView.findViewById<TextView>(R.id.tv_consume)
        val tvAddress = cardView.findViewById<TextView>(R.id.tv_address)
        val tvDurationHint = cardView.findViewById<TextView>(R.id.tv_duration_hint)
        val btnReplace = cardView.findViewById<TextView>(R.id.btn_replace)
        val timelineLine = cardView.findViewById<View>(R.id.view_timeline_line)

        // 填充数据
        tvStartTime.text = item.startTime
        tvEndTime.text = item.endTime
        tvSpotName.text = item.spot.name

        val tags = item.spot.getTypeTagList()
        if (tags.isNotEmpty()) {
            chipTypeTag.text = tags.first()
            chipTypeTag.visibility = View.VISIBLE
        }

        tvConsume.text = item.spot.getFormattedPrice()
        tvAddress.text = "${item.spot.district ?: ""}·${item.spot.address ?: ""}"

        item.spot.recommendDuration?.let { duration ->
            tvDurationHint.text = "建议停留 ${duration} 分钟"
            tvDurationHint.visibility = View.VISIBLE
        }

        // 最后一个卡片不显示时间轴线
        if (isLast) {
            timelineLine.visibility = View.GONE
        }

        // "换一批"点击事件
        btnReplace.setOnClickListener {
            viewModel.replaceItem(item.order, currentCity)
        }

        return cardView
    }

    /**
     * 保存方案到本地Room数据库
     */
    private fun savePlanToDatabase(plan: GeneratedPlan) {
        lifecycleScope.launch {
            try {
                val planJson = com.google.gson.Gson().toJson(plan)
                val entity = CachedPlanEntity(
                    planId = System.currentTimeMillis(),
                    planName = plan.templateName,
                    planData = planJson,
                    isFavorited = false,
                    createdAt = System.currentTimeMillis()
                )
                BlindWeekendApplication.getDatabase().cachedPlanDao().insert(entity)
                Toast.makeText(requireContext(), "方案已保存到「我的方案」", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
