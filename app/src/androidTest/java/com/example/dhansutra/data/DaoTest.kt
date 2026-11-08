package com.example.dhansutra.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dhansutra.data.local.AppDatabase
import com.example.dhansutra.data.local.entity.CategoryEntity
import com.example.dhansutra.data.local.entity.MonthlyBudgetEntity
import com.example.dhansutra.domain.model.RolloverType
import com.example.dhansutra.data.local.entity.TransactionEntity
import com.example.dhansutra.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: AppDatabase
    private var groceries = 0L
    private var dining = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        groceries = db.categoryDao().insert(
            CategoryEntity(name = "Groceries", iconName = "cart", defaultBudget = 100, rolloverType = RolloverType.CARRY_FORWARD),
        )
        dining = db.categoryDao().insert(
            CategoryEntity(name = "Dining", iconName = "food", defaultBudget = 50, rolloverType = RolloverType.SWEEP_TO_SAVINGS),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun tx(amount: Long, cat: Long, date: Long, type: TransactionType, notes: String = "") =
        db.transactionDao().insert(TransactionEntity(amount = amount, categoryId = cat, dateMillis = date, type = type, notes = notes))

    @Test
    fun filterByCategoryDateAndSearch() = runBlocking {
        tx(10, groceries, 1_000, TransactionType.EXPENSE, "milk")
        tx(20, dining, 2_000, TransactionType.EXPENSE, "pizza")
        tx(30, dining, 9_000, TransactionType.EXPENSE, "burger")
        val dao = db.transactionDao()

        assertEquals(3, dao.observeFiltered(0, 10_000, null, "").first().size)
        assertEquals(2, dao.observeFiltered(0, 10_000, dining, "").first().size)
        assertEquals(1, dao.observeFiltered(0, 5_000, dining, "").first().size)
        assertEquals("pizza", dao.observeFiltered(0, 10_000, null, "izz").first().single().transaction.notes)
        // search matches category name too
        assertEquals(2, dao.observeFiltered(0, 10_000, null, "dini").first().size)
        // newest first
        assertEquals(9_000, dao.observeFiltered(0, 10_000, null, "").first().first().transaction.dateMillis)
    }

    @Test
    fun totalsAndGrouping() = runBlocking {
        tx(10, groceries, 1_000, TransactionType.EXPENSE)
        tx(15, groceries, 2_000, TransactionType.EXPENSE)
        tx(7, dining, 2_000, TransactionType.EXPENSE)
        tx(500, groceries, 2_000, TransactionType.INCOME)
        val dao = db.transactionDao()

        assertEquals(32, dao.observeTotal(TransactionType.EXPENSE, 0, 10_000).first())
        assertEquals(500, dao.getTotal(TransactionType.INCOME, 0, 10_000))
        assertEquals(0, dao.getTotal(TransactionType.INCOME, 5_000, 10_000))
        val byCat = dao.getTotalsByCategory(TransactionType.EXPENSE, 0, 10_000).associate { it.categoryId to it.total }
        assertEquals(25L, byCat[groceries])
        assertEquals(7L, byCat[dining])
    }

    @Test
    fun monthlyBudgetUniquePerMonthAndCategory() = runBlocking {
        val dao = db.monthlyBudgetDao()
        val first = dao.insertIfAbsent(MonthlyBudgetEntity(monthYear = "2026-11", categoryId = groceries, allocatedAmount = 100))
        val dup = dao.insertIfAbsent(MonthlyBudgetEntity(monthYear = "2026-11", categoryId = groceries, allocatedAmount = 999))
        assertEquals(true, first > 0)
        assertEquals(-1L, dup)
        assertEquals(100L, dao.get("2026-11", groceries)?.allocatedAmount)
        assertEquals(1, dao.observeForMonth("2026-11").first().size)
    }

    @Test
    fun cannotDeleteCategoryWithTransactions() {
        runBlocking { tx(10, groceries, 1_000, TransactionType.EXPENSE) }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { db.categoryDao().delete(db.categoryDao().getById(groceries)!!) }
        }
    }
}
