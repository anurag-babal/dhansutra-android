package com.example.dhansutra.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.dhansutra.data.local.entity.MonthlyBudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthlyBudgetDao {
    @Query("SELECT * FROM monthly_budgets WHERE monthYear = :monthYear")
    fun observeForMonth(monthYear: String): Flow<List<MonthlyBudgetEntity>>

    @Query("SELECT * FROM monthly_budgets WHERE monthYear = :monthYear")
    suspend fun getForMonth(monthYear: String): List<MonthlyBudgetEntity>

    @Query("SELECT * FROM monthly_budgets WHERE monthYear = :monthYear AND categoryId = :categoryId")
    suspend fun get(monthYear: String, categoryId: Long): MonthlyBudgetEntity?

    @Query("SELECT DISTINCT monthYear FROM monthly_budgets ORDER BY monthYear")
    fun observeMonths(): Flow<List<String>>

    /** Returns the new row id, or -1 if a row for (monthYear, categoryId) already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(budget: MonthlyBudgetEntity): Long

    @Update
    suspend fun update(budget: MonthlyBudgetEntity)
}
