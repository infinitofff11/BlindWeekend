package com.example.blindweekend.ui.blindbox

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.blindweekend.data.model.BlindBox
import com.example.blindweekend.data.model.PageResponse
import com.example.blindweekend.network.RetrofitClient
import kotlinx.coroutines.launch

/**
 * 盲盒广场 ViewModel
 */
class BlindBoxSquareViewModel(
    private val api: com.example.blindweekend.network.BlindWeekendApi = RetrofitClient.api
) : ViewModel() {

    /** 加载状态 */
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /** 盲盒列表 */
    private val _blindBoxes = MutableLiveData<List<BlindBox>>(emptyList())
    val blindBoxes: LiveData<List<BlindBox>> = _blindBoxes

    /** 错误信息 */
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    /** 是否还有更多数据 */
    private var hasMore = true
    private var currentPage = 1

    // ==================== 筛选状态 ====================

    /** 当前筛选城市 */
    private var filterCity: String? = null

    /** 当前筛选标签列表 */
    private var filterTags: List<String> = emptyList()

    /** 当前筛选的标签描述（用于UI显示） */
    private val _filterDescription = MutableLiveData<String>("")
    val filterDescription: LiveData<String> = _filterDescription

    /**
     * 设置筛选条件
     */
    fun setFilter(city: String?, tags: List<String>) {
        filterCity = city
        filterTags = tags
        updateFilterDescription()
        refreshBlindBoxes()
    }

    /**
     * 清除筛选条件
     */
    fun clearFilter() {
        filterCity = null
        filterTags = emptyList()
        updateFilterDescription()
        refreshBlindBoxes()
    }

    /**
     * 获取当前筛选城市
     */
    fun getFilterCity(): String? = filterCity

    /**
     * 获取当前筛选标签
     */
    fun getFilterTags(): List<String> = filterTags

    /**
     * 更新筛选描述文字
     */
    private fun updateFilterDescription() {
        val parts = mutableListOf<String>()
        if (filterCity != null) parts.add(filterCity!!)
        if (filterTags.isNotEmpty()) parts.add(filterTags.joinToString(","))
        _filterDescription.value = if (parts.isEmpty()) "" else parts.joinToString(" · ")
    }

    /**
     * 刷新盲盒列表（下拉刷新）
     */
    fun refreshBlindBoxes() {
        currentPage = 1
        hasMore = true
        loadBlindBoxes(isRefresh = true)
    }

    /**
     * 加载更多（上拉加载）
     */
    fun loadMore() {
        if (!hasMore || (_isLoading.value == true)) return
        loadBlindBoxes(isRefresh = false)
    }

    private fun loadBlindBoxes(isRefresh: Boolean) {
        _isLoading.value = true

        // 构建筛选参数
        val tagsParam = if (filterTags.isNotEmpty()) filterTags.joinToString(",") else null

        viewModelScope.launch {
            try {
                val response = api.getBlindBoxList(
                    pageNum = currentPage,
                    pageSize = 20,
                    status = "open",
                    city = filterCity,
                    tags = tagsParam
                )

                if (response.isSuccessful && response.body()?.code == 200) {
                    val pageData = response.body()?.data
                    val list = pageData?.list ?: emptyList()

                    if (isRefresh) {
                        _blindBoxes.value = list
                    } else {
                        _blindBoxes.value = (_blindBoxes.value ?: emptyList()) + list
                    }

                    hasMore = currentPage < (pageData?.pages ?: 1)
                    currentPage++
                } else {
                    _errorMessage.value = response.message()
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "加载失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
