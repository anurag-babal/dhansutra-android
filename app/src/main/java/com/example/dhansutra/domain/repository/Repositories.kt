package com.example.dhansutra.domain.repository

import com.example.dhansutra.domain.model.BudgetEntry
import com.example.dhansutra.domain.model.Category
import com.example.dhansutra.domain.model.ExpenseType
import com.example.dhansutra.domain.model.MonthCategorySpend
import com.example.dhansutra.domain.model.MonthTotals
import com.example.dhansutra.domain.model.PeriodicExpenseSummary
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

    /** Total for [type] in the range. [expenseType] null = all expense types (real cash view). */
    fun observeTotal(
        type: TransactionType,
        startMillis: Long,
        endMillis: Long,
        expenseType: ExpenseType? = null,
    ): Flow<Long>

    suspend fun getTotal(
        type: TransactionType,
        startMillis: Long,
        endMillis: Long,
        expenseType: ExpenseType? = null,
    ): Long

    /** categoryId -> total, for [type] in the range. [expenseType] null = all expense types. */
    fun observeTotalsByCategory(
        type: TransactionType,
        startMillis: Long,
        endMillis: Long,
        expenseType: ExpenseType? = null,
    ): Flow<Map<Long, Long>>

    suspend fun getTotalsByCategory(
        type: TransactionType,
        startMillis: Long,
        endMillis: Long,
        expenseType: ExpenseType? = null,
    ): Map<Long, Long>

    /** Per-month income / all expenses / periodic expenses. */
    fun observeMonthTotals(startMillis: Long): Flow<List<MonthTotals>>

    /** Per-month, per-category expenses. [expenseType] null = all expense types. */
    fun observeMonthCategoryExpenses(
        endMillis: Long,
        expenseType: ExpenseType? = null,
    ): Flow<List<MonthCategorySpend>>

    /** Real cash balance: all-time income minus ALL expenses (regular + periodic). */
    fun observeCashBalance(): Flow<Long>

    /** Periodic / one-time expenses paid in the range, with their total and average monthly equivalent. */
    fun observePeriodicExpenses(startMillis: Long, endMillis: Long): Flow<PeriodicExpenseSummary>
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
