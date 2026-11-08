package com.example.dhansutra.data.local.dao

import androidx.room.Embedded
import com.example.dhansutra.data.local.entity.TransactionEntity
import com.example.dhansutra.domain.model.ExpenseType
import com.example.dhansutra.domain.model.TransactionType

data class TransactionWithCategory(
    @Embedded val transaction: TransactionEntity,
    val categoryName: String,
    val categoryIcon: String,
)

data class CategorySum(
    val categoryId: Long,
    val total: Long,
)

data class MonthTypeTotal(
    val monthYear: String,
    val type: TransactionType,
    val expenseType: ExpenseType,
    val total: Long,
)

data class MonthCategorySum(
    val monthYear: String,
    val categoryId: Long,
    val total: Long,
)
