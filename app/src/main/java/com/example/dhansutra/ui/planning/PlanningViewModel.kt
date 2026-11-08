package com.example.dhansutra.ui.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dhansutra.domain.model.BudgetEntry
import com.example.dhansutra.domain.model.BudgetItem
import com.example.dhansutra.domain.model.Category
import com.example.dhansutra.domain.repository.BudgetRepository
import com.example.dhansutra.domain.repository.CategoryRepository
import com.example.dhansutra.domain.repository.SettingsRepository
import com.example.dhansutra.domain.usecase.ObserveMonthlyBudget
import com.example.dhansutra.domain.usecase.RolloverManager
import com.example.dhansutra.util.MonthYear
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

enum class PlanningSortOrder(val label: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    BUDGET_DESC("Budget (High-Low)"),
    SPENT_DESC("Spent (High-Low)"),
    LEFT_ASC("Left (Low-High)"),
    LEFT_DESC("Left (High-Low)"),
    ROLLOVER("Rollover"),
}

data class PlanningUiState(
    val month: YearMonth = MonthYear.current(),
    val items: List<BudgetItem> = emptyList(),
    val showInactive: Boolean = false,
    val sortOrder: PlanningSortOrder = PlanningSortOrder.NAME_ASC,
    val rolloverEnabled: Boolean = true,
    val isLoading: Boolean = true,
    /** Non-null while the add/edit dialog is open. */
    val editor: CategoryEditor? = null,
    val message: String? = null,
) {
    val totalAllocated: Long get() = items.sumOf { it.allocated + it.carriedOver }
    val totalSpent: Long get() = items.sumOf { it.spent }
    val totalRemaining: Long get() = totalAllocated - totalSpent
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlanningViewModel(
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    settingsRepository: SettingsRepository,
    observeMonthlyBudget: ObserveMonthlyBudget,
    private val rolloverManager: RolloverManager,
) : ViewModel() {

    private val month = MutableStateFlow(MonthYear.current())
    private val showInactive = MutableStateFlow(false)
    private val sortOrder = MutableStateFlow(PlanningSortOrder.NAME_ASC)
    private val editor = MutableStateFlow<CategoryEditor?>(null)
    private val message = MutableStateFlow<String?>(null)

    private val dataFlow = combine(
        month,
        month.flatMapLatest { observeMonthlyBudget(it) },
        showInactive,
        sortOrder,
    ) { m, rawItems, inactive, sort ->
        val filtered = if (inactive) {
            rawItems
        } else {
            rawItems.filter { it.allocated != 0L || it.carriedOver != 0L || it.spent != 0L }
        }
        val sorted = when (sort) {
            PlanningSortOrder.NAME_ASC -> filtered.sortedBy { it.category.name.lowercase() }
            PlanningSortOrder.NAME_DESC -> filtered.sortedByDescending { it.category.name.lowercase() }
            PlanningSortOrder.BUDGET_DESC -> filtered.sortedByDescending { it.allocated }
            PlanningSortOrder.SPENT_DESC -> filtered.sortedByDescending { it.spent }
            PlanningSortOrder.LEFT_ASC -> filtered.sortedBy { it.remaining }
            PlanningSortOrder.LEFT_DESC -> filtered.sortedByDescending { it.remaining }
            PlanningSortOrder.ROLLOVER -> filtered.sortedBy { it.category.rolloverType.name }
        }
        PlanningData(m, sorted, inactive, sort)
    }

    val uiState: StateFlow<PlanningUiState> = combine(
        dataFlow,
        settingsRepository.rolloverEnabled,
        editor,
        message,
    ) { data, enabled, ed, msg ->
        PlanningUiState(
            month = data.month,
            items = data.items,
            showInactive = data.showInactive,
            sortOrder = data.sortOrder,
            rolloverEnabled = enabled,
            isLoading = false,
            editor = ed,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanningUiState())

    private data class PlanningData(
        val month: YearMonth,
        val items: List<BudgetItem>,
        val showInactive: Boolean,
        val sortOrder: PlanningSortOrder,
    )

    fun toggleShowInactive() = showInactive.update { !it }
    fun setSortOrder(order: PlanningSortOrder) { sortOrder.value = order }

    fun setMonth(value: YearMonth) {
        month.value = value
    }

    fun startAdd() {
        editor.value = CategoryEditor()
    }

    fun startEdit(item: BudgetItem) {
        editor.value = CategoryEditor.from(item)
    }

    fun dismissEditor() {
        editor.value = null
    }

    fun updateEditor(transform: (CategoryEditor) -> CategoryEditor) = editor.update { it?.let(transform) }

    fun consumeMessage() {
        message.value = null
    }

    fun save() {
        val e = editor.value ?: return
        val allocated = e.allocatedPaise
        if (!e.isValid || allocated == null) {
            editor.value = e.copy(showErrors = true)
            return
        }
        val monthKey = MonthYear.key(month.value)
        viewModelScope.launch {
            if (e.isNew) {
                val newId = categoryRepository.add(
                    Category(
                        name = e.name.trim(),
                        iconName = e.iconName,
                        defaultBudget = if (e.setAsDefault) allocated else 0L,
                        rolloverType = e.rolloverType,
                    ),
                )
                budgetRepository.insertIfAbsent(
                    BudgetEntry(monthYear = monthKey, categoryId = newId, allocatedAmount = allocated, carriedOverAmount = 0),
                )
            } else {
                val existing = categoryRepository.getAll().firstOrNull { it.id == e.id }
                if (existing != null) {
                    categoryRepository.update(
                        existing.copy(
                            name = e.name.trim(),
                            iconName = e.iconName,
                            rolloverType = e.rolloverType,
                            defaultBudget = if (e.setAsDefault) allocated else existing.defaultBudget,
                        ),
                    )
                }
                val row = budgetRepository.get(monthKey, e.id)
                if (row != null) {
                    budgetRepository.update(row.copy(allocatedAmount = allocated))
                } else {
                    budgetRepository.insertIfAbsent(
                        BudgetEntry(monthYear = monthKey, categoryId = e.id, allocatedAmount = allocated, carriedOverAmount = 0),
                    )
                }
            }
            rolloverManager.recalculate()
            editor.value = null
        }
    }

    fun deleteEditing() {
        val id = editor.value?.id?.takeIf { it != 0L } ?: return
        viewModelScope.launch {
            val count = categoryRepository.getTransactionCount(id)
            if (count > 0) {
                message.value = "Cannot delete: category has $count transaction" + (if (count == 1) "" else "s") + " (check other months/filters)."
            } else if (categoryRepository.delete(id)) {
                rolloverManager.recalculate()
                editor.value = null
            } else {
                message.value = "Failed to delete category."
            }
        }
    }
}
