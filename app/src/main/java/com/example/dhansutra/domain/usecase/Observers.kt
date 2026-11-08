package com.example.dhansutra.domain.usecase

import kotlinx.coroutines.flow.map

import com.example.dhansutra.domain.model.BudgetItem
import com.example.dhansutra.domain.model.CategoryBreakdown
import com.example.dhansutra.domain.model.DashboardSummary
import com.example.dhansutra.domain.model.MonthlyTrendPoint
import com.example.dhansutra.domain.model.TransactionType
import com.example.dhansutra.domain.repository.BudgetRepository
import com.example.dhansutra.domain.repository.CategoryRepository
import com.example.dhansutra.domain.repository.SettingsRepository
import com.example.dhansutra.domain.repository.TransactionRepository
import com.example.dhansutra.util.MonthYear
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth

/** Per-category allocated / carried over / spent for one month. */
class ObserveMonthlyBudget(
    private val categories: CategoryRepository,
    private val budgets: BudgetRepository,
    private val transactions: TransactionRepository,
) {
    operator fun invoke(month: YearMonth): Flow<List<BudgetItem>> = combine(
        categories.observeAll(),
        budgets.observeForMonth(MonthYear.key(month)),
        transactions.observeTotalsByCategory(
            TransactionType.EXPENSE,
            MonthYear.startMillis(month),
            MonthYear.endMillis(month),
        ),
    ) { cats, rows, spent ->
        val rowsByCat = rows.associateBy { it.categoryId }
        cats.mapNotNull { cat ->
            val row = rowsByCat[cat.id]
            val spentAmount = spent[cat.id] ?: 0L
            if (row == null && spentAmount == 0L) {
                null
            } else {
                BudgetItem(cat, row?.allocatedAmount ?: cat.defaultBudget, row?.carriedOverAmount ?: 0L, spentAmount)
            }
        }
    }
}

/** Header-card numbers for the dashboard. Income/spent are for [month]; savings = income - expenses of closed months. */
class ObserveDashboardSummary(private val transactions: TransactionRepository) {
    operator fun invoke(month: YearMonth): Flow<DashboardSummary> {
        val start = MonthYear.startMillis(month)
        val end = MonthYear.endMillis(month)
        val monthly = combine(
            transactions.observeTotal(TransactionType.INCOME, start, end),
            transactions.observeTotal(TransactionType.EXPENSE, start, end),
        ) { income, spent -> income to spent }
        val savings = transactions.observeMonthTotals(0L).map { totals ->
            RolloverCalculator.computeSavings(totals, MonthYear.current())
        }
        return combine(monthly, savings) { (income, spent), saved ->
            DashboardSummary(income = income, spent = spent, surplus = income - spent, savings = saved)
        }
    }
}

/** Expense total per category for one month, largest first (zero entries dropped). */
class ObserveCategoryBreakdown(
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository,
) {
    operator fun invoke(month: YearMonth): Flow<List<CategoryBreakdown>> = combine(
        categories.observeAll(),
        transactions.observeTotalsByCategory(
            TransactionType.EXPENSE,
            MonthYear.startMillis(month),
            MonthYear.endMillis(month),
        ),
    ) { cats, totals ->
        cats.mapNotNull { c -> totals[c.id]?.takeIf { it > 0 }?.let { CategoryBreakdown(c.id, c.name, it) } }
            .sortedByDescending { it.amount }
    }
}

/** Spent/income for each of the last [months] months ending at [end] (months without data are zero). */
class ObserveMonthlyTrend(private val transactions: TransactionRepository) {
    operator fun invoke(end: YearMonth, months: Int): Flow<List<MonthlyTrendPoint>> {
        val first = end.minusMonths((months - 1).toLong())
        return transactions.observeMonthTotals(MonthYear.startMillis(first)).let { flow ->
            kotlinx.coroutines.flow.flow {
                flow.collect { totals ->
                    val byMonth = totals.associateBy { it.monthYear }
                    emit(
                        MonthYear.range(first, end).map { m ->
                            val t = byMonth[MonthYear.key(m)]
                            MonthlyTrendPoint(m, t?.spent ?: 0L, t?.income ?: 0L)
                        },
                    )
                }
            }
        }
    }
}
