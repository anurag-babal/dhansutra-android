package com.example.dhansutra.data.repository

import com.example.dhansutra.data.local.dao.CategoryDao
import com.example.dhansutra.data.local.dao.MonthlyBudgetDao
import com.example.dhansutra.data.local.dao.TransactionDao
import com.example.dhansutra.domain.model.BudgetEntry
import com.example.dhansutra.domain.model.Category
import com.example.dhansutra.domain.model.MonthCategorySpend
import com.example.dhansutra.domain.model.MonthTotals
import com.example.dhansutra.domain.model.Transaction
import com.example.dhansutra.domain.model.TransactionFilter
import com.example.dhansutra.domain.model.TransactionItem
import com.example.dhansutra.domain.model.TransactionType
import com.example.dhansutra.domain.repository.BudgetRepository
import com.example.dhansutra.domain.repository.CategoryRepository
import com.example.dhansutra.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
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

    override fun observeTotal(type: TransactionType, startMillis: Long, endMillis: Long) =
        dao.observeTotal(type, startMillis, endMillis)

    override suspend fun getTotal(type: TransactionType, startMillis: Long, endMillis: Long) =
        dao.getTotal(type, startMillis, endMillis)

    override fun observeTotalsByCategory(type: TransactionType, startMillis: Long, endMillis: Long) =
        dao.observeTotalsByCategory(type, startMillis, endMillis).map { l -> l.associate { it.categoryId to it.total } }

    override suspend fun getTotalsByCategory(type: TransactionType, startMillis: Long, endMillis: Long) =
        dao.getTotalsByCategory(type, startMillis, endMillis).associate { it.categoryId to it.total }

    override fun observeMonthTotals(startMillis: Long): Flow<List<MonthTotals>> =
        dao.observeMonthTotals(startMillis).map { rows ->
            rows.groupBy { it.monthYear }.map { (month, list) ->
                MonthTotals(
                    monthYear = month,
                    spent = list.filter { it.type == TransactionType.EXPENSE }.sumOf { it.total },
                    income = list.filter { it.type == TransactionType.INCOME }.sumOf { it.total },
                )
            }.sortedBy { it.monthYear }
        }

    override fun observeMonthCategoryExpenses(endMillis: Long): Flow<List<MonthCategorySpend>> =
        dao.observeMonthCategoryTotals(TransactionType.EXPENSE, endMillis)
            .map { l -> l.map { MonthCategorySpend(it.monthYear, it.categoryId, it.total) } }
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
