package com.example.dhansutra.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dhansutra.domain.model.BudgetItem
import com.example.dhansutra.domain.model.DashboardSummary
import com.example.dhansutra.domain.model.LoanBalance
import com.example.dhansutra.domain.model.PeriodicExpenseSummary
import com.example.dhansutra.domain.repository.SettingsRepository
import com.example.dhansutra.domain.usecase.ObserveDashboardSummary
import com.example.dhansutra.domain.usecase.ObserveMonthlyBudget
import com.example.dhansutra.domain.usecase.ObserveOpenLoans
import com.example.dhansutra.domain.usecase.ObservePeriodicExpenses
import com.example.dhansutra.util.MonthYear
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth

enum class DashboardBudgetSort(val label: String) {
    SPENT_DESC("Spent (High-Low)"),
    PERCENT_DESC("% Spent"),
    BUDGET_DESC("Budget (High-Low)"),
    NAME_ASC("Name (A-Z)"),
}

data class DashboardUiState(
    val month: YearMonth = MonthYear.current(),
    val summary: DashboardSummary = DashboardSummary(0, 0, 0, 0),
    val budgets: List<BudgetItem> = emptyList(),
    /** Periodic / one-time lump-sum payments made this month (kept out of [budgets]). */
    val periodic: PeriodicExpenseSummary = PeriodicExpenseSummary(),
    /** Loans with an outstanding balance. */
    val openLoans: List<LoanBalance> = emptyList(),
    val rolloverEnabled: Boolean = true,
    val hideZeroSpending: Boolean = false,
    val sortOrder: DashboardBudgetSort = DashboardBudgetSort.SPENT_DESC,
    val isLoading: Boolean = true,
) {
    /** Budget rows that carry a non-zero balance into this month. */
    val carriedItems: List<BudgetItem> get() = budgets.filter { it.carriedOver != 0L }
}

class DashboardViewModel(
    observeSummary: ObserveDashboardSummary,
    observeBudget: ObserveMonthlyBudget,
    observePeriodic: ObservePeriodicExpenses,
    observeOpenLoans: ObserveOpenLoans,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val month = MonthYear.current()
    private val hideZeroSpending = MutableStateFlow(false)
    private val sortOrder = MutableStateFlow(DashboardBudgetSort.SPENT_DESC)

    private val budgetDataFlow = combine(
        observeBudget(month),
        hideZeroSpending,
        sortOrder,
    ) { budgets, hideZero, sort ->
        val visible = budgets.filter { it.available != 0L || it.spent != 0L }
        val filtered = if (hideZero) visible.filter { it.spent > 0L } else visible
        val sorted = when (sort) {
            DashboardBudgetSort.SPENT_DESC -> filtered.sortedByDescending { it.spent }
            DashboardBudgetSort.PERCENT_DESC -> filtered.sortedByDescending { it.spentFraction }
            DashboardBudgetSort.BUDGET_DESC -> filtered.sortedByDescending { it.available }
            DashboardBudgetSort.NAME_ASC -> filtered.sortedBy { it.category.name.lowercase() }
        }
        Triple(sorted, hideZero, sort)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        observeSummary(month),
        budgetDataFlow,
        observePeriodic(month),
        observeOpenLoans(),
        settingsRepository.rolloverEnabled,
    ) { summary, (budgets, hideZero, sort), periodic, loans, enabled ->
        DashboardUiState(
            month = month,
            summary = summary,
            budgets = budgets,
            periodic = periodic,
            openLoans = loans,
            rolloverEnabled = enabled,
            hideZeroSpending = hideZero,
            sortOrder = sort,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState(month))

    fun setRolloverEnabled(enabled: Boolean) = settingsRepository.setRolloverEnabled(enabled)
    fun toggleHideZeroSpending() = hideZeroSpending.update { !it }
    fun setSortOrder(order: DashboardBudgetSort) { sortOrder.value = order }
}
