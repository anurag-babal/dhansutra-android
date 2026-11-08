package com.example.dhansutra.domain.usecase

import com.example.dhansutra.domain.model.BudgetEntry
import com.example.dhansutra.domain.model.Category
import com.example.dhansutra.domain.model.MonthCategorySpend
import com.example.dhansutra.domain.model.MonthTotals
import com.example.dhansutra.domain.model.RolloverType
import com.example.dhansutra.util.MonthYear
import java.time.YearMonth

/**
 * Pure budget/rollover/savings rules.
 *
 * - A category's budget rows run from its first month (earliest existing row, earliest expense, or
 *   the current month) through the current month; missing rows are created from the category's
 *   default budget.
 * - CARRY_FORWARD: next month's carriedOver = this month's remaining (allocated + carriedOver - spent),
 *   positive or negative.
 * - SWEEP_TO_SAVINGS: next month's carriedOver = 0; this month's positive remaining goes to savings.
 * - When rollover is disabled every carriedOver is 0 and nothing is swept.
 */
object RolloverCalculator {

    private fun spendMap(spend: List<MonthCategorySpend>): Map<Pair<String, Long>, Long> =
        spend.groupBy { it.monthYear to it.categoryId }.mapValues { (_, v) -> v.sumOf { it.amount } }

    /**
     * Returns the full desired set of budget rows for every category up to [current]. Rows that
     * already exist keep their id and allocated amount; new rows have id = 0.
     */
    fun computeBudgets(
        categories: List<Category>,
        existing: List<BudgetEntry>,
        spend: List<MonthCategorySpend>,
        rolloverEnabled: Boolean,
        current: YearMonth,
    ): List<BudgetEntry> {
        val existingByKey = existing.associateBy { it.monthYear to it.categoryId }
        val spendByKey = spendMap(spend)
        val result = mutableListOf<BudgetEntry>()

        for (category in categories) {
            val candidates = buildList {
                existing.filter { it.categoryId == category.id }.forEach { add(MonthYear.parse(it.monthYear)) }
                spend.filter { it.categoryId == category.id }.forEach { add(MonthYear.parse(it.monthYear)) }
                add(current)
            }
            val start = candidates.min()
            var carried = 0L
            for (month in MonthYear.range(start, current)) {
                val key = MonthYear.key(month)
                val row = existingByKey[key to category.id]
                val allocated = row?.allocatedAmount ?: category.defaultBudget
                val carriedIn = if (rolloverEnabled) carried else 0L
                result += BudgetEntry(
                    id = row?.id ?: 0,
                    monthYear = key,
                    categoryId = category.id,
                    allocatedAmount = allocated,
                    carriedOverAmount = carriedIn,
                )
                val remaining = allocated + carriedIn - (spendByKey[key to category.id] ?: 0L)
                carried = if (category.rolloverType == RolloverType.CARRY_FORWARD) remaining else 0L
            }
        }
        return result
    }

    /**
     * Overall savings = swept surplus of closed months (SWEEP_TO_SAVINGS categories, positive remaining
     * only; an overspend is forgiven, not subtracted) + sum of (income - expenses) over all closed months.
     * A closed month is any month before [current].
     */
    fun computeSavings(
        categories: List<Category>,
        budgets: List<BudgetEntry>,
        spend: List<MonthCategorySpend>,
        monthTotals: List<MonthTotals>,
        rolloverEnabled: Boolean,
        current: YearMonth,
    ): Long {
        val currentKey = MonthYear.key(current)
        val netCashflow = monthTotals.filter { it.monthYear < currentKey }.sumOf { it.income - it.spent }
        if (!rolloverEnabled) return netCashflow

        val sweepIds = categories.filter { it.rolloverType == RolloverType.SWEEP_TO_SAVINGS }.map { it.id }.toSet()
        val spendByKey = spendMap(spend)
        val swept = budgets
            .filter { it.categoryId in sweepIds && it.monthYear < currentKey }
            .sumOf {
                val remaining = it.allocatedAmount + it.carriedOverAmount - (spendByKey[it.monthYear to it.categoryId] ?: 0L)
                remaining.coerceAtLeast(0L)
            }
        return netCashflow + swept
    }
}
