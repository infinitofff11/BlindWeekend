package com.example.blindweekend.ui.plan

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blindweekend.data.model.GeneratedPlan
import com.example.blindweekend.data.model.GeneratedPlanItem
import com.example.blindweekend.data.repository.PlanGeneratorRepository
import com.example.blindweekend.data.repository.SpotRepository
import kotlinx.coroutines.launch

/**
 * 周末玩法生成器 ViewModel
 *
 * 负责管理"一键生成方案"的UI状态和业务逻辑
 */
class PlanGeneratorViewModel(
    private val planRepo: PlanGeneratorRepository = PlanGeneratorRepository(
        com.example.blindweekend.network.RetrofitClient.api,
        SpotRepository(com.example.blindweekend.network.RetrofitClient.api)
    )
) : ViewModel() {

    // ==================== UI状态 ====================

    /** 是否正在加载 */
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /** 生成的方案 */
    private val _generatedPlan = MutableLiveData<GeneratedPlan?>()
    val generatedPlan: LiveData<GeneratedPlan?> = _generatedPlan

    /** 错误信息 */
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    /** 当前已使用的活动点ID集合（用于替换时排除） */
    private var usedSpotIds = mutableSetOf<Long>()

    // ==================== 公开方法 ====================

    /**
     * 一键生成周末方案
     *
     * @param city 城市
     * @param consumeLevel 消费水平: "low"/"high"
     * @param themeTypes 兴趣标签列表
     */
    fun generatePlan(city: String, consumeLevel: String, themeTypes: List<String>?) {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            val result = planRepo.generatePlan(
                city = city,
                consumeLevel = consumeLevel,
                themeTypes = themeTypes
            )

            if (result.isSuccess) {
                val plan = result.getOrThrow()
                _generatedPlan.value = plan
                // 记录已使用的活动点ID
                usedSpotIds = plan.items.map { it.spot.id }.toMutableSet()
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "生成失败"
            }

            _isLoading.value = false
        }
    }

    /**
     * 替换某个环节（换一批）
     *
     * @param order 要替换的环节序号
     * @param city 城市
     */
    fun replaceItem(order: Int, city: String) {
        val currentPlan = _generatedPlan.value ?: return
        val currentItem = currentPlan.items.find { it.order == order } ?: return

        _isLoading.value = true
        viewModelScope.launch {
            val result = planRepo.replacePlanItem(
                city = city,
                currentItem = currentItem,
                existingItemIds = usedSpotIds.toSet(),
                activityTypeTags = currentItem.spot.getTypeTagList(),
                consumeLevel = currentItem.spot.consumeLevel
            )

            if (result.isSuccess) {
                val newItem = result.getOrThrow()
                // 更新方案中对应的环节
                val updatedItems = currentPlan.items.map { item ->
                    if (item.order == order) newItem else item
                }
                _generatedPlan.value = currentPlan.copy(items = updatedItems)
                // 更新已使用ID集合
                usedSpotIds.add(newItem.spot.id)
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "更换失败"
            }

            _isLoading.value = false
        }
    }

    /**
     * 重新生成整个方案（换个感觉）
     */
    fun regeneratePlan(city: String, consumeLevel: String, themeTypes: List<String>?) {
        usedSpotIds.clear()
        generatePlan(city, consumeLevel, themeTypes)
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
