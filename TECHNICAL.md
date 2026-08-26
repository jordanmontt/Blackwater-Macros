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
    page.tsx                # "Hoy" — daily meal log (client component)
    peso/ estadisticas/ ajustes/ login/ metodologia/
    api/                    # Route handlers; every folder = one endpoint family
      auth/login|logout|session/
      meals/[id]/ templates/[id]/ weights/[id]/ stats/ settings/ export/[kind]/
  proxy.ts                  # Edge gate: redirects to /login without session cookie
  server/                   # Backend-only code (never imported by client)
    db/schema.ts            # Drizzle tables + MealIngredient JSONB type + protein_goal enum
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
    meals/*                 # DayNavigator, MealCard, MealForm, SaveTemplateDialog
    protein-recommendation.tsx  # Protein intake recommendation card (Hoy page)
    app-nav.tsx theme-provider.tsx theme-toggle.tsx
  lib/                      # Shared pure logic + types (importable from both sides)
    types.ts dates.ts nutrition.ts stats.ts protein.ts csv.ts api.ts utils.ts use-mounted.ts
  i18n/es.ts                # ALL user-facing Spanish copy as a typed dictionary
scripts/
  lib/env.ts                # .env/.env.local loader for scripts outside Next runtime
  create-user.ts seed.ts    # Ops scripts (tsx)
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
             protein_goal ENUM protein_goal (maintain|build|cut) NOT NULL DEFAULT 'build',
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
             ingredients JSONB, created_at
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
| `settings-service` | protein goal CRUD (read via session endpoint, updated via `PUT /api/settings`) |
| `stats-service` | builds the whole `StatsSummary` DTO including body fat and lean mass series (see §7) |
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
| GET `/api/auth/session` | ✓ | `{username, proteinGoal}` for display and settings |
| GET `/api/meals?from&to` | ✓ | Meals in date range (inclusive `YYYY-MM-DD` keys) |
| POST `/api/meals` | ✓ | Create meal (`MealInput`) → `{meal}` |
| PATCH `/api/meals/:id` | ✓ | Update meal (full payload replace) → `{meal}` or 404 |
| DELETE `/api/meals/:id` | ✓ | Delete → `{ok:true}` or 404 |
| GET/POST `/api/templates`, DELETE `/api/templates/:id` | ✓ | Template management |
| GET/POST `/api/weights`, PATCH/DELETE `/api/weights/:id` | ✓ | Weight entries (`WeightInput`: ISO timestamp + optional `bodyFatPct`) |
| PUT `/api/settings` | ✓ | Update protein goal (`{proteinGoal}`) → `{proteinGoal}` |
| GET `/api/stats?range=7d\|30d\|90d\|all&today=YYYY-MM-DD` | ✓ | Full `StatsSummary` DTO (weights, body fat, lean mass, nutrition) |
| GET `/api/export/meals.csv` · `/api/export/weights.csv` | ✓ | CSV download (BOM, es-friendly; weights includes `grasa_corporal_pct`) |

Zod schemas (`src/server/validation.ts`): `mealInputSchema`, `templateInputSchema`,
`weightInputSchema` (includes optional `bodyFatPct`), `settingsInputSchema`
(proteinGoal enum), `loginInputSchema`, plus `ingredientInputSchema` reused inside.

---

## 6. Frontend architecture

### Pages (all `"use client"` except metodologia/login shell details)

| Page | File | Highlights |
|---|---|---|
| Hoy | `app/page.tsx` | Day navigation, totals cards, template chips, meal list, MealForm dialog, delete confirm, protein recommendation card |
| Peso | `app/peso/page.tsx` | Current-weight card, body fat input field, entries grouped by day with body fat display, body fat history chart |
| Estadísticas | `app/estadisticas/page.tsx` | Range tabs, MiniStat cards, weight/body fat/lean mass charts, weekly averages; ⓘ links to /metodologia |
| Ajustes | `app/ajustes/page.tsx` | Protein goal selector, theme selector, CSV export buttons, template manager, session/logout |
| Login | `app/login/page.tsx` | Only reachable when logged out: proxy redirect + `Cache-Control: no-store` + client-side session re-check (see §4.5) |
| Metodología | `app/metodologia/page.tsx` | Static content page explaining metric formulas + protein recommendation science + citations |

### Client data layer (`lib/api.ts`)

Typed fetcher: JSON headers, network-failure → `ApiError(0)`, non-OK → `ApiError(status, serverMessage)`.
Any 401 outside the login call triggers a hard `window.location.href = "/login"`
(intentional full reload so all cached client state resets). Components catch
errors locally and show `sonner` toasts.

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

## 7. Stats pipeline (`server/services/stats-service.ts` + `lib/stats.ts`)

Inputs: user's meals in range, all user weights, requested range, client `today`.
Zero-fill: `buildDailyNutritionSeries()` inserts `{calories:0, protein:0}` for
days without meals — gaps mean "did not log", not missing data.

**Body fat and lean mass:** The weight repository returns full rows including the
nullable `bodyFatPct`. The service filters to entries with valid body fat data
(not null, > 0, < 100) and builds two additional series:

- `bodyFat` — `{ date, bodyFatPct, trend }[]` with a 7-day moving average
- `leanMass` — `{ date, leanMassKg, trend }[]` derived as `weight × (1 - bodyFatPct/100)`

Both series are empty arrays when no entries in the range have body fat data.
The `WeightStatsSummary` DTO includes body fat stats (current/change/min/max)
and lean mass stats (current/change), all null when no body fat data exists.

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
series, weight/body fat/lean mass series with pre-rounded trends, summary cards
(avg/max/current/change/rate/min/max) and weekly averages. Display formatting
happens only in components via `formatNumberEs(value, maxDecimals)` (es-ES locale).

---

## 8. Protein recommendation (`lib/protein.ts` + `components/protein-recommendation.tsx`)

Evidence-based protein intake ranges computed from the user's goal, body weight,
and optionally body fat percentage:

| Goal | BW range (g/kg/day) | Source |
|---|---|---|
| Maintain | 1.2–1.6 | ISSN position stand |
| Build | 1.6–2.0 | Morton et al. 2018 |
| Cut | 1.6–2.2 | Kokura et al. 2024 |

When goal is "cut" **and** body fat % is available, an additional FFM range is
shown: 2.3–3.1 g/kg lean mass (Helms et al.), relevant for lean athletes in
a deficit.

**Data flow:** The user's protein goal is stored as a `protein_goal` enum column
on the `users` table (default `"build"`). The goal is read via the session
endpoint and updated via `PUT /api/settings`. The `ProteinRecommendationCard`
component on the Hoy page fetches the latest weight entry (for body weight and
body fat %) and the session (for goal), then calls the pure `calculateProteinRecommendation()`
function. No server-side computation — the card is entirely client-rendered.

**Files:**
- `lib/protein.ts` — pure calculation, no dependencies
- `components/protein-recommendation.tsx` — Hoy page card with progress bar
- `server/repositories/settings-repo.ts` — protein goal CRUD on users table
- `server/services/settings-service.ts` — thin service wrapper
- `app/api/settings/route.ts` — `PUT` endpoint
- `app/ajustes/page.tsx` — goal selector (3-button toggle)

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

Both scripts load env files themselves (`scripts/lib/env.ts`) **before** importing
server modules (dynamic imports keep ordering safe):

```bash
npm run create-user -- <username> <password>   # idempotent: updates hash if exists
npm run seed [-- <username> <password>]        # default demo/demo1234
```

Seed details: deterministic PRNG (mulberry32) so output is reproducible; ~45 days
of meals in both entry modes with realistic jitter; weights trending down with
noise and occasional twice-a-day entries; 2 starter templates. Idempotency: it
deletes only the target user (cascade removes their data) then recreates — other
accounts are untouched, so running it against production refreshes the demo
without touching personal accounts. Because the 45-day window is relative to
*now*, re-running shifts the dataset forward ("fresh" demo any time).
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
production** — simplest mental model, and `seed`/`create-user` run locally take
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
| New stats metric | pure helper in `lib/stats.ts` (+ unit test) → wire into `stats-service` DTO → card/chart in Estadísticas → explain in `/metodologia` + `i18n/es.ts` |
| Add user settings | add column to `users` table + enum if needed → `settings-repo.ts` + `settings-service.ts` → `PUT /api/settings` route → toggle in Ajustes page → read via session endpoint |
| New page | `src/app/<slug>/page.tsx`, nav entry in `components/app-nav.tsx`, copy in `i18n/es.ts`; proxy already protects it |
| Second language | copy `i18n/es.ts` → `en.ts`, export a dictionary selector (structure is ready, nothing else hardcodes Spanish) |
| Real migrations | switch from `db:push` to `db:generate` + `db:migrate` (both scripted already) once schema changes risk data loss |
