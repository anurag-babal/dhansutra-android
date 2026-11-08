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

data class PlanningUiState(
    val month: YearMonth = MonthYear.current(),
    val items: List<BudgetItem> = emptyList(),
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
    private val editor = MutableStateFlow<CategoryEditor?>(null)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PlanningUiState> = combine(
        month,
        month.flatMapLatest { observeMonthlyBudget(it) },
        settingsRepository.rolloverEnabled,
        editor,
        message,
    ) { m, items, enabled, ed, msg ->
        PlanningUiState(m, items, enabled, isLoading = false, editor = ed, message = msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanningUiState())

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
                categoryRepository.add(
                    Category(
                        name = e.name.trim(),
                        iconName = e.iconName,
                        defaultBudget = allocated,
                        rolloverType = e.rolloverType,
                    ),
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
            if (categoryRepository.delete(id)) {
                editor.value = null
            } else {
                message.value = "This category has transactions and can't be deleted."
            }
        }
    }
}
