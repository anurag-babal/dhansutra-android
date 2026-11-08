package com.example.dhansutra.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.dhansutra.domain.model.Category
import com.example.dhansutra.domain.model.TransactionNature
import com.example.dhansutra.domain.model.Loan
import com.example.dhansutra.domain.model.LoanDirection
import com.example.dhansutra.domain.model.RolloverType
import com.example.dhansutra.domain.model.TransactionType
import com.example.dhansutra.ui.theme.DhanSutraTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEntryBottomSheet(
    form: TransactionForm,
    categories: List<Category>,
    loans: List<Loan>,
    onFormChange: ((TransactionForm) -> TransactionForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        TransactionEntryContent(form, categories, loans, onFormChange, onSave, onDelete)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEntryContent(
    form: TransactionForm,
    categories: List<Category>,
    loans: List<Loan>,
    onFormChange: ((TransactionForm) -> TransactionForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var durationExpanded by remember { mutableStateOf(false) }
    val selectedCategory = categories.firstOrNull { it.id == form.categoryId }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (form.id == 0L) "Add transaction" else "Edit transaction",
            style = MaterialTheme.typography.titleLarge,
        )

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TransactionType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = form.type == type,
                    onClick = { onFormChange { it.copy(type = type) } },
                    shape = SegmentedButtonDefaults.itemShape(index, TransactionType.entries.size),
                ) { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) }
            }
        }

        TransactionNatureSelector(
            selected = form.effectiveNature,
            isExpense = form.type == TransactionType.EXPENSE,
            onSelect = { selected -> onFormChange { it.copy(nature = selected) } },
        )
        natureHint(form)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (form.isPeriodic) {
            ExposedDropdownMenuBox(expanded = durationExpanded, onExpandedChange = { durationExpanded = it }) {
                OutlinedTextField(
                    value = durationLabel(form.coveredMonths),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Duration covered") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(durationExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = durationExpanded, onDismissRequest = { durationExpanded = false }) {
                    PERIODIC_DURATION_OPTIONS.forEach { months ->
                        DropdownMenuItem(
                            text = { Text(durationLabel(months)) },
                            onClick = {
                                onFormChange { it.copy(coveredMonths = months) }
                                durationExpanded = false
                            },
                        )
                    }
                }
            }
        }

        if (form.isLoan) {
            LoanSection(form, loans, onFormChange)
        }

        OutlinedTextField(
            value = form.amountText,
            onValueChange = { text -> onFormChange { it.copy(amountText = text.filter { c -> c.isDigit() || c == '.' }) } },
            label = { Text("Amount") },
            prefix = { Text("₹") },
            singleLine = true,
            isError = form.showErrors && form.amountError,
            supportingText = if (form.showErrors && form.amountError) {
                { Text("Enter an amount greater than 0 (max 2 decimals)") }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }) {
            OutlinedTextField(
                value = selectedCategory?.name.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                isError = form.showErrors && form.categoryError,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            onFormChange { it.withCategory(category) }
                            categoryExpanded = false
                        },
                    )
                }
            }
        }

        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
            Text("  " + form.date.format(dateFormat))
        }

        OutlinedTextField(
            value = form.notes,
            onValueChange = { text -> onFormChange { it.copy(notes = text) } },
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (form.id != 0L) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("Delete") }
            }
            Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save") }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = form.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onFormChange { it.copy(date = picked) }
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

/** What the transaction is: Regular, Periodic (expenses only), Investment or Loan. */
@Composable
private fun TransactionNatureSelector(selected: TransactionNature, isExpense: Boolean, onSelect: (TransactionNature) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TransactionNature.entries
            .filter { isExpense || it != TransactionNature.PERIODIC_ONE_TIME }
            .forEach { nature ->
                FilterChip(
                    selected = selected == nature,
                    onClick = { onSelect(nature) },
                    label = { Text(natureLabel(nature)) },
                )
            }
    }
}

private fun natureLabel(nature: TransactionNature) = when (nature) {
    TransactionNature.REGULAR_OPERATING -> "Regular"
    TransactionNature.PERIODIC_ONE_TIME -> "Periodic / One-time"
    TransactionNature.INVESTMENT -> "Investment"
    TransactionNature.LOAN -> "Loan"
}

/** One line explaining how the chosen nature is counted. */
private fun natureHint(form: TransactionForm): String? = when (form.effectiveNature) {
    TransactionNature.REGULAR_OPERATING -> null
    TransactionNature.PERIODIC_ONE_TIME ->
        "Full amount is deducted today. It won't count against your monthly category budgets."
    TransactionNature.INVESTMENT ->
        if (form.type == TransactionType.EXPENSE) {
            "Money put into SIP / NPS etc. Reduces cash, but is not spending."
        } else {
            "Money taken out of an investment. Adds cash, but is not income."
        }
    TransactionNature.LOAN -> loanHint(form)
}

private fun loanHint(form: TransactionForm): String {
    val expense = form.type == TransactionType.EXPENSE
    return when {
        form.loanDirection == LoanDirection.LENT && expense -> "You lend money. They now owe you this amount."
        form.loanDirection == LoanDirection.LENT -> "They pay you back. Their balance goes down."
        expense -> "You repay what you borrowed. Your balance goes down."
        else -> "You borrow money. You now owe this amount."
    }
}

/** Who the loan is with, and whether the user lent to or borrowed from them. */
@Composable
private fun LoanSection(form: TransactionForm, loans: List<Loan>, onFormChange: ((TransactionForm) -> TransactionForm) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val options = listOf(LoanDirection.LENT to "I lent", LoanDirection.BORROWED to "I borrowed")
        options.forEachIndexed { index, (direction, label) ->
            SegmentedButton(
                selected = form.loanDirection == direction,
                onClick = { onFormChange { it.copy(loanDirection = direction) } },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label) }
        }
    }
    OutlinedTextField(
        value = form.counterparty,
        onValueChange = { text -> onFormChange { it.copy(counterparty = text) } },
        label = { Text("Person") },
        singleLine = true,
        isError = form.showErrors && form.counterpartyError,
        supportingText = if (form.showErrors && form.counterpartyError) {
            { Text("Enter who this is with") }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
    val suggestions = loans.filter { it.direction == form.loanDirection }
    if (suggestions.isNotEmpty()) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { loan ->
                AssistChip(onClick = { onFormChange { it.copy(counterparty = loan.counterparty) } }, label = { Text(loan.counterparty) })
            }
        }
    }
}

private fun durationLabel(months: Int) = if (months == 1) "1 Month" else "$months Months"

private val previewCategories = listOf(
    Category(1, "Groceries", "shopping_cart", 800_000, RolloverType.CARRY_FORWARD),
    Category(2, "Dining & Swiggy", "restaurant", 400_000, RolloverType.SWEEP_TO_SAVINGS),
)
private val previewLoans = listOf(Loan(1, "Rahul", LoanDirection.LENT))

@Preview(showBackground = true)
@Composable
private fun EntryNewPreview() {
    DhanSutraTheme {
        TransactionEntryContent(
            form = TransactionForm(categoryId = 1, date = LocalDate.of(2026, 11, 8)),
            categories = previewCategories,
            loans = previewLoans,
            onFormChange = {}, onSave = {}, onDelete = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EntryPeriodicPreview() {
    DhanSutraTheme {
        TransactionEntryContent(
            form = TransactionForm(
                categoryId = 1,
                amountText = "12000",
                nature = TransactionNature.PERIODIC_ONE_TIME,
                coveredMonths = 6,
                date = LocalDate.of(2026, 11, 8),
            ),
            categories = previewCategories,
            loans = previewLoans,
            onFormChange = {}, onSave = {}, onDelete = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EntryLoanPreview() {
    DhanSutraTheme {
        TransactionEntryContent(
            form = TransactionForm(
                categoryId = 1, amountText = "10000", nature = TransactionNature.LOAN,
                counterparty = "Rahul", date = LocalDate.of(2026, 11, 8),
            ),
            categories = previewCategories,
            loans = previewLoans,
            onFormChange = {}, onSave = {}, onDelete = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EntryEditWithErrorsPreview() {
    DhanSutraTheme {
        TransactionEntryContent(
            form = TransactionForm(id = 5, amountText = "", categoryId = null, notes = "Dinner", showErrors = true, date = LocalDate.of(2026, 11, 8)),
            categories = previewCategories,
            loans = previewLoans,
            onFormChange = {}, onSave = {}, onDelete = {},
        )
    }
}
