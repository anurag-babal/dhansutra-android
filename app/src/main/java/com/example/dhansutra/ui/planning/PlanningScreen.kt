package com.example.dhansutra.ui.planning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dhansutra.domain.model.BudgetItem
import com.example.dhansutra.domain.model.Category
import com.example.dhansutra.domain.model.RolloverType
import com.example.dhansutra.ui.appViewModel
import com.example.dhansutra.ui.components.CategoryIcon
import com.example.dhansutra.ui.components.CategoryIcons
import com.example.dhansutra.ui.components.EmptyState
import com.example.dhansutra.ui.components.MonthSelector
import com.example.dhansutra.ui.theme.DhanSutraTheme
import com.example.dhansutra.util.CurrencyFormatter
import java.time.YearMonth

@Composable
fun PlanningRoute() {
    val vm = appViewModel {
        PlanningViewModel(
            it.categoryRepository,
            it.budgetRepository,
            it.settingsRepository,
            it.observeMonthlyBudget,
            it.rolloverManager,
        )
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    PlanningScreen(
        state = state,
        onMonthChange = vm::setMonth,
        onAdd = vm::startAdd,
        onEdit = vm::startEdit,
        onEditorChange = vm::updateEditor,
        onSave = vm::save,
        onDelete = vm::deleteEditing,
        onDismissEditor = vm::dismissEditor,
        onMessageShown = vm::consumeMessage,
    )
}

@Composable
fun PlanningScreen(
    state: PlanningUiState,
    onMonthChange: (YearMonth) -> Unit,
    onAdd: () -> Unit,
    onEdit: (BudgetItem) -> Unit,
    onEditorChange: ((CategoryEditor) -> CategoryEditor) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismissEditor: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Rounded.Add, contentDescription = "Add category") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MonthSelector(state.month, onMonthChange, Modifier.padding(horizontal = 8.dp))
            if (!state.rolloverEnabled) {
                Text(
                    "Rollover is turned off (see Dashboard) - balances are not carried forward.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize())
                state.items.isEmpty() -> EmptyState("No budget for this month.\nTap + to add a category.")
                else -> {
                    HeaderRow()
                    HorizontalDivider()
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(state.items, key = { it.category.id }) { item ->
                            BudgetRow(item, state.rolloverEnabled, onClick = { onEdit(item) })
                            HorizontalDivider()
                        }
                        item {
                            TotalsRow(state)
                        }
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        CategoryEditorDialog(editor, onEditorChange, onSave, onDelete, onDismissEditor)
    }
}

private const val W_CATEGORY = 1.7f
private const val W_AMOUNT = 1.2f
private val cellPadding = PaddingValues(horizontal = 4.dp)

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    bold: Boolean = false,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
) {
    Text(
        text,
        modifier = Modifier.weight(weight).padding(cellPadding),
        textAlign = TextAlign.End,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else null,
        color = color,
        maxLines = 1,
    )
}

@Composable
private fun HeaderRow() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            "Category",
            Modifier.weight(W_CATEGORY).padding(cellPadding),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
        listOf("Budget", "Spent", "Left", "Rollover").forEach {
            Text(
                it,
                Modifier.weight(W_AMOUNT).padding(cellPadding),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun RolloverType.shortLabel() = when (this) {
    RolloverType.CARRY_FORWARD -> "Carry"
    RolloverType.SWEEP_TO_SAVINGS -> "Sweep"
}

@Composable
private fun BudgetRow(item: BudgetItem, rolloverEnabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(W_CATEGORY).padding(cellPadding), verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(item.category.iconName, Modifier.size(18.dp))
            Text(
                item.category.name,
                Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
        }
        // Allocated shows the base budget; any carried-over balance is reflected in "Left" and the rollover column.
        Cell(CurrencyFormatter.format(item.allocated, showDecimals = false), W_AMOUNT)
        Cell(CurrencyFormatter.format(item.spent, showDecimals = false), W_AMOUNT)
        Cell(
            CurrencyFormatter.format(item.remaining, showDecimals = false),
            W_AMOUNT,
            bold = true,
            color = if (item.remaining < 0) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified,
        )
        Column(Modifier.weight(W_AMOUNT).padding(cellPadding), horizontalAlignment = Alignment.End) {
            Text(
                if (rolloverEnabled) item.category.rolloverType.shortLabel() else "Off",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (rolloverEnabled && item.carriedOver != 0L) {
                Text(
                    (if (item.carriedOver > 0) "+" else "") + CurrencyFormatter.compact(item.carriedOver),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.carriedOver < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TotalsRow(state: PlanningUiState) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        Text("Total", Modifier.weight(W_CATEGORY).padding(cellPadding), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Cell(CurrencyFormatter.format(state.items.sumOf { it.allocated }, showDecimals = false), W_AMOUNT, bold = true)
        Cell(CurrencyFormatter.format(state.totalSpent, showDecimals = false), W_AMOUNT, bold = true)
        Cell(
            CurrencyFormatter.format(state.totalRemaining, showDecimals = false),
            W_AMOUNT,
            bold = true,
            color = if (state.totalRemaining < 0) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified,
        )
        Spacer(W_AMOUNT)
    }
}

@Composable
private fun RowScope.Spacer(weight: Float) {
    androidx.compose.foundation.layout.Spacer(Modifier.weight(weight))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditorDialog(
    editor: CategoryEditor,
    onEditorChange: ((CategoryEditor) -> CategoryEditor) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editor.isNew) "Add category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = { v -> onEditorChange { it.copy(name = v) } },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = editor.showErrors && editor.nameError,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editor.allocatedText,
                    onValueChange = { v -> onEditorChange { it.copy(allocatedText = v.filter { c -> c.isDigit() || c == '.' }) } },
                    label = { Text(if (editor.isNew) "Monthly budget" else "Budget for this month") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    isError = editor.showErrors && editor.allocatedError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!editor.isNew) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onEditorChange { it.copy(setAsDefault = !it.setAsDefault) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = editor.setAsDefault, onCheckedChange = { v -> onEditorChange { it.copy(setAsDefault = v) } })
                        Text("Use as default for future months", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("Rollover at month end", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RolloverType.entries.forEach { type ->
                        FilterChip(
                            selected = editor.rolloverType == type,
                            onClick = { onEditorChange { it.copy(rolloverType = type) } },
                            label = {
                                Text(if (type == RolloverType.CARRY_FORWARD) "Carry forward" else "Sweep to savings")
                            },
                        )
                    }
                }
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(CategoryIcons.all.keys.toList()) { name ->
                        FilledTonalIconToggleButton(
                            checked = editor.iconName == name,
                            onCheckedChange = { onEditorChange { it.copy(iconName = name) } },
                        ) { CategoryIcon(name) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = {
            Row {
                if (!editor.isNew) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private fun cat(id: Long, name: String, icon: String, type: RolloverType) = Category(id, name, icon, 0, type)

private val previewItems = listOf(
    BudgetItem(cat(1, "Rent/EMI", "home", RolloverType.CARRY_FORWARD), 1_500_000, 0, 1_500_000),
    BudgetItem(cat(2, "Groceries", "shopping_cart", RolloverType.CARRY_FORWARD), 800_000, 120_000, 650_000),
    BudgetItem(cat(3, "Dining & Swiggy", "restaurant", RolloverType.SWEEP_TO_SAVINGS), 400_000, 0, 520_000),
    BudgetItem(cat(4, "Utilities", "bolt", RolloverType.CARRY_FORWARD), 300_000, -50_000, 100_000),
)

private val previewState = PlanningUiState(month = YearMonth.of(2026, 11), items = previewItems, isLoading = false)

@Preview(showBackground = true)
@Composable
private fun PlanningScreenPreview() {
    DhanSutraTheme { PlanningScreen(previewState, {}, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun PlanningScreenRolloverOffPreview() {
    DhanSutraTheme { PlanningScreen(previewState.copy(rolloverEnabled = false), {}, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun CategoryEditorPreview() {
    DhanSutraTheme {
        CategoryEditorDialog(CategoryEditor(id = 2, name = "Groceries", iconName = "shopping_cart", allocatedText = "8000"), {}, {}, {}, {})
    }
}
