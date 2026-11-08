package com.example.dhansutra

import com.example.dhansutra.domain.model.BudgetItem
import com.example.dhansutra.domain.model.Category
import com.example.dhansutra.domain.model.RolloverType
import com.example.dhansutra.ui.planning.CategoryEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryEditorTest {
    @Test
    fun validation() {
        assertFalse(CategoryEditor(name = " ").isValid)
        assertTrue(CategoryEditor(name = "Gym", allocatedText = "").isValid)       // blank = no budget
        assertEquals(0L, CategoryEditor(name = "Gym", allocatedText = "0").allocatedPaise)
        assertEquals(250050L, CategoryEditor(name = "Gym", allocatedText = "2500.5").allocatedPaise)
        assertNull(CategoryEditor(name = "Gym", allocatedText = "1.234").allocatedPaise)
        assertFalse(CategoryEditor(name = "Gym", allocatedText = "abc").isValid)
    }

    @Test
    fun fromBudgetItem() {
        val item = BudgetItem(Category(4, "Rent", "home", 0, RolloverType.SWEEP_TO_SAVINGS), 1_500_000, 0, 0)
        val editor = CategoryEditor.from(item)
        assertEquals("15000", editor.allocatedText)
        assertEquals(RolloverType.SWEEP_TO_SAVINGS, editor.rolloverType)
        assertFalse(editor.isNew)
    }

    @Test
    fun defaultBudgetRecovery() {
        val catWithDefault = Category(4, "Rent", "home", 1_500_000, RolloverType.CARRY_FORWARD)
        val matchingItem = BudgetItem(catWithDefault, 1_500_000, 0, 0)
        assertTrue(CategoryEditor.from(matchingItem).setAsDefault)

        val nonMatchingItem = BudgetItem(catWithDefault, 2_000_000, 0, 0)
        assertFalse(CategoryEditor.from(nonMatchingItem).setAsDefault)

        val newEditor = CategoryEditor()
        assertTrue(newEditor.isNew)
        assertTrue(newEditor.setAsDefault)
    }
}
