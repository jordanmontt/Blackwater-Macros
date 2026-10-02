# Blackwater Macros — Technical Guide

Everything a developer needs to modify this codebase with confidence.
For general usage and setup, read [README.md](../README.md) first.

> **This file is the reference.** Architecture, the API contract, tests, the TS ⇄ Kotlin rules
> and the design decisions all live here; code comments do not point to other documents.
> Keep it updated in the same change as the code.
>
> **Branches:** work on `dev` (every push deploys the web); `main` holds released code only
> (F-Droid builds its `vX.Y.Z` tags) — [RELEASING.md](./RELEASING.md). Setup and commands:
> [DEVELOPMENT.md](./DEVELOPMENT.md). Plan for accounts and end-to-end encryption:
> [FDROID-PLAN.md](./FDROID-PLAN.md).

---

## 1. Stack

| Layer | Technology | Notes |
|---|---|---|
| Framework | Next.js **16** (App Router, Turbopack) | Route handlers for the API; static pages |
| UI | React 19 · Tailwind CSS v4 · shadcn/ui v4 | shadcn components are built on **Base UI** (`@base-ui/react`), *not* Radix — see §9 |
| Charts | recharts 3 | All inside client components |
| DB | PostgreSQL (Neon) + **Drizzle ORM** | `postgres.js` driver; schema pushed with drizzle-kit |
| Auth | Custom: scrypt hashes + opaque DB sessions | No external auth service, no public registration |
| Validation | zod v4 | Single source of truth for request payloads |
| Tests | Vitest 4 (+ Testing Library, happy-dom) | Three-project setup, see §11 |

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
      one file per domain (nutrition, stats, calories, ai-providers…) — list in §11.1
    api.ts demo-store.ts demo-api.ts use-demo-mode.ts utils.ts use-mounted.ts
    csv-import.ts           # Reads the export's CSV (and Android's) back: parse, validate, import keys
  i18n/<lang>.json          # ALL user-facing copy, es (the reference) en fr it de; Android reads them too (§6 i18n)
  i18n/<lang>.ts            # Typed wrappers: every language has the shape of es (tests/unit/i18n.test.ts)
  i18n/languages.ts format.ts   # Language list + cookie/Accept-Language choice; locale-aware dates, the one number format
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
as the shared behavioral specification. See §11.1.

**Clients:** the web is a deliberately **thin client** — the backend owns all
CRUD, auth, validation and persistence. Android is **local-first** (its own Room
database, optional account sync — see §14), but it computes with the same `:core`
math and validates with the same limits as `validation.ts`. The main deliberate
duplication in the codebase is this pure, test-pinned math, which exists in both the
TS `lib/core` and the Kotlin `:core` module. That duplication is an accepted
solo-project tradeoff (compiling one shared implementation to WebAssembly / Kotlin
Multiplatform was rejected); it stays safe because the math is pure and each TS spec
has a Kotlin mirror enforced by `core:sync-check`. See §11.1.
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
> (see §5.13, Android integration notes).

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

The wire contract below is authoritative for both clients (web and Android), derived from
the zod schemas in `src/server/validation.ts` and the DTOs in `src/lib/core/types.ts`. All
paths are relative to the deployed origin.

### 5.2 Authentication

The backend supports two transport mechanisms for the **same** opaque session
token (a 32-byte base64url string stored in the `sessions` table).

| Client | Mechanism |
| ------ | --------- |
| Web (browser) | `Set-Cookie: bw_session=<token>` (httpOnly, secure, sameSite=lax) |
| Android / API | `Authorization: Bearer <token>` request header |

Session lifetime: **90 days** per login.

#### Login (get a token)

`POST /api/auth/login`

Request body:
```json
{ "username": "ana", "password": "secret" }
```

Success `200` — returns the token in the JSON body (Android) AND sets the cookie (web):
```json
{ "ok": true, "token": "<opaque-token>", "expiresAt": "2026-12-01T15:14:08.141Z" }
```

Errors:
- `400` invalid JSON: `{ "error": "Cuerpo JSON no válido" }`
- `400` missing fields: `{ "error": "Datos no válidos" }`
- `401` bad credentials: `{ "error": "Usuario o contraseña incorrectos" }`

#### Logout (invalidate a token)

`POST /api/auth/logout`

Sends the token via cookie OR `Authorization: Bearer <token>`. Success `200`:
```json
{ "ok": true }
```
Is idempotent — returning 200 even if the token was already invalid.

#### Session (current user)

`GET /api/auth/session` — auth required (cookie or Bearer).

Success `200`:
```json
{
  "username": "ana",
  "isAdmin": false,
  "calorieProfile": {
    "gender": "male" | "female" | null,
    "birthYear": 1990 | null,
    "heightCm": 178 | null,
    "gymDaysPerWeek": 3 | null,
    "gymSessionMinutes": 60 | null,
    "walkingMinutesPerDay": 30 | null,
    "calorieGoal": "cut" | "maintain" | "surplus" | null
  }
}
```
- `401` not authenticated: `{ "error": "No autenticado" }`

### 5.3 Error convention

- All errors return JSON `{ "error": "<Spanish message>", "code": "<code>" }`, e.g.
  `{ "error": "El peso debe estar entre 20 y 400 kg", "code": "weight_out_of_range" }`.
- Status codes: `400` validation, `401` unauthenticated, `403` forbidden, `404` not found, `409` conflict, `500` internal, `502` Open Food Facts down.
- **Clients show the code, not the text**, in the app language: web `t.serverErrors[code]`
  (`src/lib/server-errors.ts`), Android `server_error_<code>` strings
  (`ResponseErrorMapper.CODES`). The Spanish `error` is the fallback for a code a client does
  not know yet, and what the examples in this document show. The codes are the keys of
  `serverErrors` in `src/i18n/es.json` (Android's `server_error_*` texts are generated from
  them); a test on each side fails if the lists differ.
- Validation errors carry the code of the first problem (`title_required`,
  `weight_out_of_range`, `username_too_short`…), or `invalid_data`.

### 5.4 General conventions

- **Numbers** in the JSON wire format always use `.` as decimal separator (IEEE 754).
  The `,` separator is purely a display concern on each client.
- **Dates:** `YYYY-MM-DD` (e.g. `logDate`) are local calendar days. Timestamps like
  `measuredAt` are ISO-8601 strings.
- **Entry mode:** `per_ingredient` (totals are computed by summing `ingredients`)
  or `total_only` (totals come from the manual `total*` fields).

---

### 5.5 Meals

#### List meals

`GET /api/meals?from=YYYY-MM-DD&to=YYYY-MM-DD` — auth required.

Query params are optional; when omitted all of the user's meals are returned.

Success `200`:
```json
{ "meals": [ MealDTO ] }
```

`MealDTO`:
```json
{
  "id": "uuid",
  "logDate": "2026-06-15",
  "title": "Desayuno",
  "notes": null,
  "entryMode": "per_ingredient",
  "ingredients": [
    { "name": "Avena", "quantity": null, "calories": 150, "protein": 5, "carbs": 27, "fat": 3 }
  ],
  "totalCalories": null,
  "totalProtein": null,
  "totalCarbs": null,
  "totalFat": null,
  "resolvedCalories": 250,
  "resolvedProtein": 8.5,
  "resolvedCarbs": 32,
  "resolvedFat": 10,
  "updatedAt": "2026-06-15T08:00:00.000Z"
}
```

#### Create meal

`POST /api/meals` — auth required.

Request body (`MealInput`):
```json
{
  "logDate": "2026-06-15",
  "title": "Desayuno",
  "notes": null,
  "entryMode": "per_ingredient",
  "ingredients": [
    { "name": "Avena", "quantity": null, "calories": 150, "protein": 5, "carbs": 27, "fat": 3 }
  ],
  "totalCalories": null,
  "totalProtein": null,
  "totalCarbs": null,
  "totalFat": null
}
```
- `logDate` must match `YYYY-MM-DD`.
- `title` required, max 120 chars.
- `ingredients` max 100 entries; each item `name` required (max 200), macros optional and non-negative.
- In `total_only` mode, the `total*` fields supply the resolved totals.

Success `201`:
```json
{ "meal": MealDTO }
```

#### Update meal

`PATCH /api/meals/:id` — auth required.

Body is the same `MealInput` as create (full replace). Success `200`:
```json
{ "meal": MealDTO }
```
Errors: `404` if the meal does not belong to the caller: `{ "error": "Comida no encontrada" }`.

#### Create or replace meal (offline sync)

`PUT /api/meals/:id` — auth required. Used by the Android app, which creates
ids on the phone (UUID) so records logged offline can be uploaded later.

Body is `MealInput` plus an optional `sortOrder` (integer ≥ 0, position within
the day). Creates the meal with that id, or replaces it if it already exists.
**Idempotent**: sending the same request twice leaves exactly one meal. Without
`sortOrder` a new meal goes last in its day and an existing one keeps its
position. Success `200`:
```json
{ "meal": MealDTO }
```
Errors: `400` if `:id` is not a UUID or the body is invalid; `404` if the id
belongs to another user (`{ "error": "Comida no encontrada" }`).

#### Delete meal

`DELETE /api/meals/:id` — auth required. Success `200`:
```json
{ "ok": true }
```
`404` if not owned by caller.

#### Reorder meals

`PATCH /api/meals/reorder` — auth required.

Request body:
```json
{ "orderedIds": ["<uuid>", "<uuid>", ...] }
```
At least one id required. Reorders meals within a single day (drag-and-drop). Success `200`:
```json
{ "ok": true }
```

---

### 5.6 Meal templates

Same endpoints and rules as meals (§5.5) under `/api/templates`, wrapped as `{ "templates": […] }`
/ `{ "template": … }`, without reorder. `MealTemplateDTO` / `TemplateInput` = the meal ones with
a required `name` (max 120) instead of `logDate`. `404`: `{ "error": "Plantilla no encontrada" }`.

---

### 5.7 Weights

#### List weights

`GET /api/weights` — auth required.

Success `200`:
```json
{ "weights": [ WeightDTO ] }
```

`WeightDTO`:
```json
{
  "id": "uuid",
  "measuredAt": "2026-06-15T08:00:00.000Z",
  "weightKg": 80,
  "bodyFatPct": null,
  "note": null,
  "updatedAt": "2026-06-15T08:00:00.000Z"
}
```

#### Create weight

`POST /api/weights` — auth required.

Request body (`WeightInput`):
```json
{
  "measuredAt": "2026-06-15T08:00:00.000Z",
  "weightKg": 80,
  "bodyFatPct": null,
  "note": null
}
```
- `measuredAt` must parse as a valid date.
- `weightKg` range 20–400.
- `bodyFatPct` range 3–60 (nullable).
- `note` max 500 chars.

Success `201`:
```json
{ "weight": WeightDTO }
```

`PATCH` / `PUT` (offline upsert, as for meals) / `DELETE` `/api/weights/:id` work as for
meals: `{ "weight": WeightDTO }` or `{ "ok": true }`; `404`: `{ "error": "Registro no encontrado" }`.

---

### 5.8 Statistics

#### Get stats summary

`GET /api/stats?range=30d&today=YYYY-MM-DD` — auth required.

Query params:
- `range`: `7d` | `30d` | `90d` | `all` (default `30d`).
- `today`: optional local calendar day to anchor the range (defaults server-side).

Success `200` — the full `StatsSummary`:
```json
{
  "calories": [ { "date": "2026-06-15", "calories": 250, "protein": 8.5, "carbs": 32, "fat": 10 } ],
  "protein": [ DailyNutritionPoint ],
  "carbs": [ DailyNutritionPoint ],
  "fat": [ DailyNutritionPoint ],
  "weights": [ { "date": "2026-06-15", "weight": 80, "trend": -0.2 } ],
  "bodyFat": [ { "date": "2026-06-15", "bodyFatPct": 18, "trend": null } ],
  "caloriesAvg": 250,
  "caloriesMaxDay": { "date": "2026-06-15", "calories": 250, "protein": 8.5, "carbs": 32, "fat": 10 },
  "proteinAvg": 8.5,
  "proteinMaxDay": null,
  "carbsAvg": 32,
  "carbsMaxDay": null,
  "fatAvg": 10,
  "fatMaxDay": null,
  "weight": {
    "currentWeightKg": 80,
    "currentTrendKg": -0.2,
    "changeSinceStartKg": -1,
    "ratePerWeekKg": -0.5,
    "minKg": 79,
    "maxKg": 81,
    "currentBodyFatPct": 18,
    "changeBodyFatPct": -0.5,
    "minBodyFatPct": 17,
    "maxBodyFatPct": 19
  },
  "weeklyWeightAvg": [ { "weekStart": "2026-06-08", "avg": 79.5 } ]
}
```

---

### 5.9 Food search

#### Search products (Open Food Facts)

```
GET /api/foods/search?q=yogur%20griego&lang=es
```

Authenticated pass-through to Open Food Facts text search (Search-a-licious does not
allow browser/CORS requests). Stores nothing. `q`: 2–80 characters; `lang`: `es` (default),
`en`, `fr`, `de` or `it` — product names in that language win.

**200**
```json
{
  "products": [
    {
      "code": "8480000592170",
      "name": "Yogur griego natural",
      "brand": "Hacendado",
      "per100g": { "calories": 122, "protein": 3.5, "carbs": 4.2, "fat": 10 },
      "servingGrams": 125,
      "incomplete": false
    }
  ]
}
```

**400** invalid query · **401** not signed in · **502** Open Food Facts unavailable.

Barcode lookups do not use this route: the product API allows browser requests, so the
web calls `https://world.openfoodfacts.org/api/v2/product/<code>.json` directly and
Android calls it with its own User-Agent. Open Food Facts limits searches to about
10/min per IP; the web debounces (450 ms, ≥ 3 characters) and caches per query.

### 5.10 Settings

#### Update calorie profile

`PUT /api/settings` — auth required.

Request body (`CalorieProfileInput`), all fields nullable:
```json
{
  "gender": "male",
  "birthYear": 1990,
  "heightCm": 178,
  "gymDaysPerWeek": 3,
  "gymSessionMinutes": 60,
  "walkingMinutesPerDay": 30,
  "calorieGoal": "cut"
}
```
Constraints:
- `birthYear` 1920–2010
- `heightCm` 100–250
- `gymDaysPerWeek` 0–7
- `gymSessionMinutes` 0–300
- `walkingMinutesPerDay` 0–480

Success `200` returns the updated settings object.

---

### 5.11 Export and import

#### Export CSV

`GET /api/export/meals` — downloads `comidas.csv` (UTF-8 BOM, RFC-4180).
`GET /api/export/weights` — downloads `peso.csv`.

Auth required. Returns a `text/csv` attachment.

#### Import (web)

`POST /api/import/meals` — body `{ "meals": MealInput[] }` (at most 20 000).
`POST /api/import/weights` — body `{ "weights": WeightInput[] }` (at most 20 000).

Auth required. The browser reads the exported CSV itself (`src/lib/csv-import.ts`) and
sends the rows as JSON. The server adds what is new and skips what already exists
(meals: same day, title, mode, ingredients, kcal and protein; weights: same instant and
kilos), so importing a file twice changes nothing. Success `200`:

```json
{ "added": 12, "skipped": 3 }
```

`400` on an invalid body, `404` for another kind. Android imports locally
(`CsvBackup`) and syncs as usual; it does not use this endpoint.

---

### 5.12 Admin (admins only)

Endpoints in the table above; non-admins get `403`. `GET` → `{ "users": [ { "id", "username",
"isAdmin", "createdAt" } ] }`. Create: username min 3, password min 8, `201 { "ok": true }`,
`409` if the username exists. `PATCH` takes any of `username`, `password`, `isAdmin` (at least
one) → `{ "user": AdminUserDTO }`.

---

### 5.13 Android integration notes

Android is local-first and syncs in the background (§14.1): Bearer token from `login`;
`PUT /api/<kind>/:id` with phone-made UUIDs (idempotent, safe to retry) and `DELETE` (a `404`
counts as done); full-list pulls of meals, templates, weights and the session; the profile via
`PUT /api/settings` with **every key present** (unset = `null`). A `401` marks the session
expired and keeps local data. Statistics are computed on the phone, so `/api/stats` is unused.

---

## 6. Frontend architecture

### Pages (all `"use client"` except metodologia/login shell details)

| Page | File | Highlights |
|---|---|---|
| Comidas | `app/page.tsx` | Day navigation (double-click/double-tap the date → today), single daily-totals card, merged calorie + protein recommendations card (average, BMR, TDEE, progress bars), meal list, delete confirm then an Undo toast (`api.restoreMeal`: same id, then the day's order; also `restoreWeight` / `restoreTemplate` in Progreso and Ajustes, and the demo store). The floating + opens `AddFoodSheet`: «Escribir a mano» → `MealForm` (the review form every source ends in; asks «¿Descartar los cambios?» when closed with edits), «Copiar de otro día» and templates create meals directly via `lib/meal-payload.ts` (`copyMealPayload`) with an Undo toast |
| Progreso | `app/progreso/page.tsx` | Weight and statistics on one page (`/peso`, `/estadisticas` redirect in `next.config.ts`). One range selector drives everything: weight card (current, trend, change, rate, body fat) + weight/fat chart; daily calories chart (logged days only) with the target band; «Promedio de macros» over logged days (`macroAverages`, «Días registrados: N de M», kcal split, targets, measured expenditure); weigh-ins of the period with edit/delete; floating add-weight button (`components/weight-form-dialog.tsx`); ⓘ links to /metodologia |
| Ajustes | `app/ajustes/page.tsx` | Theme selector (only place with theme switching), language selector (`components/settings/language-card.tsx`), links to Perfil and to IA, Metodología link, «Tus datos» (CSV export + import), template manager (incl. new-template dialog), «Administración» card for admins, session/logout |
| IA | `app/ajustes/ia/page.tsx` | Ajustes → Inteligencia artificial on its own page, like Android's screen: `AiSettingsCard` (provider, key, model dropdown, model in this browser, «El coach puede ver mis datos»). «Configurar la IA» links from the coach and «Foto o texto» open it directly |
| Perfil | `app/ajustes/perfil/page.tsx` | Goal selector, calorie profile form (debounced autosave with validation) and the calorie/protein recommendations. Nested under `/ajustes` so the Ajustes tab stays active; same split as Android |
| Admin | `app/admin/page.tsx` | Admins only (403 «No tienes permiso…» otherwise): lists users with role badge, create/edit/delete dialogs; guards mirror the service (no self-demote/delete, ≥1 admin) |
| Login | `app/login/page.tsx` | Only reachable when logged out: proxy redirect + `Cache-Control: no-store` + client-side session re-check (see §4.5). Also hosts the «Explora datos de demo» entry (see §6.1) |
| Metodología | `app/metodologia/page.tsx` | Client page (a server component would always render Spanish, see i18n), its tab title set in the request's language by `metodologia/layout.tsx`: every formula (stats, BMR + factorial PAL, protein, measured expenditure) with the reasoning and Crossref-checked citations. Android: `MethodologyScreen.kt`, same content and reference list |

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
  app green (no custom Base UI Select/Switch).

### Numbers on screen and in inputs (web and Android, every language)

One format everywhere, so web and phone show the same characters in every language: **`,` as decimal separator and a narrow no-break space (U+202F) between
thousands from five digits up** — «1,6 g», «2000 kcal», «12 345 kcal».

- **Why the comma:** four of the five app languages (es, fr, it, de) write it, the project
  is Spanish-first and the inputs already used it. **Why the narrow space for thousands:**
  it is the SI / ISO 80000 convention and cannot be misread in any language (an English
  reader takes «2.000» for two, a German reader takes «2,000» for two). Four-digit numbers
  are not grouped, as in Spanish.
- **One function, in the core contract:** `formatDecimal(value, maxDecimals, grouping)`
  in `lib/core/numbers.ts` = Kotlin `Numbers.kt` (mirrored tests, §11.1): rounds like JS
  `Math.round`, drops trailing zeros, never prints «-0». Web `formatNumber`
  (`src/i18n/format.ts`) and Android `formatNumber` (`ui/Format.kt`) call it; dates stay
  in the app language.
- **Inputs:** `normalizeDecimal` (core) turns a typed `.` into `,` on every keystroke —
  web `onChange` of the numeric fields (meal macros and totals, weight, body fat, portion
  grams); Android `CompactField(decimal = true)`, which every number field uses (also for
  keyboards that only offer `.`). `toDecimalInput` hydrates a form with
  `formatDecimal(value, 6, grouping = false)` («8,5», never grouped, so it parses back).
  Parsing accepts either separator (`.replace(",", ".")` on the web, `parseDecimal` on
  Android).
- **Not affected:** the JSON wire format and CSV (`.`, IEEE 754), the text sent to AI
  models (`plainNumber` — an exact-text contract), and the free-text
  quantity field, which holds strings like «30-40 g». The web profile's height/age fields
  are native `type="number"` inputs (whole numbers in practice).

### Timezone strategy

- Days are **date keys** (`YYYY-MM-DD`) computed in the *browser's* local timezone
  (`todayKey()`). For stats, the client sends its `today` so the server anchors
  ranges correctly for each user.
- Weights are **exact instants** (`TIMESTAMPTZ` ISO strings); the Progreso page groups
  them by local day and shows them with `i18n/format.ts` (dates in the active language);
  `datetime-local` values are parsed by `lib/core/dates.ts` (`parseLocalDateTime`).

### i18n

Five languages, on the web and on Android: Spanish (the reference), English, French
(«vous»), Italian and German. **Every text is written once per language** in
`src/i18n/<lang>.json`, long prose (Metodología) included; interpolation via
`formatTemplate(t.key, { n })`. `i18n/<lang>.ts` wrap them: the other languages are typed
`Dictionary` (the shape of `es`), so a missing key fails the build, and
`tests/unit/i18n.test.ts` checks list lengths and `{placeholders}`.

**Android reads the same files.** `android/app/strings-from-web.json` names, for each
Android string shared with the web, its path in the dictionaries and its format arguments in
order (`["progreso.loggedDays", "n:d", "m:d"]` → `Días registrados: %1$d de %2$d`). At build
time `:app:generateWebStrings` (`app/build.gradle.kts`, no Node needed) writes them as
`values*/strings_web.xml` into a generated resource folder; it fails the build if a path or a
`{marker}` is missing. Android-only texts (plurals, screens the web doesn't have, texts worded
for a phone) stay in `res/values*/strings.xml`. A text that should read the same on both:
put it in the JSON and add one map line; never copy it into `strings.xml`.

**Choosing the language.** Ajustes → Idioma writes the `bw-lang` cookie (`es|en|fr|it|de`
or `system`) and reloads. The root layout reads it (else Accept-Language, else Spanish;
`resolveLanguage` in `languages.ts`) and writes `<html lang>`. In the browser,
`i18n/index.ts` reads `<html lang>` once at module load and swaps the shared `t` in place
(`Object.assign`), before any component or module-level constant reads it, so components
keep importing `t` as before.

**Rendering.** The server always renders Spanish. For another language `LanguageGate`
renders nothing on the server and the app after mount, so there is no hydration mismatch
(a blank frame on first paint, then the page in the right language).

**Also per language:** server errors (`t.serverErrors[code]`, §5.3), dates
(`i18n/format.ts`, `LOCALE_TAGS`; numbers are the same in every language, see above), food
search and barcode names (`currentLanguage()` → Open Food Facts `lc`), and the language the
AI answers in (`aiLanguage()`). Stays Spanish on purpose: CSV column names (a file format).

---

## 7. Stats pipeline (`lib/core/stats-builder.ts` + `lib/core/stats.ts`)

The core computation lives in **`lib/core/stats-builder.ts`** as the pure, client-safe
`buildStatsFromData(meals, weights, range, today)` — **shared by the server, demo mode
and Android** (Kotlin port) so their numbers never drift. `server/services/stats-service.ts`
fetches rows through injected repos and calls it; `lib/demo-api.ts` calls it on the demo store.

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
happens only in components via `formatNumber(value, maxDecimals)` from `i18n/format.ts`
(one format in every language; see «Numbers on screen and in inputs» in §6).

---

## 8. Recommendations and measured expenditure (`lib/core/calories.ts`, `protein.ts`, `expenditure.ts`)

All three are pure functions in `lib/core` (ported 1:1 to Kotlin `:core`), computed on the
client from the latest weight, the profile and the logged meals. No server-side
computation. The user-facing explanation, with citations, is the Metodología page
(`i18n/<lang>.json` `metodologia.*`; Android `meth_*` strings) — **change it together with the
math**. Why each formula was chosen, its sources, limits and the rejected alternatives are in
[METHODOLOGY.md](./METHODOLOGY.md); update it in the same change as well.

### 8.1 Calorie target (`calories.ts`)

- **BMR:** Mifflin-St Jeor (1990).
- **PAL (activity factor):** factorial method (FAO/WHO/UNU 2004) from the profile, not a
  self-rated 1–5 scale (self-reported activity is usually above accelerometer data, Prince et
  al. 2008; here the user gives only times and the app fixes the intensities):
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

The reference weight is skipped when a logged body fat is below the one that goes with BMI 25
for the sex and age (`normalBodyFatMax`, Gallagher et al. 2000 Table 4: men 20/22/25 %,
women 33/34/36 % at 20–39/40–59/60+ years; under 20 or no birth year → the 20–39 band): then
the high BMI is muscle. Callers pass the most recent non-null `bodyFatPct` from any weigh-in
and `{ heightCm, gender, age }` from the profile (`age = currentYear − birthYear`, as in
`calories.ts`).
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
- **A code with no usable product** (`core/foods.ts` `barcodeMiss`): the camera stops at the
  first code and stays off (on Android it used to restart and read the same label in a
  loop). The scanner says why: a **shop label** (`isStoreBarcode`: GS1 restricted
  circulation, EAN-13 starting with 2/02/04, UPC-A 2/4, EAN-8 2 — deli, butcher, bakery,
  often with the price or weight inside; no database can have them), a product Open Food
  Facts knows **without nutrition facts**, or an **unknown** code. Then it offers «Buscar
  por nombre», «Hacer una foto» (AI estimate) and «Escanear otro». Open Food Facts is the
  only barcode source: the largest open database (strongest in France and Europe), free
  to reuse (ODbL). The alternatives either have no nutrition (UPCitemdb), cover only the
  US (USDA Branded Foods) or forbid reuse (commercial APIs).
- A picked food + grams becomes one ingredient row (`foodToIngredient`) in the review
  form; inside the form «Buscar alimento» appends more. Recent picks: `localStorage`
  (web) / SharedPreferences (Android), 20 max.

## 8.6 AI providers and keys

**Principles (do not break these).**
1. **Simple.** Not MyFitnessPal: every screen has one job and fits «day of meals →
   recommendations → weight». No streaks, social, badges, micronutrients, recipe database.
2. **Your data stays yours.** Local-first on Android (no account, offline); the web needs login.
   AI features store nothing new on our server.
3. **API keys never leave the device/browser** except to the chosen provider: not synced, not
   sent to the Blackwater server, excluded from Android backups.
4. **Photos are never stored:** kept in memory (or a cache file deleted at once), downscaled,
   sent, discarded — never in the gallery, the database or the server.
5. **Manual entry stays first-class:** both modes (`per_ingredient`, `total_only`) keep working;
   AI, barcode and search only fill the same review form.
6. **F-Droid clean:** only free-software dependencies (no Google Play Services, ML Kit, Firebase).
7. **Web ⇄ Android parity:** pure logic in `src/lib/core` + Kotlin `:core` with mirrored tests (§11.1).

**Decisions:**

| # | Decision |
|---|---|
| D1 | No DB schema change for AI: estimated items are ordinary ingredients; keys and the onboarding flag are local. |
| D2 | Tabs: Comidas · Progreso · Coach · Ajustes (Peso + Estadísticas merged into Progreso). |
| D3 | Coach history is memory only; «Nueva conversación» clears it. |
| D4 | Cloud providers: Gemini (free key), OpenAI, Anthropic, OpenRouter, any OpenAI-compatible server (Ollama, LM Studio); the model is chosen from the provider's own list (see below). |
| D5 | Two selectors in Ajustes → IA: photos and coach each run in the cloud or on the device. |
| D6 | On-device model on Android: LiteRT-LM, downloaded on demand; models without vision are coach-only. |
| D7 | Only open food databases (Ciqual, Swiss FCDB, Open Food Facts), Spanish names added by the project; BEDCA excluded (no reuse licence); every source credited. |
| D8 | Progreso averages over logged days only, with «Días registrados: N de M». |
| D9 | Local AI on the web: a small WebLLM model (chosen from a short list) for the coach, only with WebGPU. |
| D10 | Web keys in that browser's `localStorage`; the browser calls the provider directly. |
| D11 | The coach receives a compact summary of the user's data, toggle «El coach puede ver mis datos» (on by default). |
| D12 | First steps: on a fresh Android install / first web login with an incomplete profile; «Iniciar sesión» first on Android. |

**What the coach sees** (`buildCoachContext`, core, exact text tested): profile, targets, BMR,
TDEE, measured expenditure ± margin; today's meals and what is left to the target; the last
14 days (logged days only) and averages; weight over 60 days (trend, rate, latest body fat); a
30-day weight projection computed by the app (`weightProjection`), which the system prompt tells
the model to use instead of inventing numbers. The prompt asks for short, practical answers in the
app language, no medical claims.

- **Pure part** (`core/ai-providers.ts` = Kotlin `AiProviders.kt`, byte-identical request
  bodies, mirrored tests): `buildAiRequest` for Gemini (`x-goog-api-key`, JSON mode, SSE
  streaming), OpenAI-compatible chat completions (OpenAI, OpenRouter, any `…/v1` server
  such as Ollama/LM Studio; OpenAI gets `max_completion_tokens`, the rest `max_tokens`) and
  Anthropic (`anthropic-dangerous-direct-browser-access` so the browser can call it);
  `parseAiResponse`, `parseAiStreamLine`, `parseAiStreamEnd` (how a stream ended: done,
  token limit, or an error the provider sent inside the stream — Anthropic `type: "error"`,
  `{"error": …}` from Gemini/OpenAI/OpenRouter, a finish reason such as `SAFETY`),
  `aiErrorKind` (Gemini answers a wrong key with 400 + `API_KEY_INVALID`). Default models
  (editable): `gemini-flash-latest`, `gpt-5-mini`, `claude-haiku-4-5`, `openrouter/auto`.
- **Transport**: web `lib/ai/client.ts` (`fetch`, streams read line by line), Android
  `data/ai/AiClient.kt` (OkHttp). Both call the provider **directly**: the Blackwater
  server never sees keys, photos or questions. Errors become `AiFailure` kinds with a
  user message (`t.ai.errors`, `ai_error_*`). A stream must say it is done (`[DONE]`, a
  finish reason, `message_stop`); one that stops early fails *after* the text that arrived:
  `truncated` at the token limit, the provider's own error kind, or `interrupted` when it
  just ends (connection cut). Android streams use `flowOnKeepingItems` (`AiClient.kt`), not
  plain `flowOn`: a failure there cancels the collector before it reads the buffered pieces.
- **Settings** (one key + model per provider, base URL, «El coach puede ver mis datos»):
  web `lib/ai/settings.ts` in `localStorage["bw:ai"]` (per browser), card
  `components/settings/ai-settings-card.tsx` in Ajustes; Android `data/ai/AiSettingsStore.kt`
  in the `ai_settings` prefs with the keys AES-GCM-encrypted by an Android Keystore key,
  the file excluded from backups and device transfer (`res/xml/backup_rules.xml`,
  `data_extraction_rules.xml`); screen Ajustes → Inteligencia artificial (`AiSettingsScreen`).
- **Model dropdown** (Ajustes → IA → Modelo): the list comes from the provider itself
  (`core/ai-models.ts` = Kotlin `AiModels.kt`, mirrored tests): `buildModelListRequest` (Gemini
  `GET /v1beta/models`, OpenAI/OpenRouter/compatible `GET …/models`, Anthropic `GET /v1/models`)
  and `parseModelList` (chat models only — no embeddings, image, speech, live audio or Gemma,
  which has no system instruction —; «…latest»/«auto» first, then newest by version). Cached
  per provider with a hash of key + server (never the key): web `lib/ai/model-list.ts` in
  `localStorage["bw:ai-models"]`, Android `ModelListStore` (prefs `ai_model_lists`). Refreshed
  when the app opens (at most once a day: web `AiModelRefresh` in the layout, Android
  `AppGraph.refreshOnOpen`), when the key changes and with «Actualizar». «Predeterminado» and
  «Otro…» (type a name) stay; without a list it is a text field. The quota error says to
  pick another model (each has its own daily limit).
- Tests never call a real provider: mocked `fetch` (web) / MockWebServer (Android).
- **Photo or text logging** («Foto o texto» in «Añadir comida» — photos, text alone such as a
  label's numbers, or both — and «Estimar “…” con IA» from search):
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
  nor sent. The last 20 good turns go along as history. Cloud answers get
  `COACH_MAX_TOKENS` = 8192 output tokens (the default models reason first, and that hidden
  thinking counts against the budget). An answer that stops early keeps its text in the
  bubble with the reason under it, and is not resent as history. The phone's model reads prompt, history and answer
  in one 4096-token window (`LocalEngine.MAX_TOKENS`): `fitHistoryToBudget` drops the oldest
  turns so 1536 tokens stay free for the answer. The web's browser model answers in at most
  1024 tokens and says so (`truncated`) when it reaches the limit. **Photos** (up to 5 per question,
  camera or gallery, same downscaling as «Foto o texto», memory only): sent with the question and
  again with the history, newest 5 in total; a photo without words asks
  `coach.photoPrompt`. Cloud providers and Android models that read images (Gemma); the
  web's browser model and Qwen are text-only, so the photo button explains that instead.
- **On-device AI** (Android only, D5/D6): a model from the `LocalModels` catalog (Gemma 4 E2B
  default, Gemma 4 E4B, Qwen3 1.7B text-only; Apache-2.0, SHA-256 pinned; one on the phone at
  a time) run by LiteRT-LM (`data/ai/local/`). Downloaded
  on request by a WorkManager foreground job (Wi-Fi by default, resumable, checksum) into
  `noBackupFilesDir/models`. `AiSettings.photoEngine` / `coachEngine` choose cloud or phone;
  `usableEngine` decides readiness. `LocalEngine` loads once and serves one request at a
  time, **on the CPU only** (a Mali GPU keeps a second copy of the weights: Gemma 4 E4B was
  killed for memory on an 8 GB phone). The CPU engine keeps a working copy of the weights in
  `cacheDir` (`<model>…xnnpack_cache`, deleted with the model); the image encoder loads only
  for a request with photos. A loaded model holds ~4 GB, so it is **released** 60 s after
  the last request, on «Nueva conversación» and when the app goes to the background
  (`MainActivity.onStop`, also when the camera opens); reloading takes ~20 s. Needs
  Kotlin ≥ 2.4 (the library's metadata).
- **Catalog of phone models:** `android/catalog/local-models.json`, read from the **`main`**
  branch on GitHub (`BuildConfig.MODEL_CATALOG_URL`, raw.githubusercontent.com), never from
  the Blackwater server. The app asks for it only when Ajustes → IA is opened
  (`LocalModelCatalog.refresh`, at most every 10 min), keeps the last good copy in
  `filesDir` for offline use and merges it with the built-in `LocalModels.BUILT_IN` (the file
  wins for the same id). Entries are validated (download only from huggingface.co, SHA-256,
  size, file name); a wrong one is skipped. `tests/unit/local-models-catalog.test.ts` and
  `LocalModelCatalogTest` check the file.
- **What the catalog cannot do — the engine limit.** The catalog only says *which file to
  download*; the model still runs on the LiteRT-LM engine bundled in the APK
  (`litertlm` in `gradle/libs.versions.toml`). A newer file of a family this engine already
  runs (e.g. another Gemma 4 or Qwen3 size, re-exported by `litert-community`) works straight
  away. A new model family, a new `.litertlm` format version or new features (an audio or
  vision encoder the engine does not know) may need a newer LiteRT-LM, i.e. a new app
  version. For those, give the entry `"minAppVersionCode": <the first versionCode that runs
  it>`: older apps hide it instead of offering a download that would fail. Try a new model on
  a phone with a build of the current app before publishing the entry.
- **Adding a phone model** (no app release needed, within the limit above):
  1. Pick a `.litertlm` file on huggingface.co (open licence, not gated) and copy its
     *resolve* URL (`https://huggingface.co/<org>/<repo>/resolve/main/<file>.litertlm`).
  2. Take its exact size and SHA-256 from the file page on Hugging Face (the LFS details) or
     with `curl -L -o model.litertlm <url> && stat -f%z model.litertlm && shasum -a 256 model.litertlm`.
  3. Add an entry to `android/catalog/local-models.json`: `id` (new, lowercase), `name`, `url`,
     `fileName`, `sizeBytes`, `sha256`, `recommendedPhoneGb` (measure on a phone: the app
     peaks at roughly 1.2× the file size on the CPU), `vision`, `notes` in es/en/fr/it/de,
     and `minAppVersionCode` if needed.
  4. `npm test`, commit on `dev` and bring the commit to `main` (RELEASING.md «Publishing a
     phone AI model»): phones see it the next time Ajustes → IA is opened. To retire a model,
     set `"hidden": true` (phones that have it keep it).
- **Memory:** every model can be downloaded; below `recommendedPhoneGb` (E2B 6, E4B 8, Qwen 4
  GB phones) the download shows a warning. A low-memory kill cannot be caught, so
  `ModelRunGuard` marks each run; if the next start finds the mark and
  `ApplicationExitInfo` says «low memory», the app shows «El teléfono se quedó sin memoria»
  naming the model.
- **Local AI on the web** (D9, Coach only): `lib/ai/browser-model.ts` runs a model from
  `BROWSER_MODELS` with WebLLM on WebGPU (imported lazily, own chunk) — the GPU is the only fast
  way a web page can run a model. Offered: **Qwen3.5 2B** (1.1 GB, recommended, phones too)
  and **Qwen3.5 4B** (2.4 GB, computers); Qwen3 1.7B (the first one) is only recognised in the
  cache so it can be deleted, and 0.8B is not offered (wrong numbers, loops). One model at a
  time, chosen before download (`AiSettings.browserModel`). Ajustes → IA checks `navigator.gpu`
  and `maxStorageBuffersPerShaderStage` (WebLLM needs ≥ 10; Firefox desktop has 9) and says
  why in one line when either is missing. Sampling: temperature 0.3, top_p 0.9,
  frequency_penalty 0.3 (with the defaults small models wander and loop). `withoutThinking()` drops the empty `<think></think>` block
  Qwen writes even with thinking off. The section and the coach's footer (when it runs in the
  browser) say the cloud answers much better. «Usar para el coach: Nube / Este navegador»
  (`AiSettings.coachEngine`); `coach-chat.ts` streams from it; nothing leaves the browser.
- **Install banner** (`components/pwa-install.tsx`): Chromium on Android gets «Instalar»
  (the browser's install prompt); iPhone/iPad have no prompt, so they get the steps (Safari →
  Compartir → «Añadir a pantalla de inicio»), hidden for good once closed.
- **First launch** (D12): web `/bienvenida` (your data → optional Google key → done) after a
  login with an incomplete profile, flag `localStorage["bw:onboarding-done"]`; Android route
  `bienvenida` (welcome: «Empezar», and a quieter «Tengo una cuenta» → login → data → AI →
  done) only on a fresh install (no account, nothing logged), flag
  `AppPreferences.onboardingDone`. After logging in, `afterLogin()` runs the first sync itself
  («Descargando tus datos…», up to 15 s): an account with a complete profile and a weigh-in
  skips «Tus datos»; the AI step always shows (the key is per phone). «Ver
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
Per the root AGENTS.md, consult bundled docs at `node_modules/next/dist/docs/` before
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
- **happy-dom, not jsdom** (avoids jsdom's `require(esm)` chain). CI runs Node 22. On Node
  ≥ 25 run the tests with `NODE_OPTIONS=--no-experimental-webstorage`: Node's own
  `localStorage` global otherwise shadows happy-dom's and the AI/coach tests fail.
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

The Android tests need a JDK 17–23 (CI: 17): Robolectric 4.14 cannot read newer class files.

### 11.1 The TS ⇄ Kotlin core contract (pure math implemented twice)

`src/lib/core/*.ts` holds the pure algorithms (no React, browser APIs, server code, I/O or clock
reads; every function deterministic given its inputs — «now» is passed in). Code outside may
depend on it; it depends on nothing outside. They are implemented **twice**: in TypeScript for
the web/server and as a Kotlin port in the pure-JVM `:core` module (`android/core/`), because
Android is local-first and computes totals, recommendations and statistics on the phone.

`tests/unit/*.test.ts` is the **specification for both**: each assertion has a Kotlin JUnit
mirror with the same inputs and the same expected numbers (reproducing `Math.round` /
`toFixed` exactly).

| Web spec | Covers | Kotlin mirror (`android/core/src/test/…/core/`) |
| -------- | ------ | ---------------- |
| `nutrition.test.ts` | `sumIngredientNutrition`, `resolveMealTotals`, `round1`, `round2` | `NutritionTest.kt` |
| `protein.test.ts` | `calculateProteinRecommendation`, reference weight at BMI 25 | `ProteinTest.kt` |
| `calories.test.ts` | `calculateBMR`, activity (PAL), `calculateCalorieRecommendation` (BMR floor) | `CaloriesTest.kt` |
| `expenditure.test.ts` | `estimateExpenditure`, `fitWeightTrend`, `dailyMeans` | `ExpenditureTest.kt` |
| `foods.test.ts` | portions, Open Food Facts parsing, shop labels and why a barcode missed, `searchGenericFoods` | `FoodsTest.kt` |
| `ai-providers.test.ts` | `buildAiRequest` (byte-identical bodies), answer and stream parsing, how a stream ended (`parseAiStreamEnd`), `aiErrorKind` | `AiProvidersTest.kt` |
| `ai-models.test.ts` | `buildModelListRequest`, `parseModelList`, `naturalCompare` | `AiModelsTest.kt` |
| `ai-schema.test.ts` | `parseMealEstimate`, prompts, `estimateToIngredients` | `AiSchemaTest.kt` |
| `progress.test.ts` | `macroAverages` | `ProgressTest.kt` |
| `numbers.test.ts` | `formatDecimal`, `normalizeDecimal` (the one number format, §6) | `NumbersTest.kt` |
| `coach.test.ts` (+ `coach.fixture.ts`) | `weightProjection`, `buildCoachContext` (exact text), `buildCoachSystemPrompt` | `CoachTest.kt` |
| `dates.test.ts` | date keys, `parseLocalDateTime`, `datetime-local` values | `DatesTest.kt` |
| `stats.test.ts` | moving average, weekly rate, series | `StatsTest.kt` |
| `stats-builder.test.ts` | `buildStatsFromData` | `StatsBuilderTest.kt` |
| `auth-and-csv.test.ts` (CSV part) | `toCsv` | `CsvTest.kt` |

**The rule:** changing a core algorithm means changing the TS implementation, its TS test, the
Kotlin implementation and its Kotlin test **together**. Enforced by `npm run core:sync-check`
(`scripts/check-core-sync.ts`), driven by `android/test-sync/manifest.json` (one row per pair):
it warns locally and **fails CI** when only one side changed; `npm run hooks:install` adds a
pre-commit reminder. Adding or renaming a core domain = updating the manifest and this table.

Porting notes: JSON is read as `kotlinx.serialization.json` element trees (no compiler plugin in
`:core`); regexes run on Android's ICU engine, not the JVM's — avoid JVM-only syntax such as
`(?U)` (crashes on the phone while JVM tests pass); numbers printed into text use
`plainNumber()` so Kotlin writes «80» and «70.3» like JS `String(n)`; types become `data class`
/ `enum class`; dates use `java.time`; clock reads are injected. Display formatting is not in
the core except the one number format (`numbers.ts` / `Numbers.kt`); dates are formatted in the
app language by `i18n/format.ts` / `ui/Format.kt`.
Sharing one implementation (Kotlin Multiplatform / WebAssembly) was rejected: for this much
stable, test-pinned math, duplication is cheaper. Revisit if the core grows a lot.

### 11.2 Android-only tests (`android/app/src/test/`)

`./gradlew :app:testDebugUnitTest` — JVM; Room and Android classes via Robolectric, no emulator.

| Test | What it proves |
| ---- | -------------- |
| `data/OfflineSyncTest.kt` | Real in-memory Room + real Retrofit + `FakeServer` (MockWebServer behaving like the Next.js routes): local-only mode never touches the network, and no request but the login leaves without an account; offline saves upload once, retries never duplicate; edits, deletes, reorders upload; web changes are pulled; an edit during an upload is not lost; profile sync with explicit nulls; expired session keeps data; login with local data; logout and «delete all data» wipe only the phone; undo of deleted meals, weigh-ins and templates (also after the delete reached the server); CSV import de-duplication; an HTML/captive-portal answer fails the sync without losing data |
| `data/ApiContractTest.kt` | Wire format against MockWebServer (mirrors `tests/behavior/routes-*.test.ts`): auth, `PUT /:id` upserts, deletes, settings with explicit nulls, admin; status codes and error envelopes |
| `data/ResponseErrorMapperTest.kt` | Server error codes → the app's `server_error_*` strings; unknown code → the server's text; status fallbacks; the code list equals the web's (`src/i18n/es.json`) |
| `data/CsvBackupTest.kt` | CSV round trip; reads a web export; skips rows the server would reject; unknown files |
| `data/foods/FoodSourcesTest.kt` | Open Food Facts client (barcode, why a code missed, errors, Spanish search, User-Agent); the bundled index loads with Spanish names; recent foods |
| `data/ai/AiClientTest.kt` | AI client against MockWebServer (never a real provider): «Probar», whole and streamed answers, error kinds; a stream cut by the token limit, by an error inside the stream or by the connection fails after the text that arrived, even when the screen reads slowly; keys encrypted per provider; AI prefs excluded from backups |
| `data/ai/ModelListStoreTest.kt` | The model dropdown's list: reused for a day, asked again when old, forced or for another key; survives a restart; never stores the key |
| `data/ai/MealEstimatorTest.kt` | «Foto o texto»: photos + description → one JSON request (mirrors `add-food-photo.test.tsx`); unreadable answers; the language told to the model; photo downscale; the camera FileProvider only reaches the temporary folder |
| `data/ai/local/LocalModelTest.kt` | On-device model: resumable download, SHA-256 check, device support and the low-RAM warning, engine routing, model outside backups, what counts as an out-of-memory kill |
| `data/ai/local/LocalModelCatalogTest.kt` | The site's model catalog: valid entries become models, wrong ones are skipped (non-Hugging Face URL, bad checksum, path tricks, newer app), merge with the built-in list, `hidden`; the committed file matches the built-in models |
| `ui/CoachTest.kt` | Coach (mirrors `coach-page.test.tsx`): streamed answer with the data summary, history until «Nueva conversación», failures not resent, a cut-off answer keeps its text and its reason, the phone model's history fits its window, no data without permission, photos (alone with the default question, and in the history), Markdown |
| `ui/OnboardingTest.kt` | First steps (mirrors `onboarding.test.tsx`): only a fresh install sees them; ranges; «Tus datos» saves profile + weight; after logging in, an account with data skips «Tus datos» |
| `data/NetworkHostsTest.kt` | Every host in the app's code is in the list the README promises (privacy) |
| `ui/ProgressLogicTest.kt` | Progreso: one period for everything, macro averages over logged days, weigh-ins of the period |
| `ui/ValidationTest.kt` | Form limits (same as `src/server/validation.ts`), recommendation states, measured expenditure, sync indicator states, numbers edited with a decimal comma, when «Añadir comida» asks before closing |
| `ui/TranslationsTest.kt` | Every language has every Android-only string and plural with the same placeholders (shared texts: `i18n.test.ts` + the build) |
| `data/NiceTicksTest.kt` | Chart axis ticks |

The server side of sync is covered by `tests/behavior/routes-sync-upsert.test.ts`.

### 11.3 Correspondence with the web suite

Web behavior tests describe user requirements; when one exists on both platforms, both test it.

| Web test | Android counterpart |
| -------- | ------------------- |
| `meal-management`, `nutrition-entry`, `weights-service` | `OfflineSyncTest` + `ValidationTest` |
| `routes-*` (wire contract) | `ApiContractTest` + `FakeServer` |
| `api-client` (error codes in the app language) | `ResponseErrorMapperTest` |
| `csv-export`, `routes-import` | `CsvBackupTest` |
| `stats-overview`, `unit/stats-builder` | `:core` `StatsBuilderTest` |
| `progreso-page` | `ProgressLogicTest` |
| `recommendations-card`, `perfil-page` | `ValidationTest` (`recommend`) |
| `coach-page`, `add-food-photo`, `ai-settings`, `onboarding` | `CoachTest`, `MealEstimatorTest`, `AiClientTest` + `ModelListStoreTest`, `OnboardingTest` |
| `unit/local-models-catalog` | `LocalModelCatalogTest` |
| other `*-page.test.tsx` (screens) | not automated — Compose UI tests are a known gap; screens are checked on a device |

### 11.4 Maintenance checklist

- [ ] `src/lib/core/*.ts` changed → TS test **and** Kotlin implementation + `*Test.kt` (CI enforces).
- [ ] API or server validation changed → §5 of this file, the web route tests,
      `ApiContractTest`/`FakeServer`, and the Android validation (`FormFields.kt`,
      `WeightFormDialog.kt`, `ProfileViewModel.kt`) together. A value the phone accepts but the
      server rejects stays pending forever.
- [ ] New server error → a code in `serverErrors` of the five `src/i18n/*.json`, a map line
      `server_error_<code>` in `android/app/strings-from-web.json` and the code in
      `ResponseErrorMapper.CODES` (tests on both sides fail otherwise).
- [ ] DB schema changed → `db:push`, `tests/helpers/repos.ts`, the Android wire models, Room
      entities **with a Room migration** (users without an account have no other copy).
- [ ] New UI text → the five `src/i18n/*.json`; on Android, one line in `strings-from-web.json`
      (or, for an Android-only text, the five `strings.xml`). See §6 «i18n».
- [ ] Phone-model catalog edited → `npm test` (and `LocalModelCatalogTest`) before pushing.

---

## 12. Deployment topology

```
Browser ── HTTPS ── Vercel (Hobby)
                    ├─ static pages + proxy.ts (edge)
                    ├─ serverless route handlers ── pooled connection ─┐
                    └─ env var DATABASE_URL (Production)               ▼
                                                    Neon Postgres (single project)
```

Vercel deploys the **`dev`** branch (Project → Settings → Git → Production Branch). Current
setup intentionally shares **one Neon database between local dev and production** — simplest mental model, and `create-user` run locally takes
effect immediately on the live site. To split environments later: create a second
Neon project, point Vercel's `DATABASE_URL` at it, run `db:push` + `create-user`
(and `set-admin` for the first admin) against that URL locally.

Gotchas:
- Vercel injects env vars only at deploy time → adding/changing `DATABASE_URL`
  requires a **redeploy**.
- Missing `DATABASE_URL` still builds fine (lazy client) — symptom is runtime 500s
  on every API call while pages render.

---

## 13. Extension recipes

| Want to… | Touch |
|---|---|
| New stats metric | pure helper in `lib/core/stats.ts` (+ unit test) → wire into `lib/core/stats-builder.ts` (shared with server + demo) → **port to Kotlin `:core` with its mirror test** → card/chart in Progreso on web and Android → explain in `/metodologia` (texts in `i18n/<lang>.json`, shared with Android through `strings-from-web.json`) |
| Add user settings | add column to `users` table + enum if needed → `settings-repo.ts` + `settings-service.ts` → `PUT /api/settings` route → Perfil/Ajustes page → read via session endpoint. Android: `ProfileEntity` + migration, `WireCalorieProfile`, `profileBody` (explicit nulls) and the Perfil screen |
| New field on meals/templates/weights (synced) | DB column + zod schema + DTO (web); Android: wire DTO, Room entity **+ `Migration`** (bump `LocalDatabase` version; never destructive: local-only users have no other copy), mappers, `FakeServer`; add a round-trip case to `OfflineSyncTest` |
| New page | `src/app/<slug>/page.tsx`, nav entry in `components/app-nav.tsx`, copy in the five `i18n/*.json`; proxy already protects it. Android: screen + route in `MainActivity`, texts as in «New UI text» |
| New UI text | add it to the five `src/i18n/*.json` (a missing key fails the build; `i18n.test.ts` checks `{placeholders}`). Android: map it in `android/app/strings-from-web.json` (`"key": "path"`, or `["path", "n:d"]` with arguments) and use `stringResource`; Android-only texts and plurals go in the five `res/values*/strings.xml` (`TranslationsTest`) |
| Change the logo | edit `scripts/logo/render.html`, run `scripts/logo/render-icons.sh` (writes every Android and web icon) |
| Release the Android app | see §14.7 (signing + F-Droid metadata are not set up yet) |
| Real migrations | switch from `db:push` to `db:generate` + `db:migrate` (both scripted already) once schema changes risk data loss |

---

## 14. Android app (`android/`)

Native Kotlin/Compose app, meant to be published as free software on F-Droid (no
Google services anywhere). Two Gradle modules. Wire contract: §5 (Android notes in §5.13);
tests and maintenance rules: §11.1–11.4.

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
- **Without an account nothing reaches our server.** `AccountInterceptor` (on every
  `ApiService` call) refuses any request but `POST /api/auth/login` while there is no token
  (`NoAccountException`, before the network; `OfflineSyncTest`). `NetworkHostsTest` lists
  every host the app's code knows; the README's privacy table mirrors it.
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
  needs attention); tapping it opens Ajustes. Deleting a meal, weigh-in or template asks
  first and then offers **Undo** in a snackbar (`offerUndo`; `AppRepository.restoreMeal` /
  `restoreWeight` / `restoreTemplate` put back the same id and position, even if the
  delete already reached the server). Same on the web (toast with «Deshacer»).
- **Server errors** are shown in the app language: `ResponseErrorMapper` turns the
  envelope's `code` into a `server_error_*` string (`UiText`), see §5.3.
- **Statistics** are computed on the phone with `:core` `buildStatsFromData` (same
  numbers as `/api/stats`, which Android does not call).
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
    AccountInterceptor.kt (Bearer token; nothing but login without an account),
    ResponseErrorMapper.kt (admin error messages)
  ui/
    HoyScreen/ViewModel, MealCard           # Comidas: day navigator, totals, recommendations,
                                            #   reorderable meals, undo delete/add
    AddFoodSheet.kt                         # «Añadir comida»: search, barcode, manual, copy, templates
    foods/AddFoodViewModel.kt, FoodViews.kt # search (generic + Open Food Facts), portion, CameraX + zxing-cpp scanner
    MealForm.kt, FormFields.kt              # one meal/template form (MealFormValue, toCopyRequest) +
                                            #   validation, in a GuardedBottomSheet (asks before losing edits)
    NutritionRecommendationsCard.kt, RecommendationsViewModel.kt  # calorie/protein card + intake bars
    ProgressScreen/ViewModel, WeightFormDialog  # Progreso: local stats + weigh-ins (validation = server limits)
    chart/*                                     # Canvas charts (text sizes in sp; TrendChart has a target band)
    SettingsScreen/ViewModel, SettingsComponents.kt  # Ajustes (account, templates, data, theme, language)
    ProfileScreen/ViewModel                 # Perfil: goal, body data, recommendations
    LoginScreen/ViewModel, AdminScreen/ViewModel, MethodologyScreen
    SyncIndicator.kt                        # cloud in the Comidas header (logged in only)
    Format.kt                               # dates (app language) + the one number format + app language (appLocale, setAppLanguage)
    Theme.kt, CenteredTopAppBar.kt, BottomNavBar.kt  # tokens, AppCard, header, bottom bar, AppLogo
```

### 14.3 Languages

English (default, also the fallback for any other phone language), Spanish, French, Italian
and German. Texts shared with the web are generated from `src/i18n/<lang>.json` at build time
(`strings_web.xml`, see §6 «i18n»); Android-only texts live in `res/values*/strings.xml`. The
app follows the phone language; Ajustes → Idioma changes it (`setAppLanguage` in
`ui/Format.kt`, AppCompat per-app locales; Android 13+ also in system settings via
`generateLocaleConfig`). Dates are formatted in the app language, numbers in the one format of
every language (§6). CSV column names stay Spanish (the web's file format). The app label is
«Blackwater Macros».

### 14.4 Look & feel (kept in sync with the web)

- **Palette:** `MainActivity.kt` Light/DarkColors; the web's `globals.css` uses the same
  values. One warm «ember» accent (tertiary) for the floating add button, the selected
  tab pill and an over-target progress fill.
- **Cards:** `AppCard` (surfaceContainer + hairline `outlineVariant` border) everywhere.
- **Text fields:** `CompactField` / `CompactTextArea` — 40 dp high, caret in the primary
  color (the default black caret is invisible in dark mode), fainter placeholders, a
  2 dp primary border on the focused field.
- **Numbers:** one format in every language, the same as the web: «1,6 g», «12 345 kcal»
  (§6 «Numbers on screen and in inputs»). Number fields turn a typed `.` into `,`.
- **Confirmations:** `ConfirmDialog` (Android) / `components/confirm-dialog.tsx` (web) for
  every «delete / discard / log out?» question: title, one text, Cancel, red confirm.
- **Sheets that hold input** (`GuardedBottomSheet`: the meal/template form and «Añadir
  comida»): with nothing typed they close like any sheet (swipe down, tap outside, Back).
  Once something would be lost — form edits or a pre-filled meal; in «Añadir comida» an AI
  description or photos, a search, a typed barcode, ticked meals to copy, a chosen
  portion — the sheet's gestures are off, so scrolling a long form only scrolls it, and
  tapping outside, Back, Cancel or a swipe on the handle all ask «¿Descartar los cambios?».
  Don't veto the swipe with `confirmValueChange` instead (deprecated): the sheet keeps
  following the content's scroll and jumps up and down on long forms.
- **Charts:** hand-drawn Compose Canvas; all text sizes in sp (`AxisTextSize`,
  `TooltipTextSize`) so they follow the phone's font size.
- **Intake bars** (Comidas): outlined track = what is left, solid fill = eaten (ember past
  the target), two markers = target range, «eaten / min–max unit» next to the status.
  The calorie card lists Promedio estimado, TMB, TDEE and (when the data supports it) the
  measured expenditure ± margin before the bar; the protein card shows g/kg (of lean mass
  when a cut uses body fat). Same design on the web (`components/nutrition-recommendations.tsx`).
- **Comidas:** double-tap the date → today; deleting a meal asks first, then offers Undo;
  single-ingredient meals don't repeat the numbers of the totals;
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
- **Toolchain:** `gradle/libs.versions.toml` (AGP 9.4.1 with its built-in Kotlin support — no
  separate `org.jetbrains.kotlin.android` plugin —, Kotlin 2.4.20, Compose BOM, Room,
  WorkManager, AppCompat, Retrofit/OkHttp, kotlinx-serialization). `compileSdk/targetSdk`
  37 (Android 17), `minSdk` 24 (Android 7) + desugaring for `java.time`. Gradle 9.8.0 via the
  wrapper, with `distributionSha256Sum` pinned (AGP 9.4 needs Gradle ≥ 9.6). Android Studio is
  not needed: everything builds from the command line.
- **Local SDK:** `android/local.properties` (`sdk.dir=…`), gitignored.
- **JDK:** 17–23 (21 recommended: `brew install openjdk@21`); both modules compile to Java 17
  bytecode. Newer JDKs build, but Robolectric tests fail on them. Avoid **JDK 21.0.2 on
  Apple Silicon**: a JIT bug crashes Gradle during `lint` (workaround if stuck with it:
  `-Dorg.gradle.jvmargs="-Xmx3g -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=512m"`).

### 14.6 Tests

`./gradlew :core:test :app:testDebugUnitTest` (JVM, Room via Robolectric); what each test
covers: §11.1–11.4. No Compose UI tests yet: screens are checked on a device.

### 14.7 Not done yet (candidates for a next version)

- **Release build:** `./gradlew :app:assembleRelease` → one APK per ABI in
  `app/build/outputs/apk/release/` (arm64 ~15 MB), R8-shrunk. Signed with `keystore.properties`
  (storeFile, storePassword, keyAlias, keyPassword; never committed) when present, else with the
  debug key (fine for sharing test builds, not for a store).
- **Release & F-Droid:** planned step by step in [FDROID-PLAN.md](./FDROID-PLAN.md)
  (repository, licence, catalog from the repository, metadata, signing). Builds shared so far
  are signed with the debug key. Bump `versionCode`/`versionName` for every release.
- **Room migrations:** the local database is at version 1 with `exportSchema = false`.
  Before the first schema change, turn on schema export and write a real `Migration`
  (never `fallbackToDestructiveMigration`: users without an account would lose everything).
- **Incremental sync:** every sync pulls the full lists — fine at personal scale; a
  `?since=` delta endpoint with server tombstones would be needed for large histories.
- **Compose UI tests** for the main flows.
- **Lint warnings left on purpose** (`:app:lintDebug` passes): `IconDuplicates` (the launcher
  icon is the same drawing at every density), `Typos` (false positives: «weigh-in in», German
  «seit dem», «sie sie»), `PluralsCandidate` (the counts are always several: «up to 5 photos»).
- **Target Android 17 (API 37)**: only the local-network rule mattered (see below); `minSdk` 24
  decides who can install. Raise the target for each new Android after reading its «behavior
  changes: apps targeting…» page.
- **«Otro servidor» on Android is https only (a choice):** Android blocks plain
  `http://`, and from Android 17 reaching the home network also needs a runtime permission, so
  a computer at home with an `http://192.168…` address is not reachable from the phone (the
  texts say so). On the web, `http://localhost` (Ollama on the same computer) works; other
  machines need https. Chosen to keep things simple: no new permission, no security exception.
  **To allow a home server later** (e.g. Ollama or LM Studio on a computer on the same Wi-Fi):
  1. *Plain http:* add `res/xml/network_security_config.xml` and point to it with
     `android:networkSecurityConfig` in `AndroidManifest.xml`. Domain rules cannot express IP
     ranges, so either allow cleartext for the whole app (`<base-config
     cleartextTrafficPermitted="true">` — weakens the guarantee that everything else is
     https) or list the host names users will type (`<domain-config
     cleartextTrafficPermitted="true"><domain>ollama.local</domain>…`). A middle way: keep the
     base config https-only and ask users to run the server behind https (Caddy, Tailscale).
  2. *Local network permission (Android 17, targetSdk 37):* declare
     `<uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK" />` and request it
     at runtime, only when the provider is «Otro servidor» and the address is on the home
     network (private IP ranges, `*.local`), before the first request — e.g. from
     `AiSettingsViewModel.runTest`/`refreshModels` and before the coach or photo call. Without
     it the connection fails like being offline.
  3. *Texts:* restore the example `http://192.168.1.10:11434/v1` in `ai_base_url_hint`, the
     placeholder in `AiSettingsScreen.kt`, the provider label «(Ollama, LM Studio…)» and
     `ai_local_hint` («Un modelo en tu propio ordenador es privado…») in the five
     `strings.xml`.
  4. *Test* on a phone with Android 17 against a real Ollama on the same Wi-Fi, and on an older
     phone (the permission does not exist there). The web needs nothing new: a page served over
     https cannot call `http://` machines other than `localhost` (browser rule).
