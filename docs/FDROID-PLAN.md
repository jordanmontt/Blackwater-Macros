# Plan: F-Droid release

Living plan for what comes after the AI work. **Any session (human or agent) can pick it up
from here.** Read [`TECHNICAL.md`](./TECHNICAL.md) first (architecture, conventions,
verification), then the **Status** table, then the part you are on. Update this file with the
work: tick the boxes, add a line to the **Log**, record every decision in **Decisions**.

Markers: ❓ = needs the owner's answer before that step starts · ⚠️ = changes a lot of code or
is risky · ✅ = decided.

## Status

| # | Part | State |
|---|------|-------|
| A | F-Droid release (repo public, catalog from the repo, metadata, merge request) | ◐ v2.0.1 released (signed APK on GitHub + website); merge request [!53565](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/53565) reopened on 2026-11-25, in review |

Legend: ☐ todo · ◐ in progress · ☑ done. Later features have their own plans: premium accounts
([ACCOUNTS-PLAN.md](./ACCOUNTS-PLAN.md), was Part B) and end-to-end encryption, which comes
before them ([E2EE-PLAN.md](./E2EE-PLAN.md), was Part C).

## Log

- 2026-11-25 — Merge request !53565 reopened, after the three months of active development
  F-Droid asks for (it was closed on 2026-10-30; the repository started on 2026-08-24).
- 2026-10-22 — v1.0.2 for the merge request: the review's issuebot reported «Fastlane/Triple-T
  is not found», because F-Droid reads the store listing only at the repository root; it
  moved from `android/fastlane/` to `fastlane/`. A tag can't be moved, so the recipe points to
  the new `v1.0.2`. Also: a long-press (Android) or the copy button (web) copies a whole coach
  message, instead of selecting text.
  The recipe on the fork points to `v1.0.2`; pipeline 2976725446 is green and the report now
  lists the store listing in all five languages.
- 2026-10-21 — v1.0.1, the first signed release: release key (owner), `release.yml` (a tag
  builds, signs, checks the certificate and publishes `blackwater-macros.apk` on GitHub),
  the app's website on GitHub Pages at `blackwater-macros.jordanmontt.fr` (the web app and API
  moved to `app.`), new version notice for the website's APK only (never F-Droid's, NewPipe's
  way), copyable coach messages, CI fixed (it had never run: `hashFiles` in a job `if`). The
  recipe points to `v1.0.1`; published tags are never moved again.
- 2026-10-21 — Merge request fdroid/fdroiddata!53565 opened from the fork
  `jordanmontt/fdroiddata` (branch `com.blackwatermacros.app`); its pipeline passed on the fork,
  `fdroid build` included, after adopting the formatting of F-Droid's newer `rewritemeta`.
- 2026-10-02 — F-Droid release prepared (Part A): `dev` branch for work (CI + web deploy),
  `main` for releases; catalog moved to `android/catalog/` and read from GitHub;
  `AccountInterceptor` (nothing but the login reaches our server without an account) and
  `NetworkHostsTest`; welcome «Empezar» / «Tengo una cuenta», texts without «versión web»;
  after login the first sync runs before deciding on «Tus datos»; version 1.0.0 (code 2),
  `releaseAbi`, `dependenciesInfo` off; fastlane listing in 5 languages with screenshots and
  feature graphic; recipe draft `docs/fdroid/`; `docs/RELEASING.md`, `docs/DEVELOPMENT.md`;
  user-facing README in English; «Código fuente» link (AGPL) on Android and the web; history
  checked for secrets (clean). Left to the owner: Vercel production branch → `dev`, tag
  `v1.0.0`, the merge request to fdroiddata.
- 2026-09-28 — Release cleanup: unused files and code removed (Next.js starter SVGs, logo
  drafts, three unused UI components, dead helpers), Android lint down to deliberate warnings
  (TECHNICAL.md §14.7), `androidx.exifinterface` for photo rotation, signing files ignored.
  Docs merged into TECHNICAL.md (API contract §5, tests and TS ⇄ Kotlin rules §11).
- 2026-09-28 — Plan written. The AI plan (all phases done, PR #12) was removed; its principles
  and decisions now live in `TECHNICAL.md` («AI design decisions»).

## Decisions

| # | Decision | State |
|---|----------|-------|
| F1 | The phone-model catalog is read from the **public repository** (`main` on GitHub), not from the Blackwater server. | ✅ done 2026-10-02 |
| F2 | No «Crear cuenta» for now: the welcome says «Empezar» / «Tengo una cuenta» and the login says accounts are by invitation. Later a sign-up page on the web ([ACCOUNTS-PLAN.md](./ACCOUNTS-PLAN.md)). | ✅ owner, changed 2026-10-02 |
| F3 | Public repository on **GitHub**, with a **Codeberg mirror** added when it goes public. | ✅ owner |
| F4 | Licence: **AGPL-3.0-or-later** for everything (`LICENSE`, `package.json`, README). | ✅ owner, added 2026-09-28 |
| F5 | End-to-end encryption and password recovery: moved to [E2EE-PLAN.md](./E2EE-PLAN.md) (E1). | → E1 |

---

## Part A — F-Droid release

F-Droid builds the app **from source** on its own servers and signs it with its own key.
Everything it needs must be in a public repository under a free licence.

**Evidence that it is feasible:** Chompass (`app.chompass`) is on F-Droid with the same stack
(LiteRT-LM from Google's Maven, zxing-cpp, Open Food Facts, bring-your-own cloud key). Its
recipe in `fdroiddata` is a plain Gradle build (`subdir: android/app`, `gradle: yes`,
`gradleprops: releaseAbi=arm64-v8a`, `scandelete: web`), with no anti-features declared and no
special handling for LiteRT-LM's native libraries. Checked 2026-09-28 in
`gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/app.chompass.yml`.

### Done (2026-10-02)

- [x] Repository public on GitHub, AGPL-3.0-or-later (`LICENSE`, `package.json`, README); history
      checked for secrets (no keys, passwords or keystores in any commit).
- [x] AGPL «source for network users»: «Código fuente» link in Ajustes (web and Android) and on
      the web login page.
- [x] Catalog from the repository (F1): `android/catalog/local-models.json`, read from `main`.
- [x] Privacy enforced: `AccountInterceptor`, `NetworkHostsTest`, README «Private by design».
- [x] Build: version 1.0.0 / code 2, `-PreleaseAbi=arm64-v8a`, no `dependenciesInfo` block,
      no non-free classes in the APK (checked the dex and the signing block). Only binary in the
      repo: the Gradle wrapper, so the recipe needs no `scandelete`. Android needs
      `src/i18n/*.json` (shared texts), so the web stays in the same repository.
- [x] Store listing: `fastlane/metadata/android/` in 5 languages (descriptions,
      changelog 2, screenshots en/es, feature graphic, icon).
- [x] Anti-feature `NonFreeNet` and the recipe draft: `docs/fdroid/com.blackwatermacros.app.yml`.
- [x] Process: [RELEASING.md](./RELEASING.md) (what's left, every release, occasional tasks).
- [x] Release `v1.0.0` tagged; the recipe passes `fdroid lint`, the scanner and `fdroid build`.
- [x] Two channels (NewPipe's model): F-Droid builds and signs with its key; GitHub and the
      website ship the APK signed with ours, which alone checks for new versions. One
      repository for server, web and Android (the TS ⇄ Kotlin core mirror needs it).
- [x] Release `v1.0.1` signed and published by `release.yml`; the recipe points to it.

### Left (owner)

See [RELEASING.md «Still to do»](./RELEASING.md#still-to-do-once). Optional: a Codeberg mirror
(F3).
