package com.example.dhansutra.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.dhansutra.domain.model.RolloverType

/** All monetary values in the database are stored in paise (1/100 rupee). */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconName: String,
    val defaultBudget: Long,
    val rolloverType: RolloverType,
)
