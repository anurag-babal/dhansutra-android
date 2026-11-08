package com.example.dhansutra.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dhansutra.domain.model.CategoryBreakdown
import com.example.dhansutra.domain.model.MonthlyTrendPoint
import com.example.dhansutra.domain.usecase.ObserveCategoryBreakdown
import com.example.dhansutra.domain.usecase.ObserveMonthlyTrend
import com.example.dhansutra.util.MonthYear
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

const val TREND_MONTHS = 6

data class AnalyticsUiState(
    val month: YearMonth = MonthYear.current(),
    val breakdown: List<CategoryBreakdown> = emptyList(),
    val trend: List<MonthlyTrendPoint> = emptyList(),
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(
    observeBreakdown: ObserveCategoryBreakdown,
    observeTrend: ObserveMonthlyTrend,
) : ViewModel() {

    private val month = MutableStateFlow(MonthYear.current())

    val uiState: StateFlow<AnalyticsUiState> = month.flatMapLatest { m ->
        combine(observeBreakdown(m), observeTrend(m, TREND_MONTHS)) { breakdown, trend ->
            AnalyticsUiState(m, breakdown, trend, isLoading = false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    fun setMonth(value: YearMonth) {
        month.value = value
    }
}
