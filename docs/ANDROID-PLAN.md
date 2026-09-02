# Android App — Implementation & Maintenance Plan

The companion to `docs/TECHNICAL.md` and `docs/api.md`, focused on building a
**native Android (Kotlin) version** of Blackwater Macros that shares the same
deployed Next.js backend, and on keeping it maintainable as a **solo project**.

> **Start here.** If you only read one doc to implement the Android app, read this
> one. The details of the API wire format and the test mapping live in
> `docs/api.md` and `docs/ANDROID-TEST-SPEC.md`.

---

## 1. Context and confirmed decisions

- **Existing system:** a Spanish-language nutrition/macro tracker. Web app
  (Next.js 16.3.2, React 19.2.8, App Router, shadcn/ui, Tailwind v4, Drizzle ORM +
  PostgreSQL, custom scrypt auth, PWA) with a serverless backend in `src/app/api/`
  and `src/server/`.
- **Android framework:** Native **Kotlin** with **Jetpack Compose**, **Room**,
  **Retrofit**.
- **Backend:** Android calls the **exact same deployed Next.js backend** (same
  server, same DB, same endpoints). Auth already supports both the web
  `bw_session` cookie and `Authorization: Bearer <token>`.
- **Conflict resolution:** **last-write-wins** using the `updatedAt` ISO-8601 UTC
  field exposed on every mutable DTO (meals, templates, weights). The server sets
  `updatedAt` on create and refreshes it on every update/reorder. This is the
  single authoritative clock for sync.
- **Target store:** **F-Droid** → **no Google Play Services / GMS dependencies.**
- **Wire format:** always `.` as decimal separator in JSON; display with `,` using
  `java.text.NumberFormat` with `Locale("es", "ES")`.
- **Timezones:** `logDate` days are the user's local calendar day; the client
  sends its own `today` to `/api/stats` so ranges match the user's view.

---

## 2. Architecture and the "core" contract (Option A)

### Layering

```
┌────────────────────────────────────────────────────────────┐
│  Web app (Next.js)                 │  Android app (Kotlin) │
│  UI + http + storage (browser)     │  UI (Compose) + Retrofit + Room │
├────────────────────────────────────┴───────────────────────┤
│  Shared pure algorithms                                      │
│  Web: src/lib/core/*.ts  ←spec→  Android: core/*.kt         │
├─────────────────────────────────────────────────────────────┤
│  Backend (src/server/services + src/app/api)  ← single      │
│  source of truth for CRUD, auth, and business logic.        │
└─────────────────────────────────────────────────────────────┘
```

- The **server owns all CRUD/auth/business logic**; web and Android are thin
  clients.
- The **only deliberate duplication** is the *pure math* in the core (nutrition
  totals, protein, calories/BMR, dates, stats, CSV). It exists in three places by
  design: server service, web `lib/core`, and (future) Android offline stats.
  Duplication is safe **because it is pure and test-pinned**.

### Option A: "tests as the spec"

The pure algorithms are implemented **twice** in two languages:

- **Canonical TS:** `src/lib/core/*.ts` (read directly by the web app).
- **Kotlin port:** `android/core/src/main/kotlin/com/blackwatermacros/app/core/*.kt` (pure JVM module `:core`; the Android UI lives in `:app`).
- **Shared behavioral spec:** `tests/unit/*.test.ts`.

> **The rule:** `tests/unit/*.test.ts` is the specification for both.
> When you change a core algorithm, you change **both** the TypeScript test and
> the Kotlin `*Test.kt` (and their implementations). Each assertion in the TS unit
> test maps 1:1 to a Kotlin JUnit test with the **same inputs and expected
> numbers**, reproducing floating-point behavior faithfully (`Math.round`,
> `toFixed`/round-half semantics).

This is deliberately **not** cross-compiled (no Kotlin Multiplatform for the web);
for a small, stable core (~740 lines across 8 files) the duplication is cheap, and
the tests catch any drift. If the core ever grows large or changes often, revisit
KMP — see `src/lib/core/README.md`.

---

## 3. Automatically keeping the two suites in sync

Because two copies are involved, forgetting to update one side is the #1 risk. It
is mitigated by a **machine-checked pairing** that warns (locally) and **fails the
PR** (CI).

### The pairing manifest

`android/test-sync/manifest.json` maps each pure-algorithm TS source → its TS test
→ its Kotlin test, one row per domain (nutrition, protein, calories, dates, stats,
stats-builder, csv).

### The check

`npm run core:sync-check` (backed by `scripts/check-core-sync.ts`):

1. Verifies every pair in the manifest exists on **both** sides (TS + Kotlin).
2. Detects **unpaired changes**: if a `tests/unit/*.test.ts` changed in this PR
   but its Kotlin mirror did not (or vice-versa), it warns/fails with a message
   like:
   > `tests/unit/nutrition.test.ts cambió pero su espejo Kotlin
   > (…/NutritionTest.kt) NO. Option A obliga a actualizar AMBAS suites.`

Behavior:
- Locally (`npm run core:sync-check`): prints warnings, **does not block**.
- In CI (`CI=true npm run core:sync-check`): **hard-fails the PR** on drift.

### Local git hook (optional but recommended)

`npm run hooks:install` adds a `pre-commit` hook that re-runs the check and prints
a reminder on the console if either side is missing — so you notice at commit time,
before CI. It warns and never blocks your commit.

### When the manifest must change

Whenever you add/remove/rename a core test domain, update `manifest.json` too (the
CI sync-guard checks the pairs in it). Keep the file-pair list in lockstep with the
tables in `docs/ANDROID-TEST-SPEC.md`.

---

## 4. Reading list before coding (in order)

1. **This file** — the plan and maintenance contract.
2. **`docs/ANDROID-TEST-SPEC.md`** — the full web-test → Kotlin-test mapping and the
   Android-only behaviors (offline-first, sync engine, LWW, offline stats,
   401 → re-login).
3. **`docs/api.md`** — authoritative wire contract (auth incl. Bearer, all
   endpoints, request/response shapes, error convention). The **Android
   Integration Notes** section is required reading.
4. **`src/lib/core/README.md`** — the core dependency rule and Kotlin porting notes.
5. **`src/server/services/` + `tests/behavior/` + `tests/unit/`** — exact behavior to reproduce.
6. **`TECHNICAL.md`** — architecture source of truth (data model, request lifecycle).

---

## 5. Implementation roadmap (ordered)

1. **Scaffold:** Gradle Android project in `android/` (this repo), Kotlin + Compose,
   no GMS. Package name, min SDK: confirm with the user before scaffolding.
2. **Core port:** reimplement each `src/lib/core/*.ts` algorithm in
   `android/core/src/main/kotlin/…/core/*.kt` with `*Test.kt` mirrors of
   `tests/unit/*.test.ts` (under `android/core/src/test/kotlin/…/core/`).
   Every algorithm is small and pure — port it and the test together. Keep the
   manifest and CI sync-guard green.
3. **Networking:** Retrofit interface mirroring `docs/api.md`; Bearer auth
   interceptor; 401 → re-login flow; `.` decimal parsing.
4. **Local persistence (Room):** offline-first copy of meals/templates/weights +
   calorie profile/settings. Must survive process death.
5. **Sync engine:** push/pull delta sync, offline write queue, retry with backoff,
   last-write-wins via `updatedAt`.
6. **Screens (Spanish, mirroring web):** login, Hoy/meals (both entry modes),
   peso (weight + chart), estadísticas, ajustes (calorie profile + recommendations),
   and admin user management (scope: confirm with user).
7. **Tests:** unit (core port, Room in-memory), API contract (MockWebServer),
   Compose UI flows. Keep in lockstep with web.
8. **Release/F-Droid:** versioning convention, changelog, F-Droid metadata YAML,
   no GMS.

### Progress (current reality)

- **Steps 1–2 done:** `android/` scaffolded; `:core` port of all algorithms done
  and green (`./gradlew :core:test` = 61 tests). Sync-guard manifest points at
  `:core` test paths.
- **Step 3 (networking) done:** `:app` `data/` layer — wire DTOs, Retrofit
  `ApiService`, `BearerAuthInterceptor`, `JsonConfig` (`.` decimals), and
  `ResponseErrorMapper`. **Step 6 partial:** a minimal **Login → Inicio** flow
  (`LoginViewModel`, `LoginScreen`, `HomeScreen` + `SessionManager`, with
  "Cerrar sesión"/logout) is built and proven against production at
  `https://blackwater-macros.jordanmontt.fr/`. The 401 → re-login flow / token
  persistence is deferred to Room (step 4).
- **Local emulator works:** the Homebrew Android SDK (`/opt/homebrew/
  share/android-commandlinetools`) is used via `android/local.properties`
  (gitignored); the emulator can run `:app` locally for manual testing.
  Base URL is injectable with `-Papp.baseUrl=<url>` (default production).
- **Not yet built:** steps 4, 5, 7 (UI/Room tests), 8, and most of 6.
- **Open design decision (user):** when logging in after local/offline use, what
  happens to locally-entered data (keep-separate vs upload/merge vs discard) —
  to be decided when designing the sync engine (step 5).

### Open items to confirm with the user early

- min SDK / target SDK
- package name (e.g. `com.blackwatermacros.app`)
- include the admin user-management screen on Android, or defer?
- charting library for the weight/body-fat chart (must be F-Droid-safe, no GMS)

---

## 6. Maintenance contract (solo project checklist)

After the backend or either client changes, verify:

- [ ] A `src/lib/core/*.ts` change → **both** `tests/unit/*.test.ts` and the Kotlin
      `*Test.kt` updated (CI sync-guard enforces this).
- [ ] A backend/API change → `docs/api.md`/`docs/api.yaml` and the web route tests
      **and** Android MockWebServer contract tests updated together.
- [ ] A web UI test change that alters behavior → mirrored in the Compose UI test.
- [ ] DB schema change → `db:push` + `tests/helpers/repos.ts` + both client mappings.

Run locally before pushing:
```bash
npm run core:sync-check   # pairing warning
npm test                  # web suite
npx tsc --noEmit && npm run lint
(cd android && ./gradlew test lint build)   # once the app exists
```

CI runs all of these on every PR and **blocks merges** on any drift.
