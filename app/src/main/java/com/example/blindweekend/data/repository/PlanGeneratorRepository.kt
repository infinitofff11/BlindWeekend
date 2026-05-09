package com.example.blindweekend.data.repository

import com.example.blindweekend.data.model.*
import com.example.blindweekend.network.BlindWeekendApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 方案生成仓库 - 核心业务逻辑：智能拼装周末行程方案
 *
 * 生成逻辑：
 * 1. 从后台获取启用的方案模板
 * 2. 根据用户偏好筛选合适的模板
 * 3. 随机选择一个模板
 * 4. 为每个时段从活动点库中匹配活动点
 * 5. 组装成完整的时间轴方案
 */
class PlanGeneratorRepository(
    private val api: BlindWeekendApi,
    private val spotRepository: SpotRepository
) {

    /**
     * 生成一键方案
     *
     * @param city 用户所在城市
     * @param consumeLevel 消费水平 low/high
     * @param themeTypes 偏好的活动类型标签
     */
    suspend fun generatePlan(
        city: String,
        consumeLevel: String = "low",
        themeTypes: List<String>? = null
    ): Result<GeneratedPlan> {
        return withContext(Dispatchers.IO) {
            try {
                // Step 1: 获取所有启用的活动点
                val spotsResult = spotRepository.getActiveSpots(city)
                if (spotsResult.isFailure) {
                    return@withContext Result.failure(spotsResult.exceptionOrNull()!!)
                }
                val allSpots = spotsResult.getOrThrow()

                if (allSpots.isEmpty()) {
                    return@withContext Result.failure(Exception("当前城市暂无可用活动点"))
                }

                // Step 2: 按用户偏好筛选活动点
                val filteredSpots = spotRepository.filterSpots(allSpots, tags = themeTypes, consumeLevel = consumeLevel)
                val candidateSpots = if (filteredSpots.isNotEmpty()) filteredSpots else allSpots

                // Step 3: 获取可用的方案模板
                val templatesResponse = api.getActiveTemplates(consumeLevel, themeTypes?.firstOrNull())
                if (!templatesResponse.isSuccessful || templatesResponse.body()?.code != 200) {
                    return@withContext Result.failure(Exception("获取方案模板失败"))
                }

                val templates = templatesResponse.body()?.data
                if (templates.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("暂无可用方案模板"))
                }

                // Step 4: 随机选择一个模板
                val selectedTemplate = templates.random()

                // Step 5: 获取模板的时段配置
                val segmentsResponse = api.getTemplateSegments(selectedTemplate.id)
                val segments = if (segmentsResponse.isSuccessful && segmentsResponse.body()?.code == 200) {
                    segmentsResponse.body()?.data ?: emptyList()
                } else {
                    emptyList()
                }

                // 如果没有时段配置，使用默认配置
                val finalSegments = if (segments.isEmpty()) {
                    createDefaultSegments(selectedTemplate)
                } else {
                    segments.sortedBy { it.segmentOrder }
                }

                // Step 6: 为每个时段匹配活动点
                val planItems = finalSegments.mapNotNull { segment ->
                    matchSpotForSegment(candidateSpots, segment)
                }

                if (planItems.isEmpty()) {
                    return@withContext Result.failure(Exception("无法匹配到合适的活动点，请尝试调整偏好设置"))
                }

                // 组装最终方案
                val plan = GeneratedPlan(
                    templateName = selectedTemplate.name,
                    templateDescription = selectedTemplate.description,
                    items = planItems
                )

                Result.success(plan)

            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 替换某个环节的活动点（"换一批"功能）
     */
    suspend fun replacePlanItem(
        city: String,
        currentItem: GeneratedPlanItem,
        existingItemIds: Set<Long>,
        activityTypeTags: List<String>?,
        consumeLevel: String?
    ): Result<GeneratedPlanItem> {
        return withContext(Dispatchers.IO) {
            try {
                val spotsResult = spotRepository.getActiveSpots(city)
                if (spotsResult.isFailure) return@withContext Result.failure(spotsResult.exceptionOrNull()!!)

                var candidates = spotsResult.getOrThrow()
                    .filter { it.id !in existingItemIds } // 排除已使用的

                // 按类型筛选
                if (!activityTypeTags.isNullOrEmpty()) {
                    candidates = candidates.filter { spot ->
                        spot.getTypeTagList().any { it in activityTypeTags }
                    }
                }
                // 按消费水平筛选
                if (consumeLevel != null) {
                    candidates = candidates.filter { it.consumeLevel == consumeLevel }
                }

                if (candidates.isEmpty()) {
                    // 放宽条件，从全部候选中选（排除已使用的）
                    candidates = spotsResult.getOrThrow().filter { it.id !in existingItemIds }
                }

                if (candidates.isEmpty()) {
                    return@withContext Result.failure(Exception("没有更多可选的活动点了"))
                }

                val newSpot = candidates.random()

                Result.success(
                    GeneratedPlanItem(
                        order = currentItem.order,
                        startTime = currentItem.startTime,
                        endTime = currentItem.endTime,
                        segmentName = currentItem.segmentName,
                        spot = newSpot
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 为单个时段匹配活动点
     */
    private fun matchSpotForSegment(
        spots: List<ActivitySpot>,
        segment: TemplateSegment
    ): GeneratedPlanItem? {
        val typeTags = segment.getActivityTypeList()
        val candidates = if (typeTags.isNotEmpty()) {
            // 筛选符合该时段类型标签的活动点
            spots.filter { spot ->
                spot.getTypeTagList().any { tag -> tag in typeTags }
            }
        } else {
            spots
        }.ifEmpty { spots } // 如果没有匹配的，放宽到全部

        if (candidates.isEmpty()) return null

        val selectedSpot = candidates.random()
        return GeneratedPlanItem(
            order = segment.segmentOrder,
            startTime = segment.startTime,
            endTime = segment.endTime,
            segmentName = segment.segmentName,
            spot = selectedSpot
        )
    }

    /**
     * 创建默认时段配置（当后台没有配置时）
     */
    private fun createDefaultSegments(template: PlanTemplate): List<TemplateSegment> {
        // 根据总时长自动拆分时段
        val duration = template.totalDuration ?: 240 // 默认4小时

        return when {
            duration <= 180 -> listOf( // 半日(3小时以内)
                TemplateSegment(id = 0, templateId = template.id, segmentOrder = 1,
                    startTime = "09:00", endTime = "10:30", activityTypes = null, segmentName = "第一站"),
                TemplateSegment(id = 0, templateId = template.id, segmentOrder = 2,
                    startTime = "10:45", endTime = "12:15", activityTypes = null, segmentName = "第二站")
            )
            duration <= 360 -> listOf( // 一日(6小时以内)
                TemplateSegment(id = 0, templateId = template.id, segmentOrder = 1,
                    startTime = "09:30", endTime = "11:00", activityTypes = null, segmentName = "上午场"),
                TemplateSegment(id = 0, templateId = template.id, segmentOrder = 2,
                    startTime = "11:30", endTime = "13:00", activityTypes = null, segmentName = "午餐时光"),
                TemplateSegment(id = 0, templateId = template.id, segmentOrder = 3,
                    startTime = "14:00", endTime = "16:00", activityTypes = null, segmentName = "下午场")
            )
            else -> listOf(
                TemplateSegment(id = 0, templateId = template.id, segmentOrder = 1,
                    startTime = "09:00", endTime = "11:00", activityTypes = null, segmentName = "上午"),
                TemplateSegment(id = 0, templateId = template.id, segmentOrder = 2,
                    startTime = "11:30", endTime = "13:00", activityTypes = null, segmentName = "午餐"),
                TemplateSegment(id = 0, templateId = template.id, segmentOrder = 3,
                    startTime = "14:00", endTime = "16:00", activityTypes = null, segmentName = "下午前半"),
                TemplateSegment(id = 0, templateId = template.id, segmentOrder = 4,
                    startTime = "16:30", endTime = "18:00", activityTypes = null, segmentName = "下午后半")
            )
        }
    }
}
