package com.example.dhansutra.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dhansutra.domain.model.BudgetItem
import com.example.dhansutra.domain.model.Category
import com.example.dhansutra.domain.model.DashboardSummary
import com.example.dhansutra.domain.model.RolloverType
import com.example.dhansutra.ui.appViewModel
import com.example.dhansutra.ui.components.BudgetProgressBar
import com.example.dhansutra.ui.components.CategoryIcon
import com.example.dhansutra.ui.theme.DhanSutraTheme
import com.example.dhansutra.util.CurrencyFormatter
import com.example.dhansutra.util.MonthYear
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun DashboardRoute() {
    val vm = appViewModel { DashboardViewModel(it.observeDashboardSummary, it.observeMonthlyBudget, it.settingsRepository) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    DashboardScreen(state, vm::setRolloverEnabled)
}

@Composable
fun DashboardScreen(state: DashboardUiState, onRolloverToggle: (Boolean) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(MonthYear.label(state.month), style = MaterialTheme.typography.headlineSmall)
        }
        item { SummaryCards(state.summary) }
        item {
            Text("Budget vs actual", style = MaterialTheme.typography.titleMedium)
        }
        if (state.budgets.isEmpty()) {
            item {
                Text(
                    "No budgets set for this month. Add categories in the Planning tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.budgets.size) { index -> CategoryProgress(state.budgets[index]) }
        item { RolloverCard(state, onRolloverToggle) }
    }
}

@Composable
private fun SummaryCards(summary: DashboardSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Monthly income", CurrencyFormatter.format(summary.income), Modifier.weight(1f))
            StatCard("Spent so far", CurrencyFormatter.format(summary.spent), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                "Remaining surplus",
                CurrencyFormatter.format(summary.surplus),
                Modifier.weight(1f),
                negative = summary.surplus < 0,
            )
            StatCard("Total savings", CurrencyFormatter.format(summary.savings), Modifier.weight(1f), negative = summary.savings < 0)
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier, negative: Boolean = false) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CategoryProgress(item: BudgetItem) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(item.category.iconName, Modifier.size(20.dp))
            Text(item.category.name, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
            val percent = if (item.available > 0) "${(item.spentFraction * 100).roundToInt()}%" else if (item.spent > 0) "over" else "0%"
            Text(
                percent,
                style = MaterialTheme.typography.labelLarge,
                color = if (item.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        BudgetProgressBar(item.spentFraction, Modifier.padding(vertical = 6.dp))
        Text(
            "${CurrencyFormatter.format(item.spent, false)} of ${CurrencyFormatter.format(item.available, false)}" +
                if (item.isOverBudget) " · over by ${CurrencyFormatter.format(-item.remaining, false)}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = if (item.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RolloverCard(state: DashboardUiState, onToggle: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Monthly rollover", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Carry forward or sweep unused budget at month end",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.rolloverEnabled, onCheckedChange = onToggle)
            }
            if (!state.rolloverEnabled) {
                Text("Rollover is off: every month starts fresh and nothing is swept to savings.", style = MaterialTheme.typography.bodySmall)
            } else if (state.carriedItems.isEmpty()) {
                Text("Nothing carried into this month.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Carried into this month", style = MaterialTheme.typography.labelLarge)
                state.carriedItems.forEach { item ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(item.category.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(
                            (if (item.carriedOver > 0) "+" else "") + CurrencyFormatter.format(item.carriedOver, false),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.carriedOver < 0) MaterialTheme.colorScheme.error else Color.Unspecified,
                        )
                    }
                }
            }
        }
    }
}

private fun cat(id: Long, name: String, icon: String, type: RolloverType) = Category(id, name, icon, 0, type)

private val previewState = DashboardUiState(
    month = YearMonth.of(2026, 11),
    summary = DashboardSummary(income = 8_500_000, spent = 3_270_000, surplus = 5_230_000, savings = 12_345_600),
    budgets = listOf(
        BudgetItem(cat(1, "Rent/EMI", "home", RolloverType.CARRY_FORWARD), 1_500_000, 0, 1_500_000),
        BudgetItem(cat(2, "Groceries", "shopping_cart", RolloverType.CARRY_FORWARD), 800_000, 120_000, 650_000),
        BudgetItem(cat(3, "Dining & Swiggy", "restaurant", RolloverType.SWEEP_TO_SAVINGS), 400_000, 0, 520_000),
    ),
    isLoading = false,
)

@Preview(showBackground = true)
@Composable
private fun DashboardPreview() {
    DhanSutraTheme { DashboardScreen(previewState, {}) }
}

@Preview(showBackground = true)
@Composable
private fun DashboardEmptyPreview() {
    DhanSutraTheme {
        DashboardScreen(DashboardUiState(month = YearMonth.of(2026, 11), isLoading = false, rolloverEnabled = false), {})
    }
}
