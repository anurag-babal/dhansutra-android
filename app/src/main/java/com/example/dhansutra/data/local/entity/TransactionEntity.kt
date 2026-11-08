package com.example.dhansutra.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.dhansutra.domain.model.ExpenseType
import com.example.dhansutra.domain.model.TransactionType

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId"), Index("dateMillis")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Amount in paise; always positive, sign is implied by [type]. */
    val amount: Long,
    val categoryId: Long,
    val dateMillis: Long,
    val type: TransactionType,
    val notes: String = "",
    @ColumnInfo(defaultValue = "REGULAR_OPERATING") val expenseType: ExpenseType = ExpenseType.REGULAR_OPERATING,
    @ColumnInfo(defaultValue = "1") val coveredMonths: Int = 1,
)
