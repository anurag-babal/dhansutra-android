package com.example.dhansutra

import com.example.dhansutra.domain.model.Category
import com.example.dhansutra.domain.model.LoanDirection
import com.example.dhansutra.domain.model.RolloverType
import com.example.dhansutra.domain.model.TransactionNature
import com.example.dhansutra.domain.model.Transaction
import com.example.dhansutra.domain.model.TransactionItem
import com.example.dhansutra.domain.model.TransactionType
import com.example.dhansutra.ui.transactions.TransactionForm
import com.example.dhansutra.ui.transactions.groupByDay
import com.example.dhansutra.ui.transactions.natureLabel
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
            nature = TransactionNature.PERIODIC_ONE_TIME, coveredMonths = 6,
        )
        val tx = form.toTransaction()!!
        assertEquals(TransactionNature.PERIODIC_ONE_TIME, tx.nature)
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
        assertEquals(TransactionNature.REGULAR_OPERATING, regular.nature)
        assertEquals(1, regular.coveredMonths)

        val income = TransactionForm(
            amountText = "10", categoryId = 1, type = TransactionType.INCOME,
            nature = TransactionNature.PERIODIC_ONE_TIME, coveredMonths = 6,
        ).toTransaction()!!
        assertEquals(TransactionNature.REGULAR_OPERATING, income.nature)
        assertEquals(1, income.coveredMonths)
    }

    @Test
    fun investmentAndLoanKeepTheirNature() {
        val sip = TransactionForm(amountText = "5000", categoryId = 1, nature = TransactionNature.INVESTMENT).toTransaction()!!
        assertEquals(TransactionNature.INVESTMENT, sip.nature)
        assertEquals(1, sip.coveredMonths)

        // a loan needs a person
        val noPerson = TransactionForm(amountText = "10", categoryId = 1, nature = TransactionNature.LOAN)
        assertFalse(noPerson.isValid)
        assertNull(noPerson.toTransaction())
        val loan = noPerson.copy(counterparty = "Rahul").toTransaction()!!
        assertEquals(TransactionNature.LOAN, loan.nature)
    }

    @Test
    fun categoryPreselectsAndClearsNature() {
        val sipCategory = Category(1, "SIP", "trending_up", 0, RolloverType.CARRY_FORWARD, TransactionNature.INVESTMENT)
        val groceries = Category(2, "Groceries", "cart", 0, RolloverType.CARRY_FORWARD)

        val onSip = TransactionForm().withCategory(sipCategory)
        assertEquals(TransactionNature.INVESTMENT, onSip.nature)
        // leaving an investment category goes back to regular
        assertEquals(TransactionNature.REGULAR_OPERATING, onSip.withCategory(groceries).nature)
        // an explicit periodic choice survives a category change
        val periodic = TransactionForm(nature = TransactionNature.PERIODIC_ONE_TIME)
        assertEquals(TransactionNature.PERIODIC_ONE_TIME, periodic.withCategory(groceries).nature)
    }

    @Test
    fun describesLoanTransactions() {
        fun item(type: TransactionType, dir: LoanDirection) = TransactionItem(
            Transaction(1, 100, 1, 0, type, nature = TransactionNature.LOAN), "Loans", "x", "Rahul", dir,
        )
        assertEquals("Lent to Rahul", item(TransactionType.EXPENSE, LoanDirection.LENT).natureLabel())
        assertEquals("Repayment from Rahul", item(TransactionType.INCOME, LoanDirection.LENT).natureLabel())
        assertEquals("Borrowed from Rahul", item(TransactionType.INCOME, LoanDirection.BORROWED).natureLabel())
        assertEquals("Repaid to Rahul", item(TransactionType.EXPENSE, LoanDirection.BORROWED).natureLabel())
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
