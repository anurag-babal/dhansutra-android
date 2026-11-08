package com.example.dhansutra.domain

import com.example.dhansutra.domain.model.BudgetEntry
import com.example.dhansutra.domain.model.Category
import com.example.dhansutra.domain.model.MonthCategorySpend
import com.example.dhansutra.domain.model.MonthTotals
import com.example.dhansutra.domain.model.RolloverType
import com.example.dhansutra.domain.usecase.RolloverCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class RolloverCalculatorTest {
    private val carry = Category(1, "Groceries", "cart", 1000, RolloverType.CARRY_FORWARD)
    private val sweep = Category(2, "Dining", "food", 500, RolloverType.SWEEP_TO_SAVINGS)
    private val nov = YearMonth.of(2026, 11)

    private fun spend(month: String, cat: Long, amount: Long) = MonthCategorySpend(month, cat, amount)
    private fun List<BudgetEntry>.carried(cat: Long, month: String) =
        first { it.categoryId == cat && it.monthYear == month }.carriedOverAmount

    private fun budgets(
        categories: List<Category>,
        existing: List<BudgetEntry> = emptyList(),
        spend: List<MonthCategorySpend> = emptyList(),
        enabled: Boolean = true,
    ) = RolloverCalculator.computeBudgets(categories, existing, spend, enabled, nov)

    @Test
    fun carryForwardPositiveAndNegative() {
        val rows = budgets(listOf(carry), spend = listOf(spend("2026-09", 1, 700), spend("2026-10", 1, 1500)))
        assertEquals(listOf("2026-09", "2026-10", "2026-11"), rows.map { it.monthYear })
        assertEquals(0L, rows.carried(1, "2026-09"))
        assertEquals(300L, rows.carried(1, "2026-10"))   // 1000 - 700
        assertEquals(-200L, rows.carried(1, "2026-11"))  // 1000 + 300 - 1500
    }

    @Test
    fun sweepResetsCarryOverToZeroAndForgivesOverspend() {
        val rows = budgets(listOf(sweep), spend = listOf(spend("2026-10", 2, 900)))
        assertEquals(0L, rows.carried(2, "2026-11"))
    }

    @Test
    fun rolloverDisabledNeverCarries() {
        val rows = budgets(listOf(carry), spend = listOf(spend("2026-10", 1, 100)), enabled = false)
        assertEquals(0L, rows.carried(1, "2026-11"))
    }

    @Test
    fun existingCategoryWithNoSpendingStillCarriesItsFullBudget() {
        // Utilities already had an October budget row but nothing was spent: its whole budget carries into November.
        val utilities = Category(3, "Utilities", "bolt", 300, RolloverType.CARRY_FORWARD)
        val existing = listOf(BudgetEntry(5, "2026-10", 3, 300, 0))
        val rows = budgets(listOf(carry, utilities), existing, spend = listOf(spend("2026-10", 1, 100)))
        assertEquals(listOf("2026-10", "2026-11"), rows.filter { it.categoryId == 3L }.map { it.monthYear })
        assertEquals(300L, rows.carried(3, "2026-11"))
    }

    @Test
    fun categoryCreatedThisMonthHasNoEarlierRowsAndNoCarryOver() {
        // Groceries has October spending; the brand-new Gym category must not get an October row.
        val gym = Category(4, "Gym", "fitness", 500, RolloverType.CARRY_FORWARD)
        val rows = budgets(listOf(carry, gym), spend = listOf(spend("2026-10", 1, 100)))
        assertEquals(listOf("2026-11"), rows.filter { it.categoryId == 4L }.map { it.monthYear })
        assertEquals(0L, rows.carried(4, "2026-11"))
        assertEquals(listOf("2026-10", "2026-11"), rows.filter { it.categoryId == 1L }.map { it.monthYear })
    }

    @Test
    fun noActivityOnlyCurrentMonth() {
        assertEquals(listOf("2026-11"), budgets(listOf(carry)).map { it.monthYear })
    }

    @Test
    fun keepsExistingAllocationAndIdAndIsIdempotent() {
        val existing = listOf(BudgetEntry(7, "2026-10", 1, 2000, 0))
        val spend = listOf(spend("2026-10", 1, 500))
        val first = budgets(listOf(carry), existing, spend)
        val oct = first.first { it.monthYear == "2026-10" }
        assertEquals(7L, oct.id)
        assertEquals(2000L, oct.allocatedAmount)
        assertEquals(1500L, first.carried(1, "2026-11"))
        // feeding results back yields the same rows
        val again = budgets(listOf(carry), first.map { it.copy(id = it.id.coerceAtLeast(1)) }, spend)
        assertEquals(first.map { it.copy(id = 0) }, again.map { it.copy(id = 0) })
    }

    @Test
    fun savingsIsIncomeMinusExpensesOfClosedMonthsOnly() {
        val totals = listOf(
            MonthTotals("2026-09", 100, 1000),
            MonthTotals("2026-10", 200, 1000),
            MonthTotals("2026-11", 50, 400), // current month: not closed
        )
        assertEquals((1000L - 100) + (1000L - 200), RolloverCalculator.computeSavings(totals, nov))
    }

    @Test
    fun savingsIgnoreInvestments() {
        // MonthTotals.spent/income already exclude investments and loans, `invested` is informational.
        val totals = listOf(MonthTotals("2026-10", spent = 300, income = 1000, periodicSpent = 100, invested = 500))
        assertEquals(700L, RolloverCalculator.computeSavings(totals, nov))
        assertEquals(200L, totals.single().operatingSpent)
    }

    @Test
    fun savingsCanBeNegativeAndEmptyIsZero() {
        assertEquals(-300L, RolloverCalculator.computeSavings(listOf(MonthTotals("2026-10", 500, 200)), nov))
        assertEquals(0L, RolloverCalculator.computeSavings(emptyList(), nov))
    }
}
