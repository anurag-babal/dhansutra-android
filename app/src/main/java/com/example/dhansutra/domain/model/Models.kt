package com.example.dhansutra.domain.model

import java.time.YearMonth

/** All amounts are in paise. */
data class Category(
    val id: Long = 0,
    val name: String,
    val iconName: String,
    val defaultBudget: Long,
    val rolloverType: RolloverType,
    /** Nature pre-selected in the entry form for transactions of this category (e.g. INVESTMENT for "SIP"). */
    val nature: TransactionNature = TransactionNature.REGULAR_OPERATING,
)

data class Transaction(
    val id: Long = 0,
    val amount: Long,
    val categoryId: Long,
    val dateMillis: Long,
    val type: TransactionType,
    val notes: String = "",
    /** [TransactionNature.PERIODIC_ONE_TIME] only applies to expenses. */
    val nature: TransactionNature = TransactionNature.REGULAR_OPERATING,
    /** Months of usage the (lump-sum) [amount] covers: 1 for regular, 3/6/12 for periodic. */
    val coveredMonths: Int = 1,
    /** Set when [nature] is [TransactionNature.LOAN]. */
    val loanId: Long? = null,
) {
    val isPeriodic: Boolean get() = nature == TransactionNature.PERIODIC_ONE_TIME

    /** Informational average monthly cost of this payment (paise). */
    val monthlyEquivalent: Long get() = amount / coveredMonths.coerceAtLeast(1)
}

/** A transaction joined with display info of its category. */
data class TransactionItem(
    val transaction: Transaction,
    val categoryName: String,
    val categoryIcon: String,
    /** Counterparty and direction of the linked loan, if any. */
    val loanName: String? = null,
    val loanDirection: LoanDirection? = null,
)

data class TransactionFilter(
    val startMillis: Long,
    val endMillis: Long,
    val categoryId: Long? = null,
    val query: String = "",
)

/** One (month, category) budget row. */
data class BudgetEntry(
    val id: Long = 0,
    val monthYear: String,
    val categoryId: Long,
    val allocatedAmount: Long,
    val carriedOverAmount: Long,
)

/** A category's budget for one month, combined with actual spend. */
data class BudgetItem(
    val category: Category,
    val allocated: Long,
    val carriedOver: Long,
    val spent: Long,
) {
    val available: Long get() = allocated + carriedOver
    val remaining: Long get() = available - spent

    /** Fraction of the available budget spent (0 when nothing available). May exceed 1 when over budget. */
    val spentFraction: Float
        get() = if (available > 0) spent.toFloat() / available else if (spent > 0) Float.MAX_VALUE else 0f
    val isOverBudget: Boolean get() = remaining < 0
}

data class DashboardSummary(
    /** Regular income of the month (loan proceeds and investment redemptions are not income). */
    val income: Long,
    /** Regular operating expenses of the month (what budgets are measured against). */
    val spent: Long,
    /** income - spent - periodicSpent: what the month actually left over. */
    val surplus: Long,
    /** Operating savings (regular income - regular and periodic expenses) of all closed months. */
    val savings: Long,
    /** Periodic / one-time lump-sum expenses paid in the month. */
    val periodicSpent: Long = 0,
    /** All-time income minus ALL expenses, whatever their nature: matches the real bank balance. */
    val cashBalance: Long = 0,
    /** Net amount put into investments this month (contributions - redemptions). */
    val invested: Long = 0,
    /** Net amount currently held in investments (all time, at cost). */
    val investedTotal: Long = 0,
    /** Outstanding money others owe you. */
    val owedToYou: Long = 0,
    /** Outstanding money you owe others. */
    val youOwe: Long = 0,
) {
    /** Cash + investments (at cost) + receivables - payables. */
    val netWorth: Long get() = cashBalance + investedTotal + owedToYou - youOwe
}

/** A loan with one person. The balance is derived from the transactions linked to it. */
data class Loan(
    val id: Long = 0,
    val counterparty: String,
    val direction: LoanDirection,
)

/** [outstanding] is what is still to be settled: owed to you for LENT, owed by you for BORROWED. */
data class LoanBalance(
    val loan: Loan,
    val outstanding: Long,
) {
    val isOpen: Boolean get() = outstanding > 0
}

/** One periodic lump-sum payment, for the dashboard breakdown. */
data class PeriodicExpenseItem(
    val id: Long,
    val categoryName: String,
    val categoryIcon: String,
    val notes: String,
    val dateMillis: Long,
    val amount: Long,
    val coveredMonths: Int,
) {
    val monthlyEquivalent: Long get() = amount / coveredMonths.coerceAtLeast(1)
}

/** Periodic expenses paid in one month: lump-sum total plus its average monthly equivalent. */
data class PeriodicExpenseSummary(
    val total: Long = 0,
    /** Sum of amount / coveredMonths; informational only. */
    val monthlyEquivalent: Long = 0,
    val items: List<PeriodicExpenseItem> = emptyList(),
)

data class CategoryBreakdown(
    val categoryId: Long,
    val name: String,
    val amount: Long,
)

data class MonthlyTrendPoint(
    val month: YearMonth,
    val spent: Long,
    val income: Long,
)

data class MonthTotals(
    val monthYear: String,
    /** Operating expenses of the month (regular + periodic); investments and loans are excluded. */
    val spent: Long,
    /** Regular income of the month. */
    val income: Long,
    /** The periodic part of [spent]. */
    val periodicSpent: Long = 0,
    /** Net investment contributions of the month. */
    val invested: Long = 0,
) {
    /** Regular operating spend only; used for spending trends. */
    val operatingSpent: Long get() = spent - periodicSpent
}

data class MonthCategorySpend(
    val monthYear: String,
    val categoryId: Long,
    val amount: Long,
)
