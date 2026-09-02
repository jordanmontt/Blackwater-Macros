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

- All errors return JSON `{ "error": "<Spanish message>" }`.
- Status codes: `400` validation, `401` unauthenticated, `403` forbidden, `404` not found, `409` conflict, `500` internal.
- Messages are in Spanish (user-facing).

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

1. **Auth token:** obtain via `login`, store securely (e.g. EncryptedSharedPreferences),
   send as `Authorization: Bearer <token>` on every request.
2. **401 handling:** if any request returns 401, the token is expired/invalid — prompt
   the user to log in again.
3. **Offline-first:** the Android app stores a local copy (Room/SQLite). When online,
   show local data and sync in the background via these endpoints. Use last-write-wins
   on conflicts.
4. **Sync protocol (`updatedAt`):** every mutable record — meals, templates and
   weights — exposes an `updatedAt` ISO-8601 UTC string. The server sets it on creation
   and refreshes it on every update. For last-write-wins, the client compares its local
   `updatedAt` against the server's: whichever is later wins. This is the single
   authoritative clock for sync; do not derive it from `measuredAt`/`logDate`.
5. **Numbers:** always `.` in JSON; format for display with `,` using
   `java.text.NumberFormat` with `Locale("es", "ES")`.
6. **Timezones:** `logDate` days are the user's local calendar day. Sync should send
   the Android device's own `today` day to `/api/stats` so ranges match the user's view.
