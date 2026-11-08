package com.example.dhansutra.domain.model

/** How a category's leftover balance is handled at month end. Persisted by name. */
enum class RolloverType { CARRY_FORWARD, SWEEP_TO_SAVINGS }

/** Persisted by name. */
enum class TransactionType { EXPENSE, INCOME }

/**
 * Classifies an expense. Both kinds reduce the real cash balance, but only [REGULAR_OPERATING]
 * counts against monthly category budgets, rollover and spending trends. Persisted by name.
 */
enum class ExpenseType { REGULAR_OPERATING, PERIODIC_ONE_TIME }
