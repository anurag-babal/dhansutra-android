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
 * - Every category gets a budget row for every month from the first month with any activity (an
 *   existing budget row, an expense or an income entry) through the current month; missing rows are
 *   created from the category's default budget.
 * - CARRY_FORWARD: next month's carriedOver = this month's remaining (allocated + carriedOver - spent),
 *   positive or negative.
 * - SWEEP_TO_SAVINGS: next month's carriedOver = 0 (positive leftover is dropped, overspend forgiven).
 * - When rollover is disabled every carriedOver is 0.
 * - Total savings = income - expenses of all closed months (rollover does not affect it).
 */
object RolloverCalculator {

    private fun spendMap(spend: List<MonthCategorySpend>): Map<Pair<String, Long>, Long> =
        spend.groupBy { it.monthYear to it.categoryId }.mapValues { (_, v) -> v.sumOf { it.amount } }

    /** Earliest month with any activity, or [current] when there is none. */
    fun firstActivityMonth(
        existing: List<BudgetEntry>,
        spend: List<MonthCategorySpend>,
        monthTotals: List<MonthTotals>,
        current: YearMonth,
    ): YearMonth =
        (existing.map { it.monthYear } + spend.map { it.monthYear } + monthTotals.map { it.monthYear })
            .minOrNull()?.let { MonthYear.parse(it) }?.let { minOf(it, current) }
            ?: current

    /**
     * Returns the full desired set of budget rows for every category up to [current]. Rows that
     * already exist keep their id and allocated amount; new rows have id = 0.
     */
    fun computeBudgets(
        categories: List<Category>,
        existing: List<BudgetEntry>,
        spend: List<MonthCategorySpend>,
        monthTotals: List<MonthTotals>,
        rolloverEnabled: Boolean,
        current: YearMonth,
    ): List<BudgetEntry> {
        val existingByKey = existing.associateBy { it.monthYear to it.categoryId }
        val spendByKey = spendMap(spend)
        val result = mutableListOf<BudgetEntry>()
        val start = firstActivityMonth(existing, spend, monthTotals, current)

        for (category in categories) {
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

    /** Overall savings = sum of (income - expenses) over all closed months (any month before [current]). */
    fun computeSavings(monthTotals: List<MonthTotals>, current: YearMonth): Long {
        val currentKey = MonthYear.key(current)
        return monthTotals.filter { it.monthYear < currentKey }.sumOf { it.income - it.spent }
    }
}
