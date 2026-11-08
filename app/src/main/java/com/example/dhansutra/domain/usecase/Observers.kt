package com.example.dhansutra.domain.usecase

import com.example.dhansutra.domain.model.BudgetItem
import com.example.dhansutra.domain.model.CategoryBreakdown
import com.example.dhansutra.domain.model.DashboardSummary
import com.example.dhansutra.domain.model.LoanBalance
import com.example.dhansutra.domain.model.LoanDirection
import com.example.dhansutra.domain.model.MonthlyTrendPoint
import com.example.dhansutra.domain.model.PeriodicExpenseSummary
import com.example.dhansutra.domain.model.TransactionNature
import com.example.dhansutra.domain.model.TransactionType
import com.example.dhansutra.domain.repository.BudgetRepository
import com.example.dhansutra.domain.repository.CategoryRepository
import com.example.dhansutra.domain.repository.LoanRepository
import com.example.dhansutra.domain.repository.SettingsRepository
import com.example.dhansutra.domain.repository.TransactionRepository
import com.example.dhansutra.util.MonthYear
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.YearMonth

/**
 * Per-category allocated / carried over / spent for one month. Spent counts only
 * [TransactionNature.REGULAR_OPERATING] expenses so periodic lump sums never distort monthly budgets.
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
            TransactionNature.REGULAR_OPERATING,
        ),
    ) { cats, rows, spent ->
        val rowsByCat = rows.associateBy { it.categoryId }
        cats.filter { it.nature == TransactionNature.REGULAR_OPERATING || rowsByCat.containsKey(it.id) || (spent[it.id] ?: 0L) > 0L }
            .map { cat ->
                val row = rowsByCat[cat.id]
                val spentAmount = spent[cat.id] ?: 0L
                BudgetItem(cat, row?.allocatedAmount ?: cat.defaultBudget, row?.carriedOverAmount ?: 0L, spentAmount)
            }
    }
}

/**
 * Dashboard numbers. For [month]: regular income, regular spent, periodic spent, net invested.
 * All time: cash balance (income - ALL expenses, whatever the nature), invested total, loan balances.
 * Savings = regular income - operating expenses of closed months (investments and loans excluded).
 */
class ObserveDashboardSummary(
    private val transactions: TransactionRepository,
    private val loans: LoanRepository,
) {
    operator fun invoke(month: YearMonth): Flow<DashboardSummary> {
        val start = MonthYear.startMillis(month)
        val end = MonthYear.endMillis(month)
        val monthly = combine(
            transactions.observeTotal(TransactionType.INCOME, start, end, TransactionNature.REGULAR_OPERATING),
            transactions.observeTotal(TransactionType.EXPENSE, start, end, TransactionNature.REGULAR_OPERATING),
            transactions.observeTotal(TransactionType.EXPENSE, start, end, TransactionNature.PERIODIC_ONE_TIME),
            transactions.observeNetInvested(start, end),
        ) { income, regular, periodic, invested -> MonthFigures(income, regular, periodic, invested) }
        val savings = transactions.observeMonthTotals(0L).map { totals ->
            RolloverCalculator.computeSavings(totals, MonthYear.current())
        }
        val positions = combine(
            transactions.observeCashBalance(),
            transactions.observeNetInvested(0L, Long.MAX_VALUE),
            loans.observeBalances(),
        ) { cash, investedTotal, balances ->
            Triple(cash, investedTotal, balances)
        }
        return combine(monthly, savings, positions) { m, saved, (cash, investedTotal, balances) ->
            DashboardSummary(
                income = m.income,
                spent = m.regular,
                surplus = m.income - m.regular - m.periodic,
                savings = saved,
                periodicSpent = m.periodic,
                cashBalance = cash,
                invested = m.invested,
                investedTotal = investedTotal,
                owedToYou = balances.filter { it.loan.direction == LoanDirection.LENT }.sumOf { it.outstanding },
                youOwe = balances.filter { it.loan.direction == LoanDirection.BORROWED }.sumOf { it.outstanding },
            )
        }
    }

    private data class MonthFigures(val income: Long, val regular: Long, val periodic: Long, val invested: Long)
}

/** Loans that still have an outstanding balance, largest first. */
class ObserveOpenLoans(private val loans: LoanRepository) {
    operator fun invoke(): Flow<List<LoanBalance>> =
        loans.observeBalances().map { l -> l.filter { it.isOpen }.sortedByDescending { it.outstanding } }
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
            TransactionNature.REGULAR_OPERATING,
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
