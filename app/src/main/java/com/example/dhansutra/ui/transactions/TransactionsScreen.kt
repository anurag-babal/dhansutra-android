package com.example.dhansutra.ui.transactions

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dhansutra.domain.model.Category
import com.example.dhansutra.domain.model.RolloverType
import com.example.dhansutra.domain.model.Transaction
import com.example.dhansutra.domain.model.TransactionItem
import com.example.dhansutra.domain.model.TransactionType
import com.example.dhansutra.ui.appViewModel
import com.example.dhansutra.ui.components.CategoryIcon
import com.example.dhansutra.ui.components.EmptyState
import com.example.dhansutra.ui.components.MonthSelector
import com.example.dhansutra.ui.theme.DhanSutraTheme
import com.example.dhansutra.util.CurrencyFormatter
import com.example.dhansutra.util.MonthYear
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dayFormat = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")
private val shortDate = DateTimeFormatter.ofPattern("dd MMM")

@Composable
fun TransactionsRoute() {
    val vm = appViewModel { TransactionsViewModel(it.categoryRepository, it.transactionRepository, it.loanRepository) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    TransactionsScreen(
        state = state,
        onMonthChange = vm::setMonth,
        onCategoryChange = vm::setCategory,
        onQueryChange = vm::setQuery,
        onRangeChange = vm::setRange,
        onAdd = vm::startAdd,
        onEdit = vm::startEdit,
        onFormChange = vm::updateForm,
        onSave = vm::save,
        onDelete = vm::deleteEditing,
        onDismissForm = vm::dismissForm,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    state: TransactionsUiState,
    onMonthChange: (YearMonth) -> Unit,
    onCategoryChange: (Long?) -> Unit,
    onQueryChange: (String) -> Unit,
    onRangeChange: (Pair<LocalDate, LocalDate>?) -> Unit,
    onAdd: () -> Unit,
    onEdit: (TransactionItem) -> Unit,
    onFormChange: ((TransactionForm) -> TransactionForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismissForm: () -> Unit,
) {
    var showRangePicker by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Rounded.Add, contentDescription = "Add transaction") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.filters.query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search notes or category") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.filters.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box {
                    FilterChip(
                        selected = state.filters.categoryId != null,
                        onClick = { categoryMenu = true },
                        label = {
                            Text(state.categories.firstOrNull { it.id == state.filters.categoryId }?.name ?: "All categories")
                        },
                    )
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        DropdownMenuItem(text = { Text("All categories") }, onClick = {
                            onCategoryChange(null); categoryMenu = false
                        })
                        state.categories.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = {
                                onCategoryChange(c.id); categoryMenu = false
                            })
                        }
                    }
                }
                val range = state.filters.range
                FilterChip(
                    selected = range != null,
                    onClick = { showRangePicker = true },
                    label = {
                        Text(range?.let { "${it.first.format(shortDate)} – ${it.second.format(shortDate)}" } ?: "Date range")
                    },
                    leadingIcon = { Icon(Icons.Rounded.DateRange, contentDescription = null, Modifier.size(18.dp)) },
                    trailingIcon = if (range != null) {
                        {
                            IconButton(onClick = { onRangeChange(null) }, Modifier.size(18.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear date range")
                            }
                        }
                    } else {
                        null
                    },
                )
            }

            if (state.filters.range == null) {
                MonthSelector(
                    month = state.filters.month,
                    onMonthChange = onMonthChange,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize())
                state.groups.isEmpty() -> EmptyState(
                    if (state.isFiltered) "No transactions match your filters." else "No transactions this month.\nTap + to add one.",
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    state.groups.forEach { group ->
                        item(key = "h-${group.date}") {
                            Text(
                                group.date.format(dayFormat),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(group.items, key = { it.transaction.id }) { item ->
                            TransactionRow(item, onClick = { onEdit(item) })
                        }
                    }
                }
            }
        }
    }

    state.form?.let { form ->
        TransactionEntryBottomSheet(
            form = form,
            categories = state.categories,
            loans = state.loans,
            onFormChange = onFormChange,
            onSave = onSave,
            onDelete = onDelete,
            onDismiss = onDismissForm,
        )
    }

    if (showRangePicker) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null && pickerState.selectedEndDateMillis != null,
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        val end = pickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            onRangeChange(utcDate(start) to utcDate(end))
                        }
                        showRangePicker = false
                    },
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showRangePicker = false }) { Text("Cancel") } },
        ) { DateRangePicker(state = pickerState, modifier = Modifier.weight(1f, fill = false)) }
    }
}

private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun TransactionRow(item: TransactionItem, onClick: () -> Unit) {
    val isIncome = item.transaction.type == TransactionType.INCOME
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { CategoryIcon(item.categoryIcon) },
        headlineContent = { Text(item.categoryName) },
        supportingContent = item.natureLabel().let { label ->
            if (item.transaction.notes.isNotBlank() || label != null) {
                {
                    Text(listOfNotNull(item.transaction.notes.ifBlank { null }, label).joinToString(" · "), maxLines = 1)
                }
            } else {
                null
            }
        },
        trailingContent = {
            Text(
                (if (isIncome) "+" else "-") + CurrencyFormatter.format(item.transaction.amount),
                style = MaterialTheme.typography.titleSmall,
                color = if (isIncome) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private val previewCategories = listOf(
    Category(1, "Groceries", "shopping_cart", 800_000, RolloverType.CARRY_FORWARD),
    Category(2, "Dining & Swiggy", "restaurant", 400_000, RolloverType.SWEEP_TO_SAVINGS),
)

private fun previewItem(id: Long, cat: Category, amount: Long, day: Int, type: TransactionType, notes: String) =
    TransactionItem(
        Transaction(id, amount, cat.id, MonthYear.startOfDayMillis(LocalDate.of(2026, 11, day)), type, notes),
        cat.name,
        cat.iconName,
    )

private val previewState = TransactionsUiState(
    filters = TransactionFilters(month = YearMonth.of(2026, 11)),
    categories = previewCategories,
    isLoading = false,
    groups = groupByDay(
        listOf(
            previewItem(3, previewCategories[1], 45_000, 8, TransactionType.EXPENSE, "Biryani"),
            previewItem(2, previewCategories[0], 123_450, 8, TransactionType.EXPENSE, "Weekly vegetables"),
            previewItem(1, previewCategories[0], 5_000_000, 1, TransactionType.INCOME, ""),
        ),
    ),
)

@Preview(showBackground = true)
@Composable
private fun TransactionsScreenPreview() {
    DhanSutraTheme {
        TransactionsScreen(previewState, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun TransactionsScreenEmptyPreview() {
    DhanSutraTheme {
        TransactionsScreen(previewState.copy(groups = emptyList()), {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}
