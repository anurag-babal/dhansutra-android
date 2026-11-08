package com.example.dhansutra.domain.usecase

import com.example.dhansutra.domain.model.ExpenseType
import com.example.dhansutra.domain.repository.BudgetRepository
import com.example.dhansutra.domain.repository.CategoryRepository
import com.example.dhansutra.domain.repository.SettingsRepository
import com.example.dhansutra.domain.repository.TransactionRepository
import com.example.dhansutra.util.MonthYear
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps monthly budget rows (creation + carried-over amounts) in sync with categories, spending and
 * the rollover setting. Idempotent: only writes rows that are new or changed, so it is safe to call
 * any time. [start] re-runs automatically on spending / category / setting changes; call
 * [recalculate] after editing a budget allocation.
 */
class RolloverManager(
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository,
    private val budgets: BudgetRepository,
    private val settings: SettingsRepository,
) {
    private val mutex = Mutex()

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(
                categories.observeAll(),
                transactions.observeMonthCategoryExpenses(Long.MAX_VALUE, ExpenseType.REGULAR_OPERATING),
                settings.rolloverEnabled,
            ) { _, _, _ -> }.collect { recalculate() }
        }
    }

    suspend fun recalculate() = mutex.withLock {
        val cats = categories.getAll()
        val existing = budgets.observeAll().first()
        val spend = transactions.observeMonthCategoryExpenses(Long.MAX_VALUE, ExpenseType.REGULAR_OPERATING).first()
        val desired = RolloverCalculator.computeBudgets(
            categories = cats,
            existing = existing,
            spend = spend,
            rolloverEnabled = settings.rolloverEnabled.value,
            current = MonthYear.current(),
        )
        val existingByKey = existing.associateBy { it.monthYear to it.categoryId }
        for (row in desired) {
            val old = existingByKey[row.monthYear to row.categoryId]
            when {
                old == null -> budgets.insertIfAbsent(row)
                old != row -> budgets.update(row)
            }
        }
    }
}
