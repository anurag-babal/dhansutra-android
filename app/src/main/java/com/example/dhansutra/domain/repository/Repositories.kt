package com.example.dhansutra.domain.repository

import com.example.dhansutra.domain.model.BudgetEntry
import com.example.dhansutra.domain.model.Category
import com.example.dhansutra.domain.model.MonthCategorySpend
import com.example.dhansutra.domain.model.MonthTotals
import com.example.dhansutra.domain.model.Transaction
import com.example.dhansutra.domain.model.TransactionFilter
import com.example.dhansutra.domain.model.TransactionItem
import com.example.dhansutra.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun getAll(): List<Category>
    suspend fun add(category: Category): Long
    suspend fun update(category: Category)

    /** @return false (and does nothing) if the category still has transactions. */
    suspend fun delete(categoryId: Long): Boolean
}

interface TransactionRepository {
    fun observeFiltered(filter: TransactionFilter): Flow<List<TransactionItem>>
    suspend fun getById(id: Long): Transaction?
    suspend fun save(transaction: Transaction): Long
    suspend fun delete(id: Long)

    fun observeTotal(type: TransactionType, startMillis: Long, endMillis: Long): Flow<Long>
    suspend fun getTotal(type: TransactionType, startMillis: Long, endMillis: Long): Long

    /** categoryId -> total, for [type] in the range. */
    fun observeTotalsByCategory(type: TransactionType, startMillis: Long, endMillis: Long): Flow<Map<Long, Long>>
    suspend fun getTotalsByCategory(type: TransactionType, startMillis: Long, endMillis: Long): Map<Long, Long>

    fun observeMonthTotals(startMillis: Long): Flow<List<MonthTotals>>
    fun observeMonthCategoryExpenses(endMillis: Long): Flow<List<MonthCategorySpend>>
}

interface BudgetRepository {
    fun observeAll(): Flow<List<BudgetEntry>>
    fun observeForMonth(monthYear: String): Flow<List<BudgetEntry>>
    suspend fun getForMonth(monthYear: String): List<BudgetEntry>
    suspend fun getEarliestMonth(): String?
    suspend fun get(monthYear: String, categoryId: Long): BudgetEntry?

    /** @return true if inserted, false if a row already existed. */
    suspend fun insertIfAbsent(entry: BudgetEntry): Boolean
    suspend fun update(entry: BudgetEntry)
}

interface SettingsRepository {
    /** Global switch for month-to-month rollover (carry forward and sweep). */
    val rolloverEnabled: StateFlow<Boolean>
    fun setRolloverEnabled(enabled: Boolean)
}
