package com.example.dhansutra.data.local.dao

import androidx.room.Embedded
import com.example.dhansutra.data.local.entity.TransactionEntity
import com.example.dhansutra.data.local.entity.TransactionType

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
    val total: Long,
)
