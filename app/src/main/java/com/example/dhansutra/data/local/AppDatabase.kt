package com.example.dhansutra.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.dhansutra.data.local.dao.CategoryDao
import com.example.dhansutra.data.local.dao.MonthlyBudgetDao
import com.example.dhansutra.data.local.dao.TransactionDao
import com.example.dhansutra.data.local.entity.CategoryEntity
import com.example.dhansutra.data.local.entity.MonthlyBudgetEntity
import com.example.dhansutra.data.local.entity.TransactionEntity

@Database(
    entities = [CategoryEntity::class, TransactionEntity::class, MonthlyBudgetEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun monthlyBudgetDao(): MonthlyBudgetDao

    companion object {
        const val NAME = "dhansutra.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addCallback(SeedCallback)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/** Adds periodic-expense tagging to transactions; existing rows become regular 1-month expenses. */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN expenseType TEXT NOT NULL DEFAULT 'REGULAR_OPERATING'")
        db.execSQL("ALTER TABLE transactions ADD COLUMN coveredMonths INTEGER NOT NULL DEFAULT 1")
    }
}

/** Seeds default categories (sample budgets, in paise) the first time the database is created. */
internal object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // name, iconName, defaultBudget (paise), rolloverType
        listOf(
            arrayOf<Any>("Rent/EMI", "home", 1_500_000L, "CARRY_FORWARD"),
            arrayOf<Any>("Groceries", "shopping_cart", 800_000L, "CARRY_FORWARD"),
            arrayOf<Any>("Utilities", "bolt", 300_000L, "SWEEP_TO_SAVINGS"),
            arrayOf<Any>("Dining & Swiggy", "restaurant", 400_000L, "SWEEP_TO_SAVINGS"),
            arrayOf<Any>("Shopping", "shopping_bag", 500_000L, "SWEEP_TO_SAVINGS"),
            // Gives income entries a sensible category; has no budget.
            arrayOf<Any>("Salary & Income", "savings", 0L, "CARRY_FORWARD"),
        ).forEach {
            db.execSQL(
                "INSERT INTO categories (name, iconName, defaultBudget, rolloverType) VALUES (?, ?, ?, ?)",
                it,
            )
        }
    }
}
