package com.example.blindweekend.ui.blindbox

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.blindweekend.R
import com.example.blindweekend.auth.AuthManager
import com.example.blindweekend.data.model.BlindBox
import com.example.blindweekend.databinding.FragmentBlindBoxSquareBinding

/**
 * 盲盒广场 Fragment
 *
 * 功能：
 * - 浏览同城盲盒信息流
 * - 筛选活动类型
 * - 意向响应（想组队）
 */
class BlindBoxSquareFragment : Fragment() {

    private var _binding: FragmentBlindBoxSquareBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: BlindBoxSquareViewModel
    private lateinit var adapter: BlindBoxAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentBlindBoxSquareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化ViewModel和Adapter
        viewModel = BlindBoxSquareViewModel()
        adapter = BlindBoxAdapter(
            onItemClick = { blindBox -> onBlindBoxClicked(blindBox) },
            onJoinClick = { blindBox -> onJoinClicked(blindBox) }
        )

        // 设置RecyclerView
        binding.rvBlindBoxes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBlindBoxes.adapter = adapter

        // 下拉刷新
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshBlindBoxes()
        }

        // 加载更多
        binding.rvBlindBoxes.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (!recyclerView.canScrollVertically(1)) {
                    viewModel.loadMore()
                }
            }
        })

        // 观察数据变化
        viewModel.blindBoxes.observe(viewLifecycleOwner) { boxes ->
            adapter.submitList(boxes)
            binding.layoutEmpty.visibility = if (boxes.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }

        // 首次加载数据
        viewModel.refreshBlindBoxes()

        // 顶部标题栏 - 发布盲盒按钮（直接弹出发布表单）
        binding.root.findViewById<View>(R.id.btn_publish_header).setOnClickListener {
            if (AuthManager.currentUser == null) {
                Toast.makeText(requireContext(), "请先登录后再发布盲盒", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 直接弹出发布盲盒对话框
            val dialog = PublishBlindBoxDialog(
                context = requireContext(), // 必须用 Activity 上下文，否则 BadTokenException
                plan = null, // 从盲盒广场直接发布，无方案数据
                onPublished = {
                    Toast.makeText(requireContext(), "📦 盲盒发布成功！", Toast.LENGTH_SHORT).show()
                    // 发布成功后刷新列表
                    viewModel.refreshBlindBoxes()
                }
            )
            dialog.show()
        }

        // 空状态提示点击也弹出发布框
        binding.tvEmptyPublishHint.setOnClickListener {
            if (AuthManager.currentUser != null) {
                val dialog = PublishBlindBoxDialog(
                    context = requireContext(),
                    onPublished = {
                        viewModel.refreshBlindBoxes()
                    }
                )
                dialog.show()
            } else {
                Toast.makeText(requireContext(), "请先登录后再发布盲盒", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onBlindBoxClicked(blindBox: BlindBox) {
        // 跳转到盲盒详情页
        val bundle = Bundle().apply { putParcelable("blind_box", blindBox) }
        findNavController().navigate(R.id.blind_box_detail, bundle)
    }

    private fun onJoinClicked(blindBox: BlindBox) {
        // 点击"想组队"按钮也跳转到详情页（详情页有完整的参与按钮）
        onBlindBoxClicked(blindBox)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * 盲盒列表 Adapter
     */
    class BlindBoxAdapter(
        private val onItemClick: (BlindBox) -> Unit,
        private val onJoinClick: (BlindBox) -> Unit = {}
    ) : RecyclerView.Adapter<BlindBoxAdapter.BlindBoxViewHolder>() {

        private val items = mutableListOf<BlindBox>()

        fun submitList(list: List<BlindBox>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlindBoxViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_blind_box_card, parent, false)
            return BlindBoxViewHolder(view, onItemClick, onJoinClick)
        }

        override fun onBindViewHolder(holder: BlindBoxViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class BlindBoxViewHolder(
            itemView: View,
            private val onItemClick: (BlindBox) -> Unit,
            private val onJoinClick: (BlindBox) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {

            fun bind(blindBox: BlindBox) {
                itemView.apply {
                    findViewById<TextView>(R.id.tv_blindbox_title).text = blindBox.title
                    findViewById<TextView>(R.id.tv_mood_text).text =
                        blindBox.moodText ?: ""
                    findViewById<TextView>(R.id.tv_district).text =
                        "📍 ${blindBox.district ?: "未知区域"}"
                    findViewById<TextView>(R.id.tv_time_info).text =
                        "🕐 ${blindBox.activityTimePeriod ?: ""}"

                    // 剩余名额
                    val remainingChip = findViewById<TextView>(R.id.chip_remaining)
                    val remaining = blindBox.getRemainingSlots()
                    remainingChip.text = if (remaining > 0) "缺${remaining}人" else "已满"

                    // 发布者名称（可做匿名处理）
                    findViewById<TextView>(R.id.tv_publisher_name).text = "用户${blindBox.publisherId % 1000}"

                    // 类型标签 - 使用 LinearLayout 容器动态添加标签
                    val tagContainer = findViewById<LinearLayout>(R.id.chipgroup_tags)
                    tagContainer.removeAllViews()
                    blindBox.getTypeTagList().forEach { tag ->
                        val tagView = TextView(context).apply {
                            text = tag
                            textSize = 11f
                            setTextColor(Color.WHITE)
                            setPadding(24, 6, 24, 6)
                            gravity = Gravity.CENTER
                            setBackgroundResource(R.drawable.bg_tag_chip)
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

                    // 点击整个卡片
                    setOnClickListener { onItemClick(blindBox) }

                    // 想组队按钮
                    findViewById<View>(R.id.btn_join).setOnClickListener { onJoinClick(blindBox) }
                }
            }
        }
    }
}
