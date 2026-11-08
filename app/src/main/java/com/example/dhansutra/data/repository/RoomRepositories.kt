package com.example.dhansutra.data.repository

import com.example.dhansutra.data.local.dao.CategoryDao
import com.example.dhansutra.data.local.dao.LoanDao
import com.example.dhansutra.data.local.dao.MonthlyBudgetDao
import com.example.dhansutra.data.local.dao.TransactionDao
import com.example.dhansutra.data.local.entity.LoanEntity
import com.example.dhansutra.domain.model.BudgetEntry
import com.example.dhansutra.domain.model.Category
import com.example.dhansutra.domain.model.Loan
import com.example.dhansutra.domain.model.LoanBalance
import com.example.dhansutra.domain.model.LoanDirection
import com.example.dhansutra.domain.model.MonthCategorySpend
import com.example.dhansutra.domain.model.MonthTotals
import com.example.dhansutra.domain.model.PeriodicExpenseItem
import com.example.dhansutra.domain.model.PeriodicExpenseSummary
import com.example.dhansutra.domain.model.Transaction
import com.example.dhansutra.domain.model.TransactionFilter
import com.example.dhansutra.domain.model.TransactionItem
import com.example.dhansutra.domain.model.TransactionNature
import com.example.dhansutra.domain.model.TransactionType
import com.example.dhansutra.domain.repository.BudgetRepository
import com.example.dhansutra.domain.repository.CategoryRepository
import com.example.dhansutra.domain.repository.LoanRepository
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

    override suspend fun getTransactionCount(categoryId: Long): Int = transactionDao.countByCategory(categoryId)

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

    override fun observeTotal(type: TransactionType, startMillis: Long, endMillis: Long, nature: TransactionNature?) =
        dao.observeTotal(type, startMillis, endMillis, nature)

    override suspend fun getTotal(type: TransactionType, startMillis: Long, endMillis: Long, nature: TransactionNature?) =
        dao.getTotal(type, startMillis, endMillis, nature)

    override fun observeTotalsByCategory(
        type: TransactionType,
        startMillis: Long,
        endMillis: Long,
        nature: TransactionNature?,
    ) = dao.observeTotalsByCategory(type, startMillis, endMillis, nature)
        .map { l -> l.associate { it.categoryId to it.total } }

    override suspend fun getTotalsByCategory(
        type: TransactionType,
        startMillis: Long,
        endMillis: Long,
        nature: TransactionNature?,
    ) = dao.getTotalsByCategory(type, startMillis, endMillis, nature).associate { it.categoryId to it.total }

    override fun observeMonthTotals(startMillis: Long): Flow<List<MonthTotals>> =
        dao.observeMonthTotals(startMillis).map { rows ->
            rows.groupBy { it.monthYear }.map { (month, list) ->
                fun sum(type: TransactionType, vararg natures: TransactionNature) =
                    list.filter { it.type == type && it.nature in natures }.sumOf { it.total }
                MonthTotals(
                    monthYear = month,
                    // Investments and loans are neither earning nor spending.
                    spent = sum(TransactionType.EXPENSE, TransactionNature.REGULAR_OPERATING, TransactionNature.PERIODIC_ONE_TIME),
                    income = sum(TransactionType.INCOME, TransactionNature.REGULAR_OPERATING),
                    periodicSpent = sum(TransactionType.EXPENSE, TransactionNature.PERIODIC_ONE_TIME),
                    invested = sum(TransactionType.EXPENSE, TransactionNature.INVESTMENT) -
                        sum(TransactionType.INCOME, TransactionNature.INVESTMENT),
                )
            }.sortedBy { it.monthYear }
        }

    override fun observeNetInvested(startMillis: Long, endMillis: Long): Flow<Long> =
        dao.observeNetOutflow(TransactionNature.INVESTMENT, startMillis, endMillis)

    override fun observeMonthCategoryExpenses(endMillis: Long, nature: TransactionNature?): Flow<List<MonthCategorySpend>> =
        dao.observeMonthCategoryTotals(TransactionType.EXPENSE, endMillis, nature)
            .map { l -> l.map { MonthCategorySpend(it.monthYear, it.categoryId, it.total) } }

    override fun observeCashBalance(): Flow<Long> =
        combine(
            dao.observeAllTimeTotal(TransactionType.INCOME),
            dao.observeAllTimeTotal(TransactionType.EXPENSE),
        ) { income, expenses -> income - expenses }

    override fun observePeriodicExpenses(startMillis: Long, endMillis: Long): Flow<PeriodicExpenseSummary> =
        dao.observeByNature(TransactionNature.PERIODIC_ONE_TIME, startMillis, endMillis).map { rows ->
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

class LoanRepositoryImpl(private val dao: LoanDao) : LoanRepository {
    override fun observeAll(): Flow<List<Loan>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    override fun observeBalances(): Flow<List<LoanBalance>> = dao.observeNets().map { rows ->
        rows.map { row ->
            // net = lent out minus received back. For borrowed money the sign flips. Never negative.
            val outstanding = if (row.direction == LoanDirection.LENT) row.net else -row.net
            LoanBalance(Loan(row.id, row.counterparty, row.direction), outstanding.coerceAtLeast(0))
        }
    }

    override suspend fun getOrCreate(counterparty: String, direction: LoanDirection): Long {
        val name = counterparty.trim()
        return dao.find(name, direction)?.id ?: dao.insert(LoanEntity(counterparty = name, direction = direction))
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
