package com.example.dhansutra.domain.usecase

import kotlinx.coroutines.flow.map

import com.example.dhansutra.domain.model.BudgetItem
import com.example.dhansutra.domain.model.CategoryBreakdown
import com.example.dhansutra.domain.model.DashboardSummary
import com.example.dhansutra.domain.model.ExpenseType
import com.example.dhansutra.domain.model.MonthlyTrendPoint
import com.example.dhansutra.domain.model.PeriodicExpenseSummary
import com.example.dhansutra.domain.model.TransactionType
import com.example.dhansutra.domain.repository.BudgetRepository
import com.example.dhansutra.domain.repository.CategoryRepository
import com.example.dhansutra.domain.repository.SettingsRepository
import com.example.dhansutra.domain.repository.TransactionRepository
import com.example.dhansutra.util.MonthYear
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth

/**
 * Per-category allocated / carried over / spent for one month. Spent counts only
 * [ExpenseType.REGULAR_OPERATING] expenses so periodic lump sums never distort monthly budgets.
 */
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
            ExpenseType.REGULAR_OPERATING,
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
            // Hide categories with no budget, no carry-over and no spending (e.g. "Salary & Income").
            .filter { it.allocated != 0L || it.carriedOver != 0L || it.spent != 0L }
    }
}

/**
 * Header-card numbers for the dashboard. Income / regular spent / periodic spent are for [month];
 * savings = income - ALL expenses of closed months; cashBalance = all-time income - ALL expenses.
 */
class ObserveDashboardSummary(private val transactions: TransactionRepository) {
    operator fun invoke(month: YearMonth): Flow<DashboardSummary> {
        val start = MonthYear.startMillis(month)
        val end = MonthYear.endMillis(month)
        val monthly = combine(
            transactions.observeTotal(TransactionType.INCOME, start, end),
            transactions.observeTotal(TransactionType.EXPENSE, start, end, ExpenseType.REGULAR_OPERATING),
            transactions.observeTotal(TransactionType.EXPENSE, start, end, ExpenseType.PERIODIC_ONE_TIME),
        ) { income, regular, periodic -> Triple(income, regular, periodic) }
        val savings = transactions.observeMonthTotals(0L).map { totals ->
            RolloverCalculator.computeSavings(totals, MonthYear.current())
        }
        return combine(monthly, savings, transactions.observeCashBalance()) { (income, regular, periodic), saved, cash ->
            DashboardSummary(
                income = income,
                spent = regular,
                surplus = income - regular - periodic,
                savings = saved,
                periodicSpent = periodic,
                cashBalance = cash,
            )
        }
    }
}

/** Periodic / one-time lump-sum payments made in one month, with total and monthly-equivalent. */
class ObservePeriodicExpenses(private val transactions: TransactionRepository) {
    operator fun invoke(month: YearMonth): Flow<PeriodicExpenseSummary> =
        transactions.observePeriodicExpenses(MonthYear.startMillis(month), MonthYear.endMillis(month))
}

/** Regular operating expense total per category for one month, largest first (zero entries dropped). */
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
            ExpenseType.REGULAR_OPERATING,
        ),
    ) { cats, totals ->
        cats.mapNotNull { c -> totals[c.id]?.takeIf { it > 0 }?.let { CategoryBreakdown(c.id, c.name, it) } }
            .sortedByDescending { it.amount }
    }
}

/**
 * Regular operating spend / income for each of the last [months] months ending at [end] (months
 * without data are zero). Periodic lump sums are excluded so they don't skew the trend.
 */
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
                            MonthlyTrendPoint(m, t?.operatingSpent ?: 0L, t?.income ?: 0L)
                        },
                    )
                }
            }
        }
    }
}
