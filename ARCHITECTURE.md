# DhanSutra – Technical Architecture & System Blueprint

## 1. Architectural Philosophy & Overview

DhanSutra is designed as an offline-first, privacy-respecting Android personal budget tracker. The system adheres to **Clean Architecture** principles and **Unidirectional Data Flow (UDF)** using **Jetpack Compose (Material 3)**, **MVVM**, and **Room Persistence Library**.

```
+--------------------------------------------------------------------------+
|                                UI Layer                                  |
|   Jetpack Compose M3 Screens | ViewModels (StateFlow<UiState>)           |
|   (Dashboard, Planning, Transactions, Analytics)                         |
+--------------------------------------------------------------------------+
                                    |
                                    v
+--------------------------------------------------------------------------+
|                              Domain Layer                                |
|   Use Cases / Observers | Pure Business Logic (RolloverCalculator)       |
|   Domain Models | Repository Interfaces                                  |
+--------------------------------------------------------------------------+
                                    |
                                    v
+--------------------------------------------------------------------------+
|                               Data Layer                                 |
|   Room Database (DAOs, SQLite Entities) | Repository Implementations     |
|   SharedPreferences (SettingsRepository) | Seed Callbacks & Migrations   |
+--------------------------------------------------------------------------+
```

---

## 2. Core Domain Rules & Financial Mechanics

### 2.1 Currency & Precision
* All monetary values are represented as positive `Long` **paise** (1/100 rupee) to eliminate floating-point arithmetic errors.
* Formatting uses Indian numbering system digit grouping (`₹1,23,456.00`).

### 2.2 Transaction Nature & Cash Accounting
DhanSutra separates cash movement direction (`type`: `EXPENSE` / `INCOME`) from its financial nature (`TransactionNature`):

| Event | Direction (`type`) | Nature (`nature`) | Affects Cash Balance | Affects Category Budgets |
|---|---|---|---|---|
| Groceries, Utilities, Salary | EXPENSE / INCOME | `REGULAR_OPERATING` | Yes | Yes (Expenses) |
| Annual Insurance, Gym Membership | EXPENSE | `PERIODIC_ONE_TIME` (covers N months) | Yes | No (Lump-sum ledger) |
| SIP / Mutual Fund / NPS | EXPENSE / INCOME | `INVESTMENT` | Yes | No (Net Invested) |
| Borrow / Lend / Repayment | EXPENSE / INCOME | `LOAN` | Yes | No (Loan Balances) |

* **Cash Balance:** All-time `Σ INCOME - Σ EXPENSE` across all transaction natures.
* **Net Worth:** `Cash Balance + Invested (at cost) + Owed to You - You Owe`.
* **Total Savings:** Accumulated `Σ (Regular Income - Operating Expenses)` across all closed months prior to the current month.

### 2.3 Rollover System & Budget Allocation
* Budget rows (`MonthlyBudgetEntity`) are keyed by `(monthYear, categoryId)`.
* **Carry Forward:** Unused budget or overspend is transferred to next month (`allocated + carriedOver - spent`).
* **Sweep to Savings:** Remaining budget is swept; overspend is forgiven; next month starts with fresh allocation.
* **Global Rollover Switch:** When disabled, all carried amounts evaluate to 0.
* **Inline Visualization:** Carried amounts are displayed directly inside each category's budget row on the Dashboard and Planning tabs rather than isolated cards.

---

## 3. UI/UX Design & State Management

### 3.1 Unidirectional Data Flow (UDF)
Every screen is backed by a dedicated ViewModel that exposes a single `StateFlow<UiState>`. User actions trigger intent methods that execute suspend functions or update filter flows.

### 3.2 Key Screens & Features

1. **Dashboard Tab (`ui/dashboard/`):**
   * **Collapsible Hero Card:** Displays primary Cash Balance and Net Worth by default; expands on-demand to show Invested, Owed to You, and You Owe positions.
   * **Integrated Budgets Header:** Hosts the section title, compact Rollover toggle switch, and Sort/Filter chips.
   * **Dynamic Sorting & Filtering:** Sort by Spent (High-Low), % Spent, Budget (High-Low), or Name (A-Z); filter chip to "Hide ₹0 spent".
   * **Inline Rollover Badges:** Category rows display carried balances (e.g., `+₹1,200 carried`) and base vs. available totals when rollover is enabled.

2. **Planning Tab (`ui/planning/`):**
   * **Inactive & Zero-Budget Management:** "Show ₹0 budget" filter chip ensures categories with ₹0 budget remain accessible and editable.
   * **Flexible Table Sorting:** Supports sorting by Name (A-Z, Z-A), Budget, Spent, Remaining, or Rollover mode.
   * **Category Editor:** Synchronizes and recovers default budget settings (`defaultBudget`) seamlessly during creation and edit modes.
   * **Safe Deletion Feedback:** Provides exact counts of linked transactions across all months when deletion is restricted.

3. **Transactions Tab (`ui/transactions/`):**
   * **Alphabetical Categorization:** Category dropdown menus and filter pickers display categories sorted alphabetically (A-Z) via database collation and in-memory sorting.
   * **Material 3 Entry Sheet:** Supports segmented type selection, nature tagging, date picking, duration coverage, and counterparty loan linking.

4. **Analytics Tab (`ui/analytics/`):**
   * Donut chart for category expense breakdown and 6-month historical spending trend chart rendered natively with **Compose Canvas**.

---

## 4. Data Layer & Room Schema

The persistence layer is managed by `AppDatabase` (Version 3) with SQLite foreign keys and indexes:

* `categories`: `id` (PK), `name`, `iconName`, `defaultBudget`, `rolloverType`, `nature`. Sorted alphabetically by `COLLATE NOCASE`.
* `monthly_budgets`: `id` (PK), `monthYear`, `categoryId` (FK -> categories CASCADE), `allocatedAmount`, `carriedOverAmount`. Unique index on `(monthYear, categoryId)`.
* `transactions`: `id` (PK), `amount`, `categoryId` (FK -> categories RESTRICT), `dateMillis`, `type`, `notes`, `nature`, `coveredMonths`, `loanId` (FK -> loans RESTRICT). Indexed on `categoryId`, `dateMillis`, `loanId`.
* `loans`: `id` (PK), `counterparty`, `direction` (`LENT` / `BORROWED`).

---

## 5. Dependency Injection

DhanSutra uses **Manual Dependency Injection** via a lightweight `AppContainer` initialized in `DhanSutraApp` (Application class):
* Singletons: `AppDatabase`, Repository implementations, `RolloverManager`.
* ViewModels: Created via `appViewModel { container -> ... }` in `ui/AppViewModelFactory.kt`.
