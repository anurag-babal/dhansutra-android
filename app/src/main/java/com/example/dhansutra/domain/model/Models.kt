package com.example.dhansutra.domain.model

import java.time.YearMonth

/** All amounts are in paise. */
data class Category(
    val id: Long = 0,
    val name: String,
    val iconName: String,
    val defaultBudget: Long,
    val rolloverType: RolloverType,
)

data class Transaction(
    val id: Long = 0,
    val amount: Long,
    val categoryId: Long,
    val dateMillis: Long,
    val type: TransactionType,
    val notes: String = "",
)

/** A transaction joined with display info of its category. */
data class TransactionItem(
    val transaction: Transaction,
    val categoryName: String,
    val categoryIcon: String,
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
    val income: Long,
    val spent: Long,
    /** income - spent for the month. */
    val surplus: Long,
    val savings: Long,
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
    val spent: Long,
    val income: Long,
)

data class MonthCategorySpend(
    val monthYear: String,
    val categoryId: Long,
    val amount: Long,
)
