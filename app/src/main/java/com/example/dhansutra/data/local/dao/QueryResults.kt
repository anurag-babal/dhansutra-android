package com.example.dhansutra.data.local.dao

import androidx.room.Embedded
import com.example.dhansutra.data.local.entity.TransactionEntity
import com.example.dhansutra.domain.model.LoanDirection
import com.example.dhansutra.domain.model.TransactionNature
import com.example.dhansutra.domain.model.TransactionType

data class TransactionWithCategory(
    @Embedded val transaction: TransactionEntity,
    val categoryName: String,
    val categoryIcon: String,
    val loanName: String? = null,
    val loanDirection: LoanDirection? = null,
)

data class CategorySum(
    val categoryId: Long,
    val total: Long,
)

data class MonthTypeTotal(
    val monthYear: String,
    val type: TransactionType,
    val nature: TransactionNature,
    val total: Long,
)

data class MonthCategorySum(
    val monthYear: String,
    val categoryId: Long,
    val total: Long,
)

/** [net] = money lent out minus money received back for the loan (expenses minus incomes). */
data class LoanNet(
    val id: Long,
    val counterparty: String,
    val direction: LoanDirection,
    val net: Long,
)
