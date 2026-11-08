package com.example.dhansutra.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.dhansutra.domain.model.Loan
import com.example.dhansutra.domain.model.LoanBalance
import com.example.dhansutra.domain.model.LoanDirection
import com.example.dhansutra.domain.model.PeriodicExpenseItem
import com.example.dhansutra.domain.model.PeriodicExpenseSummary
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
    val vm = appViewModel {
        DashboardViewModel(it.observeDashboardSummary, it.observeMonthlyBudget, it.observePeriodicExpenses, it.observeOpenLoans, it.settingsRepository)
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        onRolloverToggle = vm::setRolloverEnabled,
        onToggleZeroSpending = vm::toggleHideZeroSpending,
        onSortChange = vm::setSortOrder,
    )
}

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onRolloverToggle: (Boolean) -> Unit,
    onToggleZeroSpending: () -> Unit = {},
    onSortChange: (DashboardBudgetSort) -> Unit = {},
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text(MonthYear.label(state.month), style = MaterialTheme.typography.headlineSmall) }
        item { BalanceCard(state.summary) }
        item { MonthCard(state.summary) }
        item {
            BudgetsHeader(
                rolloverEnabled = state.rolloverEnabled,
                onRolloverToggle = onRolloverToggle,
                hideZeroSpending = state.hideZeroSpending,
                onToggleZeroSpending = onToggleZeroSpending,
                sortOrder = state.sortOrder,
                onSortChange = onSortChange,
            )
        }
        if (state.budgets.isEmpty()) {
            item {
                Text(
                    if (state.hideZeroSpending) "No categories with spending this month."
                    else "No budgets set for this month. Add categories in the Planning tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.budgets.size) { index -> CategoryProgress(state.budgets[index], state.rolloverEnabled) }
        if (state.periodic.items.isNotEmpty()) {
            item { PeriodicExpensesCard(state.periodic) }
        }
        if (state.openLoans.isNotEmpty()) {
            item { LoansCard(state.openLoans) }
        }
    }
}

@Composable
private fun BudgetsHeader(
    rolloverEnabled: Boolean,
    onRolloverToggle: (Boolean) -> Unit,
    hideZeroSpending: Boolean,
    onToggleZeroSpending: () -> Unit,
    sortOrder: DashboardBudgetSort,
    onSortChange: (DashboardBudgetSort) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Budgets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("Rollover", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 6.dp))
            Switch(checked = rolloverEnabled, onCheckedChange = onRolloverToggle)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = hideZeroSpending,
                onClick = onToggleZeroSpending,
                label = { Text("Hide ₹0 spent") },
            )
            var sortExpanded by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = false,
                    onClick = { sortExpanded = true },
                    label = { Text("Sort: ${sortOrder.label}") },
                    trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
                )
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    DashboardBudgetSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.label, fontWeight = if (sort == sortOrder) FontWeight.Bold else null) },
                            onClick = {
                                onSortChange(sort)
                                sortExpanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Hero card: the real bank balance, net worth, and collapsible positions that sit outside it. */
@Composable
private fun BalanceCard(summary: DashboardSummary) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Cash balance", style = MaterialTheme.typography.labelLarge)
                    Text(
                        CurrencyFormatter.format(summary.cashBalance),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.cashBalance < 0) MaterialTheme.colorScheme.error else Color.Unspecified,
                        maxLines = 1,
                    )
                    Text(
                        "Net worth ${CurrencyFormatter.format(summary.netWorth, false)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Hide details" else "Show details",
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                Row(Modifier.fillMaxWidth()) {
                    Position("Invested", summary.investedTotal, Modifier.weight(1f))
                    Position("Owed to you", summary.owedToYou, Modifier.weight(1f))
                    Position("You owe", summary.youOwe, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Position(label: String, amount: Long, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            CurrencyFormatter.format(amount, false),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** This month as a short ledger: what came in, what went out, and what was left. */
@Composable
private fun MonthCard(summary: DashboardSummary) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This month", style = MaterialTheme.typography.titleMedium)
            LedgerRow("Income", summary.income)
            LedgerRow("Regular spending", summary.spent)
            if (summary.periodicSpent != 0L) LedgerRow("Periodic payments", summary.periodicSpent)
            if (summary.invested != 0L) LedgerRow("Invested (not spending)", summary.invested)
            HorizontalDivider()
            LedgerRow("Surplus", summary.surplus, emphasis = true, negative = summary.surplus < 0)
            LedgerRow("Savings in past months", summary.savings, negative = summary.savings < 0)
        }
    }
}

@Composable
private fun LedgerRow(label: String, amount: Long, emphasis: Boolean = false, negative: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (emphasis) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (emphasis) FontWeight.SemiBold else null,
        )
        Text(
            CurrencyFormatter.format(amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasis) FontWeight.SemiBold else null,
            color = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** Collapsible list of people with an outstanding balance. */
@Composable
private fun LoansCard(loans: List<LoanBalance>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Lending & borrowing", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${loans.size} open",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }
            if (expanded) {
                loans.forEach { balance ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(balance.loan.counterparty, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(
                                if (balance.loan.direction == LoanDirection.LENT) "Owes you" else "You owe",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(CurrencyFormatter.format(balance.outstanding), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Collapsible list of periodic lump-sum payments made this month, each with a duration badge. */
@Composable
private fun PeriodicExpensesCard(periodic: PeriodicExpenseSummary) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Periodic / one-time expenses", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${CurrencyFormatter.format(periodic.total, false)} paid this month" +
                            " · ≈ ${CurrencyFormatter.format(periodic.monthlyEquivalent, false)}/month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }
            if (expanded) {
                periodic.items.forEach { PeriodicExpenseRow(it) }
            }
        }
    }
}

@Composable
private fun PeriodicExpenseRow(item: PeriodicExpenseItem) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CategoryIcon(item.categoryIcon, Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(item.notes.ifBlank { item.categoryName }, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                "≈ ${CurrencyFormatter.format(item.monthlyEquivalent, false)}/month",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AssistChip(
            onClick = {},
            label = {
                Text("${CurrencyFormatter.format(item.amount, false)} • Covers ${item.coveredMonths} Month" +
                    if (item.coveredMonths == 1) "" else "s")
            },
        )
    }
}

@Composable
private fun CategoryProgress(item: BudgetItem, rolloverEnabled: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(item.category.iconName, Modifier.size(20.dp))
            Text(item.category.name, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
            if (rolloverEnabled && item.carriedOver != 0L) {
                Text(
                    (if (item.carriedOver > 0) "+" else "") + CurrencyFormatter.compact(item.carriedOver) + " carried",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.carriedOver < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            val percent = if (item.available > 0) "${(item.spentFraction * 100).roundToInt()}%" else if (item.spent > 0) "over" else "0%"
            Text(
                percent,
                style = MaterialTheme.typography.labelLarge,
                color = if (item.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        BudgetProgressBar(item.spentFraction, Modifier.padding(vertical = 6.dp))
        val budgetDetail = if (rolloverEnabled && item.carriedOver != 0L) {
            "${CurrencyFormatter.format(item.spent, false)} of ${CurrencyFormatter.format(item.available, false)} (base ${CurrencyFormatter.format(item.allocated, false)})"
        } else {
            "${CurrencyFormatter.format(item.spent, false)} of ${CurrencyFormatter.format(item.available, false)}"
        }
        Text(
            budgetDetail + if (item.isOverBudget) " · over by ${CurrencyFormatter.format(-item.remaining, false)}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = if (item.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun cat(id: Long, name: String, icon: String, type: RolloverType) = Category(id, name, icon, 0, type)

private val previewState = DashboardUiState(
    month = YearMonth.of(2026, 11),
    summary = DashboardSummary(
        income = 8_500_000,
        spent = 3_270_000,
        surplus = 4_030_000,
        savings = 12_345_600,
        periodicSpent = 1_200_000,
        cashBalance = 16_375_600,
        invested = 500_000,
        investedTotal = 6_000_000,
        owedToYou = 1_000_000,
        youOwe = 250_000,
    ),
    periodic = PeriodicExpenseSummary(
        total = 1_200_000,
        monthlyEquivalent = 200_000,
        items = listOf(PeriodicExpenseItem(1, "Shopping", "shopping_bag", "Gym membership", 0L, 1_200_000, 6)),
    ),
    openLoans = listOf(
        LoanBalance(Loan(1, "Rahul", LoanDirection.LENT), 1_000_000),
        LoanBalance(Loan(2, "Amit", LoanDirection.BORROWED), 250_000),
    ),
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
