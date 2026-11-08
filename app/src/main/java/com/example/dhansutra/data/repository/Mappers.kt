package com.example.dhansutra.data.repository

import com.example.dhansutra.data.local.dao.TransactionWithCategory
import com.example.dhansutra.data.local.entity.CategoryEntity
import com.example.dhansutra.data.local.entity.LoanEntity
import com.example.dhansutra.data.local.entity.MonthlyBudgetEntity
import com.example.dhansutra.data.local.entity.TransactionEntity
import com.example.dhansutra.domain.model.BudgetEntry
import com.example.dhansutra.domain.model.Category
import com.example.dhansutra.domain.model.Loan
import com.example.dhansutra.domain.model.Transaction
import com.example.dhansutra.domain.model.TransactionItem

fun CategoryEntity.toDomain() = Category(id, name, iconName, defaultBudget, rolloverType, nature)
fun Category.toEntity() = CategoryEntity(id, name, iconName, defaultBudget, rolloverType, nature)

fun LoanEntity.toDomain() = Loan(id, counterparty, direction)
fun Loan.toEntity() = LoanEntity(id, counterparty, direction)

fun TransactionEntity.toDomain() = Transaction(id, amount, categoryId, dateMillis, type, notes, nature, coveredMonths, loanId)
fun Transaction.toEntity() = TransactionEntity(id, amount, categoryId, dateMillis, type, notes, nature, coveredMonths, loanId)

fun TransactionWithCategory.toDomain() = TransactionItem(transaction.toDomain(), categoryName, categoryIcon, loanName, loanDirection)

fun MonthlyBudgetEntity.toDomain() = BudgetEntry(id, monthYear, categoryId, allocatedAmount, carriedOverAmount)
fun BudgetEntry.toEntity() = MonthlyBudgetEntity(id, monthYear, categoryId, allocatedAmount, carriedOverAmount)
