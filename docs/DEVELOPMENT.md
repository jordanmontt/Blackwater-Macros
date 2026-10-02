# Developing Blackwater Macros

How to run, test and change the code. The architecture, API contract and rules live in
[TECHNICAL.md](./TECHNICAL.md); the science behind every number in
[METHODOLOGY.md](./METHODOLOGY.md); how to publish a version in [RELEASING.md](./RELEASING.md).

## The project in one paragraph

Two clients share one pure core. **Android** (`android/`, Kotlin + Jetpack Compose) is the
app people install: local-first, works fully without an account or a connection, and syncs
in the background with an optional (invite-only) account. **Web** (`src/`, Next.js) is a thin
client for account holders and is also the sync server (`/api/*`, PostgreSQL on Neon). The
math (totals, recommendations, statistics, AI request formats, number format) lives in
`src/lib/core/` and is ported 1:1 to Kotlin in `android/core/`, with mirrored tests.
Every user-visible text lives once per language in `src/i18n/<lang>.json`; Android
generates its shared strings from those files at build time.

## Branches

- **`dev`**: all work. Every push runs CI and deploys the web (Vercel production branch).
- **`main`**: released code only (what F-Droid builds, from `vX.Y.Z` tags) and the phone-model
  catalog the apps read. See [RELEASING.md](./RELEASING.md).

## Stack

| Layer | Technology |
|---|---|
| Android | Kotlin · Jetpack Compose · Room · WorkManager · Retrofit/OkHttp · CameraX + zxing-cpp · LiteRT-LM (no Google services) |
| Web + server | Next.js (App Router) · React · TypeScript · Tailwind CSS v4 · shadcn/ui (Base UI) · recharts |
| Database | PostgreSQL on Neon · Drizzle ORM |
| Auth | Username/password (scrypt) + opaque database sessions; no public sign-up |
| Tests | Vitest + Testing Library (web) · JUnit + Robolectric (Android) |

## Android

```bash
cd android
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug   # tests and lint
./gradlew :app:assembleDebug                                # app/build/outputs/apk/debug/
./gradlew :app:assembleRelease -PreleaseAbi=arm64-v8a       # what F-Droid builds
```

- **SDK:** Android SDK with platform 37 (`android/local.properties`: `sdk.dir=…`, not committed).
- **JDK:** 17–23 (21 recommended, `brew install openjdk@21`). Newer JDKs build, but the
  Robolectric tests fail on them.
- **No Node needed:** the shared texts are read from `../src/i18n/*.json` by Gradle itself.
- **Backend URL:** `BuildConfig.API_BASE_URL` (production by default; only used after logging
  in). Another one: `./gradlew :app:installDebug -Papp.baseUrl=http://10.0.2.2:3000/`.
- **Emulator:** `./gradlew :app:installDebug` then
  `adb shell am start -n com.blackwatermacros.app/.MainActivity`. Demo data for a debug build:
  see `scripts/screenshots.sh` (it loads the web demo dataset through a debug-only receiver).

## Web

One-time setup on a new clone:

```bash
npm install
cp .env.example .env.local      # paste your Neon DATABASE_URL
npm run hooks:install           # local reminder when one side of the TS ⇄ Kotlin core changes
npm run db:push                 # create the tables
npm run create-user -- me secret
npm run dev
```

Node ≥ 20.19 (22 LTS recommended; on Node ≥ 25 run the tests with
`NODE_OPTIONS=--no-experimental-webstorage`). The login page has **«Explora datos de demo»**:
45 days of sample data kept only in the browser tab, no account needed.

## Useful commands

| Command | What it does |
|---|---|
| `npm test` | Whole web suite (`tests/unit`: pure core; `tests/behavior`: user requirements) |
| `npm run typecheck` · `npm run lint` · `npm run build` | Type check, ESLint, production build |
| `npm run core:sync-check` | Fails if a core test changed on one side (TS or Kotlin) only |
| `npm run db:push` | Push the Drizzle schema to the database |
| `npm run create-user -- u p` · `npm run set-admin -- u` | Create / reset a user, make an admin |
| `npx tsx scripts/demo-csv.ts <dir> [es\|en]` | Demo dataset as the app's CSV backup |
| `scripts/screenshots.sh` | Store screenshots on an emulator (docs/RELEASING.md) |
| `scripts/logo/render-icons.sh` | Regenerate every icon from the vector logo |

The full gate before pushing is in [TECHNICAL.md §11](./TECHNICAL.md#11-testing-architecture-vitestconfigmts).

## Layout

```
android/          Android app: :core (pure Kotlin port of src/lib/core) and :app (UI + data)
  catalog/        Phone AI model catalog read by the apps from GitHub (main)
  fastlane/       F-Droid store listing (texts, screenshots) in 5 languages
src/
  app/            Pages and API routes (App Router)
  components/     UI components
  i18n/           All texts, once per language (also used by Android)
  lib/core/       Pure algorithms, mirrored in Kotlin
  server/         Repositories, services, auth, database schema
tests/            unit/ (core, shared spec with Android) and behavior/ (requirements)
scripts/          Ops scripts, logo, store images, screenshots
docs/             TECHNICAL, METHODOLOGY, RELEASING, DEVELOPMENT, FDROID-PLAN, fdroid/
```

## Deploying the web

Vercel (Hobby plan) imports the GitHub repository with `DATABASE_URL` set; its production
branch is `dev`. Apply the schema to the production database with
`DATABASE_URL=… npm run db:push` and create the first user with `create-user` (+ `set-admin`).
Environment variables only take effect after a redeploy.
