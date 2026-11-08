# DhanSutra

A modern, lightweight, privacy-focused **offline** personal budget tracker for Android. All data stays on the
device in a local Room (SQLite) database; cloud backup and device-transfer are disabled. Currency is Indian Rupee (₹).

Built with Kotlin, Jetpack Compose, Material 3 (dynamic color, light/dark), Room, Coroutines/Flow, and
MVVM + Clean Architecture with manual dependency injection.

## Features

| Tab | What it does |
| --- | --- |
| **Dashboard** | Collapsible cash balance & net worth card (with invested, owed to you and you owe); "This month" ledger; sortable budget progress with zero-spend filter; integrated rollover toggle with inline carried-over badges; collapsible periodic-payment and lending/borrowing cards. |
| **Planning** | Per-category table: Budget \| Spent \| Left \| Rollover mode. Sort by name, budget, spent, remaining, or rollover; "Show ₹0 budget" toggle to unhide inactive categories; edit budget and default template values; safe category deletion with transaction count feedback. |
| **Transactions** | FAB to add; month selector, alphabetical category filter, date-range filter and text search over notes and category names; tap a row to edit or delete. Entry is a Material 3 bottom sheet with alphabetical category dropdown, nature selector (Regular, Periodic, Investment, Loan), date picker, and duration/person linking. |
| **Analytics** | Donut chart of the month's spending by category and a 6-month spending trend line chart, both drawn with Compose Canvas. |

See [ARCHITECTURE.md](ARCHITECTURE.md) for the complete technical blueprint and domain rules.

## Rollover & savings rules

* Month keys are `yyyy-MM`; the month is taken in the device time zone.
* Each category has a budget row for every month from **its own** first month (the earliest of its budget rows and its expenses, otherwise the current month) up to the current month, including months with no spending. A category created in November therefore has no October row. Missing rows are created from the category's default budget by `RolloverManager`, which re-runs whenever categories, transactions or the rollover setting change. It is idempotent.
* The Planning and Dashboard budget lists hide categories that have no budget, no carry-over and no spending in the month (e.g. "Salary & Income").
* **Carry Forward:** next month's `carriedOverAmount` = this month's `allocated + carriedOver - spent` (positive or negative).
* **Sweep to Savings:** next month starts fresh (carry-over 0); a leftover is dropped and an overspend is forgiven.
* **Rollover switch off:** every carry-over is 0.
* **Total savings** = regular income - operating (regular + periodic) expenses summed over all closed months (any month before the current one). It does not depend on rollover or budgets.
* **Transaction nature:** only *regular* expenses count against budgets. *Periodic* lump sums, *investments* (SIP/NPS) and *loans* (lent, borrowed, repayments) all move the cash balance, but investments and loans are neither income nor spending. Net worth = cash + invested (at cost) + owed to you - you owe.
* All money is stored as `Long` **paise** and displayed with Indian digit grouping (₹1,23,456.00).

## Project structure

```
app/src/main/java/com/example/dhansutra/
├── DhanSutraApp.kt            Application; owns AppContainer
├── MainActivity.kt            Hosts theme + navigation
├── di/AppContainer.kt         Manual DI: database, repositories, use cases
├── data/
│   ├── local/                 Room: AppDatabase (+ seed), entity/, dao/
│   └── repository/            Repository implementations, mappers, SharedPreferences settings
├── domain/
│   ├── model/                 Domain models and enums (RolloverType, TransactionType)
│   ├── repository/            Repository interfaces
│   └── usecase/               RolloverCalculator (pure rules), RolloverManager, Observe* use cases
├── ui/
│   ├── AppViewModelFactory.kt appViewModel { container -> ... } helper
│   ├── navigation/            Bottom navigation + NavHost
│   ├── components/            MonthSelector, BudgetProgressBar, CategoryIcon, EmptyState
│   ├── dashboard/ planning/ transactions/ analytics/   Screen + ViewModel (StateFlow UI state) per tab
│   └── theme/                 Material 3 theme
└── util/                      CurrencyFormatter (₹), MonthYear helpers
```

Data flow: `Room DAO -> Repository -> use case -> ViewModel (StateFlow<UiState>) -> Compose screen`.
Every screen has `@Preview`s for fast iteration.

## Build & test

Requires JDK 17+ and the Android SDK (compileSdk 37; the device must run API 36+).

```
./gradlew :app:assembleDebug          # build
./gradlew :app:testDebugUnitTest      # JVM unit tests (rollover rules, formatter, form validation, charts maths)
./gradlew :app:connectedDebugAndroidTest   # Room DAO tests (needs a device/emulator)
```
