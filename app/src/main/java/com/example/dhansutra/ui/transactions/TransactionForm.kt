package com.example.dhansutra.ui.transactions

import com.example.dhansutra.domain.model.Category
import com.example.dhansutra.domain.model.LoanDirection
import com.example.dhansutra.domain.model.Transaction
import com.example.dhansutra.domain.model.TransactionItem
import com.example.dhansutra.domain.model.TransactionNature
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
    /** As selected in the UI; see [effectiveNature] for what is saved. */
    val nature: TransactionNature = TransactionNature.REGULAR_OPERATING,
    /** Only used while [isPeriodic]; other transactions are always saved with 1 month. */
    val coveredMonths: Int = PERIODIC_DURATION_OPTIONS.first(),
    /** Person of a LOAN transaction. */
    val counterparty: String = "",
    /** LENT: expense = money lent, income = repayment received. BORROWED: income = money borrowed, expense = repaid. */
    val loanDirection: LoanDirection = LoanDirection.LENT,
    val showErrors: Boolean = false,
) {
    /** Periodic only applies to expenses; an income can never be periodic. */
    val effectiveNature: TransactionNature
        get() = if (nature == TransactionNature.PERIODIC_ONE_TIME && type == TransactionType.INCOME) {
            TransactionNature.REGULAR_OPERATING
        } else {
            nature
        }

    val isPeriodic: Boolean get() = effectiveNature == TransactionNature.PERIODIC_ONE_TIME
    val isLoan: Boolean get() = effectiveNature == TransactionNature.LOAN

    val amountPaise: Long? get() = CurrencyFormatter.parseToPaise(amountText)
    val amountError: Boolean get() = amountPaise == null
    val categoryError: Boolean get() = categoryId == null
    val counterpartyError: Boolean get() = isLoan && counterparty.isBlank()
    val isValid: Boolean get() = !amountError && !categoryError && !counterpartyError

    /** The transaction to persist (without its loan link, resolved on save), or null while invalid. */
    fun toTransaction(): Transaction? {
        if (!isValid) return null
        return Transaction(
            id = id,
            amount = amountPaise ?: return null,
            categoryId = categoryId ?: return null,
            dateMillis = MonthYear.startOfDayMillis(date),
            type = type,
            notes = notes.trim(),
            nature = effectiveNature,
            coveredMonths = if (isPeriodic) coveredMonths else 1,
        )
    }

    /**
     * Picks a category. Investment / loan categories pre-select that nature; leaving one for an ordinary
     * category goes back to regular. A periodic selection is kept.
     */
    fun withCategory(category: Category): TransactionForm {
        val special = category.nature == TransactionNature.INVESTMENT || category.nature == TransactionNature.LOAN
        val wasSpecial = nature == TransactionNature.INVESTMENT || nature == TransactionNature.LOAN
        return copy(
            categoryId = category.id,
            nature = when {
                special -> category.nature
                wasSpecial -> TransactionNature.REGULAR_OPERATING
                else -> nature
            },
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
            nature = item.transaction.nature,
            coveredMonths = if (item.transaction.isPeriodic) item.transaction.coveredMonths else PERIODIC_DURATION_OPTIONS.first(),
            counterparty = item.loanName.orEmpty(),
            loanDirection = item.loanDirection ?: LoanDirection.LENT,
        )
    }
}

data class DayGroup(val date: LocalDate, val items: List<TransactionItem>)

/** Groups an already newest-first list by local calendar day, preserving order. */
fun groupByDay(items: List<TransactionItem>): List<DayGroup> =
    items.groupBy { MonthYear.toLocalDate(it.transaction.dateMillis) }
        .map { (date, list) -> DayGroup(date, list) }

/** Short description of what a transaction is beyond income / expense, or null for an ordinary one. */
fun TransactionItem.natureLabel(): String? = when (transaction.nature) {
    TransactionNature.REGULAR_OPERATING -> null
    TransactionNature.PERIODIC_ONE_TIME -> "Covers ${transaction.coveredMonths} months"
    TransactionNature.INVESTMENT ->
        if (transaction.type == TransactionType.EXPENSE) "Investment" else "Investment redeemed"
    TransactionNature.LOAN -> {
        val who = loanName ?: "someone"
        when {
            loanDirection == LoanDirection.BORROWED && transaction.type == TransactionType.INCOME -> "Borrowed from $who"
            loanDirection == LoanDirection.BORROWED -> "Repaid to $who"
            transaction.type == TransactionType.EXPENSE -> "Lent to $who"
            else -> "Repayment from $who"
        }
    }
}
