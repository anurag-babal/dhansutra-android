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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class DashboardUiState(
    val month: YearMonth = MonthYear.current(),
    val summary: DashboardSummary = DashboardSummary(0, 0, 0, 0),
    val budgets: List<BudgetItem> = emptyList(),
    /** Periodic / one-time lump-sum payments made this month (kept out of [budgets]). */
    val periodic: PeriodicExpenseSummary = PeriodicExpenseSummary(),
    /** Loans with an outstanding balance. */
    val openLoans: List<LoanBalance> = emptyList(),
    val rolloverEnabled: Boolean = true,
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

    val uiState: StateFlow<DashboardUiState> = combine(
        observeSummary(month),
        observeBudget(month),
        observePeriodic(month),
        observeOpenLoans(),
        settingsRepository.rolloverEnabled,
    ) { summary, budgets, periodic, loans, enabled ->
        // Hide categories with no budget and no activity (e.g. the income category) from the progress list.
        val visible = budgets.filter { it.available != 0L || it.spent != 0L }
        DashboardUiState(month, summary, visible, periodic, loans, enabled, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState(month))

    fun setRolloverEnabled(enabled: Boolean) = settingsRepository.setRolloverEnabled(enabled)
}
