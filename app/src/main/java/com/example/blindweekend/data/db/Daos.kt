package com.example.blindweekend.data.db

import androidx.room.*

/**
 * 用户偏好 DAO
 */
@Dao
interface PreferenceDao {

    @Query("SELECT * FROM user_preferences WHERE userId = :userId")
    suspend fun getByUserId(userId: Long): UserPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(preference: UserPreferenceEntity)

    @Query("DELETE FROM user_preferences WHERE userId = :userId")
    suspend fun deleteByUserId(userId: Long)
}

/**
 * 缓存方案 DAO
 */
@Dao
interface CachedPlanDao {

    @Query("SELECT * FROM cached_plans ORDER BY createdAt DESC")
    suspend fun getAll(): List<CachedPlanEntity>

    @Query("SELECT * FROM cached_plans WHERE planId = :planId")
    suspend fun getByPlanId(planId: Long): CachedPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: CachedPlanEntity)

    @Update
    suspend fun update(plan: CachedPlanEntity)

    @Delete
    suspend fun delete(plan: CachedPlanEntity)

    @Query("DELETE FROM cached_plans WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE cached_plans SET isFavorited = :favorited WHERE id = :id")
    suspend fun updateFavorite(id: Long, favorited: Boolean)
}
