package com.example.dhansutra.ui.transactions

import com.example.dhansutra.domain.model.ExpenseType
import com.example.dhansutra.domain.model.Transaction
import com.example.dhansutra.domain.model.TransactionItem
import com.example.dhansutra.domain.model.TransactionType
import com.example.dhansutra.util.CurrencyFormatter
import com.example.dhansutra.util.MonthYear
import java.time.LocalDate

/** Selectable durations (months) for a periodic / one-time payment. */
val PERIODIC_DURATION_OPTIONS = listOf(3, 6, 12)

/** Editable state of the add/edit transaction sheet. [id] == 0 means a new transaction. */
data class TransactionForm(
    val id: Long = 0,
    val amountText: String = "",
    val date: LocalDate = LocalDate.now(),
    val categoryId: Long? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val notes: String = "",
    val expenseType: ExpenseType = ExpenseType.REGULAR_OPERATING,
    /** Only used while [isPeriodic]; regular transactions are always saved with 1 month. */
    val coveredMonths: Int = PERIODIC_DURATION_OPTIONS.first(),
    val showErrors: Boolean = false,
) {
    /** Periodic tagging only applies to expenses. */
    val isPeriodic: Boolean
        get() = type == TransactionType.EXPENSE && expenseType == ExpenseType.PERIODIC_ONE_TIME

    val amountPaise: Long? get() = CurrencyFormatter.parseToPaise(amountText)
    val amountError: Boolean get() = amountPaise == null
    val categoryError: Boolean get() = categoryId == null
    val isValid: Boolean get() = !amountError && !categoryError

    /** The transaction to persist, or null while the form is invalid. */
    fun toTransaction(): Transaction? {
        val amount = amountPaise ?: return null
        val category = categoryId ?: return null
        return Transaction(
            id = id,
            amount = amount,
            categoryId = category,
            dateMillis = MonthYear.startOfDayMillis(date),
            type = type,
            notes = notes.trim(),
            expenseType = if (isPeriodic) ExpenseType.PERIODIC_ONE_TIME else ExpenseType.REGULAR_OPERATING,
            coveredMonths = if (isPeriodic) coveredMonths else 1,
        )
    }

    companion object {
        fun from(item: TransactionItem) = TransactionForm(
            id = item.transaction.id,
            amountText = CurrencyFormatter.toInputText(item.transaction.amount),
            date = MonthYear.toLocalDate(item.transaction.dateMillis),
            categoryId = item.transaction.categoryId,
            type = item.transaction.type,
            notes = item.transaction.notes,
            expenseType = item.transaction.expenseType,
            coveredMonths = if (item.transaction.isPeriodic) item.transaction.coveredMonths else PERIODIC_DURATION_OPTIONS.first(),
        )
    }
}

data class DayGroup(val date: LocalDate, val items: List<TransactionItem>)

/** Groups an already newest-first list by local calendar day, preserving order. */
fun groupByDay(items: List<TransactionItem>): List<DayGroup> =
    items.groupBy { MonthYear.toLocalDate(it.transaction.dateMillis) }
        .map { (date, list) -> DayGroup(date, list) }
