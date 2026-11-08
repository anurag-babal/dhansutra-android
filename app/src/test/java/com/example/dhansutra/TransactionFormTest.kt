package com.example.dhansutra

import com.example.dhansutra.domain.model.ExpenseType
import com.example.dhansutra.domain.model.Transaction
import com.example.dhansutra.domain.model.TransactionItem
import com.example.dhansutra.domain.model.TransactionType
import com.example.dhansutra.ui.transactions.TransactionForm
import com.example.dhansutra.ui.transactions.groupByDay
import com.example.dhansutra.util.MonthYear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TransactionFormTest {
    private val date = LocalDate.of(2026, 11, 8)

    @Test
    fun invalidWithoutAmountOrCategory() {
        assertFalse(TransactionForm(amountText = "", categoryId = 1).isValid)
        assertFalse(TransactionForm(amountText = "0", categoryId = 1).isValid)
        assertFalse(TransactionForm(amountText = "10", categoryId = null).isValid)
        assertNull(TransactionForm(amountText = "10", categoryId = null).toTransaction())
    }

    @Test
    fun buildsTransactionInPaise() {
        val tx = TransactionForm(amountText = "12.5", categoryId = 3, date = date, notes = " lunch ", type = TransactionType.EXPENSE)
            .toTransaction()!!
        assertEquals(1250L, tx.amount)
        assertEquals(3L, tx.categoryId)
        assertEquals("lunch", tx.notes)
        assertEquals(MonthYear.startOfDayMillis(date), tx.dateMillis)
    }

    @Test
    fun roundTripsExistingItem() {
        val item = TransactionItem(
            Transaction(9, 125050, 2, MonthYear.startOfDayMillis(date), TransactionType.INCOME, "bonus"),
            "Salary", "savings",
        )
        val form = TransactionForm.from(item)
        assertTrue(form.isValid)
        assertEquals("1250.5", form.amountText)
        assertEquals(date, form.date)
        assertEquals(item.transaction, form.toTransaction())
    }

    @Test
    fun periodicExpenseCarriesDuration() {
        val form = TransactionForm(
            amountText = "12000", categoryId = 1, date = date,
            expenseType = ExpenseType.PERIODIC_ONE_TIME, coveredMonths = 6,
        )
        val tx = form.toTransaction()!!
        assertEquals(ExpenseType.PERIODIC_ONE_TIME, tx.expenseType)
        assertEquals(6, tx.coveredMonths)
        assertEquals(200_000L, tx.monthlyEquivalent)
        // round trip
        val back = TransactionForm.from(TransactionItem(tx.copy(id = 4), "Gym", "x"))
        assertEquals(6, back.coveredMonths)
        assertTrue(back.isPeriodic)
    }

    @Test
    fun regularAndIncomeIgnorePeriodicSelection() {
        val regular = TransactionForm(amountText = "10", categoryId = 1, coveredMonths = 12).toTransaction()!!
        assertEquals(ExpenseType.REGULAR_OPERATING, regular.expenseType)
        assertEquals(1, regular.coveredMonths)

        val income = TransactionForm(
            amountText = "10", categoryId = 1, type = TransactionType.INCOME,
            expenseType = ExpenseType.PERIODIC_ONE_TIME, coveredMonths = 6,
        ).toTransaction()!!
        assertEquals(ExpenseType.REGULAR_OPERATING, income.expenseType)
        assertEquals(1, income.coveredMonths)
    }

    @Test
    fun groupsByDayPreservingOrder() {
        fun item(id: Long, day: Int) = TransactionItem(
            Transaction(id, 100, 1, MonthYear.startOfDayMillis(LocalDate.of(2026, 11, day)) + id, TransactionType.EXPENSE),
            "c", "i",
        )
        val groups = groupByDay(listOf(item(3, 8), item(2, 8), item(1, 1)))
        assertEquals(listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 1)), groups.map { it.date })
        assertEquals(listOf(2, 1), groups.map { it.items.size })
    }
}
