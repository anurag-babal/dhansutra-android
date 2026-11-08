package com.example.dhansutra.data.local.entity

/** How a category's leftover balance is handled at month end. Persisted by name. */
enum class RolloverType { CARRY_FORWARD, SWEEP_TO_SAVINGS }

/** Persisted by name. */
enum class TransactionType { EXPENSE, INCOME }
