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

    @Test
    fun carryForwardPositiveAndNegative() {
        val rows = RolloverCalculator.computeBudgets(
            listOf(carry), emptyList(),
            listOf(spend("2026-09", 1, 700), spend("2026-10", 1, 1500)),
            rolloverEnabled = true, current = nov,
        )
        assertEquals(listOf("2026-09", "2026-10", "2026-11"), rows.map { it.monthYear })
        assertEquals(0L, rows.carried(1, "2026-09"))
        assertEquals(300L, rows.carried(1, "2026-10"))   // 1000 - 700
        assertEquals(-200L, rows.carried(1, "2026-11"))  // 1000 + 300 - 1500
    }

    @Test
    fun sweepResetsCarryOverToZero() {
        val rows = RolloverCalculator.computeBudgets(
            listOf(sweep), emptyList(), listOf(spend("2026-10", 2, 100)),
            rolloverEnabled = true, current = nov,
        )
        assertEquals(0L, rows.carried(2, "2026-11"))
    }

    @Test
    fun rolloverDisabledNeverCarries() {
        val rows = RolloverCalculator.computeBudgets(
            listOf(carry), emptyList(), listOf(spend("2026-10", 1, 100)),
            rolloverEnabled = false, current = nov,
        )
        assertEquals(0L, rows.carried(1, "2026-11"))
    }

    @Test
    fun keepsExistingAllocationAndIdAndIsIdempotent() {
        val existing = listOf(BudgetEntry(7, "2026-10", 1, 2000, 0))
        val spend = listOf(spend("2026-10", 1, 500))
        val first = RolloverCalculator.computeBudgets(listOf(carry), existing, spend, true, nov)
        val oct = first.first { it.monthYear == "2026-10" }
        assertEquals(7L, oct.id)
        assertEquals(2000L, oct.allocatedAmount)
        assertEquals(1500L, first.carried(1, "2026-11"))
        // feeding results back yields the same rows
        val again = RolloverCalculator.computeBudgets(listOf(carry), first.map { it.copy(id = it.id.coerceAtLeast(1)) }, spend, true, nov)
        assertEquals(first.map { it.copy(id = 0) }, again.map { it.copy(id = 0) })
    }

    @Test
    fun savingsSweptSurplusPlusNetCashflowOfClosedMonths() {
        val budgets = listOf(
            BudgetEntry(1, "2026-10", 2, 500, 0),
            BudgetEntry(2, "2026-11", 2, 500, 0), // current month: not closed
        )
        val spend = listOf(spend("2026-10", 2, 200), spend("2026-11", 2, 50))
        val totals = listOf(MonthTotals("2026-10", 200, 1000), MonthTotals("2026-11", 50, 400))
        val savings = RolloverCalculator.computeSavings(listOf(sweep), budgets, spend, totals, true, nov)
        assertEquals(300L + 800L, savings) // swept 300 + (1000-200)
    }

    @Test
    fun savingsIgnoresSweepWhenDisabledAndForgivesOverspend() {
        val budgets = listOf(BudgetEntry(1, "2026-10", 2, 500, 0))
        val spend = listOf(spend("2026-10", 2, 900))
        val totals = listOf(MonthTotals("2026-10", 900, 1000))
        assertEquals(100L, RolloverCalculator.computeSavings(listOf(sweep), budgets, spend, totals, true, nov))
        assertEquals(100L, RolloverCalculator.computeSavings(listOf(sweep), budgets, spend, totals, false, nov))
    }
}
