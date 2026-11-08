package com.example.dhansutra.ui.transactions

import com.example.dhansutra.domain.model.Transaction
import com.example.dhansutra.domain.model.TransactionItem
import com.example.dhansutra.domain.model.TransactionType
import com.example.dhansutra.util.CurrencyFormatter
import com.example.dhansutra.util.MonthYear
import java.time.LocalDate

/** Editable state of the add/edit transaction sheet. [id] == 0 means a new transaction. */
data class TransactionForm(
    val id: Long = 0,
    val amountText: String = "",
    val date: LocalDate = LocalDate.now(),
    val categoryId: Long? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val notes: String = "",
    val showErrors: Boolean = false,
) {
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
        )
    }
}

data class DayGroup(val date: LocalDate, val items: List<TransactionItem>)

/** Groups an already newest-first list by local calendar day, preserving order. */
fun groupByDay(items: List<TransactionItem>): List<DayGroup> =
    items.groupBy { MonthYear.toLocalDate(it.transaction.dateMillis) }
        .map { (date, list) -> DayGroup(date, list) }
