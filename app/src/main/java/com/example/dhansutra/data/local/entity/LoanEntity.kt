package com.example.dhansutra.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.dhansutra.domain.model.LoanDirection

/** One lending / borrowing relationship with a person. Its balance is derived from linked transactions. */
@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val counterparty: String,
    val direction: LoanDirection,
)
