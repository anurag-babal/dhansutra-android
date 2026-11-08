package com.example.dhansutra.data.repository

import com.example.dhansutra.data.local.dao.TransactionWithCategory
import com.example.dhansutra.data.local.entity.CategoryEntity
import com.example.dhansutra.data.local.entity.MonthlyBudgetEntity
import com.example.dhansutra.data.local.entity.TransactionEntity
import com.example.dhansutra.domain.model.BudgetEntry
import com.example.dhansutra.domain.model.Category
import com.example.dhansutra.domain.model.Transaction
import com.example.dhansutra.domain.model.TransactionItem

fun CategoryEntity.toDomain() = Category(id, name, iconName, defaultBudget, rolloverType)
fun Category.toEntity() = CategoryEntity(id, name, iconName, defaultBudget, rolloverType)

fun TransactionEntity.toDomain() = Transaction(id, amount, categoryId, dateMillis, type, notes, expenseType, coveredMonths)
fun Transaction.toEntity() = TransactionEntity(id, amount, categoryId, dateMillis, type, notes, expenseType, coveredMonths)

fun TransactionWithCategory.toDomain() = TransactionItem(transaction.toDomain(), categoryName, categoryIcon)

fun MonthlyBudgetEntity.toDomain() = BudgetEntry(id, monthYear, categoryId, allocatedAmount, carriedOverAmount)
fun BudgetEntry.toEntity() = MonthlyBudgetEntity(id, monthYear, categoryId, allocatedAmount, carriedOverAmount)
