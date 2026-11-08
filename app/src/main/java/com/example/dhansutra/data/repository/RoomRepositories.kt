package com.example.dhansutra.data.repository

import com.example.dhansutra.data.local.dao.CategoryDao
import com.example.dhansutra.data.local.dao.MonthlyBudgetDao
import com.example.dhansutra.data.local.dao.TransactionDao
import com.example.dhansutra.domain.model.BudgetEntry
import com.example.dhansutra.domain.model.Category
import com.example.dhansutra.domain.model.ExpenseType
import com.example.dhansutra.domain.model.MonthCategorySpend
import com.example.dhansutra.domain.model.MonthTotals
import com.example.dhansutra.domain.model.PeriodicExpenseItem
import com.example.dhansutra.domain.model.PeriodicExpenseSummary
import com.example.dhansutra.domain.model.Transaction
import com.example.dhansutra.domain.model.TransactionFilter
import com.example.dhansutra.domain.model.TransactionItem
import com.example.dhansutra.domain.model.TransactionType
import com.example.dhansutra.domain.repository.BudgetRepository
import com.example.dhansutra.domain.repository.CategoryRepository
import com.example.dhansutra.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class CategoryRepositoryImpl(
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
) : CategoryRepository {
    override fun observeAll(): Flow<List<Category>> = categoryDao.observeAll().map { l -> l.map { it.toDomain() } }
    override suspend fun getAll(): List<Category> = categoryDao.getAll().map { it.toDomain() }
    override suspend fun add(category: Category): Long = categoryDao.insert(category.copy(id = 0).toEntity())
    override suspend fun update(category: Category) = categoryDao.update(category.toEntity())

    override suspend fun delete(categoryId: Long): Boolean {
        if (transactionDao.countByCategory(categoryId) > 0) return false
        val entity = categoryDao.getById(categoryId) ?: return true
        categoryDao.delete(entity)
        return true
    }
}

class TransactionRepositoryImpl(private val dao: TransactionDao) : TransactionRepository {
    override fun observeFiltered(filter: TransactionFilter): Flow<List<TransactionItem>> =
        dao.observeFiltered(filter.startMillis, filter.endMillis, filter.categoryId, filter.query.trim())
            .map { l -> l.map { it.toDomain() } }

    override suspend fun getById(id: Long): Transaction? = dao.getById(id)?.toDomain()

    override suspend fun save(transaction: Transaction): Long =
        if (transaction.id == 0L) {
            dao.insert(transaction.toEntity())
        } else {
            dao.update(transaction.toEntity())
            transaction.id
        }

    override suspend fun delete(id: Long) {
        dao.getById(id)?.let { dao.delete(it) }
    }

    override fun observeTotal(type: TransactionType, startMillis: Long, endMillis: Long, expenseType: ExpenseType?) =
        dao.observeTotal(type, startMillis, endMillis, expenseType)

    override suspend fun getTotal(type: TransactionType, startMillis: Long, endMillis: Long, expenseType: ExpenseType?) =
        dao.getTotal(type, startMillis, endMillis, expenseType)

    override fun observeTotalsByCategory(
        type: TransactionType,
        startMillis: Long,
        endMillis: Long,
        expenseType: ExpenseType?,
    ) = dao.observeTotalsByCategory(type, startMillis, endMillis, expenseType)
        .map { l -> l.associate { it.categoryId to it.total } }

    override suspend fun getTotalsByCategory(
        type: TransactionType,
        startMillis: Long,
        endMillis: Long,
        expenseType: ExpenseType?,
    ) = dao.getTotalsByCategory(type, startMillis, endMillis, expenseType).associate { it.categoryId to it.total }

    override fun observeMonthTotals(startMillis: Long): Flow<List<MonthTotals>> =
        dao.observeMonthTotals(startMillis).map { rows ->
            rows.groupBy { it.monthYear }.map { (month, list) ->
                val expenses = list.filter { it.type == TransactionType.EXPENSE }
                MonthTotals(
                    monthYear = month,
                    spent = expenses.sumOf { it.total },
                    income = list.filter { it.type == TransactionType.INCOME }.sumOf { it.total },
                    periodicSpent = expenses.filter { it.expenseType == ExpenseType.PERIODIC_ONE_TIME }.sumOf { it.total },
                )
            }.sortedBy { it.monthYear }
        }

    override fun observeMonthCategoryExpenses(endMillis: Long, expenseType: ExpenseType?): Flow<List<MonthCategorySpend>> =
        dao.observeMonthCategoryTotals(TransactionType.EXPENSE, endMillis, expenseType)
            .map { l -> l.map { MonthCategorySpend(it.monthYear, it.categoryId, it.total) } }

    override fun observeCashBalance(): Flow<Long> =
        combine(
            dao.observeAllTimeTotal(TransactionType.INCOME),
            dao.observeAllTimeTotal(TransactionType.EXPENSE),
        ) { income, expenses -> income - expenses }

    override fun observePeriodicExpenses(startMillis: Long, endMillis: Long): Flow<PeriodicExpenseSummary> =
        dao.observeByExpenseType(ExpenseType.PERIODIC_ONE_TIME, startMillis, endMillis).map { rows ->
            val items = rows.map {
                PeriodicExpenseItem(
                    id = it.transaction.id,
                    categoryName = it.categoryName,
                    categoryIcon = it.categoryIcon,
                    notes = it.transaction.notes,
                    dateMillis = it.transaction.dateMillis,
                    amount = it.transaction.amount,
                    coveredMonths = it.transaction.coveredMonths,
                )
            }
            PeriodicExpenseSummary(
                total = items.sumOf { it.amount },
                monthlyEquivalent = items.sumOf { it.monthlyEquivalent },
                items = items,
            )
        }
}

class BudgetRepositoryImpl(private val dao: MonthlyBudgetDao) : BudgetRepository {
    override fun observeAll(): Flow<List<BudgetEntry>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    override fun observeForMonth(monthYear: String): Flow<List<BudgetEntry>> =
        dao.observeForMonth(monthYear).map { l -> l.map { it.toDomain() } }

    override suspend fun getForMonth(monthYear: String): List<BudgetEntry> =
        dao.getForMonth(monthYear).map { it.toDomain() }

    override suspend fun getEarliestMonth(): String? = dao.getEarliestMonth()
    override suspend fun get(monthYear: String, categoryId: Long): BudgetEntry? =
        dao.get(monthYear, categoryId)?.toDomain()

    override suspend fun insertIfAbsent(entry: BudgetEntry): Boolean = dao.insertIfAbsent(entry.copy(id = 0).toEntity()) != -1L
    override suspend fun update(entry: BudgetEntry) = dao.update(entry.toEntity())
}
