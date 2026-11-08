package com.example.dhansutra.di

import android.content.Context
import com.example.dhansutra.data.local.AppDatabase
import com.example.dhansutra.data.repository.BudgetRepositoryImpl
import com.example.dhansutra.data.repository.CategoryRepositoryImpl
import com.example.dhansutra.data.repository.SharedPrefsSettingsRepository
import com.example.dhansutra.data.repository.TransactionRepositoryImpl
import com.example.dhansutra.domain.repository.BudgetRepository
import com.example.dhansutra.domain.repository.CategoryRepository
import com.example.dhansutra.domain.repository.SettingsRepository
import com.example.dhansutra.domain.repository.TransactionRepository
import com.example.dhansutra.domain.usecase.ObserveCategoryBreakdown
import com.example.dhansutra.domain.usecase.ObserveDashboardSummary
import com.example.dhansutra.domain.usecase.ObserveMonthlyBudget
import com.example.dhansutra.domain.usecase.ObserveMonthlyTrend
import com.example.dhansutra.domain.usecase.ObservePeriodicExpenses
import com.example.dhansutra.domain.usecase.RolloverManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container holding app-wide singletons. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val categoryRepository: CategoryRepository by lazy {
        CategoryRepositoryImpl(database.categoryDao(), database.transactionDao())
    }
    val transactionRepository: TransactionRepository by lazy { TransactionRepositoryImpl(database.transactionDao()) }
    val budgetRepository: BudgetRepository by lazy { BudgetRepositoryImpl(database.monthlyBudgetDao()) }
    val settingsRepository: SettingsRepository by lazy { SharedPrefsSettingsRepository(appContext) }

    val rolloverManager: RolloverManager by lazy {
        RolloverManager(categoryRepository, transactionRepository, budgetRepository, settingsRepository)
    }

    val observeMonthlyBudget by lazy { ObserveMonthlyBudget(categoryRepository, budgetRepository, transactionRepository) }
    val observeDashboardSummary by lazy { ObserveDashboardSummary(transactionRepository) }
    val observeCategoryBreakdown by lazy { ObserveCategoryBreakdown(categoryRepository, transactionRepository) }
    val observeMonthlyTrend by lazy { ObserveMonthlyTrend(transactionRepository) }
    val observePeriodicExpenses by lazy { ObservePeriodicExpenses(transactionRepository) }

    /** Starts background work (budget row maintenance). Called once from the Application. */
    fun start() {
        rolloverManager.start(appScope)
    }
}
