# Blackwater Macros — API Contract

This document is the authoritative contract for the deployable backend. Both the
web app and the Android app consume the exact same API. It is derived from the
Zod schemas in `src/server/validation.ts` and the DTOs in `src/lib/core/types.ts`.

## Base URL

- **Production:** the deployed Next.js origin (e.g. `https://blackwater-macros.example`)
- All endpoints are relative to this origin, prefixed with `/api`.

## Authentication

The backend supports two transport mechanisms for the **same** opaque session
token (a 32-byte base64url string stored in the `sessions` table).

| Client | Mechanism |
| ------ | --------- |
| Web (browser) | `Set-Cookie: bw_session=<token>` (httpOnly, secure, sameSite=lax) |
| Android / API | `Authorization: Bearer <token>` request header |

Session lifetime: **90 days** per login.

### Login (get a token)

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

### Logout (invalidate a token)

`POST /api/auth/logout`

Sends the token via cookie OR `Authorization: Bearer <token>`. Success `200`:
```json
{ "ok": true }
```
Is idempotent — returning 200 even if the token was already invalid.

### Session (current user)

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

## Error Convention

- All errors return JSON `{ "error": "<Spanish message>", "code": "<code>" }`, e.g.
  `{ "error": "El peso debe estar entre 20 y 400 kg", "code": "weight_out_of_range" }`.
- Status codes: `400` validation, `401` unauthenticated, `403` forbidden, `404` not found, `409` conflict, `500` internal, `502` Open Food Facts down.
- **Clients show the code, not the text**, in the app language: web `t.serverErrors[code]`
  (`src/lib/server-errors.ts`), Android `server_error_<code>` strings
  (`ResponseErrorMapper.CODES`). The Spanish `error` is the fallback for a code a client does
  not know yet, and what the examples in this document show. The codes are the keys of
  `serverErrors` in `src/i18n/es.ts`; a test on each side fails if the lists differ.
- Validation errors carry the code of the first problem (`title_required`,
  `weight_out_of_range`, `username_too_short`…), or `invalid_data`.

## General Conventions

- **Numbers** in the JSON wire format always use `.` as decimal separator (IEEE 754).
  The `,` separator is purely a display concern on each client.
- **Dates:** `YYYY-MM-DD` (e.g. `logDate`) are local calendar days. Timestamps like
  `measuredAt` are ISO-8601 strings.
- **Entry mode:** `per_ingredient` (totals are computed by summing `ingredients`)
  or `total_only` (totals come from the manual `total*` fields).

---

## Meals

### List meals

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

### Create meal

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

### Update meal

`PATCH /api/meals/:id` — auth required.

Body is the same `MealInput` as create (full replace). Success `200`:
```json
{ "meal": MealDTO }
```
Errors: `404` if the meal does not belong to the caller: `{ "error": "Comida no encontrada" }`.

### Create or replace meal (offline sync)

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

### Delete meal

`DELETE /api/meals/:id` — auth required. Success `200`:
```json
{ "ok": true }
```
`404` if not owned by caller.

### Reorder meals

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

## Meal Templates

Same shape as meals, plus a `name` and no `logDate`/`sortOrder`.

### List templates

`GET /api/templates` — auth required.

Success `200`:
```json
{ "templates": [ MealTemplateDTO ] }
```

`MealTemplateDTO` extends `MealDTO` with `name` instead of `logDate`:
```json
{
  "id": "uuid",
  "name": "Desayuno base",
  "title": "Desayuno",
  "notes": null,
  "entryMode": "per_ingredient",
  "ingredients": [],
  "totalCalories": null,
  "totalProtein": null,
  "totalCarbs": null,
  "totalFat": null,
  "resolvedCalories": 0,
  "resolvedProtein": 0,
  "resolvedCarbs": 0,
  "resolvedFat": 0,
  "updatedAt": "2026-06-15T08:00:00.000Z"
}
```

### Create template

`POST /api/templates` — auth required.

Body is like `MealInput` plus required `name` (max 120). Success `201`:
```json
{ "template": MealTemplateDTO }
```

### Update template

`PATCH /api/templates/:id` — auth required. Success `200`:
```json
{ "template": MealTemplateDTO }
```
`404` if not owned: `{ "error": "Plantilla no encontrada" }`.

### Create or replace template (offline sync)

`PUT /api/templates/:id` — auth required. Same semantics as `PUT /api/meals/:id`
(UUID id, idempotent create-or-replace, `404` if owned by another user). Body
is `TemplateInput`. Success `200`: `{ "template": MealTemplateDTO }`.

### Delete template

`DELETE /api/templates/:id` — auth required. Success `200`: `{ "ok": true }`.

---

## Weights

### List weights

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

### Create weight

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

### Update weight

`PATCH /api/weights/:id` — auth required. Body is `WeightInput`. Success `200`:
```json
{ "weight": WeightDTO }
```
`404` if not owned: `{ "error": "Registro no encontrado" }`.

### Create or replace weight (offline sync)

`PUT /api/weights/:id` — auth required. Same semantics as `PUT /api/meals/:id`
(UUID id, idempotent create-or-replace, `404` if owned by another user). Body
is `WeightInput`. Success `200`: `{ "weight": WeightDTO }`.

### Delete weight

`DELETE /api/weights/:id` — auth required. Success `200`: `{ "ok": true }`.

---

## Statistics

### Get stats summary

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

## Food search

### Search products (Open Food Facts)

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

## Settings

### Update calorie profile

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

## Export

### Export CSV

`GET /api/export/meals` — downloads `comidas.csv` (UTF-8 BOM, RFC-4180).
`GET /api/export/weights` — downloads `peso.csv`.

Auth required. Returns a `text/csv` attachment.

### Import (web)

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

## Admin (admins only)

All admin endpoints require the caller to have `isAdmin = true`; otherwise `403`.

### List users

`GET /api/admin/users` — success `200`:
```json
{ "users": [ { "id": "uuid", "username": "ana", "isAdmin": false, "createdAt": "ISO" } ] }
```

### Create user

`POST /api/admin/users` — body `{ "username": "...", "password": "..." }`
(username min 3, password min 8). Success `201`: `{ "ok": true }`. `409` if username exists.

### Update user

`PATCH /api/admin/users/:id` — body with optional `username`, `password`, `isAdmin`.
At least one must be present. Success `200`: `{ "user": AdminUserDTO }`.

### Delete user

`DELETE /api/admin/users/:id` — success `200`: `{ "ok": true }`.

---

## Android Integration Notes

The Android app is **local-first**: every screen reads and writes an on-device
Room database; the network is never in the UI path. Without an account the app
is fully usable and never calls the API. With an account, a background sync
mirrors the database with the server:

1. **Auth token:** obtained via `login`, stored in the app's private
   SharedPreferences, sent as `Authorization: Bearer <token>`.
2. **401 handling:** the account is marked *session expired*; local data and
   pending changes are kept and sync pauses until the user logs in again.
3. **Push:** every locally changed record is sent with `PUT /api/<kind>/:id`
   (phone-generated UUID, idempotent — safe to retry after a dropped
   connection) or `DELETE` (a `404` counts as done). The calorie profile goes
   through `PUT /api/settings` with **every key present** (unset = `null`).
4. **Pull:** full lists (`GET /api/meals`, `/api/templates`, `/api/weights`,
   `/api/auth/session`) replace every local row that has no pending change;
   rows missing on the server are removed (so deletions on the web propagate).
5. **Conflicts:** last to sync wins, per record. `updatedAt` is stored but not
   compared.
6. **Numbers:** always `.` in JSON; the UI formats them in the app language.
7. **Timezones:** `logDate` days are the user's local calendar day; statistics
   are computed on the phone with the `:core` port of the stats builder, so
   `/api/stats` is not used by Android.
