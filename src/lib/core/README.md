# `src/lib/core/` — Pure Algorithms (Shared Source of Truth)

This directory contains **pure algorithms with zero side effects**. It is the single
source of truth for the business math used across every platform of Blackwater
Macros.

## Dependency Rule

- Files in this directory import **nothing** from React, browser APIs (`window`,
  `document`, `fetch`, `localStorage`), or server code (`next/headers`, `cookies`,
  Drizzle).
- No file here performs I/O, reads the clock, or makes HTTP requests.
- Every function is deterministic given its inputs (except where a date/now value
  is explicitly passed in).
- Code outside this directory may depend on it; code inside it may not depend on
  anything outside it.

## Why "core"

Both the **web app** (TypeScript, used directly) and the **Android app** (Kotlin,
reimplemented) implement these exact algorithms. The unit tests in
`tests/unit/*.test.ts` serve as the behavioral specification for both platforms —
each assertion maps 1:1 to a Kotlin JUnit test.

## Files

| File               | Domains                                                              |
| ------------------ | -------------------------------------------------------------------- |
| `types.ts`         | Wire-format DTOs and enums shared by client and server               |
| `nutrition.ts`     | Meal/ingredient totals and rounding (`sumIngredientNutrition`, `resolveMealTotals`, `round1`, `round2`) |
| `protein.ts`       | Protein intake recommendation (`calculateProteinRecommendation`)     |
| `calories.ts`      | BMR, activity multiplier, calorie targets (Mifflin-St Jeor)          |
| `dates.ts`         | Date-key arithmetic and es-ES formatting                             |
| `stats.ts`         | Moving average, linear regression, weekly averages, series building  |
| `stats-builder.ts` | Master `buildStatsFromData` used by server and demo mode             |
| `csv.ts`           | RFC-4180 CSV serializer with BOM                                     |

## Kotlin Porting Notes

When porting these algorithms to Kotlin:

- Types become `data class` / `enum class` (`types.ts`).
- Date arithmetic uses `java.time.LocalDate` / `java.time.LocalDateTime`
  (`dates.ts`, `stats.ts`).
- es-ES formatting uses `java.text.NumberFormat` / `java.time.format.DateTimeFormatter`
  with `Locale("es", "ES")` (`dates.ts`).
- Clock reads are injected rather than called internally (e.g. `todayKey`).
- The existing unit tests in `tests/unit/` are the specification to port.

## Porting Maintenance Contract (Option A)

The pure algorithms are implemented **twice**: here in TS (web) and as a Kotlin
port (Android). `tests/unit/*.test.ts` is the shared spec for both.

**Any change to a `tests/unit/*.test.ts` file REQUIRES a mirrored change to the
Kotlin `*Test.kt` (and its implementation), and vice-versa.**

Forgetting one side is caught automatically: `android/test-sync/manifest.json`
pairs each TS spec with its Kotlin test, and `npm run core:sync-check` warns
locally / **fails CI** when only one side of a pair changed. See
`docs/ANDROID-PLAN.md`.
