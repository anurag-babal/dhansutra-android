# DhanSutra – Offline Budget Tracker: Phase-wise Implementation Plan

Source requirements: `PROMPT.md` (since deleted by the user; its content is captured in this plan)

**Build note:** Android SDK is at `/opt/homebrew/share/android-commandlinetools` (`local.properties`, git-ignored). The wrapper download times out, so compile with `/tmp/gradle-9.6.0/bin/gradle :app:compileDebugKotlin` (Gradle 9.6.0, checksum-verified).

## Top-Level Overview

**Goal:** Build a lightweight, privacy-focused, fully offline personal budget tracker for Android in the existing `DhanSutra` project (Kotlin, Jetpack Compose, Material 3, Room, MVVM + Clean Architecture, Coroutines/Flow, default currency ₹).

**Confirmed decisions**
- App name / package stay **DhanSutra** / `com.example.dhansutra`. "RupeeTrack" in `PROMPT.md` is a placeholder.
- **Manual DI** via a simple `AppContainer` created in a custom `Application` class (no Hilt).
- **Charts via Compose Canvas** (no MPAndroidChart).
- Only new libraries: Room (+ KSP), Navigation Compose, `material-icons-extended` if icon mapping needs it, and lifecycle-viewmodel/runtime-compose helpers.

**Existing project state (from codebase research)**
- Single module `:app`, namespace/applicationId `com.example.dhansutra`, compileSdk 37, minSdk 36, Java 11.
- Version catalog: [`gradle/libs.versions.toml`](gradle/libs.versions.toml) (AGP 9.5.1, Kotlin 2.2.10, Compose BOM 2026.02.01, core-ktx, lifecycle-runtime-ktx, activity-compose). **No Room, KSP, Navigation, or ViewModel-compose configured.**
- Sources: [`MainActivity.kt`](app/src/main/java/com/example/dhansutra/MainActivity.kt), theme files in `app/src/main/java/com/example/dhansutra/ui/theme/` (`Color.kt`, `Theme.kt`, `Type.kt`).
- Only template tests exist (`ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`).
- Build files: [`build.gradle.kts`](build.gradle.kts), [`app/build.gradle.kts`](app/build.gradle.kts), [`settings.gradle.kts`](settings.gradle.kts).

**Target package layout (under `com.example.dhansutra`)**
```
DhanSutraApp.kt            (Application, holds AppContainer)
di/AppContainer.kt
data/local/   (entities, dao, converters, AppDatabase, seed)
data/repository/  (impls)
domain/model/  domain/repository/  domain/usecase/
ui/navigation/  ui/components/  ui/theme/
ui/dashboard/  ui/planning/  ui/transactions/  ui/analytics/
util/ (CurrencyFormatter, MonthYear helpers)
```

**Key domain decisions to apply (derived from PROMPT.md semantics; flagged where interpretation was needed)**
1. `monthYear` is stored as `"yyyy-MM"` string.
2. Amounts stored as `Long` paise (avoids floating-point error); formatted as ₹ with Indian digit grouping (e.g. ₹1,23,456.00).
3. `MonthlyBudgetEntity` has a unique index on `(monthYear, categoryId)`; a row is lazily created for a month from `CategoryEntity.defaultBudget` the first time the month is viewed/needed.
4. **Carry Forward:** next month's `carriedOverAmount` = previous month's (allocated + carriedOver − spent), positive or negative.
5. **Sweep to Savings:** next month's `carriedOverAmount` = 0; the previous month's net remaining (allocated + carriedOver − spent) is added to the savings pool.
6. **Savings pool** has no entity in the spec → derive it rather than add a table. **Confirmed by user:** Overall Savings = Σ swept surplus of all closed months (Sweep categories) + all-time Σ (income − expenses) of closed months. (Closed month = any month before the current month.) Implementation must avoid double counting: a swept surplus is by definition unspent budget and is not an income/expense transaction, so the two terms are independent; document this in the README.
7. **Rollover global toggle** on Dashboard enables/disables month-to-month rollover entirely (when off, carriedOver = 0 and no sweeping). Persisted in `SharedPreferences` (avoids adding DataStore), exposed as a `Flow`/`StateFlow`.
8. Month rollover is computed on app open for any months between the last processed month and the current month (idempotent, no background worker needed for offline personal use).
9. Spent = Σ EXPENSE transactions in the month for that category; Income total = Σ INCOME transactions in the month.

---

## Phase 1 – Project Setup & Dependencies

- **Status:** `[x] done`
- **Intent:** Prepare build tooling and project skeleton so later phases compile cleanly.
- **Expected Outcomes:** Project syncs and builds with Room (KSP), Navigation Compose, ViewModel-Compose; custom Application class registered; package folders exist.
- **Todo List:**
  1. Add to `gradle/libs.versions.toml`: KSP plugin (version matching Kotlin 2.2.10), Room (runtime, ktx, compiler), `androidx-navigation-compose`, `androidx-lifecycle-viewmodel-compose`, `androidx-lifecycle-runtime-compose`, `androidx-compose-material-icons-extended` (BOM-managed), `room-testing`, `kotlinx-coroutines-test`.
  2. Apply KSP plugin in root and `app/build.gradle.kts`; add dependencies and Room schema export arg (`room.schemaLocation`).
  3. Create `DhanSutraApp : Application`, register in `AndroidManifest.xml` via `android:name`.
  4. Create empty package structure and `AppContainer` shell.
  5. Rename app display name in `strings.xml` if needed (keep "DhanSutra"); confirm backup rules in `xml/` are acceptable for a private offline app (consider excluding DB from cloud backup).
  6. Run a build to verify sync/compile.
- **Relevant Context:** [`gradle/libs.versions.toml`](gradle/libs.versions.toml), [`app/build.gradle.kts`](app/build.gradle.kts), [`build.gradle.kts`](build.gradle.kts), [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml), [`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml).

---

## Phase 2 – Data Layer (Room)

- **Status:** `[x] done` (DAO tests written in `app/src/androidTest/.../data/DaoTest.kt`; compiled but not run - needs device. Money is stored in paise; seed categories inserted via raw SQL in `SeedCallback`.)
- **Intent:** Deliver production-ready persistence (PROMPT deliverable #2).
- **Expected Outcomes:** Entities, enums, type converters, DAOs, and `AppDatabase` compile; DAO tests pass on an in-memory DB; default categories seeded on first launch.
- **Todo List:**
  1. `RolloverType` enum (`CARRY_FORWARD`, `SWEEP_TO_SAVINGS`) and `TransactionType` enum (`EXPENSE`, `INCOME`) with `TypeConverter`s.
  2. `CategoryEntity` (`id`, `name`, `iconName`, `defaultBudget`, `rolloverType`).
  3. `TransactionEntity` (`id`, `amount`, `categoryId`, `dateMillis`, `type`, `notes`) with FK to category and indexes on `categoryId` and `dateMillis`. Decide FK delete behaviour (restrict delete of categories having transactions; archive/soft-delete is out of scope).
  4. `MonthlyBudgetEntity` (`id`, `monthYear`, `categoryId`, `allocatedAmount`, `carriedOverAmount`) with FK + unique index `(monthYear, categoryId)`.
  5. DAOs:
     - `CategoryDao`: observe all, insert/update/delete, get by id.
     - `TransactionDao`: insert/update/delete, observe by month range, filter by category/date range, text search over notes and category name (join), sum by category per range, sum income/expense per range, per-month totals for trend.
     - `MonthlyBudgetDao`: observe by month, upsert, get by month+category, list of distinct months.
  6. Query result POJOs (e.g. `CategorySpent`, `MonthTotal`, `TransactionWithCategory`).
  7. `AppDatabase` (version 1, export schema) and a `RoomDatabase.Callback` seeding default categories (Rent/EMI, Groceries, Utilities, Dining & Swiggy, Shopping) with icon names and sample default budgets.
  8. Wire database + DAOs into `AppContainer`.
  9. Instrumented in-memory Room tests for DAO queries (filters, search, sums, unique index).
- **Relevant Context:** New package `data/local/`; test dir `app/src/androidTest/java/com/example/dhansutra/`.

---

## Phase 3 – Domain Layer & Repositories

- **Status:** `[x] done` (JVM unit tests pass. Notes for later phases: enums live in `domain.model`; `RolloverCalculator` holds pure rules; `RolloverManager` (started from `AppContainer.start()`) auto-creates/updates month budget rows on any category/spend/setting change - call `rolloverManager.recalculate()` after editing a budget row; use cases are exposed from `AppContainer` (`observeMonthlyBudget`, `observeDashboardSummary`, `observeCategoryBreakdown`, `observeMonthlyTrend`); `CurrencyFormatter` + `MonthYear` in `util/`; no separate last-processed-month setting needed since rollover is recomputed idempotently.)
- **Intent:** Encapsulate business rules (budgeting, rollover, savings) behind repository interfaces and use cases (Clean Architecture; PROMPT deliverable #3, repositories part).
- **Expected Outcomes:** Domain models and repository interfaces in `domain/`; Room-backed implementations in `data/repository/`; rollover logic unit-tested.
- **Todo List:**
  1. Domain models (`Category`, `Transaction`, `MonthlyBudgetItem` with allocated/carriedOver/spent/remaining, `DashboardSummary`, `CategoryBreakdown`, `MonthlyTrendPoint`) and mapper functions entity↔domain.
  2. Repository interfaces + implementations: `CategoryRepository`, `TransactionRepository`, `BudgetRepository`, `SettingsRepository` (rollover-enabled flag via SharedPreferences exposed as Flow).
  3. Use cases (kept small): `ObserveDashboardSummary`, `ObserveMonthlyBudget`, `EnsureMonthBudgetsExist` (create rows from defaults), `ProcessMonthRollover` (idempotent catch-up from last processed month to current), `ObserveTransactions` (filter + search), `ObserveCategorySpending`, `ObserveMonthlyTrend`, `UpsertTransaction`.
  4. Implement rollover rules per decisions 4–8 above; track the last processed month (stored in settings).
  5. Implement Overall Savings formula per decision 6 (swept surplus + Σ income − expenses of closed months).
  6. Wire into `AppContainer`.
  7. JVM unit tests with fake repositories for: carry-forward positive/negative, sweep, toggle off, multi-month catch-up, idempotency, month boundaries.
- **Relevant Context:** New packages `domain/*`, `data/repository/`, `util/` (month helpers, `CurrencyFormatter` for ₹ with Indian grouping).

---

## Phase 4 – Theme, Navigation Shell & Shared Components

- **Status:** `[x] done` (Notes: existing theme already supports dynamic color + dark/light. Each tab is `ui/<tab>/<Tab>Route.kt` (placeholders to be replaced); get ViewModels with `appViewModel { container -> ... }` from `ui/AppViewModelFactory.kt`; shared: `MonthSelector`, `BudgetProgressBar`, `EmptyState`, `CategoryIcon`/`CategoryIcons.all` (icon names match seeded categories). The FAB lives inside the Transactions screen itself, since the bottom-nav Scaffold has no FAB.)
- **Intent:** Provide the app frame: Material 3 theming with dynamic color and the 4-tab bottom navigation.
- **Expected Outcomes:** App launches to a bottom-nav scaffold with Dashboard, Planning, Transactions, Analytics tabs (placeholder content); dynamic color and dark/light work; a ViewModel factory pattern is in place.
- **Todo List:**
  1. Review/adjust existing theme in [`Theme.kt`](app/src/main/java/com/example/dhansutra/ui/theme/Theme.kt) to confirm dynamic color + dark/light support.
  2. `ViewModelProvider.Factory` utility that pulls dependencies from `AppContainer` via `DhanSutraApp`.
  3. Navigation graph (`Screen` sealed class), `Scaffold` with `NavigationBar`, FAB handled on Transactions tab (and optionally Dashboard).
  4. Update [`MainActivity.kt`](app/src/main/java/com/example/dhansutra/MainActivity.kt) to host the scaffold; call month rollover/ensure-budgets once on startup.
  5. Shared composables: `MonthSelector`, `AmountText` (₹ formatting), `CategoryIcon` (iconName → ImageVector map), `EmptyState`, `BudgetProgressBar`.
- **Relevant Context:** `ui/navigation/`, `ui/components/`, `ui/theme/`.

---

## Phase 5 – Transactions Tab & Entry BottomSheet

- **Status:** `[x] done` (Notes: added seeded category "Salary & Income" so income entries have a category; form logic is in `TransactionForm` (unit-tested); tap a row to edit, Delete button is in the edit sheet (no swipe-delete); date range filter overrides the month selector.)
- **Intent:** Enable fast data entry first, since all other screens depend on transaction data (PROMPT deliverable #4: Transaction Entry BottomSheet).
- **Expected Outcomes:** User can add/edit/delete transactions; list can be filtered by month, category, date range, and searched; state exposed via `StateFlow`.
- **Todo List:**
  1. `TransactionsViewModel` with `UiState` (list grouped by date, filters, search query, loading/empty) and events (filter changes, search, delete).
  2. `TransactionEntryViewModel` (or part of same VM): form state, validation (amount > 0, category required), save.
  3. `TransactionsScreen`: search bar, filter chips (month, category, date range), grouped list with swipe/long-press delete, FAB (+).
  4. `TransactionEntryBottomSheet` (`ModalBottomSheet`): amount (₹) field, Material 3 `DatePicker`, category dropdown (`ExposedDropdownMenuBox`), Expense/Income segmented control, notes; edit mode for existing items.
  5. Previews for the screen and the bottom sheet (light/dark).
  6. ViewModel unit tests for filtering/search/validation.
- **Relevant Context:** `ui/transactions/`, `TransactionRepository`, `CategoryRepository`.

---

## Phase 6 – Monthly Planning Tab

- **Status:** `[ ] pending`
- **Intent:** Let the user allocate budgets and choose rollover mode per category (PROMPT deliverable #4: Monthly Budget View).
- **Expected Outcomes:** Table-style view with Category | Allocated | Spent | Remaining | Rollover Status for the selected month; edit allocation and rollover mode; add/edit/remove categories.
- **Todo List:**
  1. `PlanningViewModel` with `StateFlow<PlanningUiState>` (selected month, rows, totals).
  2. `PlanningScreen`: `MonthSelector`, header row + rows (scrollable/compact layout for narrow screens), per-row remaining coloured red when negative, rollover badge (Carry Forward / Sweep).
  3. Edit dialog/bottom sheet: allocated amount (updates `MonthlyBudgetEntity` for that month, and optionally `defaultBudget`), rollover type selector (updates `CategoryEntity`).
  4. Category management: add / rename / choose icon / delete (guarded when transactions exist).
  5. Previews and ViewModel tests.
- **Relevant Context:** `ui/planning/`, `BudgetRepository`, `CategoryRepository`, `ObserveMonthlyBudget`.

---

## Phase 7 – Dashboard Tab

- **Status:** `[ ] pending`
- **Intent:** Provide the home overview (PROMPT deliverable #4: Dashboard).
- **Expected Outcomes:** Four header cards (Total Monthly Income, Spent MTD, Remaining Surplus, Overall Savings); per-category Budget vs Actual progress bars; rollover summary with global enable/disable switch.
- **Todo List:**
  1. `DashboardViewModel` combining summary, per-category progress, and rollover setting into a single `StateFlow<DashboardUiState>`.
  2. `DashboardScreen`: 2×2 header card grid, category progress list (percent spent, over-budget highlight), rollover summary card with `Switch` (and per-category carried/swept amounts for the month).
  3. Toggling rollover re-triggers `ProcessMonthRollover` handling per decision 7.
  4. Previews (populated, empty, over-budget, dark mode) and ViewModel tests.
- **Relevant Context:** `ui/dashboard/`, `ObserveDashboardSummary`, `SettingsRepository`, `BudgetProgressBar` component.

---

## Phase 8 – Analytics & Reports Tab

- **Status:** `[ ] pending`
- **Intent:** Visualise spending (PROMPT feature #4) using Compose Canvas only.
- **Expected Outcomes:** Monthly category breakdown (donut/pie or segmented bar with legend) and Month-over-Month spending line chart.
- **Todo List:**
  1. `AnalyticsViewModel` with selected month, category breakdown, last N months trend (`StateFlow`).
  2. Canvas `PieChart`/segmented bar composable with legend and percentages, using theme-derived colours.
  3. Canvas `LineChart` composable with axis labels (month abbreviations, ₹ values compacted e.g. 12.5K / 1.2L), handles empty and single-point data.
  4. `AnalyticsScreen` assembling both with month selector.
  5. Previews and ViewModel tests.
- **Relevant Context:** `ui/analytics/`, `ObserveCategorySpending`, `ObserveMonthlyTrend`.

---

## Phase 9 – Polish, Testing & Documentation

- **Status:** `[ ] pending`
- **Intent:** Harden the app and deliver the documentation deliverable (PROMPT deliverable #1, #5).
- **Expected Outcomes:** Clean build, passing unit/instrumented tests, accessibility and edge cases handled, README documents structure.
- **Todo List:**
  1. Edge cases: empty states, very large amounts, negative remaining, month with no budgets, timezone/date boundaries for month ranges.
  2. Accessibility: content descriptions, touch target sizes, contrast in dark mode, font scaling check.
  3. Replace template tests with real ones; run `./gradlew test` and `connectedAndroidTest`.
  4. Confirm all key screens have light/dark previews.
  5. Update [`README.md`](README.md): project structure explanation, architecture diagram description, rollover rules, how to build/run.
  6. Review release build config (minify/shrink for Room) and backup rules to keep data private.
- **Relevant Context:** [`README.md`](README.md), [`app/build.gradle.kts`](app/build.gradle.kts), `app/src/test`, `app/src/androidTest`.

---

## Open Items (to resolve during implementation)

1. ~~Overall Savings Accumulated formula~~ – **Resolved:** swept surplus + all-time (income − expenses) of closed months (see decision 6).
2. **Persistence of rollover toggle / last-processed month** – default `SharedPreferences`; switch to DataStore only if preferred.
3. **Category deletion policy** – default: block deletion when transactions exist.
4. **Seed default budget amounts** – sample values; user edits them in Planning tab.

## Phase Dependency Order
Phase 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 (each phase is independently reviewable and builds on the previous one).
