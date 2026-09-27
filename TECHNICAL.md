# Blackwater Macros — Technical Guide

Everything a developer needs to modify this codebase with confidence.
For general usage and setup, read [README.md](./README.md) first.

> **Work in progress:** the next version (AI food logging, barcode/search, Progreso and Coach
> tabs, onboarding) is planned in [docs/AI-PLAN.md](./docs/AI-PLAN.md). Check its Status table
> before starting any work, and keep it updated.

---

## 1. Stack

| Layer | Technology | Notes |
|---|---|---|
| Framework | Next.js **16** (App Router, Turbopack) | Route handlers for the API; static pages |
| UI | React 19 · Tailwind CSS v4 · shadcn/ui v4 | shadcn components are built on **Base UI** (`@base-ui/react`), *not* Radix — see §8 |
| Charts | recharts 3 | All inside client components |
| DB | PostgreSQL (Neon) + **Drizzle ORM** | `postgres.js` driver; schema pushed with drizzle-kit |
| Auth | Custom: scrypt hashes + opaque DB sessions | No external auth service, no public registration |
| Validation | zod v4 | Single source of truth for request payloads |
| Tests | Vitest 4 (+ Testing Library, happy-dom) | Three-project setup, see §10 |

---

## 2. Directory map

```
src/
  app/                      # App Router: pages (static) + /api routes (serverless)
    page.tsx                # "Comidas" — daily meal log (client component)
    admin/ ajustes/ login/ metodologia/ progreso/
    api/                    # Route handlers; every folder = one endpoint family
      auth/login|logout|session/
      admin/users/
      meals/[id]/ templates/[id]/ weights/[id]/ stats/ settings/ export/[kind]/ import/[kind]/
  proxy.ts                  # Edge gate: redirects to /login without session cookie
  server/                   # Backend-only code (never imported by client)
    db/schema.ts            # Drizzle tables + MealIngredient JSONB type + calorie_goal enum
    db/client.ts            # Lazy postgres pool + drizzle instance (see §4.4)
    repositories/           # Injectable data-access factories (one per table)
    services/               # Pure business logic; no HTTP knowledge
    auth/password.ts        # scrypt hash/verify (pure, unit-tested)
    auth/session.ts         # Token generation, TTL, cookie options (pure)
    composition.ts          # Composition root: real repos wired into serviceDeps
    route-utils.ts          # withUserId()/withAdmin() guards + jsonError()
    api-auth.ts             # getSessionUserIdFromRequest(): cookie OR Bearer header → sessions row → user
    validation.ts           # zod schemas shared by all mutating endpoints
  components/
    ui/*                    # shadcn/ui primitives (Base UI based)
    meals/*                 # DayNavigator, MealCard, MealForm (review form), AddFoodSheet («Añadir comida»)
    ui/sheet.tsx            # Bottom sheet on phones (drag down / outside / Esc closes), dialog from sm
    foods/*                 # FoodSearch, PortionPicker, BarcodeScanner (search / barcode in «Añadir comida»)
    nutrition-recommendations.tsx  # Merged calorie + protein recommendations card (Comidas page)
    weight-fat-chart.tsx          # Combined weight (kg) + body fat (%) chart with trend line
    demo-banner.tsx         # Persistent "demo mode" banner + exit to login
    app-nav.tsx theme-provider.tsx
  lib/                      # Shared pure logic + types (importable from both sides)
    core/                   # PURE algorithms, zero deps — single source of truth, ported to Kotlin
      types.ts dates.ts nutrition.ts stats.ts stats-builder.ts protein.ts calories.ts expenditure.ts csv.ts
    api.ts demo-store.ts demo-api.ts use-demo-mode.ts utils.ts use-mounted.ts
    csv-import.ts           # Reads the export's CSV (and Android's) back: parse, validate, import keys
  i18n/es.ts                # ALL user-facing copy as a typed dictionary (Spanish = the reference)
  i18n/en.ts fr.ts it.ts de.ts  # Same keys, same {placeholders} (tests/unit/i18n.test.ts)
  i18n/languages.ts format.ts   # Language list + cookie/Accept-Language choice; locale-aware dates/numbers
scripts/
  lib/env.ts                # .env/.env.local loader for scripts outside Next runtime
  create-user.ts             # Ops script (tsx): create / reset password
  set-admin.ts               # Ops script (tsx): promote to admin
tests/
  behavior/                 # Black-box tests of USER requirements (Spanish comments)
  unit/                     # Technical edge-case tests of pure functions
drizzle.config.ts           # drizzle-kit config; loads .env.local itself (§10)
android/                    # Native Android app — two Gradle modules (see §14)
  core/                     # :core — pure JVM module, Kotlin port of lib/core + mirror tests
  app/                      # :app — Android module: UI (Compose) + data/networking layer
```

**Dependency rule:** `app/api → composition → services → repositories → client`.
`lib/` is importable by everyone. Client code must never import from `server/`
(except type-only, e.g. tests reuse repo interfaces).

**`lib/core/` stricter rule:** this subdirectory contains *pure algorithms* with
zero side effects — it imports nothing from React, browser APIs, or server code,
and is the single source of truth for business math. Both the web app (TypeScript)
and the Android app (Kotlin port) implement these exact algorithms, with `tests/unit/`
as the shared behavioral specification. See `src/lib/core/README.md`.

**Clients:** the web is a deliberately **thin client** — the backend owns all
CRUD, auth, validation and persistence. Android is **local-first** (its own Room
database, optional account sync — see §14), but it computes with the same `:core`
math and validates with the same limits as `validation.ts`. The main deliberate
duplication in the codebase is this pure, test-pinned math, which exists in both the
TS `lib/core` and the Kotlin `:core` module. That duplication is an accepted
solo-project tradeoff (compiling one shared implementation to WebAssembly / Kotlin
Multiplatform was rejected); it stays safe because the math is pure and each TS spec
has a Kotlin mirror enforced by `core:sync-check`. See `docs/ANDROID-TEST-SPEC.md` §1.
Validation limits are the other duplication (server zod schemas ⇄ Android forms) and
have no automatic guard — change them together.

---

## 3. Data model (`src/server/db/schema.ts`)

Six tables, all UUID-keyed via `gen_random_uuid()`, all user data cascade-deleted
with its owner.

```
users        id, username (unique), password_hash, is_admin (default false),
             gender ENUM gender (male|female),
             birth_year, height_cm, gym_days_per_week,
             gym_session_minutes, walking_minutes_per_day,
             calorie_goal ENUM calorie_goal (cut|maintain|surplus),
             created_at
sessions     token (PK), user_id → users(cascade), expires_at, created_at
             index: sessions_user_id_idx
meals        id, user_id → users(cascade), log_date (DATE 'YYYY-MM-DD'),
             title, notes, entry_mode (ENUM per_ingredient|total_only),
             ingredients JSONB, total_calories, total_protein,
             resolved_calories, resolved_protein,
             created_at, updated_at
             index: meals_user_date_idx(user_id, log_date)
meal_templates  id, user_id → users(cascade), name, title, notes,
             entry_mode (ENUM per_ingredient|total_only), ingredients JSONB,
             total_calories/protein/carbs/fat, resolved_calories/protein/carbs/fat,
             created_at, updated_at
weights      id, user_id → users(cascade), measured_at TIMESTAMPTZ,
             weight_kg DOUBLE PRECISION, body_fat_pct DOUBLE PRECISION (nullable),
             note, created_at, updated_at
             index: weights_user_measured_idx(user_id, measured_at)
```

> **Sync clock (`updated_at`):** meals, templates, and weights all carry an
> `updated_at` TIMESTAMPTZ, set on create and refreshed on every update/reorder.
> It is exposed as `updatedAt` (ISO-8601 UTC) on every DTO and is the single
> authoritative clock for last-write-wins sync between the web app and Android
> (see [`docs/api.md`](./docs/api.md) § Android Integration Notes).

### The two meal entry modes

- **`per_ingredient`**: user enters name/quantity/kcal/protein per ingredient;
  `total_*` columns stay NULL; `resolvedCalories/resolvedProtein` are computed at
  write time (`lib/core/nutrition.ts#resolveMealTotals`) and stored.
- **`total_only`**: user enters one kcal/protein pair; those land in
  `total_calories/total_protein`; resolved columns copy them.

The MealForm defaults **new** meals to `per_ingredient` ("Por ingrediente"); editing keeps
the stored mode. The DB column default (`per_ingredient`) is never relied upon —
the service always writes an explicit value.

**Why persisted `resolved_*` columns:** stats and CSV export become trivial
`SUM(...)` queries over one column instead of re-parsing JSONB on every request.

### MealIngredient (JSONB)

```ts
{ name: string; quantity?: string; calories?: number; protein?: number;
  carbs?: number; fat?: number }
```

Calories, protein, carbs and fat are collected per ingredient (or as totals in
`total_only` mode) and resolved into the `resolved_*` columns on every write.

---

## 4. Backend architecture

### 4.1 Request lifecycle (every authenticated endpoint)

```
fetch /api/meals
  → src/app/api/meals/route.ts        (thin handler)
    → withUserId(request, handler)    route-utils.ts
       ├─ getSessionUserIdFromRequest(request, deps)   api-auth.ts
       │    reads bw_session cookie (async cookies(), Next 16) FIRST
       │    falls back to `Authorization: Bearer <token>` header (Android/API)
       │    looks up sessions row, rejects expired, returns userId
       │    → null ⇒ 401 {"error":"No autenticado"}
       └─ handler(userId)
            ├─ parse body → validation.ts zod schema (ZodError ⇒ 400 first issue)
            ├─ service call (services/*-service.ts)
            │    └─ repository call (repositories/*-repo.ts, always scoped by userId)
            └─ NextResponse.json({ meal | meals | ok … })
```

The session token is transport-agnostic: web sends it as the `bw_session` cookie,
Android/API clients send it as a Bearer header. The same `sessions` row backs both.

Errors: `ZodError` → 400 with the first issue message; anything else → logged +
500 `{"error":"Error interno"}`. Handlers never try/catch manually.

Admin endpoints (`/api/admin/*`) go through `withAdmin(request, ...)` instead: the
same cookie-or-Bearer → session → user lookup, plus a DB role check of
`actor.isAdmin` (non-admins get 403 and handlers receive the acting `UserRow`).
The role is re-checked on every request, so a demotion takes effect immediately
on sessions already open.

### 4.2 Repositories (injectable factories)

Each file exports `createXRepository(db: AppDb): XRepository` returning plain
functions (`listInRange`, `getById`, `create`, `update`, `delete`). The `users`
repo also exposes `findById`, `list`, `update` and `delete` to back the admin UI.
Two reasons:

1. **Every query is forced through a userId parameter** — isolation is structural.
2. **Tests swap them for in-memory Maps** (see `tests/behavior/*.test.ts`) — no DB
   needed to test business rules.

### 4.3 Services (pure logic, no HTTP)

| Service | Responsibility |
|---|---|
| `auth-service` | `login(deps, username, password)` → verify scrypt hash, issue session row + token; `logout` deletes session; `register` (create account, used by the admin UI) |
| `admin-service` | user CRUD behind `/api/admin/*`: list (no secrets), update username/password/role, delete; guards: no self-demote/self-delete, never demote/delete the last admin |
| `meals-service` | create/update/delete/list meals; computes `resolved_*` on every write |
| `templates-service` | CRUD over meal_templates |
| `weights-service` | CRUD over weights (timestamps kept exact, UTC); DTOs include `bodyFatPct` |
| `settings-service` | calorie profile CRUD (read via session endpoint, updated via `PUT /api/settings`) |
| `stats-service` | builds the whole `StatsSummary` DTO including body fat series (see §7) |
| `export-service` | CSV builders using `lib/core/csv.ts` (RFC-escaped, UTF-8 BOM for Excel) |

Services receive their repos via a `deps` argument — production wiring lives only
in `composition.ts`:

```ts
export const serviceDeps = {
  auth:     { users, sessions },
  meals:    { meals },
  templates:{ templates },
  weights:  { weights },
  settings: { settings },
  stats:    { meals, weights },
};
```

To change where data comes from (or fake it), touch exactly this file.

### 4.4 Lazy DB client (`db/client.ts`)

`db` is exported as a Proxy that creates the real postgres/drizzle instance on
first property access. Consequences:

- `next build` succeeds without `DATABASE_URL` (route modules are imported during
  page-data collection); misconfiguration surfaces at first query instead.
- Scripts can set `process.env` programmatically before importing composition
  (`scripts/lib/env.ts` runs before dynamic imports).

Pool sizing: 5 connections in production, 1 in dev. SSL is auto-enabled unless the
URL contains `localhost`/`127.0.0.1`.

### 4.5 Authentication internals

- **Hash format:** `scrypt$<N>$<r>$<p>$<salt-b64>$<hash-b64>` with N=16384, r=8,
  p=1, keylen 64, salt 16 bytes random. Verification parses stored params and uses
  `timingSafeEqual`. `maxmem: 64 MB` is required because OpenSSL's default caps out
  below 128·N·r·p.
- **Sessions:** 32-byte random token (base64url), stored raw in `sessions.token`,
  TTL 90 days (`SESSION_TTL_DAYS`). Web clients receive it as cookie `bw_session`
  (httpOnly + secure (prod) + sameSite lax + path `/`); API/Android clients receive
  the same token in the login JSON body (`{ok, token, expiresAt}`) and send it as
  `Authorization: Bearer <token>`.
- **Two-layer check:** `proxy.ts` performs an optimistic cookie-*presence* redirect
  (cheap edge check, protects pages); every API route independently verifies the
  session against the DB via `withUserId`/`withAdmin` (source of truth). Deleting
  a session row revokes access immediately even if the browser keeps the cookie.
- **Demo cookie:** the edge gate additionally allows pages through when the
  `bw_demo=1` cookie is present (client-side demo mode, see §6.1). This cookie is
  **not** a session — it grants no API access, and on `/login` only a real
  `bw_session` bounces back to `/`, so the demo user can always reach the login
  screen to exit their demo session.
- **Login page hardening:** `/login` is served with `Cache-Control: no-store`
  (`next.config.ts`) so the back/forward cache cannot resurrect it after login,
  and the page re-checks `/api/auth/session` on mount and on `pageshow`
  (`event.persisted` restores bypass the proxy entirely). That check uses raw
  `fetch`, *not* `api.session()` — the global 401 handler would hard-reload /login
  for logged-out visitors.
- **No self-service registration** — accounts are created by admins in the
  `/admin` UI (`POST /api/admin/users`) or via the `create-user`/`set-admin`
  scripts.

---

## 5. API reference

All bodies JSON unless noted. Errors: `{ "error": string }`.

| Method & path | Auth | Purpose |
|---|---|---|
| POST `/api/auth/login` | – | `{username,password}` → sets cookie AND returns `{ok, token, expiresAt}` body; 401 on bad credentials |
| POST `/api/auth/logout` | ✓ | Deletes current session row (cookie or Bearer header) + clears cookie |
| GET `/api/auth/session` | ✓ | `{username, isAdmin, calorieProfile}` for display and settings |
| GET `/api/admin/users` | ✓ + admin | List all users as `{username,isAdmin,createdAt}` (no credentials) |
| POST `/api/admin/users` | ✓ + admin | Create account → `{ok:true}` (201); 409 if username exists |
| PATCH `/api/admin/users/:id` | ✓ + admin | Update username/password/role → `{user}`; guards: no self-demote, ≥1 admin |
| DELETE `/api/admin/users/:id` | ✓ + admin | Delete account → `{ok:true}`; guards: no self-delete, ≥1 admin |
| GET `/api/meals?from&to` | ✓ | Meals in date range (inclusive `YYYY-MM-DD` keys) |
| POST `/api/meals` | ✓ | Create meal (`MealInput`) → `{meal}` |
| PATCH `/api/meals/:id` | ✓ | Update meal (full payload replace) → `{meal}` or 404 |
| PUT `/api/meals/:id` | ✓ | Idempotent create-or-replace with a client UUID (+ optional `sortOrder`) → `{meal}`; 404 if owned by another user. Android offline sync |
| DELETE `/api/meals/:id` | ✓ | Delete → `{ok:true}` or 404 |
| GET/POST `/api/templates`, PATCH/PUT/DELETE `/api/templates/:id` | ✓ | Template management (PUT = idempotent upsert, as for meals) |
| GET/POST `/api/weights`, PATCH/PUT/DELETE `/api/weights/:id` | ✓ | Weight entries (`WeightInput`: ISO timestamp + optional `bodyFatPct`; PUT = idempotent upsert) |
| PUT `/api/settings` | ✓ | Update calorie profile (`CalorieProfile`) → `{calorieProfile}` |
| GET `/api/stats?range=7d\|30d\|90d\|all&today=YYYY-MM-DD` | ✓ | Full `StatsSummary` DTO (weights, body fat, nutrition) |
| GET `/api/export/meals.csv` · `/api/export/weights.csv` | ✓ | CSV download (BOM, es-friendly; weights includes `grasa_corporal_pct`) |
| POST `/api/import/meals` · `/api/import/weights` | ✓ | `{meals: MealInput[]}` / `{weights: WeightInput[]}` (≤ 20 000) → `{added, skipped}`. The browser parses the CSV (`lib/csv-import.ts`); the server adds what is new and skips duplicates (same keys as Android's `CsvBackup`), so importing twice is harmless |

Zod schemas (`src/server/validation.ts`): `mealInputSchema`, `templateInputSchema`,
`weightInputSchema` (includes optional `bodyFatPct`), `calorieProfileInputSchema`
(calorieGoal enum: cut|maintain|surplus), `loginInputSchema`, `registerInputSchema`,
`adminUpdateUserSchema`, `mealUpsertSchema` (meal + optional `sortOrder`), `recordIdSchema`
(UUID path ids for PUT), plus `ingredientInputSchema` reused inside.

Each repository also has `upsert(userId, id, data)`: UPDATE scoped by user, else INSERT
… ON CONFLICT DO NOTHING — so a UUID owned by someone else yields `null` (404), never an
overwrite.

> **Full wire contract** (request/response shapes, auth, error format, Android
> sync protocol) lives in [`docs/api.md`](./docs/api.md). Android tests, the TS ⇄
> Kotlin core contract and the maintenance checklist live in
> [`docs/ANDROID-TEST-SPEC.md`](./docs/ANDROID-TEST-SPEC.md).

---

## 6. Frontend architecture

### Pages (all `"use client"` except metodologia/login shell details)

| Page | File | Highlights |
|---|---|---|
| Comidas | `app/page.tsx` | Day navigation (double-click/double-tap the date → today), single daily-totals card, merged calorie + protein recommendations card (average, BMR, TDEE, progress bars), meal list, delete confirm. The floating + opens `AddFoodSheet`: «Escribir a mano» → `MealForm` (the review form every source ends in; asks «¿Descartar los cambios?» when closed with edits), «Copiar de otro día» and templates create meals directly via `lib/meal-payload.ts` (`copyMealPayload`) with an Undo toast |
| Progreso | `app/progreso/page.tsx` | Peso + Estadísticas merged (old URLs redirect in `next.config.ts`). One range selector drives everything: weight card (current, trend, change, rate, body fat) + weight/fat chart; daily calories chart (logged days only) with the target band; «Promedio de macros» over logged days (`macroAverages`, «N de M días registrados», kcal split, targets, measured expenditure); weigh-ins of the period with edit/delete; floating add-weight button (`components/weight-form-dialog.tsx`); ⓘ links to /metodologia |
| Ajustes | `app/ajustes/page.tsx` | Theme selector (only place with theme switching), language selector (`components/settings/language-card.tsx`), link to Perfil, Metodología link, «Tus datos» (CSV export + import), template manager (incl. new-template dialog), «Administración» card for admins, session/logout |
| Perfil | `app/ajustes/perfil/page.tsx` | Goal selector, calorie profile form (debounced autosave with validation) and the calorie/protein recommendations. Nested under `/ajustes` so the Ajustes tab stays active; same split as Android |
| Admin | `app/admin/page.tsx` | Admins only (403 «No tienes permiso…» otherwise): lists users with role badge, create/edit/delete dialogs; guards mirror the service (no self-demote/delete, ≥1 admin) |
| Login | `app/login/page.tsx` | Only reachable when logged out: proxy redirect + `Cache-Control: no-store` + client-side session re-check (see §4.5). Also hosts the «Explora datos de demo» entry (see §6.1) |
| Metodología | `app/metodologia/page.tsx` | Static page: every formula (stats, BMR + factorial PAL, protein, measured expenditure) with the reasoning and Crossref-checked citations. Android: `MethodologyScreen.kt`, same content and reference list |

### Client data layer (`lib/api.ts`)

Typed fetcher: JSON headers, network-failure → `ApiError(0)`, non-OK → `ApiError(status, serverMessage)`.
A 401 outside the login call throws `ApiError(401)` and dispatches the
`AUTH_EXPIRED_EVENT` custom event; `components/auth-redirect.tsx` listens and
navigates to `/login` (a graceful SPA redirect instead of a hard reload).
`api.session()` swallows failures and resolves to an empty profile. Components
catch errors locally and show `sonner` toasts.

### 6.1 Client-side demo mode (`lib/api.ts` + `lib/demo-store.ts` + `lib/demo-api.ts`)

A **demo mode** lets visitors explore a fully-populated dataset entirely in the
browser, with **no credentials and no database**. Every page/component already
routes data through the single `api` object, so demo mode is implemented as a
drop-in switch inside `lib/api.ts`: when the demo cookie is active, each method
delegates to `lib/demo-api.ts` (a client-side equivalent of the API) backed by
`lib/demo-store.ts` instead of hitting `/api/*`.

- **Entry:** the login screen shows an «Explora datos de demo» card. Clicking it
  calls `enterDemoMode()` (sets the `bw_demo` cookie, see §4.5) and navigates to
  `/`. The dataset is generated once per tab with `buildDemoStore()` — a
  deterministic generator (mulberry32 PRNG, same meal pools,
  ~45 days, weight/body-fat trend) anchored to the **browser's local `todayKey()`**
  so the "Comidas" page is never empty.
- **Isolation & persistence:** demo data lives in **sessionStorage** (`per-tab`,
  survives in-tab reloads) plus the `bw_demo` cookie. Each tab gets its own
  isolated copy; everything resets when the tab/browser closes or when site
  cookies are cleared.
- **Mutability:** users can add/edit/delete meals, weights, and templates, and
  update the calorie profile freely. All writes go through `lib/demo-store.ts`,
  which reuses `resolveMealTotals` (`lib/core/nutrition.ts`) so totals are computed
  identically to the server. Nothing is ever sent to the network.
- **Exit:** a persistent `DemoBanner` (rendered in `layout.tsx` on every app
  page) shows «Estás en modo demo…» with a **«Iniciar sesión»** button that calls
  `exitDemoMode()` (clears the cookie + storage) and returns to `/login`. The
  Ajustes page also exposes the exit action and disables CSV export and import in demo
  mode (those buttons target server endpoints that require a real session).
- **Stats:** `api.stats()` in demo computes the `StatsSummary` via the shared
  `lib/core/stats-builder.ts` (see §7) — the exact same code the server service uses.

Demo mode intentionally grants **no** API access: the `bw_demo` cookie is only an
edge allow-list marker (see §4.5); every API route still requires a real
`bw_session`. Real authentication is completely unchanged.

Demo sessions always report `isAdmin: false`, so the Ajustes «Administración»
card (and therefore the `/admin` page) is unreachable in demo mode.

### Look & feel: native controls, shared palette

- **Palette:** `globals.css` uses the exact colors of the Android theme (`MainActivity.kt`
  Light/DarkColors): forest-green primary, warm paper background, and one warm «ember»
  accent (`--tertiary*`) used only for the floating add buttons and the selected tab pill.
  Chart series follow Android (ember, clay, gold, blue, forest).
- **Native per browser:** the system font stack, the browser's own form controls and
  pickers (`<select>` via `components/ui/native-select.tsx`, a native checkbox with
  `role="switch"` in Admin — Safari 17.4+ draws it as an iOS switch — and
  `datetime-local`), native scrollbars, and `accent-color` so they are tinted with the
  app green. Custom Base UI Select/Switch were removed for this reason.

### Numeric input convention

Every numeric input uses **`,` as decimal separator** (Spanish convention).
`lib/utils.ts` provides the two helpers:

- `normalizeDecimal(value)` — converts typed `.` to `,` on every keystroke;
  wired into `onChange` of all numeric fields (MealForm kcal/protein, meal
  totals, peso weight).
- `toDecimalInput(number)` — formats a stored number with `,` when a form is
  hydrated for editing.

Parsing accepts either separator: forms call `.replace(",", ".")` before
`Number()` (`parseNumber` in MealForm, inline in the peso handler). The
free-text quantity field is exempt — it holds strings like "30-40 g".

### Timezone strategy

- Days are **date keys** (`YYYY-MM-DD`) computed in the *browser's* local timezone
  (`todayKey()`). For stats, the client sends its `today` so the server anchors
  ranges correctly for each user.
- Weights are **exact instants** (`TIMESTAMPTZ` ISO strings); the Progreso page groups
  them by local day via string slice and renders with the active language's formatters
  (`i18n/format.ts`: `formatDateKeyLong`, `formatTimestamp`, `formatNumber`…; parsing stays in
  `lib/core/dates.ts`: `nowDateTimeLocalValue`, `parseLocalDateTime`).

### i18n

Five languages, as on Android: Spanish (`es.ts`, the reference, `as const`), English,
French («vous»), Italian and German. Every visible string lives in them, including long
prose (methodology page). Interpolation via `formatTemplate(t.key, { n })`. The other
dictionaries are typed `Dictionary` (the shape of `es.ts`), so a missing key fails the
build; `tests/unit/i18n.test.ts` also checks list lengths and `{placeholders}`.

**Choosing the language.** Ajustes → Idioma writes the `bw-lang` cookie (`es|en|fr|it|de`
or `system`) and reloads. The root layout reads it (else Accept-Language, else Spanish;
`resolveLanguage` in `languages.ts`) and writes `<html lang>`. In the browser,
`i18n/index.ts` reads `<html lang>` once at module load and swaps the shared `t` in place
(`Object.assign`), before any component or module-level constant reads it, so components
keep importing `t` as before.

**Rendering.** The server always renders Spanish. For another language `LanguageGate`
renders nothing on the server and the app after mount, so there is no hydration mismatch
(a blank frame on first paint, then the page in the right language). Reading cookies
makes the layout dynamic, which it already was in practice (session checks).

**Also per language:** dates and numbers (`i18n/format.ts`, `LOCALE_TAGS`), food search
and barcode names (`currentLanguage()` → Open Food Facts `lc`), and the language the AI
answers in (`aiLanguage()`). Stay Spanish on purpose: CSV column names (a file format) and
server error messages (§14.7).

Adding UI text = add the key to `es.ts` and the four others, then use it.

---

## 7. Stats pipeline (`lib/core/stats-builder.ts` + `lib/core/stats.ts`)

The core computation lives in **`lib/core/stats-builder.ts`** as the pure, client-safe
`buildStatsFromData(meals, weights, range, today)` — **shared by both the server
and demo mode** so their numbers never drift. `server/services/stats-service.ts`
is now a thin adapter: it fetches rows through injected repos (preserving
testability), maps them to `StatsMeal`/`StatsWeight`, and calls the shared
builder. `lib/demo-api.ts` calls the same builder on the local demo store.

Inputs: user's meals in range, all user weights, requested range, client `today`.
Zero-fill: `buildDailyNutritionSeries()` inserts `{calories:0, protein:0}` for
days without meals — gaps mean "did not log", not missing data.

**Body fat:** The weight repository returns full rows including the nullable
`bodyFatPct`. The service filters to entries with valid body fat data (not null,
> 0, < 100) and builds one extra series:

- `bodyFat` — `{ date, bodyFatPct, trend }[]` with a 7-day moving average

The series is an empty array when no entries in the range have body fat data.
The `CompositionStats` DTO includes body fat stats (current/change/min/max),
all null when no body fat data exists. The client renders weight, its trend and
body fat in a single combined chart (`WeightFatChart`, dual Y axes kg / %).

Pure helpers in `lib/core/stats.ts` (unit-tested):

- `movingAverageByDays(points, 7)` — trailing calendar-window average; days without
  entries contribute nothing rather than counting as zero (weights) — used both for
  the weight trend line (server-side) and chart overlays (client-side, rounded to
  1 decimal before render).
- `linearRatePerWeek(points)` — ordinary least squares slope × 7
  (`β = Σ(xi−x̄)(yi−ȳ)/Σ(xi−x̄)²`).
- `weeklyAverages(points)` — Monday-start buckets, arithmetic mean.
- Rounding helpers live in `lib/core/nutrition.ts` (`round1`, `round2`).

The service returns one `StatsSummary` DTO (`lib/core/types.ts`): dense calorie/protein
series, weight/body fat series with pre-rounded trends, summary cards
(avg/max/current/change/rate/min/max) and weekly averages. Display formatting
happens only in components via `formatNumberEs(value, maxDecimals)` (es-ES locale).

---

## 8. Recommendations and measured expenditure (`lib/core/calories.ts`, `protein.ts`, `expenditure.ts`)

All three are pure functions in `lib/core` (ported 1:1 to Kotlin `:core`), computed on the
client from the latest weight, the profile and the logged meals. No server-side
computation. The user-facing explanation, with citations, is the Metodología page
(`i18n/es.ts` `metodologia.*`; Android `meth_*` strings) — **change it together with the
math**.

### 8.1 Calorie target (`calories.ts`)

- **BMR:** Mifflin-St Jeor (1990).
- **PAL (activity factor):** factorial method (FAO/WHO/UNU 2004) from the profile, not a
  self-rated 1–5 scale (self-report overestimates activity, Prince et al. 2008):
  `gym = gymDays × gymMinutes / 7`;
  `PAL = ((1440 − gym − walking) × 1.4 + gym × 4.0 + walking × 3.5) / 1440`.
  1.4 = no-exercise day (low end of FAO's 1.40–1.69 band); 4.0 = resistance training with
  rests (Compendium 2024: 3.5–6 METs); 3.5 = moderate walking. Monotonic: more training
  never lowers it. Profile limits cap it at ≈ 2.64.
- **TDEE** = `round(BMR × PAL)`; `activityFactor` (PAL, 2 decimals) is returned for display.
- **Target:** fixed offsets — cut −400 (−500…−300), maintain ±100, surplus +300 (+200…+400).
  **Floor:** `target`, `targetMin` and `targetMax` never go below the BMR (only reached by a
  cut for small, sedentary people).
- METs are used as multiples of BMR (an approximation: 1 MET is usually a bit above the real
  RMR, Byrne et al. 2005, so exercise energy comes out low — conservative).

### 8.2 Protein (`protein.ts`)

| Goal | g/kg | Basis | Source |
|---|---|---|---|
| Maintain | 1.4–2.0 | body weight | Jäger et al. 2017 (ISSN) |
| Surplus | 1.6–2.2 | body weight | Morton et al. 2018; Iraki et al. 2019 |
| Cut | 1.8–2.7 | body weight | ≈ 2.3–3.1 g/kg FFM at typical body fat |
| Cut, body fat known | 2.3–3.1 | lean mass = weight × (1 − BF%) | Helms et al. 2014 (IJSNEM); Jäger 2017 |
| Any goal, BMI > 25 (not the lean-mass case) | as above | reference weight = 25 × height² | McClave 2016; checked against Kokura 2024 (>1.3 g/kg actual) |

The reference weight is skipped when a logged body fat is normal (< 25 % men, < 33 %
women ≈ BMI 25, Gallagher et al. 2000): then the high BMI is muscle. Callers pass the most
recent non-null `bodyFatPct` from any weigh-in and `{ heightCm, gender }` from the profile.
The result has `basis` (`bodyWeight` | `leanMass` | `referenceWeight`), `basisKg`, `range`
(g/day), `perKg` and `target` (midpoint).

### 8.3 Measured expenditure (`expenditure.ts`)

Energy balance over the **28 days before today** (MacroFactor-style):
`TDEE = mean intake of logged days − β × 7700`, β = least-squares weight slope (kg/day).

- Days without meals (or summing to 0 kcal) are **excluded**, never counted as 0.
- Weigh-ins are reduced to **one point per day** (`dailyMeans`) before the fit: same-day
  weigh-ins are not independent measurements (also for the coach's weight projection).
- Shown only when: ≥ 21 logged days, ≥ 4 weigh-in days spanning ≥ 14 days, and the 95 %
  margin `1.96 × SE(β) × 7700 ≤ 300 kcal/day`, with `SE(β) = σ / √Σ(x − x̄)²` and σ the
  residual SD (n − 2 df), floored at 0.5 kg so a few aligned weigh-ins can't look precise.
  Weekly weigh-ins never qualify; ~3/week do; daily gives ≈ ±180.
- It is displayed **next to** the formula TDEE (home card and Perfil, `± margin`); it
  **never replaces the target**. Perfil explains what is missing when it isn't available.
- Web: `lib/use-measured-expenditure.ts` (meals `meals:<from>:<yesterday>` + `weights`
  cache keys). Android: `recommend(weights, profile, meals, today)` in
  `RecommendationsViewModel.kt`.

### 8.4 Where it is stored and shown

The unified goal (`cut` | `maintain` | `surplus`) is the `calorie_goal` enum column on
`users`, read via the session endpoint and updated via `PUT /api/settings` together with the
rest of the calorie profile.

**Files:**
- `lib/core/calories.ts`, `protein.ts`, `expenditure.ts` — pure calculations
- `components/nutrition-recommendations.tsx` — merged calorie + protein card on "Comidas"
- `app/ajustes/perfil/page.tsx` — goal, profile form and the full breakdown
- `server/repositories/settings-repo.ts`, `server/services/settings-service.ts`,
  `app/api/settings/route.ts` — calorie profile storage

---

## 8.5 Foods: offline generic index, Open Food Facts, barcode

- **Bundled index** `public/foods/generic.json` = Android `assets/foods/generic.json`, built
  by `scripts/foods/build_generic_index.py` (Python stdlib; downloads to
  `scripts/foods/.cache/`) from the Swiss Food Composition Database and CIQUAL 2020
  (baby food and brand mineral waters left out). **Spanish first**: every food has a
  Spanish name from `scripts/foods/names-es.tsv` (reviewable by hand; foods with a
  repeated Spanish name are dropped, Swiss first). Rebuild after editing the TSV.
- **Search** (`core/foods.ts` `searchGenericFoods`): every query word (or its singular, or
  a Spain/Latin-America synonym from `SPANISH_SYNONYMS`) must start a word of the name;
  exact name > starts with > rest, whole-word matches first, app-language names first.
- **Open Food Facts**: web barcode lookups go straight from the browser (the product
  API allows CORS); web text search goes through `GET /api/foods/search` (signed-in
  pass-through; Search-a-licious has no CORS; demo mode has no online search). Android
  calls both directly with a `BlackwaterMacros/<version>` User-Agent
  (`data/foods/OpenFoodFactsClient.kt`). Limits ~10 searches/min/IP → debounce + cache.
- **Barcode**: web uses the browser `BarcodeDetector` or the `barcode-detector` ponyfill
  (zxing-wasm; the `.wasm` is copied to `public/wasm/` by `scripts/copy-zxing-wasm.mjs`
  before dev/build — never loaded from a CDN). Android: CameraX + zxing-cpp
  (`CAMERA` permission asked on first use; typing the code always works). Frames are
  analysed in memory and never stored.
- A picked food + grams becomes one ingredient row (`foodToIngredient`) in the review
  form; inside the form «Buscar alimento» appends more. Recent picks: `localStorage`
  (web) / SharedPreferences (Android), 20 max.

## 8.6 AI providers and keys

- **Pure part** (`core/ai-providers.ts` = Kotlin `AiProviders.kt`, byte-identical request
  bodies, mirrored tests): `buildAiRequest` for Gemini (`x-goog-api-key`, JSON mode, SSE
  streaming), OpenAI-compatible chat completions (OpenAI, OpenRouter, any `…/v1` server
  such as Ollama/LM Studio; OpenAI gets `max_completion_tokens`, the rest `max_tokens`) and
  Anthropic (`anthropic-dangerous-direct-browser-access` so the browser can call it);
  `parseAiResponse`, `parseAiStreamLine`, `aiErrorKind` (Gemini answers a wrong key with
  400 + `API_KEY_INVALID`). Default models (editable): `gemini-flash-latest`,
  `gpt-5-mini`, `claude-haiku-4-5`, `openrouter/auto`.
- **Transport**: web `lib/ai/client.ts` (`fetch`, streams read line by line), Android
  `data/ai/AiClient.kt` (OkHttp). Both call the provider **directly**: the Blackwater
  server never sees keys, photos or questions. Errors become `AiFailure` kinds with a
  user message (`t.ai.errors`, `ai_error_*`).
- **Settings** (one key + model per provider, base URL, «El coach puede ver mis datos»):
  web `lib/ai/settings.ts` in `localStorage["bw:ai"]` (per browser), card
  `components/settings/ai-settings-card.tsx` in Ajustes; Android `data/ai/AiSettingsStore.kt`
  in the `ai_settings` prefs with the keys AES-GCM-encrypted by an Android Keystore key,
  the file excluded from backups and device transfer (`res/xml/backup_rules.xml`,
  `data_extraction_rules.xml`); screen Ajustes → Inteligencia artificial (`AiSettingsScreen`).
- Tests never call a real provider: mocked `fetch` (web) / MockWebServer (Android).
- **Photo logging** («Foto» in «Añadir comida», and «Estimar “…” con IA» from search):
  `buildMealEstimateSystemPrompt` + `buildMealEstimateUserText` (core) → JSON-mode call →
  `parseMealEstimate` → review form pre-filled per ingredient with a notice line
  («Estimación de la IA (confianza media)…»). Photos are downscaled to ≤ 1024 px JPEG 80 %
  in memory (web canvas → data URL; Android `PhotoCodec`) and dropped after the call.
  Android camera photos go through a temporary `cache/ai-photos/` file (FileProvider scoped
  to that folder) deleted right after reading; the folder is also wiped at app start.
- **Coach** (tab `/coach`, Android `CoachScreen`): streamed chat kept in memory only (web
  module state, Android activity-scoped ViewModel). Each question sends
  `buildCoachSystemPrompt(language, buildCoachContext(input))`, the input read fresh from the
  same data as the Comidas card (profile, targets, measured expenditure, 4 weeks of meals,
  60 days of weigh-ins); with «El coach puede ver mis datos» off the data is neither read
  nor sent. The last 20 good turns go along as history.
- **On-device AI** (Android only, D5/D6): a model from the `LocalModels` catalog (Gemma 4 E2B
  default, Gemma 4 E4B, Qwen3 1.7B text-only; Apache-2.0, SHA-256 pinned; one on the phone at
  a time) run by LiteRT-LM (`data/ai/local/`). Downloaded
  on request by a WorkManager foreground job (Wi-Fi by default, resumable, checksum) into
  `noBackupFilesDir/models`. `AiSettings.photoEngine` / `coachEngine` choose cloud or phone;
  `usableEngine` decides readiness. `LocalEngine` loads once (GPU, else CPU; a request that
  fails on GPU is retried on CPU), serves one request at a time, JSON via constrained
  decoding. Needs Kotlin ≥ 2.4 (the library's metadata).
- **Local AI on the web** (D9, Coach only): `lib/ai/browser-model.ts` runs Qwen3 1.7B with
  WebLLM on WebGPU (imported lazily, own chunk). Ajustes → IA → «Modelo en este navegador»
  checks `navigator.gpu` (a one-line reason when missing), downloads ~1 GB into the
  browser cache with progress, and «Usar para el coach: Nube / Este navegador»
  (`AiSettings.coachEngine`). `coach-chat.ts` streams from it instead of the provider;
  nothing leaves the browser.
- **First launch** (D12): web `/bienvenida` (your data → optional Google key → done) after a
  login with an incomplete profile, flag `localStorage["bw:onboarding-done"]`; Android route
  `bienvenida` (welcome with «Iniciar sesión» first → data → AI → done) only on a fresh
  install (no account, nothing logged), flag `AppPreferences.onboardingDone`. «Ver
  tutorial» in Ajustes on both.

---

## 9. React conventions in this repo

ESLint enforces `react-hooks/set-state-in-effect` — no synchronous setState inside
effects. The codebase follows three sanctioned patterns; keep using them:

1. **Loaded-state pattern** (data pages): store `{ day/range, data }` together;
   derive `loading = loaded?.key !== selectedKey`. Effects only `.then(setLoaded)`
   asynchronously. Never clear state synchronously when a parameter changes.
2. **Inner dialog component**: dialogs that need per-open initial values mount an
   inner component *only while open* (`{open ? <FormFields meal={meal}/> : null}`);
   its `useState(() => initFrom(meal))` replaces reset-effects entirely.
3. **Hydration flag**: `useMounted()` (`lib/use-mounted.ts`) wraps
   `useSyncExternalStore` instead of the classic `useEffect(() => setMounted(true))`.

Base UI gotchas (differs from Radix-era shadcn docs):

- No `asChild` — use `render={...}`: `<Button render={<Link href=…/>}>label</Button>`.
- Rendering an anchor inside `Button` requires `nativeButton={false}`.
- Select uses `onValueChange`; `<SelectValue>` renders the **raw value** (e.g. the
  enum string) unless `<Select>` receives an `items` array/record mapping values
  to labels — see `modeItems` in `components/meals/meal-form.tsx`.
- `AlertDialogAction` accepts Button props (`variant="destructive"` works directly).

Next.js 16 specifics honored throughout: `params` in route handlers is a Promise
(`await context.params`), `cookies()` is async, middleware lives in `src/proxy.ts`.
Per AGENTS.md, consult bundled docs at `node_modules/next/dist/docs/` before
assuming an API shape.

---

## 10. Ops scripts

The script loads env files itself (`scripts/lib/env.ts`) **before** importing
server modules (dynamic imports keep ordering safe):

```bash
npm run create-user -- <username> <password>   # idempotent: create / reset password
npm run set-admin -- <username>                # promote existing account to admin
```

Both scripts hit the repo's env DB (currently the shared Neon production
database) — permanent and cross-environment. `create-user` creates a new account
or updates the password hash if it already exists (safe password reset, since the
hashes are salted); `set-admin` flips `is_admin`.

These scripts are the **bootstrap/emergency** path: the first admin account must
exist before the `/admin` UI is reachable, and a lost password can be reset
without logging in. Day-to-day account management happens in the `/admin` UI.

Demo data for testers is now provided entirely by the browser-local demo mode
(see §6.1), so the old `seed`/`delete-user` scripts were removed.

`drizzle.config.ts` duplicates the env-file loader because drizzle-kit does not
read `.env.local` on its own.

---

## 11. Testing architecture (`vitest.config.mts`)

Three projects, one run (`npm test`):

| Project | Environment | Scope |
|---|---|---|
| `unit` | node | Pure-function edge cases (`stats`, `dates`, password vectors, CSV escaping) |
| `behavior` | node | Black-box requirements written in Spanish, exercising **services** through injected in-memory fakes — no HTTP, no DB |
| `behavior-ui` | happy-dom | Renders actual pages and components (`today-page`, `progreso-page`, `ajustes-page`, `perfil-page`, `recommendations-card`, `day-navigator`, …) with mocked `@/lib/api` |

Notes:
- **happy-dom, not jsdom**: Node ≥20.19 supports `require(esm)` but the pinned
  local Node (20.18) does not; happy-dom avoids that chain entirely.
- Demo-mode store tests live in `tests/behavior/demo-mode.test.tsx` (happy-dom,
  because `sessionStorage` + `document.cookie` are needed). The shared stats
  builder is a pure, node-safe unit test in `tests/unit/stats-builder.test.ts`.
- Config file must be `vitest.config.mts` (package has `"type": "module"`).
- The `@/` alias must be declared **inside each project's** `resolve.alias`.
- Behavior-test philosophy: assert user-visible outcomes ("los cambios al editar se
  ven inmediatamente"), never internals. If you add a feature, add its behavior
  test first.

Verification gate before pushing (web, core contract, Android):

```bash
npx tsc --noEmit && npm run lint && npm test && npm run build
npm run core:sync-check
(cd android && ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug)
```

---

## 12. Deployment topology

```
Browser ── HTTPS ── Vercel (Hobby)
                    ├─ static pages + proxy.ts (edge)
                    ├─ serverless route handlers ── pooled connection ─┐
                    └─ env var DATABASE_URL (Production)               ▼
                                                    Neon Postgres (single project)
```

Current setup intentionally shares **one Neon database between local dev and
production** — simplest mental model, and `create-user` run locally takes
effect immediately on the live site. To split environments later: create a second
Neon project, point Vercel's `DATABASE_URL` at it, run `db:push` + `create-user`
(and `set-admin` for the first admin) against that URL locally.

Gotchas learned the hard way:
- Vercel injects env vars only at deploy time → adding/changing `DATABASE_URL`
  requires a **redeploy**.
- Missing `DATABASE_URL` still builds fine (lazy client) — symptom is runtime 500s
  on every API call while pages render.

---

## 13. Extension recipes

| Want to… | Touch |
|---|---|
| New stats metric | pure helper in `lib/core/stats.ts` (+ unit test) → wire into `lib/core/stats-builder.ts` (shared with server + demo) → **port to Kotlin `:core` with its mirror test** → card/chart in Progreso on web and Android → explain in `/metodologia` + `i18n/es.ts` + Android `meth_*` strings |
| Add user settings | add column to `users` table + enum if needed → `settings-repo.ts` + `settings-service.ts` → `PUT /api/settings` route → Perfil/Ajustes page → read via session endpoint. Android: `ProfileEntity` + migration, `WireCalorieProfile`, `profileBody` (explicit nulls) and the Perfil screen |
| New field on meals/templates/weights (synced) | DB column + zod schema + DTO (web); Android: wire DTO, Room entity **+ `Migration`** (bump `LocalDatabase` version; never destructive: local-only users have no other copy), mappers, `FakeServer`; add a round-trip case to `OfflineSyncTest` |
| New page | `src/app/<slug>/page.tsx`, nav entry in `components/app-nav.tsx`, copy in `i18n/es.ts`; proxy already protects it. Android: screen + route in `MainActivity`, strings in the 5 `strings.xml` |
| New Android text | add the key to `res/values/strings.xml` (English) and `values-es/fr/it/de`; use `stringResource` / `pluralStringResource`. `TranslationsTest` checks keys and placeholders |
| New web text | add the key to `i18n/es.ts` **and** `en/fr/it/de.ts` (the build fails otherwise); `tests/unit/i18n.test.ts` checks placeholders |
| Change the logo | edit `scripts/logo/render.html`, run `scripts/logo/render-icons.sh` (writes every Android and web icon) |
| Release the Android app | see §14.7 (signing + F-Droid metadata are not set up yet) |
| Real migrations | switch from `db:push` to `db:generate` + `db:migrate` (both scripted already) once schema changes risk data loss |

---

## 14. Android app (`android/`)

Native Kotlin/Compose app, meant to be published as free software on F-Droid (no
Google services anywhere). Two Gradle modules. Wire contract: `docs/api.md`
(§ Android Integration Notes); tests and maintenance rules: `docs/ANDROID-TEST-SPEC.md`.

### 14.1 Local-first design (the one idea to keep in mind)

There is only one app: the local one. **Every screen reads and writes the Room
database on the phone; nothing in the UI waits for the network.** The account is an
optional add-on that turns on a background sync.

```
Screens (Comidas · Progreso · Ajustes)
        │ read/write — always local, instant
        ▼
AppRepository ── Room (meals, templates, weights, profile)
        │ (only when logged in)
        ▼
SyncEngine ⇄ server          scheduled by WorkManager (runs when online, even if the app is closed)
```

- **No login gate.** The app opens on Comidas. Ajustes → «Cuenta» offers «Iniciar
  sesión» (invite-only accounts) and shows sync status afterwards.
- **Offline.** A save is on disk before the UI returns; with an account the row is
  flagged `pending` and uploaded by the next sync (app start/resume, 2 s after any
  edit, when the network comes back, or «Sincronizar»).
- **Sync = push, then pull.** Push: each pending row → `PUT /api/<kind>/:id`
  (phone-generated UUID, idempotent) or `DELETE` for tombstones (404 = done). Pull:
  full lists replace every non-pending row; rows gone from the server are removed.
  Last to sync wins per record. A push only clears `pending` if the row was not edited
  again meanwhile (`updatedAt` doubles as a strictly increasing local edit stamp).
- **Login with local data** → dialog: upload it to the account, or discard it.
  Re-login after an expired session keeps everything (another account is refused).
- **Logout** → a last sync attempt, a warning if changes would be lost, then all local
  data is deleted (it is safe on the server) and the app is back in local mode.
- **Delete all data** (Ajustes → Tus datos) wipes the phone only, after confirmation.
  Without an account that is permanent; with one, the server is untouched and the next
  sync downloads the account again (unsynced changes are lost — the dialog says how many).
- **Settings split:** Ajustes keeps Cuenta, Plantillas, Tus datos, Apariencia,
  Metodología and Admin; goal, body data and recommendations live in a pushed
  **Perfil** page (`ProfileScreen`), like the web's `/ajustes/perfil`.
- **Comidas header** shows a small cloud when logged in (synced / N pending / syncing /
  needs attention); tapping it opens Ajustes. Deleting a meal is immediate with an
  **Undo** snackbar (`AppRepository.restoreMeal` puts back the same id and position,
  even if the delete already reached the server).
- **Statistics** are computed on the phone with `:core` `buildStatsFromData` (same
  numbers as `/api/stats`, which Android no longer calls).
- **Validation** uses the server's limits (weight 20–400 kg, fat 3–60 %, profile
  ranges) so a record saved offline is never rejected at sync time.

### 14.2 Modules and structure

- **`:core`** — pure JVM Kotlin port of `src/lib/core/*.ts` with `*Test.kt` mirrors of
  `tests/unit/*.test.ts` (`npm run core:sync-check` enforces parity).
- **`:app`** — everything Android.

```
app/src/main/kotlin/com/blackwatermacros/app/
  AppGraph.kt                # service locator + BlackwaterApp (Application) — builds everything once
  MainActivity.kt            # AppCompatActivity (per-app language); theme from AppPreferences;
                             #   NavHost: 4 tabs + login / perfil / metodologia / admin pages;
                             #   requests a sync on every onStart when logged in
  data/
    local/LocalDatabase.kt   # Room entities (pending/deleted flags) + DAOs (version 1)
    local/Mappers.kt         # DTO ⇄ entity, totals via :core resolveMealTotals, nowIso() edit stamp
    AppRepository.kt         # the only data API the screens use (Flows + writes + undo + CSV import)
    AccountStore.kt          # persisted account (token, username, isAdmin, sessionExpired, lastSyncAt)
    AccountController.kt     # verify → connect (upload/discard) → logout / deleteLocalData (wipe)
    AppPreferences.kt        # theme (Sistema/Claro/Oscuro)
    CsvBackup.kt             # CSV export/import in the web export's exact format
    sync/SyncEngine.kt       # push + pull, typed SyncOutcome/SyncProblem, never throws
    sync/SyncScheduler.kt    # SyncScheduler interface + WorkManager implementation + SyncWorker
    ApiService.kt, ApiClient.kt, WireModels.kt, JsonConfig.kt (profileBody: explicit nulls),
    BearerAuthInterceptor.kt, ResponseErrorMapper.kt (admin error messages)
  ui/
    HoyScreen/ViewModel, MealCard           # Comidas: day navigator, totals, recommendations,
                                            #   reorderable meals, undo delete/add
    AddFoodSheet.kt                         # «Añadir comida»: search, barcode, manual, copy, templates
    foods/AddFoodViewModel.kt, FoodViews.kt # search (generic + Open Food Facts), portion, CameraX + zxing-cpp scanner
    MealForm.kt, FormFields.kt              # one meal/template form (MealFormValue, toCopyRequest) +
                                            #   validation; swipe-to-close asks before discarding edits
    NutritionRecommendationsCard.kt, RecommendationsViewModel.kt  # calorie/protein card + intake bars
    ProgressScreen/ViewModel, WeightFormDialog  # Progreso: local stats + weigh-ins (validation = server limits)
    chart/*                                     # Canvas charts (text sizes in sp; TrendChart has a target band)
    SettingsScreen/ViewModel, SettingsComponents.kt  # Ajustes (account, templates, data, theme, language)
    ProfileScreen/ViewModel                 # Perfil: goal, body data, recommendations
    LoginScreen/ViewModel, AdminScreen/ViewModel, MethodologyScreen
    SyncIndicator.kt                        # cloud in the Comidas header (logged in only)
    Format.kt                               # dates/numbers + app language (appLocale, setAppLanguage)
    Theme.kt, CenteredTopAppBar.kt, BottomNavBar.kt  # tokens, AppCard, header, bottom bar, AppLogo
```

### 14.3 Languages

All UI text lives in `res/values*/strings.xml`: English (default, also the fallback for
any other phone language), Spanish, French, Italian and German — edit these XML files
directly. The app follows the phone language; it can also be changed in Ajustes →
Idioma (`setAppLanguage` in `ui/Format.kt`, AndroidX AppCompat per-app locales: the
system per-app setting on Android 13+, stored by AppCompat's
`AppLocalesMetadataHolderService` before) or in Android 13+ system settings
(`generateLocaleConfig`). Dates and numbers are formatted with `ui/Format.kt` in the
app language (not with `:core`'s es-ES formatters). `TranslationsTest` fails if a
language misses a key or a placeholder. CSV column names stay Spanish on purpose
(they are the web's file format). The app label is «Blackwater Macros».

### 14.4 Look & feel (kept in sync with the web)

- **Palette:** `MainActivity.kt` Light/DarkColors; the web's `globals.css` uses the same
  values. One warm «ember» accent (tertiary) for the floating add button, the selected
  tab pill and an over-target progress fill.
- **Cards:** `AppCard` (surfaceContainer + hairline `outlineVariant` border) everywhere.
- **Text fields:** `CompactField` / `CompactTextArea` — 40 dp high, caret in the primary
  color (the default black caret is invisible in dark mode), fainter placeholders, a
  2 dp primary border on the focused field.
- **Charts:** hand-drawn Compose Canvas; all text sizes in sp (`AxisTextSize`,
  `TooltipTextSize`) so they follow the phone's font size.
- **Intake bars** (Comidas): outlined track = what is left, solid fill = eaten (ember past
  the target), two markers = target range, «eaten / min–max unit» next to the status.
  The calorie card lists Promedio estimado, TMB, TDEE and (when the data supports it) the
  measured expenditure ± margin before the bar; the protein card shows g/kg (of lean mass
  when a cut uses body fat). Same design on the web (`components/nutrition-recommendations.tsx`).
- **Comidas:** double-tap the date → today; deleting a meal shows Undo instead of a
  confirmation; single-ingredient meals don't repeat the numbers of the totals;
  ingredient columns have fixed widths that scale with the font size.
- **Logo:** the 20 kg «BW» plate with a steel hub. `scripts/logo/render.html` is the
  single vector source; `scripts/logo/render-icons.sh` renders every size with headless
  Chrome: the Android adaptive icon (foreground = the plate filling the visible area,
  background `ic_launcher_background` = the rim green, so launchers show just the plate;
  monochrome layer for themed icons; round legacy icons), `drawable-nodpi/logo_plate.png`
  (login page), and the web's `public/logo.png`, `src/app/icon.png` and PWA icons
  (maskable = plate in the 80 % safe circle on rim green). `public/logo.svg` is a copy of
  the vector. Tab headers have no logo (same on the web).

### 14.5 Configuration

- **Base URL:** `BuildConfig.API_BASE_URL`, default `https://blackwater-macros.jordanmontt.fr/`,
  override with `-Papp.baseUrl=<url>`. Only used after logging in.
- **Version:** `versionCode 1` / `versionName "0.1.0"` in `app/build.gradle.kts`.
- **Toolchain:** `gradle/libs.versions.toml` (AGP 8.13, Kotlin 2.4.20, Compose BOM, Room,
  WorkManager, AppCompat, Retrofit/OkHttp, kotlinx-serialization). `compileSdk/targetSdk`
  36, `minSdk` 24 + desugaring for `java.time`.
- **Local SDK:** `android/local.properties` (`sdk.dir=…`), gitignored.
- **JDK 21.0.2 on Apple Silicon** has a JIT bug that crashes Gradle during `lint`; use a
  newer JDK or pass `-Dorg.gradle.jvmargs="-Xmx3g -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=512m"`.

### 14.6 Tests

`./gradlew :core:test :app:testDebugUnitTest` — JVM only, Room via Robolectric. What each
test covers, the TS ⇄ Kotlin core contract and the maintenance checklist are in
[`docs/ANDROID-TEST-SPEC.md`](./docs/ANDROID-TEST-SPEC.md). Compose UI tests don't exist
yet; screens are verified manually on an emulator.

### 14.7 Not done yet (candidates for a next version)

- **Release build:** `./gradlew :app:assembleRelease` → one APK per ABI in
  `app/build/outputs/apk/release/` (arm64 ~15 MB), R8-shrunk. Signed with `keystore.properties`
  (storeFile, storePassword, keyAlias, keyPassword; never committed) when present, else with the
  debug key (fine for sharing test builds, not for a store).
- **Release & F-Droid:** no F-Droid metadata
  (`fastlane/metadata/android/…` or an fdroiddata recipe) yet; builds shared so far are
  debug APKs. Bump `versionCode`/`versionName` for every release.
- **Room migrations:** the local database is at version 1 with `exportSchema = false`.
  Before the first schema change, turn on schema export and write a real `Migration`
  (never `fallbackToDestructiveMigration`: users without an account would lose everything).
- **Incremental sync:** every sync pulls the full lists — fine at personal scale; a
  `?since=` delta endpoint with server tombstones would be needed for large histories.
- **Compose UI tests** for the main flows.
- **Server errors:** messages returned by the API (validation, admin) stay in Spanish in every web language.
