package com.example.dhansutra.domain.model

/** How a category's leftover balance is handled at month end. Persisted by name. */
enum class RolloverType { CARRY_FORWARD, SWEEP_TO_SAVINGS }

/** Persisted by name. */
enum class TransactionType { EXPENSE, INCOME }

/**
 * What a transaction represents, independent of its cash direction ([TransactionType]).
 * Every nature moves the real cash balance, but only some count as income / spending:
 * - [REGULAR_OPERATING]: everyday income and spending; the only nature measured against monthly budgets.
 * - [PERIODIC_ONE_TIME]: lump-sum expense covering several months (gym, annual insurance); kept out of budgets.
 * - [INVESTMENT]: SIP / NPS etc. Expense = contribution, income = redemption; neither is spending nor earning.
 * - [LOAN]: money lent to / borrowed from someone, and its repayments; neither is spending nor earning.
 * Persisted by name.
 */
enum class TransactionNature { REGULAR_OPERATING, PERIODIC_ONE_TIME, INVESTMENT, LOAN }

/** Whether the user lent money to someone or borrowed it. Persisted by name. */
enum class LoanDirection { LENT, BORROWED }
