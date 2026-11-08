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
 * - Each category gets a budget row for every month from its own first month (earliest of its existing
 *   budget rows and its expenses, else the current month) through the current month, including months
 *   without spending; missing rows are created from the category's default budget. A category created
 *   in November therefore has no October row.
 * - CARRY_FORWARD: next month's carriedOver = this month's remaining (allocated + carriedOver - spent),
 *   positive or negative.
 * - SWEEP_TO_SAVINGS: next month's carriedOver = 0 (positive leftover is dropped, overspend forgiven).
 * - When rollover is disabled every carriedOver is 0.
 * - Total savings = income - expenses of all closed months (rollover does not affect it).
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
            val start = (
                existing.filter { it.categoryId == category.id }.map { it.monthYear } +
                    spend.filter { it.categoryId == category.id }.map { it.monthYear }
                ).minOrNull()?.let { minOf(MonthYear.parse(it), current) } ?: current
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
