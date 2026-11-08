package com.example.dhansutra.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dhansutra.domain.model.CategoryBreakdown
import com.example.dhansutra.domain.model.MonthlyTrendPoint
import com.example.dhansutra.ui.appViewModel
import com.example.dhansutra.ui.components.MonthSelector
import com.example.dhansutra.ui.theme.DhanSutraTheme
import java.time.YearMonth

@Composable
fun AnalyticsRoute() {
    val vm = appViewModel { AnalyticsViewModel(it.observeCategoryBreakdown, it.observeMonthlyTrend) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    AnalyticsScreen(state, vm::setMonth)
}

@Composable
fun AnalyticsScreen(state: AnalyticsUiState, onMonthChange: (YearMonth) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MonthSelector(state.month, onMonthChange)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Spending by category", style = MaterialTheme.typography.titleMedium)
                if (state.breakdown.isEmpty() && !state.isLoading) {
                    Text(
                        "No expenses recorded this month.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    CategoryDonutChart(state.breakdown, Modifier.padding(top = 8.dp))
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Spending trend (last $TREND_MONTHS months)", style = MaterialTheme.typography.titleMedium)
                SpendingTrendChart(state.trend, Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AnalyticsPreview() {
    DhanSutraTheme {
        AnalyticsScreen(
            AnalyticsUiState(
                month = YearMonth.of(2026, 11),
                breakdown = listOf(CategoryBreakdown(1, "Rent/EMI", 1_500_000), CategoryBreakdown(2, "Groceries", 650_000)),
                trend = listOf(
                    MonthlyTrendPoint(YearMonth.of(2026, 9), 3_900_000, 0),
                    MonthlyTrendPoint(YearMonth.of(2026, 10), 3_400_000, 0),
                    MonthlyTrendPoint(YearMonth.of(2026, 11), 2_150_000, 0),
                ),
                isLoading = false,
            ),
            {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AnalyticsEmptyPreview() {
    DhanSutraTheme { AnalyticsScreen(AnalyticsUiState(isLoading = false), {}) }
}
