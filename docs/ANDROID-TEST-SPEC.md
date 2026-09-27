# Android tests and the TS ⇄ Kotlin core contract

How the Android app is tested, how its tests relate to the web suite, and the
rules that keep the two platforms consistent. Architecture lives in
[`TECHNICAL.md`](../TECHNICAL.md) §14; the wire contract in [`api.md`](./api.md).

## 1. The core contract (pure math implemented twice)

The pure algorithms in `src/lib/core/*.ts` (meal totals, protein, calories/BMR,
measured expenditure, foods, AI answer parsing, progress averages, coach context,
dates, stats, the stats builder, CSV) are implemented **twice**: in TypeScript for
the web/server and as a Kotlin port in the pure-JVM `:core` module
(`android/core/`). Android needs them locally because it is local-first: it
computes totals, recommendations and statistics on the phone, without the server.

`tests/unit/*.test.ts` is the **specification for both**. Each assertion has a
Kotlin JUnit mirror with the same inputs and the same expected numbers
(reproducing `Math.round` / `toFixed` behavior exactly).

| Web spec | Functions | Kotlin mirror (`android/core/src/test/…/core/`) |
| -------- | --------- | ---------------- |
| `tests/unit/nutrition.test.ts` | `sumIngredientNutrition`, `resolveMealTotals`, `round1`, `round2` | `NutritionTest.kt` |
| `tests/unit/protein.test.ts` | `calculateProteinRecommendation` | `ProteinTest.kt` |
| `tests/unit/calories.test.ts` | `calculateBMR`, `getActivityMultiplier`, `isCalorieProfileComplete`, `calculateCalorieRecommendation` | `CaloriesTest.kt` |
| `tests/unit/expenditure.test.ts` | `estimateExpenditure`, `fitWeightTrend` | `ExpenditureTest.kt` |
| `tests/unit/foods.test.ts` | `scalePer100g`, `foodToIngredient`, `parseServingGrams`, `normalizeText`, `parseOffProduct`, `parseOffSearch`, `searchGenericFoods` | `FoodsTest.kt` |
| `tests/unit/ai-schema.test.ts` | `parseMealEstimate`, `extractJson`, `estimateToIngredients` | `AiSchemaTest.kt` |
| `tests/unit/progress.test.ts` | `macroAverages` | `ProgressTest.kt` |
| `tests/unit/coach.test.ts` (+ `coach.fixture.ts`) | `weightProjection`, `buildCoachContext` (exact text), `buildCoachSystemPrompt` | `CoachTest.kt` |
| `tests/unit/dates.test.ts` | date keys, `parseLocalDateTime`, es-ES formatters | `DatesTest.kt` |
| `tests/unit/stats.test.ts` | `movingAverageByDays`, `linearRatePerWeek`, `weeklyAverages`, `buildDailyNutritionSeries`, `rangeToDays` | `StatsTest.kt` |
| `tests/unit/stats-builder.test.ts` | `buildStatsFromData` | `StatsBuilderTest.kt` |
| `tests/unit/auth-and-csv.test.ts` (CSV part) | `toCsv` | `CsvTest.kt` |

**The rule:** changing a core algorithm means changing the TS implementation, its
TS test, the Kotlin implementation and its Kotlin test **together**.

**Enforced by** `npm run core:sync-check` (`scripts/check-core-sync.ts`), driven by
`android/test-sync/manifest.json` (one row per pair above). It warns locally and
**fails CI** when only one side of a pair changed. `npm run hooks:install` adds a
pre-commit reminder. Adding, removing or renaming a core domain means updating the
manifest and this table.

Why not share one implementation (Kotlin Multiplatform / WebAssembly)? For ~750
lines of stable, test-pinned math, duplication is cheaper for a solo project. Revisit
if the core grows large or changes often.

## 2. Android-only tests (`android/app/src/test/`)

Run with `./gradlew :app:testDebugUnitTest` (JVM; Room and Android classes via
Robolectric, no emulator needed).

| Test | What it proves |
| ---- | -------------- |
| `data/OfflineSyncTest.kt` | End to end with a real in-memory Room database, the real Retrofit client and `FakeServer` (a MockWebServer dispatcher that behaves like the Next.js routes): local-only mode never touches the network; a meal saved offline survives and uploads once, retries never duplicate; edits, deletes and reorders upload; web edits/deletions are pulled; an edit made during an upload is not lost; profile sync with explicit nulls; expired session keeps data; login with local data (upload / discard) and wrong credentials; logout and «delete all data» wipe only the phone; undo delete; CSV import de-duplication; a captive-portal/HTML response fails the sync without losing data |
| `data/ApiContractTest.kt` | Wire format against MockWebServer (mirrors `tests/behavior/routes-*.test.ts`): auth, `PUT /:id` upserts, deletes, settings with explicit nulls, admin; status codes and `{ "error": … }` envelopes |
| `data/CsvBackupTest.kt` | CSV export/import round trip; reads a file exported by the web; skips rows the server would reject; unknown files |
| `ui/ValidationTest.kt` | Meal form, weight and profile limits (same as `src/server/validation.ts`); recommendation states, latest body fat for protein, measured expenditure from meals + weigh-ins; sync indicator states |
| `ui/TranslationsTest.kt` | Every language has every string and plural with the same placeholders |
| `data/ResponseErrorMapperTest.kt`, `data/NiceTicksTest.kt` | Error envelope decoding; chart axis ticks |

Server side of sync: `tests/behavior/routes-sync-upsert.test.ts` (web suite) covers
the idempotent `PUT /api/<kind>/:id` endpoints the app uploads with.

## 3. Correspondence with the web suite

Web behavior tests describe *user requirements*. When a requirement exists on both
platforms, both should be tested:

| Web test | Android counterpart |
| -------- | ------------------- |
| `meal-management`, `nutrition-entry`, `weights-service` (services) | `OfflineSyncTest` (repository behavior) + `ValidationTest` (form rules) |
| `routes-*` (wire contract) | `ApiContractTest` + `FakeServer` |
| `csv-export` | `CsvBackupTest` |
| `stats-overview`, `tests/unit/stats-builder` | `:core` `StatsBuilderTest` (Android computes stats locally) |
| `recommendations-card`, `perfil-page` (recommendation rules) | `ValidationTest` (`recommend`) |
| `*-page.test.tsx` (screens) | Not automated yet — Compose UI tests are a known gap; screens are checked manually on an emulator |

## 4. Maintenance checklist

After changing either platform:

- [ ] `src/lib/core/*.ts` changed → TS test **and** Kotlin implementation + `*Test.kt` changed (CI enforces).
- [ ] API or server validation changed → `docs/api.md`, the web route tests, `ApiContractTest`/`FakeServer`, and the Android validation (`FormFields.kt`, `WeightFormDialog.kt`, `ProfileViewModel.kt`) updated together. A value the phone accepts but the server rejects stays pending forever.
- [ ] DB schema changed → `db:push`, `tests/helpers/repos.ts`, the Android wire models, Room entities **with a Room migration** (users without an account have no other copy of their data).
- [ ] New Android UI text → added to all five `res/values*/strings.xml` (`TranslationsTest` fails otherwise).

Verification gate:

```bash
npx tsc --noEmit && npm run lint && npm test && npm run build
npm run core:sync-check
(cd android && ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug)
```
