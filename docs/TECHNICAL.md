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
| Tests | Vitest 5 (+ Testing Library, happy-dom) | Three-project setup, see §11 |

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
      meals/[id]/ templates/[id]/ weights/[id]/ stats/ settings/ export/
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
    foods/*                 # FoodSearch, PortionPicker, BarcodeScanner (search / barcode in «Añadir comida»),
                            #   NewProductForm («Añadir este producto» / «Corregir valores» from the label, §8.5),
                            #   LabelPhotoViewer (the label photo at full size, with zoom)
    own-product-uploads.tsx # Sends the products added from a label when the app opens / comes back online
    nutrition-recommendations.tsx  # Merged calorie + protein recommendations card (Comidas page)
    weight-fat-chart.tsx          # Combined weight (kg) + body fat (%) chart with trend line
    demo-banner.tsx         # Persistent "demo mode" banner + exit to login
    page-header.tsx         # Every page's centred title, with an optional control at each side
    subpage-header.tsx      # Title + arrow back to Ajustes, at the start (Perfil, IA, Admin)
    chart-legend.tsx        # The legend under Progreso's charts (also names the target band)
    chart-style.ts          # The charts' shared tooltip style
    app-nav.tsx theme-provider.tsx
  lib/                      # Shared pure logic + types (importable from both sides)
    core/                   # PURE algorithms, zero deps — single source of truth, ported to Kotlin
      one file per domain (nutrition, stats, calories, ai-providers…) — list in §11.1
    api.ts demo-store.ts demo-api.ts use-demo-mode.ts utils.ts use-mounted.ts
    core/food-questions/    # The coach's questions about the food log (§8.7): a shared JSON + one file per analysis
    meal-payload.ts         # A meal's/template's content as sent and stored (mealContent, storedMealContent)
    local-json-store.ts     # A JSON value in localStorage as a useSyncExternalStore store (AI settings, model lists)
    foods/own-products.ts   # Products added from a label: kept in this browser, sent straight to Open Food Facts (§8.5)
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
website/                    # The app's site on GitHub Pages: plain HTML/CSS + latest.js (§14.7 «Releases»)
.github/workflows/          # ci.yml (tests), release.yml (signed APK on a tag), website.yml (Pages)
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
             profile_history JSONB (ProfilePeriod[], default []),
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
             note, fasted BOOLEAN NOT NULL DEFAULT true,
             time_zone TEXT (nullable: IANA zone of the device when saved),
             created_at, updated_at
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

Both rules live in one place, `lib/meal-payload.ts`: `mealContent` (the content as sent,
meal-level totals only for `total_only`) and `storedMealContent` (plus the resolved totals),
used by the meals and templates services, the demo store and the client (copy, undo). The
shared types are `MealContent` and `ResolvedTotals` (`lib/core/types.ts`); the DTOs, the
repositories' write data and the form payload extend them, and the server
schemas share `mealContentShape` (`validation.ts`). Android: `Mappers.kt` `toEntity`.

The MealForm defaults **new** meals to `per_ingredient` ("Por ingrediente"); editing keeps
the stored mode. The DB column default (`per_ingredient`) is never relied upon —
the service always writes an explicit value.

Each ingredient row's kcal, protein, carbs and fat fields show their unit inside, at the
end («145 kcal», «15 g proteína»; placeholder «0»): a placeholder alone left a row filled
from search, barcode or AI as four bare numbers. Web `UnitField` in
`components/meals/nutrition-fields.tsx` (the unit is also the field's `aria-label`; the
quantity field is «Cantidad»), Android `DecimalField(unit = …)` in `ui/FormFields.kt`
(the unit in `CompactField`'s trailing slot, read by TalkBack with the value). «Solo el
total» keeps its labels above the fields.

**Why persisted `resolved_*` columns:** stats and the export file become trivial
`SUM(...)` queries over one column instead of re-parsing JSONB on every request.

### MealIngredient (JSONB)

The core `IngredientInput` (`lib/core/types.ts`):

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
| `settings-service` | calorie profile CRUD and its history (read via session endpoint, updated via `PUT /api/settings`; no history sent = kept) |
| `stats-service` | builds the whole `StatsSummary` DTO including body fat series (see §7) |
| `export-service` | `buildUserBackup`: the user's export file with `lib/core/backup.ts` (§5.11) |

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

- **Usernames are always lowercase.** Creating an account (`register`) and renaming one
  (admin `updateUser`) trim and lowercase the name; `users-repo` lowercases again on every
  insert, update and lookup. At login the schema trims and `findByUsername` lowercases, so
  `Maria`, `MARIA` and ` maria ` are all `maria`. Only case is folded: accents and other
  letters count (`María` → `maría` ≠ `maria`), which is why username fields have no
  autocorrect (§14.4, «Password fields»). **Passwords** are compared exactly as typed
  (case-sensitive, never trimmed).
- **Hash format:** `scrypt$<N>$<r>$<p>$<salt-b64>$<hash-b64>` with N=16384, r=8,
  p=1, keylen 64, salt 16 bytes random. Verification parses stored params and uses
  `timingSafeEqual`. `maxmem: 64 MB` is required because OpenSSL's default caps out
  below 128·N·r·p.
- **Sessions:** 32-byte random token (base64url), stored raw in `sessions.token`,
  90 days (`SESSION_TTL_DAYS`), renewed while in use (below). Web clients receive it as cookie `bw_session`
  (httpOnly + secure (prod) + sameSite lax + path `/`); API/Android clients receive
  the same token in the login JSON body (`{ok, token, expiresAt}`) and send it as
  `Authorization: Bearer <token>`.
- **Sliding expiry:** a session lasts 90 days *since it was last used*. When a request
  arrives with less than half the window left (`shouldRenew`), `resolveSessionDetails`
  pushes `expiresAt` to now + 90 days (`sessions.extend`): at most one write every ~45
  days, not one per request. For the web, `api-auth.ts` also re-sets the `bw_session`
  cookie with the new end (the browser drops a cookie at its own `expires`); Bearer
  clients just keep their token. So a phone that syncs, or a browser in use, is never
  logged out; a session unused for 90 days expires as before, and logout ends it at once.
  The login's `expiresAt` is only the first end date. Tests: `auth-access.test.ts`,
  `routes-auth.test.ts`.
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
- **One account per browser on the web, on purpose.** Only the Android app keeps several
  accounts and switches between them (§14.1 «Several accounts on one phone»); don't add a
  switcher to the web.
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
| GET `/api/export?day=YYYY-MM-DD` | ✓ | The export file (JSON, §5.11), named `blackwater-macros-<day>.json` |

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
  },
  "profileHistory": [ { "until": "2026-10-14", "gender": "male", "...": "every profile field" } ]
}
```
`profileHistory` holds the former profiles, each with the last day it applied to (§8.1.1).
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
  "fasted": true,
  "timeZone": "Europe/Zurich",
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
  "note": null,
  "fasted": true,
  "timeZone": "Europe/Zurich"
}
```
- `measuredAt` must parse as a valid date.
- `weightKg` range 20–400.
- `bodyFatPct` range 3–60 (nullable).
- `note` max 500 chars.
- `fasted`: on waking, before eating or drinking; absent = `true` (older Android clients).
  The weight trend compares only weigh-ins taken alike (§8.3).
- `timeZone`: the device's IANA zone (`deviceTimeZone()`), max 64 chars, nullable. Clients
  send it when the weigh-in is created and send the stored one back when editing.

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
  "weights": [ { "date": "2026-06-15", "weight": 80, "trend": 80.2, "inTrend": true } ],
  "bodyFat": [ { "date": "2026-06-15", "bodyFatPct": 18, "trend": 18.1, "inTrend": true } ],
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
Products added from a label are written to Open Food Facts the same way, straight from the
browser or the phone, never through this server (§8.5).

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

Optional `profileHistory`: the former profiles (§8.1.1), each with every profile key (same
limits, explicit nulls) plus `until` (a valid `YYYY-MM-DD`), at most 1000. The client computes
it (`withProfileChange`) and sends the whole list. **Absent = unchanged**: the Android 1.0.0
app, which doesn't know the field, never wipes it.

Success `200` returns the updated settings object: `{ calorieProfile, profileHistory }`.

---

### 5.11 Export and import

**One file with everything, readable without the app.** Export writes the user's profile,
earlier profiles (so past days keep their targets), weigh-ins, meals and templates as one
indented JSON file with plain English names (`lib/core/backup.ts` `buildBackup`, mirrored by
Kotlin `core/Backup.kt`, which writes byte for byte the same text: `BackupTest`). Not in it:
the account, AI keys and models, theme, language.

```json
{
  "app": "Blackwater Macros",
  "format": 1,
  "exportedAt": "2026-10-26T21:00:00.000Z",
  "profile": { "sex": "male", "birthYear": 1990, "heightCm": 178, "gymDaysPerWeek": 3,
               "gymMinutesPerSession": 60, "walkingMinutesPerDay": 30, "goal": "bulk" },
  "pastProfiles": [ { "until": "2026-10-14", "sex": "male", …, "goal": null } ],
  "weighIns": [ { "time": "2026-10-26T07:15:00.000Z", "kg": 80.2, "bodyFatPct": 18, "note": null,
                 "fasted": true, "timeZone": "Europe/Zurich" } ],
  "meals": [ { "date": "2026-10-26", "title": "Breakfast", "notes": null, "mode": "foods",
               "foods": [ { "name": "Oats", "amount": "80 g", "kcal": 300, "protein": 10.5, "carbs": 54, "fat": 6 } ],
               "total": { "kcal": 300, "protein": 10.5, "carbs": 54, "fat": 6 } } ],
  "templates": [ { "name": "Shake", "title": "Protein shake", …same as a meal without "date" } ]
}
```

- **Names** are English for every language (a file format; values too: `sex` male/female,
  `goal` cut/maintain/bulk). `mode` is `foods` (the total is the sum of the foods; on import it
  is recomputed) or `total` (the total was typed; carbs and fat may be null). A food lists only
  the values it has. Meals are in day order (date, then their order in the day), weigh-ins
  oldest first.
- **`format`** is 1. A change that an older app couldn't read raises it; an app refuses a file
  with a higher number («update the app»). `fasted` and `timeZone` were added without raising
  it: a file without them imports as `true` / `null`.

#### Export

- **Web:** Ajustes → Tus datos → «Exportar mis datos» links to `GET /api/export?day=<browser
  date>` (auth required; `withUserId`), which builds the file on the server
  (`export-service` `buildUserBackup`) and returns it as an `application/json` attachment named
  `blackwater-macros-<day>.json` (`day` only names the file; without a valid one, the server's
  UTC date). Disabled in demo mode.
- **Android:** the same row writes `repository.backupData()` through `buildBackup` to a file
  the user picks (`CreateDocument("application/json")`), with or without an account.
- **Feedback (Android):** a done export says «Datos guardados en <name>» in a snackbar (the
  name the picker returned, `OpenableColumns.DISPLAY_NAME`); a done import or deletion says so
  the same way. Errors (not an export, damaged, failed) stay as a red line in the Data group
  until «OK» (`SettingsScreen.kt` `DataGroup`). No progress bar: the file is built in memory
  and a year of data is well under a megabyte, so it takes a moment. The web export is a
  browser download, which shows its own progress.

#### Import (Android, only without an account)

The import **replaces** the phone's meals, weigh-ins, templates and profile with the file's
(move to a new phone, restore after reinstalling). It never reaches the server:

- **With an account** the data is the server's and the phone is its copy: imported rows would
  upload, or the next sync would undo the import. So the row is disabled with «Tus datos están
  en tu cuenta…», and `AppRepository.replaceAll` refuses to run when logged in. The web has no
  import (a browser always has an account).
- **Reading** (`data/BackupImport.kt`): all or nothing. Not JSON or not this app's file →
  «not an export»; a higher `format` → «update the app»; any value the server would refuse
  (the limits of `src/server/validation.ts`, text trimmed as the server stores it) → «damaged
  or edited by hand (meals › 13 › foods › 1 › kcal)». Nothing changes in any of these cases.
- **Confirmation:** «¿Sustituir todos tus datos?» with what the file holds («412 comidas, 96
  pesajes y 5 plantillas, del 2 nov 2025 al 26 oct 2026») and «No se puede deshacer».
- **Saving** (`replaceAll`, one Room transaction): every meal, template and weigh-in row is
  deleted, the file's are inserted with new ids (meals numbered in file order within each day),
  and the profile and its history are the file's.
- **Logging in later** asks, as for any local data, whether to upload it to the account or
  discard it.

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
| Comidas | `app/page.tsx` | Day navigation (on another day the reserved line under the date says «Volver a hoy» and goes back — on Android a tap on the date does too; double-click/double-tap the date → today), one day summary card (`DaySummaryCard` in `components/nutrition-recommendations.tsx`, Android `DaySummaryCard`): a header «Objetivos de hoy» («Objetivos de ese día» on a past day) with «ⓘ Cómo se calculan» on its right — in the header so it reads as being about every target, not the totals next to it —, a bar each for calories (with the goal) and protein, what was eaten as the big number («2707 kcal consumidas», also when over) with the range next to it, and what is missing or over in words under the bar (a past day with no meals says «Sin comidas registradas» in gray, once, under calories: an unlogged day is not a day without food; `nothingLogged`), the expenditure notices on today, then carbs and fat as plain totals in two equal columns (label above, value under it, tabular figures, so the values line up and the layout does not change with the numbers; with no targets — no weight — calories, protein, carbs and fat as a 2 × 2 grid, with no header or link). «Cómo se calculan» opens «Cómo se calculan tus objetivos», a sheet with where the ranges come from: the range and estimated average, then the chain from the inputs to the range in two lines with the user's numbers («Gasto usado: 89 % tu gasto medido (2625) + 11 % el de tu perfil (2615) ≈ 2624 kcal/día», or «el de tu perfil, TMB × actividad = TDEE» before there is a measurement; «Definición: −400 kcal (−500 a −300) → 2224 de media, rango 2124–2324», offsets taken from the numbers shown, so the BMR floor shows too; `expenditureChain` / `goalChain`, `t.calorias.chain*`), protein per kg, «Ver el cálculo» on today (Perfil's dialog: BMR, activity, TDEE and the measured expenditure with its ±, as formulas; `ExpenditureCalculationButton`, Android `ExpenditureCalculationSheet`) and the full methodology. Four nearly equal expenditure figures side by side used to say nothing about how they made the target: the user's numbers one tap away, so the day's meals start on the first screen (message → numbers → methodology). A past day shows the targets it had, §8.1.1. Then the meal list (each card: its ingredients as one row each — the name, up to two lines before «…» (one line left about 13 characters on a phone), then fixed-width, end-aligned columns for the quantity, kcal and «x g prot.», sized in rem so they grow with the font, as Android's `IngredientCell`; carbs and fat only in the card's totals, since four labelled macros per row left no room for the name on a phone; a single ingredient shows no numbers, they equal the totals; the totals under it put the label first, «Proteína 38 g», as the day card and Progreso do — then edit, «Guardar como plantilla» — saved at once under the meal's title via `mealContent`, with an Undo toast that deletes it — and delete), delete confirm then an Undo toast (`api.restoreMeal`: same id, then the day's order; also `restoreWeight` / `restoreTemplate` in Progreso and Ajustes, and the demo store). The floating + opens `AddFoodSheet` (on another day its title names the day, «Añadir comida · dom, 4 oct», and so does «Nueva comida»: `formatDateKeyWeekdayShort`, Android `formatDateWeekdayShort`; after browsing old days, today's lunch could land on one of them): «Escribir a mano» → `MealForm` (the review form every source ends in; asks «¿Descartar los cambios?» when closed with edits), «Copiar de otro día» and templates create meals directly via `lib/meal-payload.ts` (`copyMealPayload`) with an Undo toast. The templates come right under the three sources (repeating a meal is the fastest way to log); «Gestionar» shows an edit and a delete button on each, and holding a template (or a right click: Base UI `ContextMenu`; Android `TemplateChip`, a long-press `DropdownMenu`) stays a shortcut to Editar and Borrar without going to Ajustes: the sheet closes, then the page shows `TemplateForm` or the delete confirm with an Undo (`restoreTemplate`), as Ajustes does |
| Progreso | `app/progreso/page.tsx` | Weight and statistics on one page (`/peso`, `/estadisticas` redirect in `next.config.ts`). One range selector drives everything: weight card (current, trend, change, rate, body fat) + weight chart (`components/weight-fat-chart.tsx`, Android `chart/WeightFatChart.kt`: one axis at a time; the 7-day trend bold, each weigh-in a light dot with no line between them; a weigh-in taken otherwise than most of the range (fasted or not, `inTrend` false in `StatsSummary.weights`) is a hollow dot, left out of the trend and «Cambio» (`buildStatsFromData`: `movingAverageByDays(…, counts)`; one dot a day, a counted one first) with one line under the chart, `progreso.otherWeighInsNote` (METHODOLOGY.md §4); «Peso» / «Grasa corporal» chips when there are body-fat measurements (the choice lives in the page, not the card: a save reads the stats again behind a skeleton, and the card's own state went back to «Peso»); body fat is drawn the same way, its 7-day trend bold over light dots, a reading taken otherwise than most of the readings with body fat hollow (`StatsSummary.bodyFat[].inTrend`) with its own line, `progreso.otherFatReadingsNote` — the chart once joined every reading, so one after dinner bent the line); daily calories chart (logged days only; straight segments, as on Android: a smoothed curve drew amounts nobody ate) with the target band (it steps where a day's target changed, §8.1.1), its axis fitted to the days and the band rather than from 0, and a legend naming each day, the 7-day average and the range (`components/chart-legend.tsx`; both charts call the trend «Tendencia (media 7 días)», `t.stats.trendLine`); «Promedio de macros» over logged days (`macroAverages`, «Días registrados: N de M», kcal split with a colour swatch before each share in the legend (not colour alone), targets = mean of the logged days' own targets, measured expenditure, a button that opens «Ver el cálculo», where its ± is explained; a line when fat is outside 20–35 % of calories, §8.4.1); weigh-ins of the period with edit/delete (each with its time, `formatTime` — a day can hold a fasted and a not-fasted one — and its body fat after the weight, on its own line below 400 px, never hidden); floating add-weight button, round like Comidas' + with the scale icon, named «Registrar peso» for screen readers and as a tooltip (the labelled one, ~210 px wide in French, covered the chart's last days; `components/weight-form-dialog.tsx`); ⓘ links to /metodologia |
| Ajustes | `app/ajustes/page.tsx` | A grouped list, the same as Android: a short heading per group and one card of rows (`components/settings/settings-list.tsx`: `SettingsGroup`, `SettingsRow` — icon, title, a line under it, an arrow or a control), every setting on the page and one tap away. **Cuenta** (who is logged in, logout; in the demo «Salir de la demo»), **Personal** (Perfil, IA, Open Food Facts, templates: collapsed, with the manager and the new-template dialog), **Tus datos** («Exportar mis datos», one file, §5.11; no import on the web; in the demo the row is off and says why on itself), **Aplicación** (theme — Sistema / Claro / Oscuro, the default first, as on Android — the only place to switch it; language, `language-row.tsx`; the tutorial), **Acerca de** (Metodología, the website `WEBSITE_URL` from `lib/links.ts`, source code, «Administración» for admins), then one quiet last line, «Creado por jordanmontt», linking to `AUTHOR_URL` |
| IA | `app/ajustes/ia/page.tsx` | Ajustes → Inteligencia artificial on its own page, like Android's screen: `AiSettingsCard` (provider, key, model dropdown, model in this browser, «El coach puede ver mis datos»). «Configurar la IA» links from the coach and «Foto o texto» open it directly, with `?from=coach` / `?from=hoy` (`lib/ai-settings-origin.ts`): the back arrow then returns there («Volver a Coach») and the menu keeps that tab, instead of dropping the user in Ajustes. The query is read with `useSearchParam` (`lib/use-search-param.ts`, `useSyncExternalStore`): Next's `useSearchParams` needs a Suspense boundary on a static page. Android pops its back stack, which already returns to the caller. The Coach's empty state without AI says it once: what the coach does, three example questions (they lead to the setup and wait in the box afterwards; the list's name says so), one sentence and «Configurar la IA» |
| Open Food Facts | `app/ajustes/open-food-facts/page.tsx` | The user's own Open Food Facts account for the products they add (checked with `/cgi/auth.pl`, then kept in `localStorage["bw:off-account"]`, this browser only; in the demo, the tab); without one they go as Blackwater Macros (§8.5) |
| Perfil | `app/ajustes/perfil/page.tsx` | Goal selector with one line on what the goal does (§8.2.1), calorie profile form (debounced autosave with validation; changes count from today, with a note linking to `/metodologia#past-days`) and the calorie/protein recommendations: the calorie range as the headline (as on Today), the estimated average with the weight the BMR uses («Con tu último peso: 81,7 kg»), the expenditure check's line with «Ver el cálculo» (BMR, activity, TDEE and the measured expenditure as formulas live there; before a measurement, one line «TMB × actividad = TDEE»), then protein. About ten lines of small numbers used to sit here. Nested under `/ajustes` so the Ajustes tab stays active; same split as Android |
| Admin | `app/admin/page.tsx` | Admins only (403 «No tienes permiso…» otherwise): lists users with role badge, create/edit/delete dialogs; guards mirror the service (no self-demote/delete, ≥1 admin) |
| Login | `app/login/page.tsx` | Only reachable when logged out: proxy redirect + `Cache-Control: no-store` + client-side session re-check (see §4.5). Also hosts the «Explora datos de demo» entry (see §6.1) |
| Metodología | `app/metodologia/page.tsx` | Client page (a server component would always render Spanish, see i18n), its tab title set in the request's language by `metodologia/layout.tsx`: every formula (stats, BMR + factorial PAL, why past days keep their targets at `#past-days`, protein, measured expenditure, the profile check at `#check`) with the reasoning and Crossref-checked citations. Every section is an `<h2>` (Android: a `heading()`) with an anchor, the same ids on both (`sectionGroups()` / `SECTION_GROUPS`: `scale`, `fasted`, `moving-average`, `rate`, `daily`, `protein`, `calories`, `recomposition`, `past-days`, `measured`, `adaptive`, `check`, `fat`, `ai`, `food-questions`, `sources`, `references`), and an «En esta página» index at the top links to each: about 11 000 px of text with one heading left screen readers nothing to jump between. The index starts closed, one line («En esta página · 17 secciones», web a `<details>`), so the first section shows at once; open, a compact list with each section's icon in four groups in page order (`metodologia.indexGroups`: Peso, Comida y calorías, IA, Fuentes). A list one tall row per section used to fill the first screen. `MethodologyIndexTest.kt` keeps Android's groups, ids and order equal to the web's and to the page. Today's «Metodología completa» opens `#calories`. Android: `MethodologyScreen.kt`, same content and reference list |

### Client data layer (`lib/api.ts`)

Typed fetcher: JSON headers, network-failure → `ApiError(0)`, non-OK → `ApiError(status, serverMessage)`.
A 401 outside the login call throws `ApiError(401)` and dispatches the
`AUTH_EXPIRED_EVENT` custom event; `components/auth-redirect.tsx` listens and
navigates to `/login` (a graceful SPA redirect instead of a hard reload).
`api.session()` swallows failures and resolves to an empty profile without
`profileHistory`, so a profile saved then sends no history and the server keeps its own. Components
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
  so the "Comidas" page is never empty. Meal titles, ingredients and notes are
  `t.demoData`, in the app's language when the demo starts (a French visitor sees
  «Dîner», not «Cena»); the numbers are the same in every language.
- **Isolation & persistence:** demo data lives in **sessionStorage** (`per-tab`,
  survives in-tab reloads) plus the `bw_demo` cookie. Each tab gets its own
  isolated copy; everything resets when the tab/browser closes or when site
  cookies are cleared. What the app otherwise keeps in `localStorage` as the user's
  data — recent foods, own products (§8.5), the Open Food Facts account, the AI
  settings with their key (§8.6) — goes through `userDataStorage()` (`lib/demo-store.ts`), which in the demo is the tab's
  `sessionStorage`: the demo never sees the browser's own copies, and a real login
  in the same browser never sees the demo's. `exitDemoMode()` also removes those
  (`bw:*` keys in `sessionStorage`).
- **Mutability:** users can add/edit/delete meals, weights, and templates, and
  update the calorie profile freely (its history stays empty: the demo profile is
  not kept). All writes go through `lib/demo-store.ts`,
  which reuses `storedMealContent` (`lib/meal-payload.ts`) so totals are computed
  identically to the server. Nothing is ever sent to the network: a product added
  from a label stays in the tab and is never sent to Open Food Facts
  (`sendOwnProducts` returns at once), and the form and the portion screen say so
  in place of the consent and status lines (`t.newProduct.demoNotSent`).
- **Exit:** a persistent `DemoBanner` (rendered in `layout.tsx` on every app
  page) shows «Estás en modo demo…» (on phones the one-line `t.demo.bannerShort`: the
  sentence took about 16 % of the screen on every page) with a **«Iniciar sesión»** button that calls
  `exitDemoMode()` (clears the cookie + storage) and returns to `/login`. The
  Ajustes page also exposes the exit action and disables the export in demo mode (it
  targets a server endpoint that requires a real session).
- **Stats:** `api.stats()` in demo computes the `StatsSummary` via the shared
  `lib/core/stats-builder.ts` (see §7) — the exact same code the server service uses.
- **Cache:** pages read through `useCachedResource` (`lib/client-cache.ts`,
  stale-while-revalidate in memory and `sessionStorage`). Every write in `lib/api.ts`
  goes through `written(write, ...changed)`, which drops the changed cache keys
  (`"meals:"`, `"stats:"`, `"weights"`, `"templates"`, `"session"`) after the server's
  or the demo's write alike, so a page showing them reads again. The demo once
  returned before that step: after a weigh-in Progreso refreshed the list but kept the
  old chart, trend and «Cambio» until the page was left.

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

### Accessibility (web)

- **Where you are:** the main menu marks the current page's tab with `aria-current="page"`
  (`components/app-nav.tsx`; Ajustes' subpages count as Ajustes). Android's bottom bar
  already says «seleccionado».
- **Library texts in the app's language:** dnd-kit's keyboard instructions and its
  announcements while a meal is moved (`dragAccessibility` in `app/page.tsx`:
  `t.hoy.drag*`, with the meal's title and place) and sonner's region label
  (`containerAriaLabel`, `t.common.notifications`). Their defaults are English.
- **Charts** carry a one-sentence name with the user's numbers (Recharts `title`, rendered
  as the svg's `<title>`): `chartSummary` in `components/weight-fat-chart.tsx` («Peso: la
  tendencia pasa de 83,4 a 81,8 kg entre el 26 oct y el 24 nov»), and the calorie chart's
  average over the logged days (`t.stats.*ChartSummary`).
- **Icon buttons** name what they act on («Editar Desayuno», `t.meal.*Named`, with the
  same text as a tooltip), since a screen reader lists buttons out of their cards. On a
  touch screen every icon button is at least 44 px (`pointer-coarse:min-*-11` in
  `components/ui/button.tsx`), whatever its drawn size.
- **Contrast:** small status text in green (`text-green-800` in the light theme: «en rango»,
  «Añadido a Open Food Facts») passes 4.5:1; `green-700` was 4.49:1.
- **Focus:** keyboard focus is 2 px in the full `--ring` colour: `:focus-visible` outline in
  `globals.css`, and `focus-visible:ring-2 ring-ring` on the controls that draw their own
  ring (button, input, select, textarea, badge). At 50 % it vanished on the pale chips.

### Forms and page headers (web and Android)

- **Placeholders** are examples, introduced as such («Ej. Desayuno», «Ej. 30 g»; «E.g.», «Ex.»,
  «Z. B.», «Es.»), so an empty field never looks filled. Optional fields say so in the label
  («Grasa corporal (%, opcional)»). One line, as the field (Android `CompactField`'s hint
  never wraps); the ingredient quantity field is 6rem / 96 dp × font scale on both, so «Z. B.
  30 g» fits (on Android a quarter of the row once wrapped «E.g. 30 g» onto two lines).
- **Labels side by side** that may wrap (Perfil's gym days and session length) keep two lines
  and put the text at the bottom, on its field: no gap between a short label and its field,
  and both fields line up.
- **Leaving a page:** every page opened from somewhere else has the ← at the start (Perfil and
  IA to Ajustes or where they were opened; Metodología `BackPageButton`, `router.back()`). No ✕.

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
- **Not affected:** the JSON wire format and the export file (`.`, IEEE 754), the text sent to AI
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
- A new weigh-in also stores the device's **time zone name** (`deviceTimeZone()`, web
  `Intl…resolvedOptions().timeZone`, Android `ZoneId.systemDefault().id`). It is not used
  for dates: only to tell the weight trend that weigh-ins before and after a move were
  probably taken on different scales (§8.3).

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
`{marker}` is missing. A list in the JSON (Metodología's paragraphs) becomes one string with a
blank line between the paragraphs. Android-only texts (plurals, screens the web doesn't have, texts worded
for a phone) stay in `res/values*/strings.xml`. A text that should read the same on both:
put it in the JSON and add one map line; never copy it into `strings.xml`.

**Punctuation is each language's.** Code never adds «: » after a label: a label with
its value is `formatTemplate(t.common.labelValue, { label, value })` (Android
`R.string.label_value`), «{label}: {value}», «{label} : {value}» in French. French puts a
space before `: ; ? !` and inside « »; `tests/unit/i18n.test.ts` checks it.

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
AI answers in (`aiLanguage()`). The export file's names are English in every language (a
file format, §5.11).

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

- `bodyFat` — `{ date, bodyFatPct, trend, inTrend }[]` with a 7-day moving average of
  the readings taken like most of the readings with body fat (`inTrend`, the same rule
  as `weights`, METHODOLOGY.md §4)

The series is an empty array when no entries in the range have body fat data.
The `CompositionStats` DTO includes body fat stats (current/change/min/max; the
change from the usual readings), all null when no body fat data exists. The client
draws weight or body fat, one axis at a time (`WeightFatChart`, §6 Progreso).

Pure helpers in `lib/core/stats.ts` (unit-tested):

- `movingAverageByDays(points, 7)` — trailing calendar-window average; days without
  entries contribute nothing rather than counting as zero (weights) — used both for
  the weight trend line (server-side) and chart overlays (client-side, rounded to
  1 decimal before render).
- `linearRatePerWeek(points)` — ordinary least squares slope × 7
  (`β = Σ(xi−x̄)(yi−ȳ)/Σ(xi−x̄)²`).
- Progreso's **weekly rate** is not that line but `slopeWithinGroups(groupWeighIns(…))` × 7
  (`expenditure.ts`): the slope compared only within weigh-ins taken alike, so a switch
  from after-breakfast to fasted weigh-ins doesn't show as a loss (§8.3). `StatsWeight`
  carries `fasted` and `timeZone` for it.
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
- **Expenditure** (what the target is built on): `tdee` combined with the measured one
  when there is one (`calculateCalorieRecommendation(profile, kg, year, measured)` →
  `combineExpenditure`, §8.3.2); returned as `expenditure` and `dataShare` (0–1), `tdee`
  stays the profile's.
- **Target:** fixed offsets on `expenditure` — cut −400 (−500…−300), maintain ±100, surplus +300 (+200…+400).
  **Floor:** `target`, `targetMin` and `targetMax` never go below the BMR (only reached by a
  cut for small, sedentary people).
- METs are used as multiples of BMR (an approximation: 1 MET is usually a bit above the real
  RMR, Byrne et al. 2005, so exercise energy comes out low — conservative).

### 8.1.1 Past days keep their targets (`profile-history.ts`)

Like MyFitnessPal: editing the profile counts from today and never rewrites past days. Why and
the magnitudes: [METHODOLOGY.md](./METHODOLOGY.md) §1.4.

- **Data:** `profileHistory: ProfilePeriod[]` next to the profile (`users.profile_history`,
  Android `ProfileEntity.history` as JSON). A period is a former profile plus `until`, the last
  day it applied to; after the last period the current profile applies. Empty (every existing
  user) = the current profile for every day, as before.
- **Saving** (`withProfileChange(previous, history, next, today)`): appends
  `{...previous, until: yesterday}` unless nothing changed, `previous` gave no calorie target
  (an unfinished profile; the new one covers the earlier days too) or a period already ends
  yesterday or later (a second edit the same day). Web: `api.updateSettings(profile, session)`
  (without a known history it sends none); Android: `AppRepository.saveProfile`, in one
  transaction.
- **Reading** (`targetsOn(profile, history, weights, day, measuredOn?)`): `profileOn` (the earliest period
  with `until ≥ day`, else the current profile), `weightOn` (last weigh-in ≤ day, else the
  first), `bodyFatOn` (same, among weigh-ins with body fat), age from the day's year, and
  `measuredOn(day)` (`weeklyExpenditure`: the measurement of that day's Monday; none by default). Today
  gives the usual numbers. `averageTargets` is the mean of several days' ranges.
- **Where:** the Today card on a past day (web `DaySummaryCard day`, Android
  `RecommendationsUiState.Ready.targets`), with «Objetivos de ese día…» when they differ from
  today's; the expenditure notices only on today. Progreso: the calorie band steps with each
  logged day's range (web Recharts `Area type="step"`, Android `TrendChart(bands)`; one range
  for all = a flat band, named in the legend), and «objetivo» next to the averages is `averageTargets`
  of the logged days. Perfil: «Los cambios cuentan desde hoy…», linked to
  `/metodologia#past-days` (Android `metodologia?section=past-days`).
- **Unchanged:** the profile check and the coach read the current profile (and this week's measurement).
- **Sync:** the history travels with the profile (same last-write-wins): `SyncEngine` sends it
  in `PUT /api/settings`; the download replaces it when the local profile isn't pending, and a
  server without the field (`profileHistory` null) leaves the phone's alone.
- **Export file:** the history is in it as `pastProfiles` (§5.11), and an import restores it.

### 8.2 Protein (`protein.ts`)

| Goal | g/kg | Basis | Source |
|---|---|---|---|
| Maintain, strength training | 1.6–2.2 | body weight | Morton et al. 2018; Nunes et al. 2022 |
| Maintain, no strength training | 1.4–2.0 | body weight | Jäger et al. 2017 (ISSN) |
| Surplus | 1.6–2.2 | body weight | Morton et al. 2018; Iraki et al. 2019 |
| Cut | 1.8–2.7 | body weight | ≈ 2.3–3.1 g/kg FFM at typical body fat |
| Cut, body fat known | 2.3–3.1 | lean mass = weight × (1 − BF%) | Helms et al. 2014 (IJSNEM); Jäger 2017 |
| Any goal, BMI > 25 (not the lean-mass case) | as above | reference weight = 25 × height² | McClave 2016; checked against Kokura 2024 (>1.3 g/kg actual) |

The reference weight is skipped when a logged body fat is below the one that goes with BMI 25
for the sex and age (`normalBodyFatMax`, Gallagher et al. 2000 Table 4: men 20/22/25 %,
women 33/34/36 % at 20–39/40–59/60+ years; under 20 or no birth year → the 20–39 band): then
the high BMI is muscle. Callers pass the most recent non-null `bodyFatPct` from any weigh-in
and `{ heightCm, gender, age, strengthTraining }` from the profile (`age = currentYear −
birthYear`, as in `calories.ts`; `strengthTraining = doesStrengthTraining(profile)`).

### 8.2.1 Strength training: muscle in every goal (`doesStrengthTraining`)

Science: [METHODOLOGY.md](./METHODOLOGY.md) §2.4. `doesStrengthTraining(profile)`
(`calories.ts` / `Calories.kt`) is true with gym days **and** minutes above 0; walking does
not count. Then the app assumes the user also wants to gain muscle in every goal, and says
so; it changes no calorie target.

- **Protein:** maintain uses 1.6–2.2 g/kg instead of 1.4–2.0 (§8.2). `targetsOn` passes the
  day's own profile, so a past day keeps the range its training gave.
- **Perfil:** one line under the goal picker (web `goalHint`, Android `GoalCard`):
  `ajustes.goalHint{Cut,Maintain,Surplus}` with training, `…NoTraining` without (Android
  `goal_hint_*`). Not in the first-steps form, where the activity is folded away.
- **Profile check:** a «lower» verdict adds `expenditureCheck.noticeLowerTraining` to the
  notice and `lowerReasonTraining` to the causes (muscle stores ~1800 kcal/kg against the
  7700 the measured expenditure assumes). Web `trains(inputs)` in `expenditure-check.tsx`,
  Android `trains(inputs)` in `ExpenditureCheck.kt`.
- **Coach:** `GOAL_TEXT` / `goalText(goal, training)` and, with training, the
  `RECOMPOSITION_TEXT` line after «Activity» (§8.6).
- **Metodología:** the «Perder grasa y ganar músculo a la vez» section (`#recomposition`,
  `metodologia.recomp*`, Android `meth_recomp_*`), after the TDEE one.
The result has `basis` (`bodyWeight` | `leanMass` | `referenceWeight`), `basisKg`, `range`
(g/day), `perKg` and `target` (midpoint).

### 8.3 Measured expenditure (`expenditure.ts`)

Energy balance over the **28 days before a day** (MacroFactor-style); the app always measures
on **this week's Monday** (`weekStartOf(today)`, `dates.ts`):
`TDEE = mean intake of logged days − β × 7700`, β = least-squares weight slope (kg/day).

- Days without meals (or summing to 0 kcal) are **excluded**, never counted as 0.
- **Only weigh-ins taken alike are compared** (`groupWeighIns`, `WeighIn`). A weigh-in
  belongs to a group = (fasted or not, time-zone period); the period goes up when a
  weigh-in's known `timeZone` differs from the last known one (null never splits; summer
  time keeps the name). One point per group and day: the mean (same-day weigh-ins are not
  independent measurements). Groups come only from the switch and the zone, never from
  the weights. Science and the simulation: [METHODOLOGY.md](./METHODOLOGY.md) §3.
- `fitWeightTrend(points, groups)` is a fixed-effects fit: one level per group, one common
  slope, `β = Σ_g Sxy_g / Σ_g Sxx_g`. Single-day groups carry no slope and are left out.
  With one group (no conditions given, as in older data) it is exactly the ordinary
  least-squares line. `WeightTrend.meanX/meanY/levelN` are the latest group's level (the
  coach projects from it).
- Measured when ≥ 21 logged days and ≥ 4 weigh-in days spanning ≥ 14 days, always with its
  95 % margin `1.96 × SE(β) × 7700`, with `SE(β) = σ / √Σ_g Sxx_g` and σ the residual SD
  (n − G − 1 df), floored at 0.5 kg so a few aligned weigh-ins can't look precise. Weekly
  weigh-ins give ≈ ±480, ~3/week ≈ ±270, daily ≈ ±180. Enough days but no group with 2+
  days (no trend) reports `weighIns` as missing.
- `ExpenditureEstimate.groups / mixedFasting / newZoneSince` say what was split; Perfil and
  the «¿Por qué?» add one line only when `groups > 1` (`weighInConditionsText`, web
  `expenditure-check.tsx`, Android `ExpenditureCheck.kt`). The coach's context marks
  weigh-ins «(not fasted)».
- **No largest margin** (until 2026-11 it had to be ≤ ±300): a wide margin counts less in
  the target (§8.3.2) and needs a bigger gap for the check (§8.3.1), both gradually. The
  cut-off dropped a ±317 measurement the data weighed 60 % in, and a real target fell
  ~480 kcal from Sunday to Monday ([METHODOLOGY.md](./METHODOLOGY.md) §3.2).
- It is displayed next to the formula TDEE (home card and Perfil, `± margin`) and the
  target is built on both combined (§8.3.2). Perfil explains what is missing when it isn't available.
- Web: `lib/use-measured-expenditure.ts`: every day's kcal from the all-time stats
  (`stats:all:<today>`, `api.stats("all")`, the same cache key as Progreso's «all» tab, so
  a past day can get its week's measurement) + `weights`; `useMeasuredOn()`,
  `useMeasuredExpenditure()` (this week's), `useExpenditureCheck()`. `useRecommendations`
  passes `measuredOn` to `targetsOn`. Android: `recommend(weights, profile, meals, today)`
  in `RecommendationsViewModel.kt` (`TargetHistory(…, measuredOn)`).
- `measureExpenditure` returns the counts (logged days, weigh-in days, span, margin) even
  when they aren't enough; `estimateExpenditure` is its `.estimate`.

### 8.3.1 Checking the profile against the data (`checkExpenditure`)

Science and every threshold: [METHODOLOGY.md](./METHODOLOGY.md) §3.1. In short, the
verdict is «higher»/«lower» only when the whole 95 % range of the measured value lies
outside the profile's TDEE ± 10 % (`FORMULA_TOLERANCE`), and it is `confirmed` only if the
measurement of the previous Monday says the same (callers pass `weekStartOf(today)`, so the
check reads the 35 days before Monday). Otherwise «matches», or `notEnoughData` with `missing` in fixing order (meals,
weigh-ins, span).

- **Layers** (the app's transparency rule: show only when needed, explain all of it):
  1. *Comidas card* (`ExpenditureNotices`): one line, only when `showsGapNotice` (confirmed
     gap, not dismissed for the same verdict in the last 28 days), with «¿Por qué?» and
     «Ahora no». Or, once, «weigh yourself ~3×/week» when only weigh-ins are missing
     (`onlyWeighInsMissing`).
  2. *«¿Por qué?»* (web dialog, Android bottom sheet): the user's numbers in each formula
     (Mifflin, the gym minutes per day — «3 × 60 min ÷ 7 = 25,7 min», `gymPerDay` — then PAL,
     BMR × PAL, energy balance), the normal band vs the measured range drawn
     on one bar, the verdict, the implied PAL, the causes for that direction, «Revisar mi
     actividad» and «Cómo funciona» (→ `/metodologia#check`, Android
     `metodologia?section=check`).
  3. *Metodología*: the «Comparar tu perfil con tus datos» section and its references.
- **Perfil** (`ExpenditureCheckStatus`) always shows the status: what is missing, or the
  verdict with «Ver el cálculo». It follows the profile being edited, so changing the
  activity shows at once whether it now matches.
- **«Ahora no»** is a per-device convenience: web `localStorage`
  (`bw-expenditure-notice-dismissed`, `bw-weigh-in-hint-dismissed`, `lib/expenditure-notices.ts`),
  Android `AppPreferences` (`expenditureNoticeDismissal`, `weighInHintDismissed`).
- **Files:** core `expenditure.ts` / `Expenditure.kt` (mirror tests); web
  `components/expenditure-check.tsx`; Android `ui/ExpenditureCheck.kt`. Texts in
  `expenditureCheck.*` and `metodologia.check*` of the five JSON, mapped as `check_*` and
  `meth_check_*`.
- **With strength training** a «lower» notice and its causes also name muscle gained while
  losing fat (§8.2.1).
- **The target already follows the data** (§8.3.2), so the notices end with «your target
  already takes it into account» and the sheet's `targetNote` says the check is about
  whether the profile fits.

### 8.3.2 The target follows the data (`combineExpenditure`, `weeklyExpenditure`)

Science, the case that motivated it and every number: [METHODOLOGY.md](./METHODOLOGY.md) §3.2.

- `combineExpenditure(formulaTdee, measured)`: inverse-variance weighting with
  `FORMULA_SD` = 0.10 (σ_profile = 10 % of the profile's TDEE) and σ_measured =
  `margin / Z_95`; returns `{ expenditure, dataShare }` (rounded kcal, share to 2 decimals).
  No measurement → the profile's, share 0.
- `weeklyExpenditure(intake, weights)` returns `(day) => estimate` memoized per Monday
  (`weekStartOf`): the measurement is taken on Monday from the 28 days before, so targets
  change once a week; Progreso asks for every day of a period.
- **Shown:** «Gasto usado para tu objetivo: N kcal/día (S % de tus datos)»
  (`calorias.targetExpenditure*`; web `TargetExpenditure` in
  `nutrition-recommendations.tsx`, Android `TargetExpenditureLine`) on the Comidas card and
  in Perfil, only when `dataShare > 0`, linked to `/metodologia#adaptive` (Android
  `metodologia?section=adaptive`, `ADAPTIVE_SECTION`).
- **Coach:** with `dataShare > 0` the target line says the expenditure it is built on and
  the data's share (`coach.ts` / `Coach.kt`).
- **Weight form:** the web (`weight-form-dialog.tsx`) and Android (`WeightFormDialog.kt`)
  show the same form: weight and body fat side by side, date and time apart (native pickers),
  «En ayunas» with its switch on the right, then the note. Both check the server's limits
  first (20–400 kg, 3–60 % body fat: `peso.weightRange` / `peso.bodyFatRange`, Android
  `validateWeight`) and say the range inside the form, not in a toast.
- **Weigh-in timing:** the weight form asks for morning weigh-ins (`peso.timingHint`,
  Android `weight_timing_hint`), as do `expenditureCheck.weighInHint` and
  `metodologia.measuredMorning`. Its «En ayunas» switch has one line (`peso.fastedHint`) and,
  outside the switch so its name stays short, «¿Por qué pesarse en ayunas?» →
  `/metodologia#fasted` (Android `FASTED_SECTION`; the form closes, as the web's link leaves
  the page): why fasted, and that a weigh-in after eating is fine if the switch is off.
- **Tests** that build data relative to today pin the clock to a Monday (web
  `vi.setSystemTime`, Android `today = "2026-03-02"`), so «the 4 weeks before Monday» are the
  4 weeks before today whatever day the suite runs.

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

### 8.4.1 Fat as a share of calories (`checkFatShare`, `progress.ts`)

Science and sources: [METHODOLOGY.md](./METHODOLOGY.md) §4.1. The app recommends only
calories and protein; this says when the fat they leave is outside the adult range.

- `checkFatShare(averages)` (TS / Kotlin `Progress.kt`, mirror tests): `null` with fewer
  than `FAT_CHECK_MIN_DAYS` (7) logged days, no macros, or `split.fat` (the rounded share
  the card shows) within `FAT_SHARE_MIN`–`FAT_SHARE_MAX` (20–35); else
  `{ verdict: "low" | "high", share, fat, fatKcal, macroKcal }` for the «why».
- **Shown:** Progreso, «Promedio de macros», under «Media por día registrado», for the
  selected period: web `FatShareNotice` (`components/fat-share.tsx`), Android
  `FatShareNotice` (`ui/FatShare.kt`). «¿Por qué?» opens a dialog / bottom sheet with
  `progreso.fatNumbers`, the 20–35 % band on a 0–100 % bar, the reasons and what to change
  (`fatLowWhy`/`fatLowWhat` or `fatHighWhy`/`fatHighWhat`), and «Cómo se calcula» →
  `/metodologia#fat` (Android `FAT_SECTION`; `ProgressScreen` now takes
  `onOpenMethodology(section)` like Hoy and Perfil). Low: amber icon; high: grey, worded as
  what to favour, since the app cannot see saturated fat.
- Information only: no target, no dismissal; texts in `progreso.fat*` and
  `metodologia.fat*` (Android `progress_fat_*`, `meth_fat_*` via `strings-from-web.json`).

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
  por nombre», «Hacer una foto» (AI estimate) and «Escanear otro» — not without a camera,
  where the field says «Escribe el código» instead of «O escribe el código» (web
  `noCamera`, Android `granted`). Open Food Facts is the
  only barcode source: the largest open database (strongest in France and Europe), free
  to reuse (ODbL). The alternatives either have no nutrition (UPCitemdb), cover only the
  US (USDA Branded Foods) or forbid reuse (commercial APIs).
- **«Añadir este producto»** (an unknown code, or one Open Food Facts has without
  nutrition; not a shop label, whose code changes with every package): the main button of
  the miss card opens «Nuevo producto» (web `NewProductForm`, Android `NewProductView`),
  which then goes on to the portion like a found product. Pure part in `core/new-product.ts`
  = Kotlin `NewProduct.kt` (mirrored tests):
  - **The label as printed:** name (required; Open Food Facts' own name and brand when it
    knows the product), brand, «per 100 g / 100 ml / a serving» with the serving size as
    text («200 ml», read by `parseServingGrams`; required per serving, optional otherwise,
    then it adds «1 ración»), kcal and protein required, carbs and fat optional (0).
    `newProductPer100g` converts a serving to per 100 (Bolivian milk is labelled per
    200 ml); `newProductProblems` refuses what is impossible for 100 g (> 900 kcal or
    > 100 g of nutrients: a misplaced digit or the wrong basis).
  - **Photo of the label:** optional but asked for, one, downscaled like «Foto o texto»
    (camera or gallery). It goes to Open Food Facts as the nutrition image, so its
    volunteers can check the values; without it they can't. So the form opens with it as
    a highlighted card («Añade una foto de la tabla nutricional · Recomendada», before the
    name, with «Hacer foto» and «Galería»), and Save without any label photo (none taken,
    none waiting, none on Open Food Facts) asks once, in place of the button: «Sin foto, los
    voluntarios…» with «Hacer foto» / «Galería» and «Guardar sin foto» under them. Not when
    editing: the user already answered.
  - **Checking the photo:** once there is one, the card says whose it is («Tu foto de la
    tabla» / «La foto de la tabla de Open Food Facts») and what happens to it («Se envía con
    los valores al guardar», waiting on the device, or «Comprueba que coincide con el envase.
    Si no, cámbiala»). The 76 px thumbnail, with an expand badge, opens a full-screen viewer
    (web `components/foods/label-photo-viewer.tsx` on the dialog, Android
    `ui/foods/LabelPhotoViewer.kt`): labels are small print, so it zooms (web: the zoom button
    or a double click, panning by scrolling; Android: pinch, a double tap or the button) and
    shows the photo itself, not the thumbnail. It is optional: a new photo doesn't open it,
    since the camera app already asks to confirm the shot. «Cambiar foto» (a menu: «Hacer
    foto» / «Elegir de la galería»; web `components/ui/dropdown-menu.tsx`, Android
    `DropdownMenu`) and the viewer's own buttons replace it from the camera or a photo
    already taken; only the user's own photo can be removed (Open Food Facts' one is only
    replaced). The words follow the state: no photo → «Hacer foto» / «Galería»; a photo →
    «Cambiar foto».
  - **Read by AI only when asked:** with AI set up for photos, «Rellenar con la IA» next
    to the photo reads it (`buildLabelReadingSystemPrompt`, `parseLabelReading`: copy the
    numbers as printed, never estimate, null when unreadable; web `lib/ai/label.ts`,
    Android `LabelReader`) and fills the form for the user to check; a name or brand
    already typed stays. Not on taking the photo: the model on the phone can take a while,
    and the user may prefer to type. While it reads, «Cancelar» stops it (Save waits);
    after a failure the button says «Reintentar». It also reads the saved photo (the one
    waiting on the device, or Open Food Facts' one when editing or correcting, downloaded
    from their image server).
  - **What Save refuses** (`newProductProblems`), each with its message under the field:
    no name, no kcal, no protein, no serving size when the values are per serving, values
    impossible for 100 g, and for a correction nothing changed and no photo. Save takes the
    cursor to the first of them, in the fields' order (web `PROBLEM_FIELD` + `focus()`,
    Android `FocusRequester`s), since the form is longer than the screen.
  - **The package's language, not the app's** (`lang` in `OffWriteContext`): Open Food
    Facts keeps names (`product_name_<lang>`) and label photos (`nutrition_<lang>`) per
    language, and a product has one main language (`lang`). The first real test (app in
    French, Bolivian milk in Spanish) saved the Spanish name as the French one and made the
    product French. So a new product asks «Idioma del envase» (web `NativeSelect`, Android
    dropdown; `PACKAGE_LANGS` first, names from `Intl.DisplayNames` / `Locale`), starting in
    the app's language; reading the photo also fills it (`LabelReading.language`, the label's
    ISO 639-1 code), and it is sent as `lc`, `lang` and the photo's field. A product Open
    Food Facts already has keeps its main language (`offKnownProduct` / `parseOffCorrection`
    read `lang`, or `lc`, through `packageLang`): no choice, no `lang` sent, the photo goes
    under that language. Saved with the product (`OwnProduct.lang`), so «Editar» keeps it.
  - **Sent to Open Food Facts, not to us:** straight from the browser (their write
    endpoints allow any origin) or the phone, never through the Blackwater server, with or
    without a Blackwater account. `POST /cgi/product_jqm2.pl` with `offProductFields` (the
    label as printed: `nutrition_data_per`, `serving_size`, `nutriment_<id>` + unit;
    `add_brands`, never replacing; a product Open Food Facts already had keeps its name),
    then `POST /cgi/product_image_upload.pl` (`imagefield=nutrition_<lang>`, the JPEG as
    `imgupload_nutrition_<lang>`). Their answers: `offFieldsOutcome` / `offImageOutcome`
    (ok, retry on 408/429/5xx/offline, account, rejected). Open Food Facts no longer takes
    anonymous edits («no user credentials»), so every write carries an account: the
    shared `blackwater-macros-app` (`OFF_APP_USER_ID`/`OFF_APP_PASSWORD` in both cores; the
    password is public on purpose, as in other open-source apps: it only says «sent by this
    app», RELEASING.md), or the user's own from Ajustes → Open Food Facts. Each write also
    sends `app_name`, `app_version` and `app_uuid` (a random id per install/browser, so
    moderators can block one install, not the app). Without the app password everything
    stays pending.
  - **Kept on the device:** web `localStorage["bw:own-products"]` (`lib/foods/own-products.ts`;
    in the demo the tab's `sessionStorage`, and nothing is sent, §6.1),
    Android `own_products` prefs (`data/foods/OwnProducts.kt`); 50 at most (the oldest sent
    ones are forgotten). Not synced: Open Food Facts is where the product lives. The copy
    exists because a write can fail (Open Food Facts asks apps to queue writes until
    confirmed) and because Open Food Facts caches lookups, «not found» included (a second
    request for an unknown code came back `x-cache-status: HIT`), so the next scan checks
    the device first (`lookupBarcode` / `AddFoodViewModel.lookUpBarcode`). It also gives
    «Tu producto · Editar» on the portion screen (only on the device that added it), the
    status line (added / will be sent when online / the account or the product refused)
    and «Tus productos» first in search (`searchOwnProducts`). An edit is sent again.
  - **The photo** waits only until Open Food Facts has it: web in the same record (base64),
    Android in `noBackupFilesDir/off-photos`, deleted once sent (or refused for good).
    «Editar» shows it in the form while it waits («Espera en este dispositivo…»); once sent,
    the form shows the one Open Food Facts has (`fetchLabelImage` /
    `OpenFoodFactsClient.labelImage`: `image_nutrition_url` with `lc`, loaded after the form
    opens; nothing when offline). Only `https://images.openfoodfacts.(org|net)/` is loaded
    as a photo. A new photo replaces the one shown.
  - **When it is sent:** web right after saving, when the app opens and on `online`
    (`OwnProductUploads` in the layout); Android through WorkManager (`OwnProductUploadWorker`,
    unique work `own-products`, needs a connection, linear backoff), enqueued on save, on
    an account change and on open when something is pending.
  - **The user's own account** (optional): checked with `POST /cgi/auth.pl`; web
    `localStorage["bw:off-account"]`, Android `off_account` prefs with the password
    AES-GCM-encrypted by a Keystore key and the file excluded from backups (like the AI
    keys). Products the old account couldn't send are tried again.
  - **Trying it** without touching the real database: their test server,
    `NEXT_PUBLIC_OFF_SERVER=https://world.openfoodfacts.net` (web) /
    `-PoffServer=https://world.openfoodfacts.net` (Android), with a test-server account
    entered in Ajustes → Open Food Facts (production and test accounts are separate).
- **«Corregir valores»** (a product Open Food Facts has, but its values don't match the
  label in hand: the recipe changed, or it was typed wrong): a quiet link on the portion
  screen of any Open Food Facts product that isn't already the user's («¿Los valores no
  coinciden con la tabla? Corrígelos»). Open Food Facts is a wiki: any account may change a
  product's values, every change stays in its history with who made it, and moderators can
  revert it, so the label photo matters most here. It reuses «Nuevo producto»:
  - **It starts from what Open Food Facts has now**, fetched again
    (`fetchCorrection` / `OpenFoodFactsClient.correction`, with `nutrition_data_per`) and
    read by `parseOffCorrection` (`OffCorrection`: `per` = how it stores the values, and
    `base` = name, brand, serving size and its values in that basis, missing ones empty).
    Offline (or gone) the link says it couldn't reach Open Food Facts and stays put.
  - **The form** shows the name and brand as text (never sent: `existing`), a line asking
    to check that the barcode and the package size match, and under each value that
    differs «Open Food Facts: 64» (`correctionBaseline`: its values converted to the
    form's basis and serving; `correctionChanges`: what differs by more than 0.5 kcal or
    0.05 g, an empty field changes nothing). Save refuses a correction that changes
    nothing and brings no photo (`errors.unchanged`).
  - **Sent in the basis Open Food Facts stores** (`OffWriteContext.keepPer` →
    `newProductInBasis`): Open Food Facts keeps one `nutrition_data_per` per product and
    reads every entered nutrient in it, so switching a product stored per 100 g to «per
    serving» while sending only our four values could change the meaning of the ones we
    don't send (sugars, salt…). A label per 200 ml is converted to per 100 ml before
    sending (and a per-100 label to per serving for a product stored per serving, which
    then needs the serving size: `newProductProblems(product, keepPer)`).
  - **Its label photo is shown** («La foto de la tabla que tiene Open Food Facts»), to
    compare with the package in hand: `image_nutrition_url` asked with `lc=<lang>` in the
    same request; it opens in the viewer and «Cambiar foto» replaces it (camera or gallery).
  - **Then like an added product:** kept on the device with its `correction` (so «Editar»
    shows the same hints and keeps the basis), «Tu corrección · Editar», «Corregido en Open
    Food Facts», sent and retried the same way.
- The amount screen (`components/foods/portion-picker.tsx`, Android `PortionView`) starts at
  one serving or 100 g; it offers «100 g» and «1 ración (x g)» only when the food has a
  serving — alone, «100 g» repeated what the field already said.
- A picked food + grams becomes one ingredient row (`foodToIngredient`) in the review
  form; inside the form «Buscar alimento» appends more. Recent picks: `localStorage`
  (web; in the demo the tab's `sessionStorage`, §6.1) / SharedPreferences (Android), 20 max.

## 8.6 AI providers and keys

**Principles (do not break these).**
1. **Simple.** Not MyFitnessPal: every screen has one job and fits «day of meals →
   recommendations → weight». No streaks, social, badges, micronutrients, recipe database.
2. **Your data stays yours.** Local-first on Android (no account, offline); the web needs login.
   AI features store nothing new on our server.
3. **API keys never leave the device/browser** except to the chosen provider: not synced, not
   sent to the Blackwater server, excluded from Android backups.
4. **Photos are never stored:** kept in memory (or a cache file deleted at once), downscaled,
   sent, discarded — never in the gallery, the database or the server. The one exception is
   the label photo of a product the user adds (§8.5): it is published on Open Food Facts,
   sent straight there, and waits on the device only until it is sent.
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
| D13 | A product Open Food Facts lacks is added from its label and always sent to Open Food Facts (no «this device only» option: the text above Save is the consent), with the app's shared account or the user's own; the device keeps only a copy for the next scan, editing and pending uploads (§8.5). Values that don't match the label are corrected the same way, in the basis Open Food Facts stores. |
| D14 | Questions about the food log: the model only turns the question into a request, the app computes, the model explains (§8.7); switch «El coach puede ver lo que como», on by default. |

**What the coach sees** (`buildCoachContext`, core, exact text tested): profile (the goal
worded by whether the user does strength training, plus a recomposition note when they do,
§8.2.1), targets, BMR,
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
- **Settings** (one key + model per provider, base URL, «El coach puede ver mis datos», «El coach puede ver lo que como»):
  web `lib/ai/settings.ts` in `localStorage["bw:ai"]` (per browser; in the demo the
  tab's `sessionStorage`, §6.1, so a key typed there goes with the tab), card
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
  module state, Android activity-scoped ViewModel). Before AI is set up the tab says what the
  coach does and offers three example questions with the setup (Android `CoachSetupInvite`);
  a tapped example opens the setup and then waits in the box once the coach is ready (web
  `sessionStorage["bw:coach-pending-question"]`, Android `CoachViewModel.pendingQuestion`). It
  is never sent by itself: sending passes the user's data to the AI provider. Each question sends
  `buildCoachSystemPrompt(language, buildCoachContext(input))`, the input read fresh from the
  same data as the Comidas card (profile, targets, measured expenditure, 4 weeks of meals,
  60 days of weigh-ins); with «El coach puede ver mis datos» off the data is neither read
  nor sent. A question about the food log first goes through §8.7 (two more steps). The last 20 good turns go along as history. Cloud answers get
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
  **Copying** a message takes all of it, as in messaging apps (no text selection): a long-press
  on Android (`copyText`; Android 13+ confirms the copy itself, older versions get a toast), a
  copy button on the web, shown on hover with a mouse and always on a touch screen. The
  coach's answer is copied as it reads (no `**`), the user's words as typed. An answer still
  being written can be copied too (what has arrived so far): the phone's model can take a while.
- **On-device AI** (Android only, D5/D6): a model from the `LocalModels` catalog (Gemma 4 E2B
  default, Gemma 4 E4B, Qwen3 1.7B text-only; Apache-2.0, SHA-256 pinned; one on the phone at
  a time) run by LiteRT-LM (`data/ai/local/`). Downloaded
  on request by a WorkManager foreground job (Wi-Fi by default, resumable, checksum) into
  `noBackupFilesDir/models`. `AiSettings.photoEngine` / `coachEngine` choose cloud or phone;
  `usableEngine` decides readiness. `LocalEngine` loads once and serves one request at a
  time, **on the CPU only** (a Mali GPU keeps a second copy of the weights: Gemma 4 E4B was
  killed for memory on an 8 GB phone). The CPU engine keeps a working copy of the weights in
  `cacheDir` (`<model>…xnnpack_cache`, deleted with the model); the image encoder loads only
  for a request with photos. Loading takes ~20 s, so the coach **prepares** it
  (`LocalEngine.prepare`) while the user types: when the Coach tab opens (`onScreen`), on
  «Nueva conversación», and with the image encoder when a photo is added. The coach's
  conversation is **kept between questions**: a follow-up reads only the new question, not
  the system prompt and every earlier turn again. It is kept only while it holds exactly
  what the next question needs (`canContinueChat`: same system prompt, so a meal logged
  since starts afresh; the same earlier turns; room in the window by its `getTokenCount`);
  a stopped or failed answer, a meal estimate (one conversation at a time) and a reload
  drop it. A loaded model holds ~4 GB: it stays loaded while the Coach tab is on screen
  (`hold`), is **released** 60 s after the tab is left or the last request, and at once when
  the app goes to the background (`MainActivity.onStop`, also when the camera opens). Needs
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
- **First launch** (D12): every run of the steps (also «Ver tutorial») opens on a welcome
  page before asking for anything: logo, tagline and five points (log in seconds, targets,
  «Ciencia totalmente transparente», checked against your real data, private and free;
  `onboarding.welcomeTagline`, `feature*`, `science*`), same texts and order on both
  platforms; the step counter starts after it. Web `/bienvenida` (welcome → your data →
  optional Google key → done) after a
  login with an incomplete profile, flag `localStorage["bw:onboarding-done"]`; Android route
  `bienvenida` (welcome: «Empezar», and a quieter «Tengo una cuenta» → login, or «Saltar»
  when opened from Ajustes with an account → data → AI → done) only on a fresh install (no account, nothing logged), flag
  `AppPreferences.onboardingDone`. After logging in, `afterLogin()` runs the first sync itself
  («Descargando tus datos…», up to 15 s): an account with a complete profile and a weigh-in
  skips «Tus datos»; the AI step always shows (the key is per phone). «Ver
  tutorial» in Ajustes on both.

---

## 8.7 Food questions (`lib/core/food-questions/`)

The coach answers questions about the user's own food log («¿Cuánta leche chocolatada tomo?»,
«¿Como más el fin de semana?», «¿Cambió mi peso desde que dejé la cerveza?»). The model never
computes a number: small local models add up badly and a wrong average would read as a fact.
The statistics rules are in [METHODOLOGY.md](./METHODOLOGY.md) §6.1.

**Three steps per question** (web `lib/ai/coach-chat.ts` → `lib/ai/food-questions.ts`, Android
`CoachViewModel.readFoodQuestion`):

1. **The question becomes a request.** A separate, short call: system prompt
   `buildFoodRequestPrompt(today, language, previous)` (no data summary, so it fits the phone's
   4096-token window), the question as the only message, JSON mode. Cloud: `aiComplete` /
   `AiClient.complete` with `requestMaxTokens` (thinking models spend part of it); browser:
   `browserCompleteJson` (WebLLM's JSON mode); phone: `LocalEngine.completeAside`, a short
   conversation of its own **next to** the coach's kept one (which stays, so the answer in step 3
   does not read the system prompt again). `parseFoodRequest` reads the answer: an analysis from
   the registry or `none`, its fields (search words, dates `YYYY-MM-DD`, metric, split); a field
   the analysis does not use is dropped, a wrong value, a missing required field or a period
   that ends before it starts makes it unreadable. `previous` is the conversation's last request
   (kept on the question's `ChatMessage.foodRequest`) so «¿y el mes pasado?» keeps the foods.
2. **The app computes it** from the whole log (web `loadFoodLog`: every meal up to today and
   every weigh-in; Android: the phone's database): `answerFoodRequest(log, request)` →
   `result` (a text block for the model plus `FoodDetails`), `no_match`, `not_enough`, or null
   (not answerable as asked).
3. **The model explains it.** The usual coach call (same system prompt and history); the
   question is sent as `foodQuestionMessage(question, result)`: the user's words, a blank line,
   «FOOD LOG RESULT…» (≤ `resultMaxChars` = 1200 characters, ≈ 400 tokens, cut at whole lines).
   It is kept as the question's `modelText`, so follow-ups and the phone's kept conversation
   read the same text; the bubble shows the words as typed.

**When the app answers itself** (no model call in step 3): an unreadable request or one not
answerable as asked («No entendí bien… Por ejemplo: ¿Cuántas veces por semana como «Avena»?»,
with the user's most-logged food), `no_match` (what was searched, the period, when it was last
logged, similar names) and `not_enough` (how many days there are and how many are needed). These
replies (`byApp` on the web, `ChatMessage.foodReply` on Android, written in the app language by
the screen) and their questions are **left out of the history** the model sees, like failed
answers, so the phone's kept conversation stays valid. `none` → the question is answered as
before. A question with photos skips step 1.

**«Cómo se calculó»** under the answer (web `FoodDetailsPanel`, Android `FoodDetailsPanel` in
`ui/CoachFood.kt`), closed until tapped, written by the app from `FoodDetails`: the analysis, each
part of the log with its logged days, where a food-vs-weight period was split and why, the names
counted, entries without an amount, «few days», «unlogged days are not zero», «an association is
not a cause», and a link to Metodología → «Preguntas sobre tus comidas» (`#food-questions`).

**The switch** «El coach puede ver lo que como» (`AiSettings.coachSeesFood`, on by default,
only with «El coach puede ver mis datos»): its line says where food names and amounts go: to the
provider in the cloud, recommending a local model; nowhere with the browser's or the phone's
model. Off: no step 1.

**Example question.** With the switch on, the Coach's examples start with one about the user's
own most-logged food of the last 4 weeks (`mostLoggedFood`; web `useFoodExample`, Android
`CoachViewModel.foodExample`), or «¿Qué alimentos me dan más proteína?» with an empty log.

**Built to grow: one registry, one data file.**

- `food-questions.json` holds everything that can be data, for both platforms: the rules (minimum
  days, default periods, caps), the step-1 prompt, each analysis's description, fields and
  required fields, the examples, and the result's text templates. The web imports it;
  Android's `:core` bundles the same file as a resource (`processResources` in
  `core/build.gradle.kts`) and reads it with `FoodQuestionsSpec`. The step-1 prompt is generated
  from it, so a new analysis is described to the model by adding its entry.
- Code, written twice (TypeScript in `lib/core/food-questions/`, Kotlin in `:core`
  `foodquestions/`): `log` (food items, «Solo total» titles, name matching, g/ml amounts, date
  ranges), `request` (prompt and parsing), `answer` (shared helpers, `FoodAnswer`,
  `FoodDetails`, the `Analysis` interface), one file per analysis in `analyses/`, and the
  registry `ANALYSES` (`index.ts` / `FoodQuestions.kt`).
- Shared expectations: `tests/fixtures/food-questions/` (a sample log, the step-1 prompt, and
  cases: the model's raw answer → the request → the app's answer, with exact texts). Both
  `tests/unit/food-questions.test.ts` and `FoodQuestionsTest.kt` read them, so the two
  implementations cannot drift. The fixtures were written by the TypeScript code and checked by
  hand; regenerate them only on purpose, and review the diff.

**Adding an analysis:**

1. Its entry in `food-questions.json` → `analyses` (description, fields, required) and any new
   result templates; new fields also go in `fields` (and in `FoodRequest` / `parseFoodRequest`
   on both platforms).
2. One file in `analyses/` per platform, implementing `Analysis.answer`, plus one line in
   `ANALYSES` (and an `AnalysisId` entry in Kotlin).
3. Cases in `tests/fixtures/food-questions/` (both suites pick them up) and an example in the
   JSON if the model needs one.
4. Its name for «Cómo se calculó»: `coach.food.analyses.<id>` in the five `src/i18n/*.json` and
   one line in `android/app/strings-from-web.json`; Metodología and METHODOLOGY.md §6.1 if it
   adds a rule.

The coach screens, the providers and the AI clients do not change.

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
- **Touch targets.** `components/ui/button.tsx` gives every size a minimum on touch screens
  (`pointer-coarse:min-h-*`, towards 44 px); with a mouse the compact sizes stay. A minimum,
  not a height, so a button made bigger on purpose keeps its size. Small icons in a row get a
  44 px box with a negative margin (the meal's drag handle). Android uses 48 dp (`IconButton`).
- **Floating buttons** (Comidas' +, Progreso's weigh-in) sit at the right edge of the 42 rem
  content column on wide screens (`right-[max(1rem,calc(50%-21rem+1rem))]`), not in the
  window's corner.

**Shared building blocks** — reuse them instead of copying the code again:

| Need | Web | Android |
|---|---|---|
| A caught error as a toast text | `errorText(error)` (`lib/api.ts`) | `ResponseErrorMapper` (`UiText`) |
| A page's title (centred, an optional control at each side) | `PageHeader` | `CenteredTopAppBar` |
| A page opened from Ajustes (Back at the start) | `SubpageHeader` | `BackTopAppBar` |
| A chart's legend | `ChartLegend` (`components/chart-legend.tsx`) | `LegendDot` (`chart/TrendChart.kt`) |
| «Label: value» in the language's punctuation | `t.common.labelValue` | `R.string.label_value` |
| A meal's or template's content to send or store | `mealContent` / `storedMealContent` | `Mappers.kt` `toEntity` |
| A setting kept in this browser | `localJsonStore` | `SharedPreferences` stores |
| A chart tooltip | `TOOLTIP_STYLE` (`components/chart-style.ts`) | `drawSelection` (`chart/TrendChart.kt`) |
| «g/kg» of the protein recommendation | `proteinPerKgText` | `proteinPerKg` |

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
npm run db:check                               # does the database have every table, column and enum value the code uses?
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
| `unit` | node | Pure-function edge cases (`stats`, `dates`, password vectors, the export file text, the database check before a build) |
| `behavior` | node | Black-box requirements written in Spanish, exercising **services** through injected in-memory fakes — no HTTP, no DB |
| `behavior-ui` | happy-dom | Renders actual pages and components (`today-page`, `progreso-page`, `ajustes-page`, `perfil-page`, `recommendations-card`, `day-navigator`, …) with mocked `@/lib/api` |

Notes:
- **happy-dom, not jsdom** (avoids jsdom's `require(esm)` chain). CI runs Node 22; the
  tests also pass on Node ≥ 25. Node 25+ has its own `localStorage` global; Vitest 5
  replaces it with happy-dom's (Vitest 4 didn't, and the 43 AI/coach/onboarding tests
  failed), so keep Vitest ≥ 5 and `@types/node` ≥ 22.
- Demo-mode store tests live in `tests/behavior/demo-mode.test.tsx` (happy-dom,
  because `sessionStorage` + `document.cookie` are needed). The shared stats
  builder is a pure, node-safe unit test in `tests/unit/stats-builder.test.ts`.
- Config file must be `vitest.config.mts` (package has `"type": "module"`).
- The `@/` alias must be declared **inside each project's** `resolve.alias`.
- Behavior-test philosophy: assert user-visible outcomes ("los cambios al editar se
  ven inmediatamente"), never internals. If you add a feature, add its behavior
  test first.

Verification gate before pushing (web, core contract, Android). `npm run build` first checks
the database against the code (§12); with `.env.local` that is the shared database, so a
schema change without `db:push` fails here, before it reaches `dev`:

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
| `protein.test.ts` | `calculateProteinRecommendation` (maintain with strength training), reference weight at BMI 25 | `ProteinTest.kt` |
| `calories.test.ts` | `calculateBMR`, activity (PAL), `doesStrengthTraining`, `calculateCalorieRecommendation` (BMR floor, built on a measured expenditure) | `CaloriesTest.kt` |
| `profile-history.test.ts` | `withProfileChange`, `profileOn`, `weightOn`, `bodyFatOn`, `targetsOn`, `averageTargets` (each day with its week's measurement) | `ProfileHistoryTest.kt` |
| `expenditure.test.ts` | `estimateExpenditure`, `measureExpenditure`, `fitWeightTrend`, `dailyMeans`, `checkExpenditure` (verdicts, confirmation, what is missing), `showsGapNotice`, `onlyWeighInsMissing`, `approximateGap`, `combineExpenditure`, `weeklyExpenditure` | `ExpenditureTest.kt` |
| `foods.test.ts` | portions, Open Food Facts parsing, shop labels and why a barcode missed, `searchGenericFoods` | `FoodsTest.kt` |
| `new-product.test.ts` | «Añadir este producto»: per serving → per 100, form problems, the Open Food Facts write fields and answers (in the package's language; `lang` only for a new product), `offKnownProduct` (with its main language), `packageLang`, `searchOwnProducts`, reading a label with AI (and its language); «Corregir valores»: `parseOffCorrection`, `newProductInBasis`, `correctionBaseline`/`correctionChanges`, the fields in the stored basis | `NewProductTest.kt` |
| `ai-providers.test.ts` | `buildAiRequest` (byte-identical bodies), answer and stream parsing, how a stream ended (`parseAiStreamEnd`), `aiErrorKind` | `AiProvidersTest.kt` |
| `ai-models.test.ts` | `buildModelListRequest`, `parseModelList`, `naturalCompare` | `AiModelsTest.kt` |
| `ai-schema.test.ts` | `parseMealEstimate`, prompts, `estimateToIngredients` | `AiSchemaTest.kt` |
| `progress.test.ts` | `macroAverages` | `ProgressTest.kt` |
| `numbers.test.ts` | `formatDecimal`, `normalizeDecimal` (the one number format, §6) | `NumbersTest.kt` |
| `food-questions.test.ts` (+ `tests/fixtures/food-questions/`, read by both) | food questions (§8.7): amounts, names, matching, the step-1 prompt, every shared case (request → answer, exact text), the registry | `foodquestions/FoodQuestionsTest.kt` |
| `coach.test.ts` (+ `coach.fixture.ts`) | `weightProjection`, `buildCoachContext` (exact text; goal text with and without strength training), `buildCoachSystemPrompt` | `CoachTest.kt` |
| `dates.test.ts` | date keys, `weekStartOf`, `parseLocalDateTime`, `datetime-local` values | `DatesTest.kt` |
| `stats.test.ts` | moving average, weekly rate, series | `StatsTest.kt` |
| `stats-builder.test.ts` | `buildStatsFromData` | `StatsBuilderTest.kt` |
| `backup.test.ts` | `buildBackup` (the exact file text), `backupFileName` | `BackupTest.kt` |

**The rule:** changing a core algorithm means changing the TS implementation, its TS test, the
Kotlin implementation and its Kotlin test **together**. Enforced by `npm run core:sync-check`
(`scripts/check-core-sync.ts`), driven by `android/test-sync/manifest.json` (one row per pair):
it warns locally and **fails CI** when only one side changed; `npm run hooks:install` adds a
pre-commit reminder. Adding or renaming a core domain = updating the manifest and this table.

Food questions go further (§8.7): their rules, prompt and text templates are one JSON file both
platforms read, and their expectations are fixture files both test suites read.

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
| `data/OfflineSyncTest.kt` | Real in-memory Room + real Retrofit + `FakeServer` (MockWebServer behaving like the Next.js routes): local-only mode never touches the network, and no request but the login leaves without an account; offline saves upload once, retries never duplicate; edits, deletes, reorders upload; web changes are pulled (edits, deletions, a new order, macros added to a meal), also when one list can't be downloaded (the others are saved, the cloud turns grey after an hour) or uploaded (its rows wait, the other lists still go up and come down); an edit during an upload is not lost; profile sync with explicit nulls; the profile history (past days keep their targets) syncs both ways, and a server without it leaves the phone's alone; expired session keeps data; login with local data; logout and «delete all data» wipe only the phone; undo of deleted meals, weigh-ins and templates (also after the delete reached the server); the import replaces the phone's data and sends nothing, is refused with an account, and is uploaded only on a later login with «upload»; an HTML/captive-portal answer fails the sync without losing data; the header cloud stays green when only the download fails, grey (not red) when the server saved a change but the answer was lost or answered «try again later», and red while the server refuses a change |
| `data/MultiAccountTest.kt` | Several accounts on one phone (Android only), with real Room + Retrofit and a two-user `FakeServer`: a single-account install keeps its files; no «Añadir cuenta» without an account; a new account starts empty and downloads only its own data; switching shows each account's meals; no duplicates; wrong password/offline adds nothing; accounts and the active one survive a restart; expired sessions show in the switcher; background sync uploads every account's changes to the right account; logging out of one removes only its data, of the last one goes back to local mode |
| `ui/AddAccountLoginTest.kt` | The login screen in «Añadir cuenta» mode: connects without the local-data question; wrong password / no network / empty fields read like a normal login |
| `data/ReleaseRulesTest.kt` | Every `ApiService` response type is a `@Serializable` class of `data`, and `proguard-rules.pro` keeps them all (R8 removed an unread response and broke the profile sync in release builds, §14.7) |
| `data/ApiContractTest.kt` | Wire format against MockWebServer (mirrors `tests/behavior/routes-*.test.ts`): auth, `PUT /:id` upserts, deletes, settings with explicit nulls (also each period of `profileHistory`; no history = key left out), admin; status codes and error envelopes |
| `data/ResponseErrorMapperTest.kt` | Server error codes → the app's `server_error_*` strings; unknown code → the server's text; status fallbacks; the code list equals the web's (`src/i18n/es.json`) |
| `data/BackupImportTest.kt` | Reads the web's export file; not an export, a newer format, and one wrong value (with where it is) refuse the whole file; text trimmed as the server stores it |
| `data/foods/FoodSourcesTest.kt` | Open Food Facts client (barcode, why a code missed and the name it already knows, errors, Spanish search, User-Agent, the values, basis and label photo for «Corregir valores», a photo only from Open Food Facts' image server); the bundled index loads with Spanish names; recent foods |
| `data/foods/OwnProductsTest.kt` | «Añadir este producto» (mirrors `add-food-new-product.test.tsx`): values then the label photo go to Open Food Facts with the app's or the user's account and the photo file is deleted; offline / 503 waits and the next run sends it; a refused account is retried after changing it; a refused product says why and stops; an edit is sent again; nothing is sent without the app password; a correction is sent in the stored basis without the name and keeps it after a restart and an edit; the account check; the password encrypted and kept out of backups |
| `data/ai/AiClientTest.kt` | AI client against MockWebServer (never a real provider): «Probar», whole and streamed answers, error kinds; a stream cut by the token limit, by an error inside the stream or by the connection fails after the text that arrived, even when the screen reads slowly; keys encrypted per provider; AI prefs excluded from backups |
| `data/ai/ModelListStoreTest.kt` | The model dropdown's list: reused for a day, asked again when old, forced or for another key; survives a restart; never stores the key |
| `data/ai/MealEstimatorTest.kt` | «Foto o texto»: photos + description → one JSON request (mirrors `add-food-photo.test.tsx`); unreadable answers; the language told to the model; photo downscale; the camera FileProvider only reaches the temporary folder |
| `data/ai/local/LocalEngineTest.kt` | On-device model: when the coach's kept conversation takes the next question (same system prompt and turns, room in the window) and when a new one starts |
| `data/ai/local/LocalModelTest.kt` | On-device model: resumable download, SHA-256 check, device support and the low-RAM warning, engine routing, model outside backups, what counts as an out-of-memory kill |
| `data/ai/local/LocalModelCatalogTest.kt` | The site's model catalog: valid entries become models, wrong ones are skipped (non-Hugging Face URL, bad checksum, path tricks, newer app), merge with the built-in list, `hidden`; the committed file matches the built-in models |
| `ui/CoachTest.kt` | Coach (mirrors `coach-page.test.tsx`): streamed answer with the data summary, history until «Nueva conversación» (earlier answers resent as written), copying a whole message (also one still being written), failures not resent, a cut-off answer keeps its text and its reason, the phone model's history fits its window, no data without permission, photos (alone with the default question, and in the history), Markdown |
| `ui/CoachFoodQuestionsTest.kt` | Questions about the food log (mirrors `coach-food-questions.test.tsx`): step 1 is a short JSON request, the answer reads the app's numbers, «Cómo se calculó», the app's own replies (no match, not understood with an example of the user's food) are not resent, `none` answers as usual, no step 1 without the switch, a follow-up carries the previous request |
| `ui/OnboardingTest.kt` | First steps (mirrors `onboarding.test.tsx`): only a fresh install sees them; they open on the welcome page, also from «Ver tutorial» with an account (which neither syncs nor skips ahead); ranges; «Tus datos» saves profile + weight; after logging in, an account with data skips «Tus datos» |
| `data/NetworkHostsTest.kt` | Every host in the app's code is in the list the README promises (privacy) |
| `data/update/UpdateCheckerTest.kt` | New version notice: versions compare by number, GitHub asked at most once a day and never by another key's build (F-Droid) or with the switch off, «Not now» hides only that version, errors stay silent; «Check now» asks right away even with the switch off, says up to date / failed (keeping what it knew), shows the newest version and brings back one hidden with «Not now» |
| `ui/ProgressLogicTest.kt` | Progreso: one period for everything, macro averages over logged days, weigh-ins of the period, each logged day's own target in the band and the average |
| `data/local/MigrationTest.kt` | Updating the app keeps the phone's data: a version 1 database built from `app/schemas/…/1.json` opens with today's code through `MIGRATIONS` (the profile gets an empty history); a version 2 one keeps its weigh-ins, which count as fasted with no time zone |
| `data/AppPreferencesTest.kt` | «Ahora no» on the expenditure notices survives a restart |
| `ui/NewProductFieldsTest.kt` | «Idioma del envase»: a new product starts in the app's language and takes the one the AI read; one Open Food Facts has keeps its own |
| `ui/NewProductViewModelTest.kt` | «Nuevo producto» (mirrors `add-food-new-product.test.tsx`): a photo alone reads nothing until «Rellenar con la IA»; Save without a label photo asks once and «Guardar sin foto» saves; editing doesn't ask; the AI reads the photo waiting on the phone |
| `ui/ValidationTest.kt` | Form limits (same as `src/server/validation.ts`), recommendation states, a past day's targets (its profile and weight), measured expenditure, the profile check and the formula inputs behind its «¿Por qué?», sync indicator states (red only for an expired session or a refused change, grey once the server has failed for over an hour), numbers edited with a decimal comma, when «Añadir comida» asks before closing, a meal saved as a template (its content, totals only for «Solo total») |
| `ui/MethodologyIndexTest.kt` | Metodología's index: the web's groups and ids in the same order, every section of the page in page order, every section another screen opens |
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
| `routes-export` | `:core` `BackupTest` + `BackupImportTest` |
| `stats-overview`, `unit/stats-builder` | `:core` `StatsBuilderTest` |
| `progreso-page` | `ProgressLogicTest` |
| `recommendations-card`, `perfil-page` | `ValidationTest` (`recommend`) |
| `coach-page`, `add-food-photo`, `ai-settings`, `onboarding` | `CoachTest`, `MealEstimatorTest`, `AiClientTest` + `ModelListStoreTest`, `OnboardingTest` |
| `coach-food-questions` | `CoachFoodQuestionsTest` |
| `add-food-new-product` | `OwnProductsTest`, `NewProductViewModelTest` (the form's layout and focus, the photo viewer and «Cambiar foto» menu: not automated, checked on a device) |
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
- [ ] DB schema changed → `db:push` **before pushing to `dev`** (Vercel builds production from
      it; its database check fails the deploy otherwise, §12), `tests/helpers/repos.ts`, the Android wire models, Room
      entities **with a Room migration** (users without an account have no other copy).
- [ ] New UI text → the five `src/i18n/*.json`; on Android, one line in `strings-from-web.json`
      (or, for an Android-only text, the five `strings.xml`). See §6 «i18n».
- [ ] Phone-model catalog edited → `npm test` (and `LocalModelCatalogTest`) before pushing.

---

## 12. Deployment topology

```
app.blackwater-macros.jordanmontt.fr ── HTTPS ── Vercel (Hobby)       web app + API
  (browsers, Android after login)                ├─ static pages + proxy.ts (edge)
                                                 ├─ serverless route handlers ── pooled ─┐
                                                 └─ env var DATABASE_URL (Production)    ▼
                                                                    Neon Postgres (single project)
blackwater-macros.jordanmontt.fr ── HTTPS ── GitHub Pages             the app's website (website/)
github.com/…/releases/latest ────────────── GitHub Releases          the signed APK (release.yml)
```

**Domains:** DNS at OVH (zone `jordanmontt.fr`): `app.blackwater-macros` → `CNAME` to the
target Vercel gives, `blackwater-macros` → `CNAME jordanmontt.github.io.`. The web app lived on
`blackwater-macros.jordanmontt.fr` until 1.0.1; `website/404.html` forwards old links to `app.`.
The Android app's server address (`API_BASE_URL`) is built into every APK, so it must never
change again without keeping the old one working.

Vercel deploys the **`dev`** branch (Project → Settings → Git → Production Branch). Current
setup intentionally shares **one Neon database between local dev and production** — simplest mental model, and `create-user` run locally takes
effect immediately on the live site. To split environments later: create a second
Neon project, point Vercel's `DATABASE_URL` at it, run `db:push` + `create-user`
(and `set-admin` for the first admin) against that URL locally.

**The database is checked before every build** (`prebuild` → `scripts/check-db-schema.ts`,
the comparison in `src/server/db/schema-check.ts`): every table, column and enum value of
`schema.ts` must exist, and no column the database requires (NOT NULL, no default) may be
unknown to the code. Vercel sets `VERCEL=1`, and there a mismatch or an unreachable database
fails the build, so the version already online keeps running and Vercel e-mails the failure.
Fix: `npm run db:push`, then redeploy. Locally it reads `.env.local`; an unreachable database
only warns (builds work offline); without `DATABASE_URL` (CI, previews) it is skipped. Why: on
24 Nov 2026 a new weigh-in column went live without `db:push`; `/api/weights` answered 500 for
hours, phones stopped downloading (§14.1) and their cloud stayed green.

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
| Add user settings | add column to `users` table + enum if needed → `settings-repo.ts` + `settings-service.ts` → `PUT /api/settings` route → Perfil/Ajustes page → read via session endpoint. Android: `ProfileEntity` + migration (§14.2), `WireCalorieProfile`, `profileBody` (explicit nulls) and the Perfil screen. A field that changes the targets is kept per day too: add it to `ProfilePeriod` (`profile-history.ts`), `profilePeriodSchema` and `WireProfilePeriod` |
| New field on meals/templates/weights (synced) | DB column + zod schema + DTO (web); Android: wire DTO, Room entity **+ `Migration`** (bump `LocalDatabase` version; never destructive: local-only users have no other copy), mappers, `FakeServer`; add a round-trip case to `OfflineSyncTest` |
| New page | `src/app/<slug>/page.tsx`, nav entry in `components/app-nav.tsx`, copy in the five `i18n/*.json`; proxy already protects it. Android: screen + route in `MainActivity`, texts as in «New UI text» |
| New UI text | add it to the five `src/i18n/*.json` (a missing key fails the build; `i18n.test.ts` checks `{placeholders}`). Android: map it in `android/app/strings-from-web.json` (`"key": "path"`, or `["path", "n:d"]` with arguments) and use `stringResource`; Android-only texts and plurals go in the five `res/values*/strings.xml` (`TranslationsTest`) |
| New question the coach answers from the food log | «Adding an analysis» in §8.7: an entry in `food-questions.json`, one file per platform in `analyses/`, one line in `ANALYSES`, cases in `tests/fixtures/food-questions/`, its name in the five `src/i18n/*.json` + `strings-from-web.json` |
| Change the logo | edit `scripts/logo/render.html`, run `scripts/logo/render-icons.sh` (writes every Android and web icon); seasonal logo: `render-icons.sh <variant>`, back with no argument |
| Release the Android app | docs/RELEASING.md (tag → signed GitHub Release + website; F-Droid builds the tag) |
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
- **New version notice (website APK only).** `data/update/UpdateChecker.kt`: if the APK is
  signed with the release certificate (`releaseCertSha256` in `android/gradle.properties` →
  `BuildConfig.RELEASE_CERT_SHA256`, compared with the installed signature), the app reads the
  latest GitHub release at most once a day on open; a newer version shows a card under the
  date in Comidas (Download opens the website, «Not now» hides that version). Ajustes has one
  row for it (`UpdateCheckRow`): the switch is the daily check (on by default) and «Check now»
  asks right away (`checkNow`, with the switch on or off, run in `AppGraph`'s background scope
  so leaving the screen doesn't cut it). Under the hint the row says «Last checked: …», then
  «Checking…» in the button, «You have the latest version (x)», «Version x is available» with
  Download (and «Check now» to refresh: it always shows the newest version known, and the
  website always serves the newest APK), or «Couldn't check…» with «Retry»; the status is a
  TalkBack live region. A manual check forgets «Not now», since the user asked. F-Droid signs
  with its own key, so its build never asks GitHub and shows neither (NewPipe's way;
  `UpdateCheckerTest`). To try it with a debug build: `-PreleaseCertSha256=<debug cert>`, and a
  lower `versionName` to see a newer version.
- **Offline.** A save is on disk before the UI returns; with an account the row is
  flagged `pending` and uploaded by the next sync (app start/resume, 2 s after any
  edit, when the network comes back, or «Sincronizar»).
- **Sync = push, then pull.** Push: each pending row → `PUT /api/<kind>/:id`
  (phone-generated UUID, idempotent) or `DELETE` for tombstones (404 = done). Pull:
  full lists replace every non-pending row; rows gone from the server are removed.
  **Each list on its own** (meals, templates, weigh-ins, profile), both ways: a list the
  server can't take now (`408`/`5xx` on its upload) keeps its rows pending, and one it
  can't send is skipped; the run still fails (retried, shown), but the other lists go up
  and come down. On 24 Nov 2026 production lacked a weigh-in column, `/api/weights`
  answered 500, and with one all-or-nothing run no web change reached the phone (one
  pending weigh-in would also have stopped the meals' uploads). A `429` still ends the run. `lastSyncAt` moves only when the
  whole run succeeds. Last to sync wins per record. A push only clears `pending` if the row was not edited
  again meanwhile (`updatedAt` doubles as a strictly increasing local edit stamp).
- **Login with local data** → dialog: upload it to the account, or discard it.
  Re-login after an expired session keeps everything (another account is refused: a
  second account goes through «Añadir cuenta», see «Several accounts on one phone»).
- **Logout** → a last sync attempt, a warning if changes would be lost, then that
  account's local data is deleted (it is safe on the server). With other accounts on the
  phone one of them becomes active; otherwise the app is back in local mode.
- **Several accounts (Android only, on purpose)** — see «Several accounts on one phone» below.
- **Delete all data** (Ajustes → Tus datos) wipes the phone only, after confirmation.
  Without an account that is permanent; with one, the server is untouched and the next
  sync downloads the account again (unsynced changes are lost — the dialog says how many).
- **Settings:** the web's grouped list (`SettingsGroup` / `SettingsRow` in
  `SettingsComponents.kt`, rows are Material `ListItem`s): **Cuenta** (sync status, sync
  now, switch or add an account, logout; logged out, one «Iniciar sesión» row), **Personal**
  (Perfil, IA, Open Food Facts, templates), **Tus datos** (export; import only without an account, §5.11; delete all), **Aplicación** (theme,
  language, «Check for updates» in own-key builds, tutorial), **Acerca de** (Metodología,
  website `BuildConfig.WEBSITE_URL`, source code, donate, Admin), then the last line «Creado por
  jordanmontt» (`AUTHOR_URL`, opened in the browser). Every row has an icon and
  is one tap away. Goal, body data and recommendations live in a pushed **Perfil** page
  (`ProfileScreen`), like the web's `/ajustes/perfil`.
- **Comidas header** shows a small cloud when logged in (synced / N pending / syncing /
  needs attention); tapping it opens Ajustes. **Red («needs attention») only when a
  change won't reach the server on its own:** an expired session, or a pending change the
  server refused (`syncIndicatorState`). Everything retried automatically (no or poor
  signal, timeouts, `408`/`429`/`5xx`, a Wi-Fi login page) shows the grey cloud with its
  neutral count badge; a failed run with nothing pending stays «synced», **unless the server
  has failed for over an hour** (`downloadsFailing`: the last run ended in `SERVER_ERROR` or
  `UNEXPECTED`, not offline, and `lastSyncAt` is older than `DOWNLOAD_FAILING_AFTER`): then a
  grey crossed cloud, and Ajustes → Cuenta says the web's changes aren't arriving and when it
  last synced (`sync_download_failing`). It used to stay green for the whole outage above. With poor signal
  the server may save a change whose answer never arrives: it stays «pending» until the
  next run re-sends it (same id, no duplicate). Hence OkHttp timeouts of 20 s connect /
  30 s read-write (not the 10 s default) and a **linear** WorkManager backoff (30 s, 1 min,
  1.5 min…), so recovery takes minutes, not hours. Ajustes → Cuenta follows the same
  rule. Deleting a meal, weigh-in or template asks
  first and then offers **Undo** in a snackbar (`offerUndo`; `AppRepository.restoreMeal` /
  `restoreWeight` / `restoreTemplate` put back the same id and position, even if the
  delete already reached the server, with every field: a weigh-in keeps «En ayunas» and
  its time zone). Same on the web (toast with «Deshacer»).
- **Server errors** are shown in the app language: `ResponseErrorMapper` turns the
  envelope's `code` into a `server_error_*` string (`UiText`), see §5.3.
- **Statistics** are computed on the phone with `:core` `buildStatsFromData` (same
  numbers as `/api/stats`, which Android does not call).
- **Validation** uses the server's limits (weight 20–400 kg, fat 3–60 %, profile
  ranges) so a record saved offline is never rejected at sync time.

#### Several accounts on one phone (Android only, on purpose)

One phone often logs the macros of several people (e.g. someone who also enters their
family's meals), so the app keeps several accounts side by side and switches between them
with a tap from the header. **The web does not have this and must not get it:** a browser is one
person's, and switching there is just logging out and in. Don't port it; the web stays
one account per browser.

- **One profile per account** (`data/AccountProfiles.kt`): each has its own Room database
  and its own session (`AccountStore`), plus the clients built on them (`ProfileScope`:
  `ApiService`, `SyncEngine`, `AppRepository`, `AccountController`). Accounts never share
  a table, so a meal can't reach the wrong account. `AppGraph.account/api/repository/sync/
  accounts` are the **active** profile's.
- **Files:** the first profile is `default`, which uses the files of a single-account
  install (`blackwater.db`, `account` preferences): updating the app migrates nothing.
  Others are `blackwater-<uuid>.db` and `account-<uuid>`; the `profiles` preferences keep
  the ids in order and the active one.
- **Invariant:** one profile in local mode (no account, as before), or one or more
  profiles that all have an account. Logging in from local mode is the usual flow (upload
  or discard the local data); «Añadir cuenta» is offered only once an account is
  connected, and the new account starts empty and downloads its data with the first sync.
  An account already on the phone is not added twice (it becomes the active one, with a
  new session).
- **Switching:** with two or more accounts every tab header shows the active account's
  initial (`AccountSwitcherButton`); tapping it opens `AccountSwitcherSheet` (accounts,
  ✓ on the active one, «Sesión caducada» where needed, «Añadir cuenta»). Ajustes → Cuenta
  offers «Añadir cuenta», or «Cambiar de cuenta» once there are several. Changing the
  active id restarts `MainActivity` (it watches `profiles.activeId`), so every screen and
  ViewModel is built again on that account's data.
- **Sync:** the background job syncs **every** account (`AccountProfiles.syncAll`), so a
  meal logged for one person before switching still uploads to that person's account.
- **Logout** of one of several accounts wipes only its data and session, and the first
  remaining account becomes active; its empty database file is deleted at the next start
  (screens may still read it until `MainActivity` restarts). Logging out of the last one is
  the single-account logout (back to local mode).
- **Shared by all accounts on the phone:** theme, language, AI keys and settings, the
  on-device model, recent foods and «first steps done». Per account: meals, weigh-ins,
  templates, profile (Perfil), sync status; the Coach conversation is in memory and starts
  over on a switch.
- **Tests:** `data/MultiAccountTest.kt`, `ui/AddAccountLoginTest.kt` (§11.2).

### 14.2 Modules and structure

- **`:core`** — pure JVM Kotlin port of `src/lib/core/*.ts` with `*Test.kt` mirrors of
  `tests/unit/*.test.ts` (`npm run core:sync-check` enforces parity).
- **`:app`** — everything Android.
- **Room schema and migrations:** every version's schema is exported to `app/schemas/`
  (committed) and every change gets a real `Migration` in `LocalDatabase.MIGRATIONS`, never
  `fallbackToDestructiveMigration` (users without an account would lose everything).
  `MigrationTest` builds each old version from its schema file, seeds it and opens it with
  today's code. 1 → 2 added `profile.history` (§8.1.1). Changing an entity = bump the version,
  build (writes the new schema), add the migration and a `MigrationTest` case.

```
app/src/main/kotlin/com/blackwatermacros/app/
  AppGraph.kt                # service locator + BlackwaterApp (Application) — builds everything once
  MainActivity.kt            # AppCompatActivity (per-app language); theme from AppPreferences;
                             #   NavHost: 4 tabs + login / perfil / metodologia / admin pages;
                             #   requests a sync on every onStart when logged in
  data/
    local/LocalDatabase.kt   # Room entities (pending/deleted flags) + DAOs + MIGRATIONS (version 3)
    local/Mappers.kt         # DTO ⇄ entity, totals via :core resolveMealTotals, nowIso() edit stamp
    AppRepository.kt         # the only data API the screens use (Flows + writes + undo + export data + replaceAll)
    AccountStore.kt          # persisted account (token, username, isAdmin, sessionExpired, lastSyncAt)
    AccountProfiles.kt       # several accounts on one phone (Android only): one database + session each, the active one
    AccountController.kt     # verify → connect (upload/discard) → logout / deleteLocalData (wipe)
    AppPreferences.kt        # theme (Sistema/Claro/Oscuro)
    BackupImport.kt          # reads the export file back for the import (all or nothing, server limits)
    sync/SyncEngine.kt       # push + pull, typed SyncOutcome/SyncProblem, never throws
    sync/SyncScheduler.kt    # SyncScheduler interface + WorkManager implementation + SyncWorker
    update/UpdateChecker.kt  # «A new version is available» for the website APK only (§14.1)
    foods/OwnProducts.kt     # products added from a label: store, Open Food Facts account, uploader (§8.5)
    foods/OwnProductUploads.kt # WorkManager job that sends them when there is a connection
    ApiService.kt, ApiClient.kt, WireModels.kt, JsonConfig.kt (profileBody: explicit nulls),
    AccountInterceptor.kt (Bearer token; nothing but login without an account),
    ResponseErrorMapper.kt (admin error messages)
  ui/
    HoyScreen/ViewModel, MealCard           # Comidas: day navigator, totals, recommendations,
                                            #   reorderable meals, save a meal as a template
                                            #   (`toCopyTemplateRequest`), undo delete/add/save
    AddFoodSheet.kt                         # «Añadir comida»: search, barcode, manual, copy, templates (hold: edit/delete)
    foods/AddFoodViewModel.kt, FoodViews.kt # search (generic + Open Food Facts), portion, CameraX + zxing-cpp scanner
    foods/NewProductView.kt                 # «Nuevo producto»: the label typed or read by AI (§8.5)
    foods/LabelPhotoViewer.kt               # the label photo at full size: pinch / double-tap zoom, change or remove
    OffAccountScreen.kt                     # Ajustes → Open Food Facts (the user's own account)
    MealForm.kt, FormFields.kt              # one meal/template form (MealFormValue, toCopyRequest) +
                                            #   validation, in a GuardedBottomSheet (asks before losing edits)
    NutritionRecommendationsCard.kt, RecommendationsViewModel.kt  # DaySummaryCard (header with «How they're calculated», intake bars, totals grid, the sheet), Perfil's sections
    ProgressScreen/ViewModel, WeightFormDialog  # Progreso: local stats + weigh-ins (validation = server limits)
    chart/*                                     # Canvas charts (text sizes in sp; TrendChart has a per-point target band;
                                                #   TrendChart.kt also holds what both share: drawDateLabels, drawSelection, LegendDot)
    SettingsScreen/ViewModel, SettingsComponents.kt  # Ajustes: grouped rows (SettingsGroup, SettingsRow)
    UpdateNotice.kt                         # the new version card (Comidas) and the updates row: switch + «Check now» (Ajustes)
    ProfileScreen/ViewModel                 # Perfil: goal, body data, recommendations
    LoginScreen/ViewModel, AdminScreen/ViewModel, MethodologyScreen
    SyncIndicator.kt                        # cloud in the Comidas header (logged in only)
    AccountSwitcher.kt                      # header avatar + account switcher sheet (2+ accounts, Android only)
    Format.kt                               # dates (app language) + the one number format + app language (appLocale, setAppLanguage)
    Theme.kt, CenteredTopAppBar.kt, BottomNavBar.kt  # tokens, AppCard, header (BackTopAppBar = title + arrow back), bottom bar, AppLogo
```

### 14.3 Languages

English (default, also the fallback for any other phone language), Spanish, French, Italian
and German. Texts shared with the web are generated from `src/i18n/<lang>.json` at build time
(`strings_web.xml`, see §6 «i18n»); Android-only texts live in `res/values*/strings.xml`. The
app follows the phone language; Ajustes → Idioma changes it (`setAppLanguage` in
`ui/Format.kt`, AppCompat per-app locales; Android 13+ also in system settings via
`generateLocaleConfig`). Dates are formatted in the app language, numbers in the one format of
every language (§6). The export file's names are English (a file format, §5.11). The app label is
«Blackwater Macros».

### 14.4 Look & feel (kept in sync with the web)

- **Palette:** `MainActivity.kt` Light/DarkColors; the web's `globals.css` uses the same
  values. One warm «ember» accent (tertiary) for the floating add button, the selected
  tab pill and an over-target progress fill.
- **Cards:** `AppCard` (surfaceContainer + hairline `outlineVariant` border) everywhere.
- **Text fields:** `CompactField` / `CompactTextArea` — 40 dp high, caret in the primary
  color (the default black caret is invisible in dark mode), fainter placeholders, a
  2 dp primary border on the focused field. A number's unit goes inside the field, at the
  end (`DecimalField(unit = …)`, the ingredient rows; §3 «The two meal entry modes»).
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
  following the content's scroll and jumps up and down on long forms. Let a sheet take its
  content's height (no `fillMaxHeight(0.9f)`): with a fixed fraction, a long meal scrolled
  to the bottom made the sheet jump up and down by itself. A meal form taller than the
  screen (two or more foods) also turns the sheet's gestures off (`contentScrolls` =
  the form's `ScrollState.maxValue > 0`), edits or not: a drag on the form went partly to
  its scroll and partly to the sheet, which lagged the finger and shook. Then the form
  only scrolls and the handle closes it; a short form still drags closed.
- **Orientation:** portrait only (`screenOrientation="portrait"` in the manifest); no
  screen has a landscape layout.
- **Password fields** (login, admin user forms) are hidden by default with an eye button to
  show them: `PasswordVisibilityToggle` (Android) and `components/ui/password-input.tsx`
  (web); texts `auth.showPassword` / `auth.hidePassword`. The AI key fields have their own eye.
  **Username fields** have no autocorrect or capital letters (`autoCorrectEnabled = false`;
  web `autoCapitalize="none" autoCorrect="off"`): a keyboard «fixing» a username makes the login fail. The
  login fields also carry autofill hints (`ContentType.Username` / `Password`), so a
  password manager fills and saves the right account.
- **Charts:** hand-drawn Compose Canvas; all text sizes in sp (`AxisTextSize`,
  `TooltipTextSize`) so they follow the phone's font size.
- **Intake bars** (Comidas): outlined track = what is left, solid fill = eaten (ember past
  the calorie target; protein over its range keeps the normal fill and says «X g above the
  range» in grey, `overIsFine`, because more protein is not a problem, METHODOLOGY §2.1),
  two markers = target range, «eaten / min–max unit» next to the status.
  The calorie card lists Promedio estimado, TMB, TDEE and (when the data supports it) the
  measured expenditure ± margin, the expenditure the target uses (§8.3.2) and the profile
  check's notice (§8.3.1) before the bar;
  the protein card shows g/kg (of lean mass
  when a cut uses body fat). Same design on the web (`components/nutrition-recommendations.tsx`).
- **Comidas:** double-tap the date → today; deleting a meal asks first, then offers Undo;
  single-ingredient meals don't repeat the numbers of the totals;
  ingredient columns have fixed widths that scale with the font size.
- **Day navigator** (Comidas and «Copiar de otro día», web and Android): the arrows stay
  in place whatever the date's length, and the «Hoy» line is always reserved. In «Copiar de
  otro día» the meal list has a fixed height (40 % of the screen, scrolls inside), so
  stepping back through days never moves the arrows.
- **Meal form:** title, ingredient names and notes start with a capital letter on the
  phone keyboard (`autoCapitalize="sentences"` / `KeyboardCapitalization.Sentences`).
  Guardar with no title marks the title field (red border, «Escribe un título…» under it)
  and focuses it; other errors show next to Guardar.
- **Logo:** the 20 kg «BW» plate with a steel hub. `scripts/logo/render.html` is the
  single vector source; `scripts/logo/render-icons.sh` renders every size with headless
  Chrome: the Android adaptive icon (foreground = the plate filling the visible area,
  background `ic_launcher_background` = the rim green, so launchers show just the plate;
  monochrome layer for themed icons; round legacy icons), `drawable-nodpi/logo_plate.png`
  (login page), and the web's `public/logo.png`, `src/app/icon.png` and PWA icons
  (maskable = plate in the 80 % safe circle on rim green). `public/logo.svg` is a copy of
  the vector. Tab headers have no logo (same on the web).
  Seasonal variants (`xmas-hat`, `xmas-holly`, `xmas-red`, `xmas-red-hat`, `xmas-snow`,
  `xmas-bauble`) live in the `variants` table of `render.html`; `render-icons.sh <variant>`
  renders one, and `render-icons.sh` with no argument always renders the original again.
  A variant with another rim colour (`xmas-red*`, rim `#5A0E13`) also needs
  `ic_launcher_background` in `values/colors.xml` (original: `#0F2318`). The monochrome
  layer is always the plain plate, so it stays unchanged. Christmas 2026 ships `xmas-red-hat`.

### 14.5 Configuration

- **Base URL:** `BuildConfig.API_BASE_URL`, default `https://app.blackwater-macros.jordanmontt.fr/`,
  override with `-Papp.baseUrl=<url>`. Only used after logging in.
- **Version:** `versionCode` / `versionName` in `app/build.gradle.kts` (the tag is `v<versionName>`).
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

### 14.7 Releases

- **Release build:** `./gradlew :app:assembleRelease` → one APK per ABI in
  `app/build/outputs/apk/release/` (arm64 ~15 MB), R8-shrunk. Signed with `keystore.properties`
  (storeFile, storePassword, keyAlias, keyPassword; never committed) when present, else with the
  debug key (fine for local test builds only).
- **Debug build:** `applicationIdSuffix = ".debug"` (`com.blackwatermacros.app.debug`, the
  launcher name «Blackwater Macros (debug)» from `src/debug`): it installs next to the real app
  with its own data. Classes keep the `com.blackwatermacros.app` package, so an `adb` component
  is `com.blackwatermacros.app.debug/com.blackwatermacros.app.MainActivity`.
  **R8 and Retrofit:** a suspend call's response type lives only in the method's generic
  signature, so R8 removed `SettingsResponse` (never read) and `updateSettings` failed in
  release builds only («Unable to create converter for class java.lang.Object»): the profile
  never uploaded and each sync stopped there. `proguard-rules.pro` keeps every `@Serializable`
  class of `data`; `ReleaseRulesTest` checks every response type is one of them. Debug builds
  and unit tests don't shrink, so check a release APK on a phone after touching the API.
- **Releases (two channels, two keys):** a `v*` tag runs `.github/workflows/release.yml`: tests,
  build, signs with the release key from the repository secrets, refuses an APK whose
  certificate is not `releaseCertSha256`, and publishes `blackwater-macros.apk` (+ `.sha256`) as a
  GitHub Release with the en-US changelog. The name never changes, so
  `releases/latest/download/blackwater-macros.apk` is always the newest; the website
  (`website/`, deployed by `website.yml`, screenshots and fingerprint copied in at deploy) links
  there. For search engines and link previews the page has a descriptive `<title>`, Open Graph
  tags (the image is `social.png`, the store's `featureGraphic.png` copied in at deploy; absolute
  URLs, as WhatsApp needs), schema.org `SoftwareApplication` JSON-LD, `robots.txt` and
  `sitemap.xml`; `404.html` is `noindex`. The footer ends with «Created by jordanmontt», as Settings does. F-Droid builds the same tag and signs with its own key: the two can't update each
  other, so switching means reinstalling (said on the website). Steps: docs/RELEASING.md.
- **F-Droid:** the recipe is `docs/fdroid/com.blackwatermacros.app.yml` (reference copy; the
  live one is in fdroiddata, merge request !53565, and F-Droid's bot adds each new tag to it).
  History and decisions: [FDROID-PLAN.md](./FDROID-PLAN.md). Steps for every release:
  [RELEASING.md](./RELEASING.md).

### 14.8 Not done yet (candidates for a next version)

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
