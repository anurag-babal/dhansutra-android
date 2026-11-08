package com.example.dhansutra.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "monthly_budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["monthYear", "categoryId"], unique = true), Index("categoryId")],
)
data class MonthlyBudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Format "yyyy-MM", e.g. "2026-11". */
    val monthYear: String,
    val categoryId: Long,
    /** Paise. */
    val allocatedAmount: Long,
    /** Paise; may be negative (carried-forward overspend). */
    val carriedOverAmount: Long = 0,
)
