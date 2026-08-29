# Blackwater Macros — Technical Guide

Everything a developer needs to modify this codebase with confidence.
For general usage and setup, read [README.md](./README.md) first.

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
    peso/ estadisticas/ ajustes/ login/ metodologia/
    api/                    # Route handlers; every folder = one endpoint family
      auth/login|logout|session/
      meals/[id]/ templates/[id]/ weights/[id]/ stats/ settings/ export/[kind]/
  proxy.ts                  # Edge gate: redirects to /login without session cookie
  server/                   # Backend-only code (never imported by client)
    db/schema.ts            # Drizzle tables + MealIngredient JSONB type + calorie_goal enum
    db/client.ts            # Lazy postgres pool + drizzle instance (see §4.4)
    repositories/           # Injectable data-access factories (one per table)
    services/               # Pure business logic; no HTTP knowledge
    auth/password.ts        # scrypt hash/verify (pure, unit-tested)
    auth/session.ts         # Token generation, TTL, cookie options (pure)
    composition.ts          # Composition root: real repos wired into serviceDeps
    route-utils.ts          # withUserId() guard + jsonError()
    api-auth.ts             # getSessionUserId(): cookie → sessions row → user
    validation.ts           # zod schemas shared by all mutating endpoints
  components/
    ui/*                    # shadcn/ui primitives (Base UI based)
    meals/*                 # DayNavigator, MealCard, MealForm
    nutrition-recommendations.tsx  # Merged calorie + protein recommendations card (Comidas page)
    weight-fat-chart.tsx          # Combined weight (kg) + body fat (%) chart with trend line
    demo-banner.tsx         # Persistent "demo mode" banner + exit to login
    app-nav.tsx theme-provider.tsx
  lib/                      # Shared pure logic + types (importable from both sides)
    types.ts dates.ts nutrition.ts stats.ts stats-builder.ts protein.ts csv.ts api.ts
    demo-store.ts demo-api.ts use-demo-mode.ts utils.ts use-mounted.ts
  i18n/es.ts                # ALL user-facing Spanish copy as a typed dictionary
scripts/
  lib/env.ts                # .env/.env.local loader for scripts outside Next runtime
  create-user.ts             # Ops script (tsx)
tests/
  behavior/                 # Black-box tests of USER requirements (Spanish comments)
  unit/                     # Technical edge-case tests of pure functions
drizzle.config.ts           # drizzle-kit config; loads .env.local itself (§10)
```

**Dependency rule:** `app/api → composition → services → repositories → client`.
`lib/` is importable by everyone. Client code must never import from `server/`
(except type-only, e.g. tests reuse repo interfaces).

---

## 3. Data model (`src/server/db/schema.ts`)

Six tables, all UUID-keyed via `gen_random_uuid()`, all user data cascade-deleted
with its owner.

```
users        id, username (unique), password_hash,
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
             created_at
weights      id, user_id → users(cascade), measured_at TIMESTAMPTZ,
             weight_kg DOUBLE PRECISION, body_fat_pct DOUBLE PRECISION (nullable),
             note, created_at
             index: weights_user_measured_idx(user_id, measured_at)
```

### The two meal entry modes

- **`per_ingredient`**: user enters name/quantity/kcal/protein per ingredient;
  `total_*` columns stay NULL; `resolvedCalories/resolvedProtein` are computed at
  write time (`lib/nutrition.ts#resolveMealTotals`) and stored.
- **`total_only`**: user enters one kcal/protein pair; those land in
  `total_calories/total_protein`; resolved columns copy them.

The MealForm defaults **new** meals to `total_only` ("Solo total"); editing keeps
the stored mode. The DB column default (`per_ingredient`) is never relied upon —
the service always writes an explicit value.

**Why persisted `resolved_*` columns:** stats and CSV export become trivial
`SUM(...)` queries over one column instead of re-parsing JSONB on every request.

### MealIngredient (JSONB)

```ts
{ name: string; quantity?: string; calories?: number; protein?: number;
  carbs?: number; fat?: number }   // carbs/fat reserved, not collected yet
```

Adding future macros requires: schema type already allows it → extend
`ingredientInputSchema` + `resolveMealTotals` → add UI fields. No migration.

---

## 4. Backend architecture

### 4.1 Request lifecycle (every authenticated endpoint)

```
fetch /api/meals
  → src/app/api/meals/route.ts        (thin handler)
    → withUserId(handler)             route-utils.ts
       ├─ getSessionUserId()          api-auth.ts
       │    reads bw_session cookie (async cookies(), Next 16)
       │    looks up sessions row, rejects expired, returns userId
       │    → null ⇒ 401 {"error":"No autenticado"}
       └─ handler(userId)
            ├─ parse body → validation.ts zod schema (ZodError ⇒ 400 first issue)
            ├─ service call (services/*-service.ts)
            │    └─ repository call (repositories/*-repo.ts, always scoped by userId)
            └─ NextResponse.json({ meal | meals | ok … })
```

Errors: `ZodError` → 400 with the first issue message; anything else → logged +
500 `{"error":"Error interno"}`. Handlers never try/catch manually.

### 4.2 Repositories (injectable factories)

Each file exports `createXRepository(db: AppDb): XRepository` returning plain
functions (`listInRange`, `getById`, `create`, `update`, `delete`). Two reasons:

1. **Every query is forced through a userId parameter** — isolation is structural.
2. **Tests swap them for in-memory Maps** (see `tests/behavior/*.test.ts`) — no DB
   needed to test business rules.

### 4.3 Services (pure logic, no HTTP)

| Service | Responsibility |
|---|---|
| `auth-service` | `login(deps, username, password)` → verify scrypt hash, issue session row + token; `logout` deletes session |
| `meals-service` | create/update/delete/list meals; computes `resolved_*` on every write |
| `templates-service` | CRUD over meal_templates |
| `weights-service` | CRUD over weights (timestamps kept exact, UTC); DTOs include `bodyFatPct` |
| `settings-service` | calorie profile CRUD (read via session endpoint, updated via `PUT /api/settings`) |
| `stats-service` | builds the whole `StatsSummary` DTO including body fat series (see §7) |
| `export-service` | CSV builders using `lib/csv.ts` (RFC-escaped, UTF-8 BOM for Excel) |

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
  TTL 90 days (`SESSION_TTL_DAYS`). Cookie `bw_session`:
  httpOnly + secure (prod) + sameSite lax + path `/`.
- **Two-layer check:** `proxy.ts` performs an optimistic cookie-*presence* redirect
  (cheap edge check, protects pages); every API route independently verifies the
  session against the DB via `withUserId` (source of truth). Deleting a session row
  revokes access immediately even if the browser keeps the cookie.
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
- **No registration UI** — accounts exist only via `create-user` script or SQL.

---

## 5. API reference

All bodies JSON unless noted. Errors: `{ "error": string }`.

| Method & path | Auth | Purpose |
|---|---|---|
| POST `/api/auth/login` | – | `{username,password}` → sets cookie, `{ok:true}`; 401 on bad credentials |
| POST `/api/auth/logout` | ✓ | Deletes current session row + clears cookie |
| GET `/api/auth/session` | ✓ | `{username, calorieProfile}` for display and settings |
| GET `/api/meals?from&to` | ✓ | Meals in date range (inclusive `YYYY-MM-DD` keys) |
| POST `/api/meals` | ✓ | Create meal (`MealInput`) → `{meal}` |
| PATCH `/api/meals/:id` | ✓ | Update meal (full payload replace) → `{meal}` or 404 |
| DELETE `/api/meals/:id` | ✓ | Delete → `{ok:true}` or 404 |
| GET/POST `/api/templates`, DELETE `/api/templates/:id` | ✓ | Template management |
| GET/POST `/api/weights`, PATCH/DELETE `/api/weights/:id` | ✓ | Weight entries (`WeightInput`: ISO timestamp + optional `bodyFatPct`) |
| PUT `/api/settings` | ✓ | Update calorie profile (`CalorieProfile`) → `{calorieProfile}` |
| GET `/api/stats?range=7d\|30d\|90d\|all&today=YYYY-MM-DD` | ✓ | Full `StatsSummary` DTO (weights, body fat, nutrition) |
| GET `/api/export/meals.csv` · `/api/export/weights.csv` | ✓ | CSV download (BOM, es-friendly; weights includes `grasa_corporal_pct`) |

Zod schemas (`src/server/validation.ts`): `mealInputSchema`, `templateInputSchema`,
`weightInputSchema` (includes optional `bodyFatPct`), `calorieProfileInputSchema`
(calorieGoal enum: cut|maintain|surplus), `loginInputSchema`, plus
`ingredientInputSchema` reused inside.

---

## 6. Frontend architecture

### Pages (all `"use client"` except metodologia/login shell details)

| Page | File | Highlights |
|---|---|---|
| Comidas | `app/page.tsx` | Day navigation, single daily-totals card, template chips, meal list, MealForm dialog, delete confirm, merged nutrition recommendations card, floating add-meal button |
| Peso | `app/peso/page.tsx` | Current-weight summary (peso actual, grasa actual, cambio grasa 7 días), combined weight+fat chart, entries grouped by day, floating register button |
| Estadísticas | `app/estadisticas/page.tsx` | Range tabs, 6 composition MiniStat cards, combined weight/body fat chart, weekly averages, macro summary + 4 macro trend charts; ⓘ links to /metodologia |
| Ajustes | `app/ajustes/page.tsx` | Unified goal selector (first card), calorie profile form, theme selector (only place with theme switching), CSV export buttons, template manager (incl. new-template dialog), session/logout |
| Login | `app/login/page.tsx` | Only reachable when logged out: proxy redirect + `Cache-Control: no-store` + client-side session re-check (see §4.5). Also hosts the «Explora datos de demo» entry (see §6.1) |
| Metodología | `app/metodologia/page.tsx` | Static content page explaining metric formulas + protein recommendation science + citations |

### Client data layer (`lib/api.ts`)

Typed fetcher: JSON headers, network-failure → `ApiError(0)`, non-OK → `ApiError(status, serverMessage)`.
Any 401 outside the login call triggers a hard `window.location.href = "/login"`
(intentional full reload so all cached client state resets). Components catch
errors locally and show `sonner` toasts.

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
  which reuses `resolveMealTotals` (`lib/nutrition.ts`) so totals are computed
  identically to the server. Nothing is ever sent to the network.
- **Exit:** a persistent `DemoBanner` (rendered in `layout.tsx` on every app
  page) shows «Estás en modo demo…» with a **«Iniciar sesión»** button that calls
  `exitDemoMode()` (clears the cookie + storage) and returns to `/login`. The
  Ajustes page also exposes the exit action and hides the CSV export card in demo
  mode (those buttons target server endpoints that require a real session).
- **Stats:** `api.stats()` in demo computes the `StatsSummary` via the shared
  `lib/stats-builder.ts` (see §7) — the exact same code the server service uses.

Demo mode intentionally grants **no** API access: the `bw_demo` cookie is only an
edge allow-list marker (see §4.5); every API route still requires a real
`bw_session`. Real authentication is completely unchanged.

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
- Weights are **exact instants** (`TIMESTAMPTZ` ISO strings); the peso page groups
  them by local day via string slice and renders with es-ES formatters
  (`lib/dates.ts`: `formatDateKeyLong`, `formatTimestamp`, `nowDateTimeLocalValue`,
  `parseLocalDateTime`).

### i18n

`src/i18n/es.ts` exports a single `t` object (`as const`). Every visible string
lives there, including long prose (methodology page). Interpolation via
`formatTemplate(t.key, { n })` replacing `{n}`. Adding UI text = add key + use it;
the type system flags missing usage sites.

---

## 7. Stats pipeline (`lib/stats-builder.ts` + `lib/stats.ts`)

The core computation lives in **`lib/stats-builder.ts`** as the pure, client-safe
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

Pure helpers in `lib/stats.ts` (unit-tested):

- `movingAverageByDays(points, 7)` — trailing calendar-window average; days without
  entries contribute nothing rather than counting as zero (weights) — used both for
  the weight trend line (server-side) and chart overlays (client-side, rounded to
  1 decimal before render).
- `linearRatePerWeek(points)` — ordinary least squares slope × 7
  (`β = Σ(xi−x̄)(yi−ȳ)/Σ(xi−x̄)²`).
- `weeklyAverages(points)` — Monday-start buckets, arithmetic mean.
- Rounding helpers live in `lib/nutrition.ts` (`round1`, `round2`).

The service returns one `StatsSummary` DTO (`lib/types.ts`): dense calorie/protein
series, weight/body fat series with pre-rounded trends, summary cards
(avg/max/current/change/rate/min/max) and weekly averages. Display formatting
happens only in components via `formatNumberEs(value, maxDecimals)` (es-ES locale).

---

## 8. Protein recommendation (`lib/protein.ts` + `components/nutrition-recommendations.tsx`)

Evidence-based protein intake ranges computed from the user's unified goal, body weight,
and optionally body fat percentage:

| Goal | BW range (g/kg/day) | Source |
|---|---|---|
| Maintain | 1.2–1.6 | ISSN position stand |
| Surplus | 1.6–2.0 | Morton et al. 2018 |
| Cut | 1.6–2.2 | Kokura et al. 2024 |

The unified goal (`cut` | `maintain` | `surplus`) is stored as the `calorie_goal` enum
column on the `users` table and drives both protein recommendations and calorie targets.
The goal is read via the session endpoint and updated via `PUT /api/settings`.
The merged `NutritionRecommendationsCard` component on the "Comidas" page fetches
the latest weight entry (for body weight and body fat %) and the session (for goal),
then calls the pure `calculateCalorieRecommendation()` and
`calculateProteinRecommendation()` functions. No server-side computation — the card
is entirely client-rendered.

**Files:**
- `lib/protein.ts` — pure calculation, no dependencies
- `components/nutrition-recommendations.tsx` — merged calorie + protein card on "Comidas"
- `server/repositories/settings-repo.ts` — calorie profile CRUD on users table
- `server/services/settings-service.ts` — thin service wrapper
- `app/api/settings/route.ts` — `PUT` endpoint
- `app/ajustes/page.tsx` — goal selector (3-button toggle, first card)

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
npm run create-user -- <username> <password>   # idempotent: updates hash if exists
```

`create-user` creates a new account (or updates the password hash if it already
exists). It hits the repo's env DB (currently the shared Neon production
database) — permanent and cross-environment. Real accounts are created only via
this script or direct SQL (there is no registration UI).

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
| `behavior-ui` | happy-dom | Renders actual pages (`today-page.test.tsx`) with mocked `@/lib/api` |

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

Verification gate before pushing:

```bash
npx tsc --noEmit && npm run lint && npm test && npm run build
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
against that URL locally.

Gotchas learned the hard way:
- Vercel injects env vars only at deploy time → adding/changing `DATABASE_URL`
  requires a **redeploy**.
- Missing `DATABASE_URL` still builds fine (lazy client) — symptom is runtime 500s
  on every API call while pages render.

---

## 13. Extension recipes

| Want to… | Touch |
|---|---|
| Add carbs/fat tracking | schema type comment already reserves fields → extend `validation.ts` + `resolveMealTotals` + `MealForm` fields + stats series |
| New stats metric | pure helper in `lib/stats.ts` (+ unit test) → wire into `lib/stats-builder.ts` (shared with server + demo) → card/chart in Estadísticas → explain in `/metodologia` + `i18n/es.ts` |
| Add user settings | add column to `users` table + enum if needed → `settings-repo.ts` + `settings-service.ts` → `PUT /api/settings` route → toggle in Ajustes page → read via session endpoint |
| New page | `src/app/<slug>/page.tsx`, nav entry in `components/app-nav.tsx`, copy in `i18n/es.ts`; proxy already protects it |
| Second language | copy `i18n/es.ts` → `en.ts`, export a dictionary selector (structure is ready, nothing else hardcodes Spanish) |
| Real migrations | switch from `db:push` to `db:generate` + `db:migrate` (both scripted already) once schema changes risk data loss |
