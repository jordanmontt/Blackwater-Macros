# Android Test Specification

This document maps the existing web test suite to corresponding Android (Kotlin)
tests. The web tests are the **source of truth** for behavior. Each Android test
must reproduce the same user-visible outcome.

## Guiding principles

- Pure algorithms (`src/lib/core/*`) are reimplemented in Kotlin; port the unit
  tests **1:1** to JUnit.
- HTTP/API behavior is validated against the same endpoints; the route tests in
  `tests/behavior/routes-*.test.ts` define the wire contract (see `docs/api.md`).
- UI tests are *behavioral* (what the user sees/does), not implementation-specific.
  Port the flows, not the markup.

## Unit tests (pure algorithms — port directly to Kotlin JUnit)

| Web test | Functions under test | Android (Kotlin) |
| -------- | -------------------- | ---------------- |
| `tests/unit/nutrition.test.ts` | `sumIngredientNutrition`, `resolveMealTotals`, `round1`, `round2` | `NutritionTest.kt` |
| `tests/unit/protein.test.ts` | `calculateProteinRecommendation` | `ProteinTest.kt` |
| `tests/unit/calories.test.ts` | `calculateBMR`, `getActivityMultiplier`, `isCalorieProfileComplete`, `calculateCalorieRecommendation` | `CaloriesTest.kt` |
| `tests/unit/dates.test.ts` | `isValidDateKey`, `toDateKey`, `addDaysToKey`, `daysBetweenKeys`, `parseLocalDateTime`, formatters | `DatesTest.kt` (use `java.time`) |
| `tests/unit/stats.test.ts` | `movingAverageByDays`, `linearSlopePerDay`, `linearRatePerWeek`, `weeklyAverages`, `buildDailyNutritionSeries`, `rangeToDays` | `StatsTest.kt` |
| `tests/unit/stats-builder.test.ts` | `buildStatsFromData` | `StatsBuilderTest.kt` |
| `tests/unit/auth-and-csv.test.ts` (CSV part only) | `toCsv` | `CsvTest.kt` |

Each assertion must produce the identical result. Watch floating-point behavior:
`Math.round`, `toFixed` semantics must be reproduced faithfully in Kotlin.

## Service / business-rule tests (port to Kotlin with Room in-memory)

These validate the same business rules but use the Android local repository
(Room in-memory) as the fake backend instead of the in-memory Maps.

| Web test | Business rules | Android (Kotlin) |
| -------- | -------------- | ---------------- |
| `tests/behavior/meal-management.test.ts` | Create/edit/delete/reorder meals; user isolation; sorting | `MealRepositoryTest.kt` |
| `tests/behavior/weights-service.test.ts` | Weight CRUD, ordering, optional fields | `WeightRepositoryTest.kt` |
| `tests/behavior/nutrition-entry.test.ts` | Entry modes, resolved totals | `NutritionEntryTest.kt` |
| `tests/behavior/stats-overview.test.ts` | Stats aggregation over local data | `StatsOverviewTest.kt` |

## API contract tests (Retrofit / MockWebServer)

These validate that the Android client produces and consumes the exact wire
format. Reference `tests/behavior/routes-*.test.ts` and `docs/api.md`.

| Web test | Endpoints | Android (Kotlin) |
| -------- | --------- | ---------------- |
| `tests/behavior/routes-auth.test.ts` | login/logout/session (incl. Bearer) | `AuthApiTest.kt` |
| `tests/behavior/routes-meals.test.ts` | `/api/meals` CRUD + reorder | `MealApiTest.kt` |
| `tests/behavior/routes-templates.test.ts` | `/api/templates` CRUD | `TemplateApiTest.kt` |
| `tests/behavior/routes-weights.test.ts` | `/api/weights` CRUD | `WeightApiTest.kt` |
| `tests/behavior/routes-stats.test.ts` | `/api/stats` | `StatsApiTest.kt` |
| `tests/behavior/routes-settings.test.ts` | `/api/settings` | `SettingsApiTest.kt` |
| `tests/behavior/routes-export.test.ts` | `/api/export/*` | `ExportApiTest.kt` |
| `tests/behavior/routes-admin.test.ts` | `/api/admin/users` | `AdminApiTest.kt` |

Contract elements to assert: status codes (200/201/400/401/403/404/409), JSON
shapes (`{ "meals": [...] }`, `{ "error": "..." }`), decimal `.` separator,
`YYYY-MM-DD` and ISO-8601 date formats.

## UI / Compose tests (Jetpack Compose UI tests)

Port the *user flows* from the React page tests. BEHAVIOR is the spec, not markup.

| Web test | User flow | Android (Compose) |
| -------- | --------- | ----------------- |
| `tests/behavior/today-page.test.tsx` | Daily totals, day navigation, template application with loading state, no double submit | `TodayScreenTest.kt` |
| `tests/behavior/peso-page.test.tsx` | Add/edit weight, chart display | `WeightScreenTest.kt` |
| `tests/behavior/ajustes-page.test.tsx` | Calorie profile settings, recommendations | `SettingsScreenTest.kt` |
| `tests/behavior/login-page.test.tsx` | Login, error states, session restore | `LoginScreenTest.kt` |
| `tests/behavior/admin-page.test.tsx` | User management (admin) | `AdminScreenTest.kt` |
| `tests/behavior/recommendations-card.test.tsx` | Calorie/protein recommendations card | `RecommendationsTest.kt` |

## New behavior Android must add (not covered by web tests)

These require new tests beyond the web suite because they only exist on Android:

| Behavior | Required tests |
| -------- | -------------- |
| Offline-first storage | Room in-memory CRUD tests; surviving process death |
| Sync engine | Push/pull delta sync, offline write queue, retry with backoff |
| Conflict resolution (last-write-wins) | Two devices edit same record; server `updatedAt` comparison wins (meals, templates and weights each expose `updatedAt` and the server refreshes it on every update) |
| Offline stats | `buildStatsFromData` port runs on cached local data |
| 401 → re-login | Expired token intercepted → login screen; token cleared |

## Verification gate (mirror of web)

Android must pass its own:
- `./gradlew test` (unit, service, API contract)
- `./gradlew connectedAndroidTest` (instrumentation/UI) when devices available
- Lint and full build must be green.

A web test change that alters behavior MUST be mirrored by a corresponding
change in the Android suite (and vice-versa), keeping both platforms in lockstep.
