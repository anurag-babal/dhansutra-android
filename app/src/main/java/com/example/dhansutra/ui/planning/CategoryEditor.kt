package com.example.dhansutra.ui.planning

import com.example.dhansutra.domain.model.BudgetItem
import com.example.dhansutra.domain.model.RolloverType
import com.example.dhansutra.util.CurrencyFormatter

/** Editable state of the add/edit category + budget dialog. [id] == 0 means a new category. */
data class CategoryEditor(
    val id: Long = 0,
    val name: String = "",
    val iconName: String = "category",
    val allocatedText: String = "",
    val rolloverType: RolloverType = RolloverType.CARRY_FORWARD,
    /** Edit mode only: also store the amount as the category's default for future months. */
    val setAsDefault: Boolean = false,
    val showErrors: Boolean = false,
) {
    val isNew: Boolean get() = id == 0L
    val nameError: Boolean get() = name.isBlank()

    /** Paise; blank or zero is a valid "no budget"; null means unparseable. */
    val allocatedPaise: Long?
        get() {
            val cleaned = allocatedText.replace(",", "").trim()
            if (cleaned.isEmpty()) return 0L
            val value = cleaned.toBigDecimalOrNull() ?: return null
            if (value.signum() == 0) return 0L
            return CurrencyFormatter.parseToPaise(cleaned)
        }
    val allocatedError: Boolean get() = allocatedPaise == null
    val isValid: Boolean get() = !nameError && !allocatedError

    companion object {
        fun from(item: BudgetItem) = CategoryEditor(
            id = item.category.id,
            name = item.category.name,
            iconName = item.category.iconName,
            allocatedText = if (item.allocated == 0L) "" else CurrencyFormatter.toInputText(item.allocated),
            rolloverType = item.category.rolloverType,
        )
    }
}
