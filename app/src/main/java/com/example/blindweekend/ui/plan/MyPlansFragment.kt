package com.example.blindweekend.ui.plan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blindweekend.R
import com.example.blindweekend.auth.AuthManager
import com.example.blindweekend.data.db.CachedPlanEntity
import com.example.blindweekend.data.model.ActivitySpot
import com.example.blindweekend.data.model.GeneratedPlan
import com.example.blindweekend.data.model.GeneratedPlanItem
import com.example.blindweekend.ui.blindbox.PublishBlindBoxDialog
import com.google.gson.Gson
import kotlinx.coroutines.launch

/**
 * 我的方案页面 - 展示用户已保存的周末行程方案
 *
 * 功能：
 * - 展示已保存方案列表（来自Room本地数据库）
 * - 收藏/取消收藏
 * - 删除方案
 * - 空状态引导到首页生成
 */
class MyPlansFragment : Fragment() {

    private lateinit var viewModel: MyPlansViewModel
    private lateinit var rvPlans: RecyclerView
    private lateinit var layoutEmpty: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_my_plans, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = MyPlansViewModel()

        rvPlans = view.findViewById(R.id.rv_plans)
        layoutEmpty = view.findViewById(R.id.layout_empty)

        // RecyclerView 设置
        rvPlans.layoutManager = LinearLayoutManager(requireContext())
        val adapter = SavedPlanAdapter(
            onItemClick = { plan -> showPlanDetail(plan) },
            onFavoriteClick = { plan -> viewModel.toggleFavorite(plan.id, plan.isFavorited) },
            onDeleteClick = { plan -> confirmDelete(plan) }
        )
        rvPlans.adapter = adapter

        // 观察方案列表
        viewModel.plans.observe(viewLifecycleOwner) { plans ->
            adapter.submitList(plans)
        }

        // 观察空状态
        viewModel.isEmpty.observe(viewLifecycleOwner) { empty ->
            layoutEmpty.visibility = if (empty) View.VISIBLE else View.GONE
            rvPlans.visibility = if (empty) View.GONE else View.VISIBLE
        }

        // 下拉刷新：重新加载
        // TODO: 添加SwipeRefreshLayout
    }

    /**
     * 显示方案详情（弹窗展示）+ 发布为盲盒入口
     */
    private fun showPlanDetail(plan: CachedPlanEntity) {
        val context = requireContext()

        // 尝试解析JSON数据
        val parsedItems = try {
            val data = Gson().fromJson(
                plan.planData,
                com.google.gson.internal.LinkedTreeMap::class.java
            ) as? Map<String, Any>
            (data?.get("items") as? List<*>)?.mapIndexed { index, item ->
                item as? Map<String, Any>
            }
        } catch (_: Exception) { null }

        if (parsedItems != null && parsedItems.isNotEmpty()) {
            // 构建详情文本
            val sb = StringBuilder()
            sb.appendLine("【${plan.planName ?: "未命名方案"}】")
            for ((index, item) in parsedItems.withIndex()) {
                val spot = item?.get("spot") as? Map<String, Any>
                val segmentName = item?.get("segmentName") as? String ?: ""
                val spotName = spot?.get("name") as? String ?: "未知地点"
                val address = spot?.get("address") as? String ?: ""
                val startTime = item?.get("startTime") as? String ?: ""
                sb.appendLine("${index + 1}. [$startTime] $segmentName - $spotName")
                if (address.isNotEmpty()) sb.appendLine("   📍 $address")
            }

            // 深色内容容器（与个人页面盲盒弹窗风格统一）
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(28, 16, 28, 8)
                setBackgroundColor(context.getColor(R.color.nav_bg))
            }
            val tvContent = TextView(context).apply {
                text = sb.toString().trim()
                textSize = 14f
                setTextColor(0xFFFFFFFF.toInt())
                setLineSpacing(1.2f, 1f)
            }
            container.addView(tvContent)

            AlertDialog.Builder(context)
                .setTitle(plan.planName ?: "方案详情")
                .setView(container)
                .setPositiveButton("确定", null)
                .setNeutralButton("✨ 发布为盲盒") { _, _ ->
                    // 检查登录状态
                    if (!AuthManager.isLoggedIn) {
                        Toast.makeText(context, "请先登录后再发布盲盒", Toast.LENGTH_SHORT).show()
                        return@setNeutralButton
                    }

                    // 解析 planData → GeneratedPlan 并打开发布弹窗
                    val generatedPlan = parseToGeneratedPlan(plan)
                    if (generatedPlan == null) {
                        Toast.makeText(context, "方案数据解析失败，无法发布", Toast.LENGTH_SHORT).show()
                    } else {
                        PublishBlindBoxDialog(
                            context = context,
                            plan = generatedPlan,
                            onPublished = {
                                Toast.makeText(context, "📦 盲盒发布成功！去广场看看吧~", Toast.LENGTH_LONG).show()
                            }
                        ).show()
                    }
                }
                .show()
        } else {
            Toast.makeText(context, "方案数据解析失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 确认删除对话框
     */
    private fun confirmDelete(plan: CachedPlanEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除「${plan.planName ?: "该方案"}」吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deletePlan(plan.id)
                Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 将 CachedPlanEntity 的 planData(JSON) 解析为 GeneratedPlan
     * 供 PublishBlindBoxDialog 预填充使用
     */
    private fun parseToGeneratedPlan(plan: CachedPlanEntity): GeneratedPlan? {
        return try {
            val data = Gson().fromJson(
                plan.planData,
                com.google.gson.internal.LinkedTreeMap::class.java
            ) as? Map<String, Any> ?: return null

            val itemsRaw = data["items"] as? List<*> ?: return null

            val generatedItems = itemsRaw.mapIndexed { index, item ->
                val map = item as? Map<String, Any>
                val spotMap = map?.get("spot") as? Map<String, Any>

                // 构建 ActivitySpot（从 Map 中提取字段）
                val spot = ActivitySpot(
                    id = (spotMap?.get("id") as? Double)?.toLong() ?: 0L,
                    name = spotMap?.get("name") as? String ?: "未知地点",
                    typeTags = (spotMap?.get("typeTags")
                        ?: spotMap?.get("type_tags") as? String) as String?,
                    consumePerPerson = (spotMap?.get("consumePerPerson")
                        ?: spotMap?.get("consume_per_person")) as? Double,
                    address = spotMap?.get("address") as? String,
                    latitude = (spotMap?.get("latitude")) as? Double,
                    longitude = (spotMap?.get("longitude")) as? Double,
                    city = spotMap?.get("city") as? String,
                    district = spotMap?.get("district") as? String,
                    // 补全剩余7个字段
                    suggestTimePeriod = (spotMap?.get("suggestTimePeriod")
                        ?: spotMap?.get("suggest_time_period")) as? String,
                    suitableCapacity = (spotMap?.get("suitableCapacity")
                        ?: spotMap?.get("suitable_capacity")) as? String,
                    coverImageUrl = (spotMap?.get("coverImageUrl")
                        ?: spotMap?.get("cover_image_url")) as? String,
                    description = spotMap?.get("description") as? String,
                    recommendDuration = ((spotMap?.get("recommendDuration")
                        ?: spotMap?.get("recommend_duration")) as? Double)?.toInt(),
                    consumeLevel = (spotMap?.get("consumeLevel")
                        ?: spotMap?.get("consume_level")) as? String,
                    status = ((spotMap?.get("status")) as? Double)?.toInt()
                )

                GeneratedPlanItem(
                    order = index + 1,
                    startTime = map?.get("startTime") as? String ?: "",
                    endTime = map?.get("endTime") as? String ?: "",
                    segmentName = map?.get("segmentName") as? String ?: "",
                    spot = spot
                )
            }

            GeneratedPlan(
                templateName = plan.planName ?: "周末出行方案",
                items = generatedItems
            )
        } catch (_: Exception) { null }
    }

    /**
     * 方案列表 Adapter
     */
    private inner class SavedPlanAdapter(
        private val onItemClick: (CachedPlanEntity) -> Unit,
        private val onFavoriteClick: (CachedPlanEntity) -> Unit,
        private val onDeleteClick: (CachedPlanEntity) -> Unit
    ) : RecyclerView.Adapter<SavedPlanViewHolder>() {

        private var dataList: List<CachedPlanEntity> = emptyList()

        fun submitList(list: List<CachedPlanEntity>) {
            dataList = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedPlanViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_saved_plan, parent, false)
            return SavedPlanViewHolder(view, onItemClick, onFavoriteClick, onDeleteClick)
        }

        override fun onBindViewHolder(holder: SavedPlanViewHolder, position: Int) {
            holder.bind(dataList[position])
        }

        override fun getItemCount(): Int = dataList.size
    }

    /**
     * ViewHolder
     */
    private class SavedPlanViewHolder(
        itemView: View,
        private val onItemClick: (CachedPlanEntity) -> Unit,
        private val onFavoriteClick: (CachedPlanEntity) -> Unit,
        private val onDeleteClick: (CachedPlanEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvPlanName: TextView = itemView.findViewById(R.id.tv_plan_name)
        private val tvPlanSummary: TextView = itemView.findViewById(R.id.tv_plan_summary)
        private val tvPlanDate: TextView = itemView.findViewById(R.id.tv_plan_date)
        private val ivFavorite: ImageView = itemView.findViewById(R.id.iv_favorite)

        private var currentPlan: CachedPlanEntity? = null

        init {
            itemView.setOnClickListener { currentPlan?.let(onItemClick) }
            ivFavorite.setOnClickListener { currentPlan?.let(onFavoriteClick) }
            // 长按删除
            itemView.setOnLongClickListener {
                currentPlan?.let(onDeleteClick)
                true
            }
        }

        fun bind(plan: CachedPlanEntity) {
            currentPlan = plan
            tvPlanName.text = plan.planName ?: "未命名方案"

            // 解析摘要信息
            try {
                val data = com.google.gson.Gson().fromJson(
                    plan.planData,
                    com.google.gson.internal.LinkedTreeMap::class.java
                ) as? Map<String, Any>
                val items = data?.get("items") as? List<*>
                val count = items?.size ?: 0
                tvPlanSummary.text = "$count 个环节 · ${formatTime(plan.createdAt)}"
            } catch (_: Exception) {
                tvPlanSummary.text = formatTime(plan.createdAt)
            }

            tvPlanDate.text = formatDate(plan.createdAt)

            // 收藏图标（简单用alpha区分）
            ivFavorite.alpha = if (plan.isFavorited) 1.0f else 0.3f
        }

        private fun formatDate(timestamp: Long): String {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
            return sdf.format(java.util.Date(timestamp))
        }

        private fun formatTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            return when {
                diff < 60_000L -> "刚刚"
                diff < 3600_000L -> "${diff / 60_000L} 分钟前"
                diff < 86400_000L -> "${diff / 3600_000L} 小时前"
                else -> "${diff / 86400_000L} 天前"
            }
        }
    }
}
