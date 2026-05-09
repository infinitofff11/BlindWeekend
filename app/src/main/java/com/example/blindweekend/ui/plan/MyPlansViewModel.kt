package com.example.blindweekend.ui.plan

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blindweekend.BlindWeekendApplication
import com.example.blindweekend.data.db.CachedPlanEntity
import kotlinx.coroutines.launch

/**
 * 我的方案 ViewModel
 *
 * 管理已保存/缓存方案的列表展示、收藏状态等
 */
class MyPlansViewModel : ViewModel() {

    /** 方案列表 */
    private val _plans = MutableLiveData<List<CachedPlanEntity>>(emptyList())
    val plans: LiveData<List<CachedPlanEntity>> = _plans

    /** 是否正在加载 */
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /** 是否为空列表 */
    private val _isEmpty = MutableLiveData<Boolean>(true)
    val isEmpty: LiveData<Boolean> = _isEmpty

    init {
        loadPlans()
    }

    /**
     * 从本地数据库加载所有缓存的方案
     */
    fun loadPlans() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val dao = BlindWeekendApplication.getDatabase().cachedPlanDao()
                val planList = dao.getAll()
                _plans.value = planList
                _isEmpty.value = planList.isEmpty()
            } catch (e: Exception) {
                _plans.value = emptyList()
                _isEmpty.value = true
            }
            _isLoading.value = false
        }
    }

    /**
     * 切换收藏状态
     */
    fun toggleFavorite(planId: Long, currentFavorited: Boolean) {
        viewModelScope.launch {
            try {
                val dao = BlindWeekendApplication.getDatabase().cachedPlanDao()
                dao.updateFavorite(planId, !currentFavorited)
                // 刷新列表
                loadPlans()
            } catch (_: Exception) {}
        }
    }

    /**
     * 删除方案
     */
    fun deletePlan(planId: Long) {
        viewModelScope.launch {
            try {
                val dao = BlindWeekendApplication.getDatabase().cachedPlanDao()
                dao.deleteById(planId)
                loadPlans()
            } catch (_: Exception) {}
        }
    }
}
