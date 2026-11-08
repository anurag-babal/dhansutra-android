---
name: dhansutra-guide
description: Comprehensive architecture, domain rules, and implementation guide for DhanSutra budget tracker — use when modifying, refactoring, or building new features in DhanSutra.
---

# DhanSutra Developer & Architecture Guide

This guide provides immediate, complete context for building, modifying, or refactoring code in the **DhanSutra** Android application.

---

## 1. Project & Tech Stack Overview

* **App Name / Package:** DhanSutra / `com.example.dhansutra`
* **Architecture:** Clean Architecture + MVVM + Unidirectional Data Flow (UDF)
* **UI Toolkit:** Jetpack Compose with Material 3 (Dynamic Color, Light/Dark support)
* **Persistence:** Room (SQLite) with KSP, Version 3
* **Concurrency:** Kotlin Coroutines & Reactive `Flow` / `StateFlow`
* **Dependency Injection:** Manual DI via `AppContainer` (No Hilt / Dagger)
* **Charts:** Native Jetpack Compose Canvas (No MPAndroidChart / third-party chart libs)
* **Build Tooling:** AGP 9.5.1, Kotlin 2.2.10, compileSdk 37, minSdk 36, Java 17

---

## 2. Directory & Package Layout

```
app/src/main/java/com/example/dhansutra/
├── DhanSutraApp.kt                 # Application class holding AppContainer
├── MainActivity.kt                 # Single Activity hosting AppNavHost & Material theme
├── di/
│   └── AppContainer.kt             # Composition root: Database, DAOs, Repositories, Use Cases
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt          # Room Database (v3) + SeedCallback + Migrations
│   │   ├── entity/                 # CategoryEntity, TransactionEntity, MonthlyBudgetEntity, LoanEntity
│   │   └── dao/                    # CategoryDao, TransactionDao, MonthlyBudgetDao, LoanDao
│   └── repository/                 # RepositoryImpls, Mappers, SharedPrefsSettingsRepository
├── domain/
│   ├── model/                      # Domain models, Enums (RolloverType, TransactionType, TransactionNature, LoanDirection)
│   ├── repository/                 # Interfaces: CategoryRepository, TransactionRepository, BudgetRepository, LoanRepository, SettingsRepository
│   └── usecase/                    # RolloverCalculator, RolloverManager, Observers (Dashboard, Budget, Periodic, Loans, Trend)
├── ui/
│   ├── AppViewModelFactory.kt      # Factory helper: appViewModel { container -> MyViewModel(...) }
│   ├── navigation/                 # Screen sealed class, AppNavHost (Dashboard, Planning, Transactions, Analytics)
│   ├── components/                 # Shared widgets: MonthSelector, BudgetProgressBar, CategoryIcon, EmptyState
│   ├── dashboard/                  # DashboardScreen, DashboardViewModel (Collapsible Hero, Ledger, Budgets)
│   ├── planning/                   # PlanningScreen, PlanningViewModel, CategoryEditor (Table, Sort, ₹0 toggle, Dialog)
│   ├── transactions/               # TransactionsScreen, TransactionsViewModel, TransactionEntryBottomSheet, TransactionForm
│   ├── analytics/                  # AnalyticsScreen, AnalyticsViewModel, Charts.kt (Donut & Trend Line charts)
│   └── theme/                      # Theme.kt, Color.kt, Type.kt
└── util/
    ├── CurrencyFormatter.kt        # Indian digit grouping formatting (₹1,23,456.00) & paise conversion
    └── MonthYear.kt                # "yyyy-MM" helpers, range math, and millisecond epoch bounds
```

---

## 3. Financial Mechanics & Domain Rules

### 3.1 Monetary Representation (Paise)
* **Strict Rule:** All monetary amounts are stored and calculated as positive `Long` **paise** (1/100 rupee).
* Never use `Float` or `Double` for financial calculations.
* Use `CurrencyFormatter.format(amountPaise, showDecimals = true/false)` for display.
* Use `CurrencyFormatter.parseToPaise(inputString)` for user input.

### 3.2 Transaction Types vs. Natures
DhanSutra separates cash flow direction (`TransactionType`: `EXPENSE` / `INCOME`) from the economic meaning (`TransactionNature`):

1. **`REGULAR_OPERATING`**:
   * Day-to-day income and expenses (Groceries, Utilities, Rent, Salary).
   * **Only this nature counts toward category monthly budgets.**
2. **`PERIODIC_ONE_TIME`** (Expenses only):
   * Lump-sum payments covering multiple months (e.g. 6-month gym membership, annual insurance).
   * Deducted from real cash immediately; stored with `coveredMonths`; tracked in periodic ledger without distorting monthly category budgets.
3. **`INVESTMENT`**:
   * SIP / Mutual Funds / NPS contributions (Expense) or redemptions (Income).
   * Moves real cash balance, but is excluded from regular spending and income. Tracked as "Net Invested (at cost)".
4. **`LOAN`**:
   * Money lent, borrowed, or repaid. Linked to `LoanEntity` (`LENT` / `BORROWED`).
   * Moves cash balance, updates outstanding loan receivables/payables; excluded from budget spending.

### 3.3 Financial Formulas
* **Cash Balance:** All-time `Σ INCOME - Σ EXPENSE` of **every** transaction nature.
* **Net Worth:** `Cash Balance + Invested (at cost) + Owed to You - You Owe`.
* **Total Savings:** Accumulated `Σ (Regular Income - Regular & Periodic Expenses)` across all closed months prior to the current month.
* **Surplus (This Month):** `Monthly Income - Regular Spend - Periodic Spend`.

### 3.4 Rollover Calculations (`RolloverCalculator` & `RolloverManager`)
* **`CARRY_FORWARD`:** Next month's `carriedOverAmount` = `allocated + carriedOver - spent` (positive surplus or negative overspend).
* **`SWEEP_TO_SAVINGS`:** Leftover surplus is cleared to 0; overspend is forgiven; next month starts with fresh allocation.
* **Global Rollover Switch:** Persisted in `SettingsRepository` (`SharedPreferences`). When disabled, all `carriedOverAmount` evaluate to 0.
* **Recalculation:** `RolloverManager.recalculate()` is idempotent and automatically reconciles month rows on any category, spend, or setting change. Call `rolloverManager.recalculate()` after adding/editing/deleting a category or budget.

---

## 4. UI Patterns & Best Practices

1. **ViewModel Instantiation:**
   Always use `appViewModel { container -> ... }` in Composable route functions:
   ```kotlin
   @Composable
   fun MyRoute() {
       val vm = appViewModel { MyViewModel(it.categoryRepository, it.observeMonthlyBudget) }
       val state by vm.uiState.collectAsStateWithLifecycle()
       MyScreen(state, ...)
   }
   ```

2. **StateFlow UI Pipeline:**
   Expose a single immutable `uiState: StateFlow<MyUiState>` using `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyUiState())`.

3. **Category Sorting & Alphabetization:**
   * Category queries in `CategoryDao` use `ORDER BY name COLLATE NOCASE`.
   * Dropdowns and filter selectors should always present categories alphabetically.

4. **Category Deletion Safety:**
   * Category deletion is blocked if `categoryRepository.getTransactionCount(id) > 0`.
   * Always inform the user of the exact count of linked transactions preventing deletion.

5. **Inline Rollover Visualization:**
   * When Rollover is ON, carried amounts are displayed inline inside the category's budget row (e.g., `+₹1.2K carried` and base vs. total budget breakdown).
   * When Rollover is OFF, standard base budgets are shown without rollover badges.

---

## 5. Development & Testing Commands

Run all validation commands from the project root:

```bash
# Run all JVM Unit Tests
sh ./gradlew :app:testDebugUnitTest

# Assemble Debug APK
sh ./gradlew :app:assembleDebug

# Run Room DAO Instrumented Tests (requires connected emulator/device)
sh ./gradlew :app:connectedDebugAndroidTest
```
