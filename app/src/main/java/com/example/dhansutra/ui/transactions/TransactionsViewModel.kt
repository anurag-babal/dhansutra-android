package com.example.dhansutra.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dhansutra.domain.model.Category
import com.example.dhansutra.domain.model.Loan
import com.example.dhansutra.domain.model.TransactionFilter
import com.example.dhansutra.domain.model.TransactionItem
import com.example.dhansutra.domain.model.TransactionNature
import com.example.dhansutra.domain.model.TransactionType
import com.example.dhansutra.domain.repository.CategoryRepository
import com.example.dhansutra.domain.repository.LoanRepository
import com.example.dhansutra.domain.repository.TransactionRepository
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
import java.time.LocalDate
import java.time.YearMonth

data class TransactionFilters(
    val month: YearMonth = MonthYear.current(),
    /** When set, takes precedence over [month]. Inclusive dates. */
    val range: Pair<LocalDate, LocalDate>? = null,
    val categoryId: Long? = null,
    val query: String = "",
) {
    fun toFilter() = TransactionFilter(
        startMillis = range?.let { MonthYear.startOfDayMillis(it.first) } ?: MonthYear.startMillis(month),
        endMillis = range?.let { MonthYear.endOfDayMillis(it.second) } ?: MonthYear.endMillis(month),
        categoryId = categoryId,
        query = query,
    )
}

data class TransactionsUiState(
    val filters: TransactionFilters = TransactionFilters(),
    val categories: List<Category> = emptyList(),
    /** Existing people, to suggest in the loan section of the form. */
    val loans: List<Loan> = emptyList(),
    val groups: List<DayGroup> = emptyList(),
    val isLoading: Boolean = true,
    /** Non-null while the add/edit sheet is open. */
    val form: TransactionForm? = null,
) {
    val isFiltered: Boolean get() = filters.categoryId != null || filters.query.isNotBlank() || filters.range != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val loanRepository: LoanRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(TransactionFilters())
    private val form = MutableStateFlow<TransactionForm?>(null)

    private val items = filters.flatMapLatest { transactionRepository.observeFiltered(it.toFilter()) }

    val uiState: StateFlow<TransactionsUiState> = combine(
        filters,
        form,
        categoryRepository.observeAll(),
        loanRepository.observeAll(),
        items,
    ) { f, form, cats, loans, list ->
        TransactionsUiState(f, cats, loans, groupByDay(list), isLoading = false, form = form)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    // --- filters ---
    fun setMonth(month: YearMonth) = filters.update { it.copy(month = month, range = null) }
    fun setCategory(categoryId: Long?) = filters.update { it.copy(categoryId = categoryId) }
    fun setQuery(query: String) = filters.update { it.copy(query = query) }
    fun setRange(range: Pair<LocalDate, LocalDate>?) = filters.update { it.copy(range = range) }

    // --- form ---
    fun startAdd() {
        val first = uiState.value.categories.firstOrNull()
        form.value = first?.let { TransactionForm().withCategory(it) } ?: TransactionForm()
    }

    fun startEdit(item: TransactionItem) {
        form.value = TransactionForm.from(item)
    }

    fun dismissForm() {
        form.value = null
    }

    fun updateForm(transform: (TransactionForm) -> TransactionForm) = form.update { it?.let(transform) }

    fun save() {
        val current = form.value ?: return
        val transaction = current.toTransaction()
        if (transaction == null) {
            form.value = current.copy(showErrors = true)
            return
        }
        viewModelScope.launch {
            val loanId = if (transaction.nature == TransactionNature.LOAN) {
                loanRepository.getOrCreate(current.counterparty, current.loanDirection)
            } else {
                null
            }
            transactionRepository.save(transaction.copy(loanId = loanId))
            form.value = null
        }
    }

    fun deleteEditing() {
        val id = form.value?.id?.takeIf { it != 0L } ?: return
        viewModelScope.launch {
            transactionRepository.delete(id)
            form.value = null
        }
    }

    fun setType(type: TransactionType) = updateForm { it.copy(type = type) }
}
