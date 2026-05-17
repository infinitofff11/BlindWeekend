package com.example.blindweekend.ui.blindbox

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import com.example.blindweekend.R
import com.example.blindweekend.auth.AuthManager
import com.example.blindweekend.data.model.BlindBoxCreateRequest
import com.example.blindweekend.data.model.GeneratedPlan
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 发布盲盒 BottomSheetDialog
 *
 * 支持两种场景：
 * 1. 从方案页发布：plan 非空，自动预填充标题/标签/区域
 * 2. 从盲盒广场直接发布：plan 为空，用户手动填写全部信息
 */
class PublishBlindBoxDialog(
    context: android.content.Context,
    private val plan: GeneratedPlan? = null,
    private val onPublished: () -> Unit = {}
) : BottomSheetDialog(context) {

    // 支持的城市列表
    private val supportedCities = listOf("北京", "惠州")
    private var selectedCity: String = "北京"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_publish_blindbox)

        // 绑定视图
        val etTitle = findViewById<TextInputEditText>(R.id.et_title)!!
        val tilTitle = findViewById<TextInputLayout>(R.id.til_title)!!
        val etMood = findViewById<TextInputEditText>(R.id.et_mood)!!
        val etDate = findViewById<TextInputEditText>(R.id.et_date)!!
        val etTimePeriod = findViewById<TextInputEditText>(R.id.et_time_period)!!
        val etRequiredCount = findViewById<TextInputEditText>(R.id.et_required_count)!!
        val tilCount = findViewById<TextInputLayout>(R.id.til_required_count)!!
        val actvCity = findViewById<MaterialAutoCompleteTextView>(R.id.actv_city)!!
        val tilCity = findViewById<TextInputLayout>(R.id.til_city)!!
        val etDistrict = findViewById<TextInputEditText>(R.id.et_district)!!
        val etTags = findViewById<TextInputEditText>(R.id.et_tags)!!
        val btnPublish = findViewById<MaterialButton>(R.id.btn_publish)!!

        // 初始化城市选择器
        val cityAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, supportedCities)
        actvCity.setAdapter(cityAdapter)
        actvCity.setOnItemClickListener { _, _, position, _ ->
            selectedCity = supportedCities[position]
        }

        // 预填充：有方案数据时自动填入（从方案页发布）
        plan?.let { p ->
            // 从方案名称生成默认标题
            etTitle.setText(p.templateName)

            // 从方案环节提取类型标签
            val allTags = p.items.flatMap { item ->
                item.spot.getTypeTagList()
            }.distinct()
            if (allTags.isNotEmpty()) {
                etTags.setText(allTags.joinToString(","))
            }

            // 提取城市（取第一个地点的城市）
            p.items.firstOrNull()?.spot?.city?.let { city ->
                selectedCity = city
                actvCity.setText(city, false)
            }

            // 提取区域（取第一个地点的区域）
            p.items.firstOrNull()?.spot?.district?.let { district ->
                etDistrict.setText(district)
            }
        }

        // 如果没有方案数据，设置默认城市
        if (plan == null) {
            actvCity.setText(selectedCity, false)
        }

        // 默认需要2人
        etRequiredCount.setText("2")

        // 日期选择器：点击弹出 DatePickerDialog，自动格式化为 yyyy-MM-dd
        val calendar = java.util.Calendar.getInstance()
        etDate.isFocusable = false
        etDate.isFocusableInTouchMode = false
        etDate.setOnClickListener {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    etDate.setText("%04d-%02d-%02d".format(year, month + 1, dayOfMonth))
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }

        // 发布按钮点击
        btnPublish.setOnClickListener {
            var valid = true

            if (etTitle.text.isNullOrBlank()) {
                tilTitle.error = "请输入标题"
                valid = false
            } else {
                tilTitle.error = null
            }

            // 验证城市
            val cityText = actvCity.text?.toString()?.trim() ?: ""
            if (cityText.isBlank()) {
                tilCity.error = "请选择城市"
                valid = false
            } else {
                selectedCity = cityText
                tilCity.error = null
            }

            if (etRequiredCount.text.isNullOrBlank() || (etRequiredCount.text.toString().toIntOrNull() ?: 0) < 1) {
                tilCount.error = "请输入有效人数"
                valid = false
            } else {
                tilCount.error = null
            }

            if (!valid) return@setOnClickListener

            // 构建请求数据对象（Gson 可正确序列化所有字段）
            val requestData = BlindBoxCreateRequest(
                publisherId = AuthManager.currentUser?.id ?: 1,
                title = etTitle.text.toString().trim(),
                requiredCount = etRequiredCount.text.toString().toInt(),
                city = selectedCity,
                moodText = etMood.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                activityDate = etDate.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                activityTimePeriod = etTimePeriod.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                district = etDistrict.text?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                activityTypeTags = etTags.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { tags ->
                    // 将逗号分隔文本转为JSON数组字符串，适配MySQL JSON类型列
                    try {
                        JSONArray(tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray()).toString()
                    } catch (_: Exception) { tags }
                }
            )

            btnPublish.isEnabled = false
            btnPublish.text = "⏳ 发布中..."

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val api = com.example.blindweekend.network.RetrofitClient.api
                    val response = withContext(Dispatchers.IO) {
                        api.createBlindBox(requestData)
                    }

                    if (response.isSuccessful && response.body()?.code == 200) {
                        Toast.makeText(context, "📦 盲盒发布成功！", Toast.LENGTH_SHORT).show()
                        onPublished()
                        dismiss()
                    } else {
                        Toast.makeText(
                            context,
                            response.body()?.message ?: "发布失败",
                            Toast.LENGTH_SHORT
                        ).show()
                        resetButton(btnPublish)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "发布失败：${e.message}", Toast.LENGTH_SHORT).show()
                    resetButton(btnPublish)
                }
            }
        }
    }

    private fun resetButton(btn: MaterialButton) {
        btn.isEnabled = true
        btn.text = "🚀 发布盲盒"
    }
}
