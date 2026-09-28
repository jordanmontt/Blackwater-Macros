# Plan: F-Droid release, accounts and end-to-end encryption

Living plan for what comes after the AI work. **Any session (human or agent) can pick it up
from here.** Read [`TECHNICAL.md`](../TECHNICAL.md) first (architecture, conventions,
verification), then the **Status** table, then the part you are on. Update this file with the
work: tick the boxes, add a line to the **Log**, record every decision in **Decisions**.

Markers: ❓ = needs the owner's answer before that step starts · ⚠️ = changes a lot of code or
is risky · ✅ = decided.

## Status

| # | Part | State |
|---|------|-------|
| A | F-Droid release (repo public, catalog from the repo, «Crear cuenta · Próximamente», metadata, merge request) | ☐ |
| B | Premium accounts (paid sign-up on the web) | ☐ later |
| C | End-to-end encryption for accounts ⚠️ | ☐ later, before B goes live |

Legend: ☐ todo · ◐ in progress · ☑ done.

## Log

- 2026-09-28 — Release cleanup: unused files and code removed (Next.js starter SVGs, logo
  drafts, three unused UI components, dead helpers), Android lint down to deliberate warnings
  (TECHNICAL.md §14.7), `androidx.exifinterface` for photo rotation, signing files ignored.
  Docs merged into TECHNICAL.md (API contract §5, tests and TS ⇄ Kotlin rules §11).
- 2026-09-28 — Plan written. The AI plan (all phases done, PR #12) was removed; its principles
  and decisions now live in `TECHNICAL.md` («AI design decisions»).

## Decisions

| # | Decision | State |
|---|----------|-------|
| F1 | The phone-model catalog is read from the **public repository**, not from the Blackwater server. | ✅ owner |
| F2 | «Crear cuenta» shows **«Próximamente»** until paid accounts exist; later it opens a web page to sign up and pay. | ✅ owner |
| F3 | Public repository on **GitHub**, with a **Codeberg mirror** added when it goes public. | ✅ owner |
| F4 | Licence: **AGPL-3.0-or-later** for everything (`LICENSE`, `package.json`, README). | ✅ owner, added 2026-09-28 |
| F5 | End-to-end encryption and password recovery (see Part C: a truly end-to-end design cannot let the operator restore data alone). | ❓ |

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

### A1. Make the repository public ❓ F3, F4

- [x] **Host (F3):** GitHub; when public, add a Codeberg mirror (Codeberg → «New migration» →
      mirror of the GitHub repository, synced automatically). The F-Droid recipe and the
      catalog URL (A2) point at GitHub; the mirror is a backup and a non-GitHub way to read
      the code.
- [x] **Licence (F4):** AGPL-3.0-or-later, `LICENSE` at the root (canonical text),
      `"license"` in `package.json`, a section in the README.
- [ ] **AGPL «source for network users»** (section 13): once the repository is public, the web
      app must offer its users a link to the source — add «Código fuente» (link to the
      repository) in Ajustes and on the login page. The Android app can show the same link in
      Ajustes → Metodología (not required, but consistent).
- [ ] Optional: a short licence header in source files (`SPDX-License-Identifier:
      AGPL-3.0-or-later`); not required when `LICENSE` is at the root.
- [ ] **Secrets check before publishing**: run `gitleaks detect` (or `trufflehog`) over the
      whole history. A quick check found no `.env`, keystore or credential file ever committed;
      `.env*` is ignored and `android/keystore.properties` was added to `.gitignore` on
      2026-09-28. Also check `scripts/` and test fixtures for real usernames/passwords.
- [ ] **Data licences of bundled assets.** F-Droid wants assets under free terms too. The
      generic-food index bundles Ciqual (Licence Ouverte/Etalab 2.0 — free) and the Swiss Food
      Composition Database (free use incl. diary apps with attribution — **not a classic free
      licence**). Chompass ships a Swiss index and is on F-Droid, so it is probably accepted, but
      state it in the merge request. Open Food Facts is only queried online (ODbL). ❓ confirm
      nothing else is bundled (`android/app/src/main/assets`, `public/foods`).
- [ ] Decide whether the web (`src/`, Next.js) stays in the same repository. F-Droid only needs
      `android/`; like Chompass, the recipe can `scandelete` the web folders so the scanner does
      not trip on `node_modules`-style content. Keeping one repository is simpler (shared
      `src/lib/core` ⇄ `android/core` parity). ✅ suggested: one repository.

### A2. Phone-model catalog from the repository (F1)

Today: `public/models/local-models.json` is served by the Blackwater site and read by
`LocalModelCatalog` from `BuildConfig.API_BASE_URL + "models/local-models.json"` when
Ajustes → IA is opened.

- [ ] Move the file to a path that is not part of the web app, e.g.
      `android/catalog/local-models.json` (or keep it; only the URL matters).
- [ ] Add `buildConfigField("String", "MODEL_CATALOG_URL", …)` in `app/build.gradle.kts`,
      pointing at the raw file on the default branch:
      - GitHub: `https://raw.githubusercontent.com/<owner>/<repo>/main/<path>`
      - Codeberg: `https://codeberg.org/<owner>/<repo>/raw/branch/main/<path>`
      and use it in `AppGraph` instead of the API base URL.
- [ ] Remove the `/models/` exemption from `src/proxy.ts` if the file leaves `public/`; update
      `tests/unit/local-models-catalog.test.ts` (path), `LocalModelCatalogTest` (path),
      `TECHNICAL.md` («Catalog of phone models», «Adding a phone model») and `README.md`.
- [ ] Keep the rules: fetched only when Ajustes → IA opens, last good copy kept offline,
      entries validated (huggingface.co only, SHA-256), `minAppVersionCode`, `hidden`.
- Note: publishing a catalog change = merging to the default branch (no deploy needed).
  Anyone reading the repo sees the same list the phones get. The download host (GitHub or
  Codeberg) sees the phone's IP when Ajustes → IA is opened, nothing else.

### A3. «Crear cuenta · Próximamente» (F2)

Accounts are invite-only today (Ajustes → Cuenta → Iniciar sesión; the web requires login).
No public sign-up should appear to work before paid accounts exist.

- [ ] **Android**: in the account card and in the first-launch welcome, next to «Iniciar
      sesión», add «Crear cuenta» that opens a small dialog: «Las cuentas con sincronización
      llegarán pronto. La app funciona completa sin cuenta.» Remove wording that says
      «solo por invitación» if it confuses. Strings in the 5 languages.
- [ ] **Web**: on `/login`, a line «¿No tienes cuenta? Próximamente» (no link yet). The demo
      entry stays.
- [ ] Later (Part B): the same button opens `https://<site>/cuenta/nueva` in the browser.
      Payments happen only on the web (no in-app payments, nothing from Google Play).
- ❓ Existing invited accounts (owner, testers) keep working as they are? Suggested: yes.

### A4. Android build for F-Droid

- [ ] **Versioning**: `versionCode = 1`, `versionName = "0.1.0"` today. Pick a scheme (e.g.
      `versionCode` +1 per release) and tag releases `v0.1.0`, so the recipe can use
      `UpdateCheckMode: Tags ^v[0-9.]+$` and `AutoUpdateMode: Version` like Chompass.
- [ ] **ABI**: LiteRT-LM needs arm64. Add a Gradle property (like Chompass' `releaseAbi`) so
      F-Droid builds only `arm64-v8a`; keep the current splits for local builds.
- [ ] **Signing** ⚠️: F-Droid signs with its own key. Anyone who installed an APK built here
      (signed with the debug key today) **cannot update to the F-Droid build**: Android refuses a
      different signature, and uninstalling deletes the phone's data. Before switching, testers
      must export CSV (Ajustes → Tus datos) or use an account. Optional later: reproducible
      builds + `AllowedAPKSigningKeys`, so F-Droid ships APKs signed with the owner's key and
      both channels update each other (more work: byte-identical builds). ❓
- [ ] Create a real release key (`keystore.properties`, never committed) for builds shared
      outside F-Droid.
- [ ] Build check: a clean clone builds with `./gradlew :app:assembleRelease` without network
      access other than Maven repositories (Google, Maven Central), without Node, and without
      files outside the repository. `API_BASE_URL` defaults to production (fine).
- [ ] Dependencies are all free software (checked 2026-09-28): AndroidX/Compose, Room,
      WorkManager, Retrofit/OkHttp, kotlinx.serialization, CameraX, zxing-cpp, LiteRT-LM
      (Apache-2.0, prebuilt native code from Google's Maven — accepted for Chompass). No Google
      Play Services, ML Kit, Firebase or trackers. Re-check with the F-Droid scanner
      (`fdroid scanner`) and Exodus before submitting.
- [ ] Permissions to explain in the description: `INTERNET`, `CAMERA` (photos, barcode),
      `POST_NOTIFICATIONS` + `FOREGROUND_SERVICE(_DATA_SYNC)` (model download progress).

### A5. Store listing and anti-features

- [ ] Fastlane metadata in the repo: `android/fastlane/metadata/android/<locale>/`
      (`es-ES`, `en-US`, `fr-FR`, `it-IT`, `de-DE`): `title.txt`, `short_description.txt`
      (≤ 80 chars), `full_description.txt`, `changelogs/<versionCode>.txt`,
      `images/icon.png`, `images/featureGraphic.png`, `images/phoneScreenshots/*.png`.
- [ ] **Anti-features** ❓: F-Droid may tag
      - `NonFreeNet` — the app can use non-free network services: cloud AI (Google, OpenAI,
        Anthropic, OpenRouter, all optional, user's own key), model downloads from Hugging Face.
        The sync server is free software in the same repo (self-hostable), so it does not
        count. Chompass has no anti-feature, but declaring `NonFreeNet` honestly avoids a
        back-and-forth with reviewers. Suggested: declare it.
- [ ] Merge request to `fdroiddata` with `metadata/com.blackwatermacros.app.yml`
      (Categories: Sports & Health; License; SourceCode; IssueTracker; Builds with
      `subdir: android/app`, `gradle: yes`, the ABI property, `scandelete` for the web folders).
- [ ] After merge: the first build takes days; fix scanner complaints if any.

---

## Part B — Premium accounts (later)

- [ ] Sign-up page on the web (`/cuenta/nueva`): choose username/password (or passkey, see C),
      pay, account created. ❓ payment provider (Stripe, Paddle, Lemon Squeezy — card payments
      and EU VAT handling differ), price, monthly/yearly.
- [ ] Subscription state on the server (`users.plan`, `paid_until`); webhook from the payment
      provider. ❓ What happens when it expires: read-only? sync stops but the phone keeps
      working locally (suggested — local-first stays)?
- [ ] «Crear cuenta» (A3) opens that page; after paying, the user logs in from the app.
- [ ] Self-hosting stays possible (the code is public): document running your own server, which
      is also what keeps the `NonFreeNet` question simple.
- ⚠️ Do Part C **before** selling accounts: migrating paying users' data to encryption later is
  harder than starting encrypted.

---

## Part C — End-to-end encryption (E2EE) ⚠️ ❓ F5

### What was asked

Premium data end-to-end encrypted; usable from Android **and** the web; no data lost if the
phone is lost; and no data lost if the password is lost — «I can change the password and they
have everything».

### The honest answer

The first three are doable. **The last one contradicts end-to-end encryption.** «End-to-end»
means only the user's devices hold the key; the server (and its operator, a thief who copies
the database, or a court order) cannot read the data. If the operator can restore everything
after a lost password *without anything from the user*, the operator has the key — then it is
**encryption at rest**, not end-to-end. Every service faces this trade-off:

| Recovery option | Lost phone | Lost password | Operator can read | Comment |
|---|---|---|---|---|
| 1. Password only | ✅ (log in elsewhere) | ❌ data lost | No | Too harsh for most users. |
| 2. **Recovery code** (24 words / printed key, shown once at sign-up) | ✅ | ✅ with the code | No | Proton, Bitwarden, 1Password's «Secret Key». |
| 3. **Another signed-in device** resets the password | ✅ | ✅ if a device is still signed in | No | Signal/WhatsApp-style. |
| 4. **Passkey** (WebAuthn PRF) synced by the phone/browser's password manager | ✅ | no password to lose | No | Modern; not every browser/OS supports PRF yet. |
| 5. **Operator escrow** (the server keeps a copy of the key) | ✅ | ✅ via support | **Yes** | Not end-to-end; must not be called that. |

**Suggested ❓:** 2 + 3 (true E2EE). The user loses data only if they lose the password **and**
the recovery code **and** every signed-in device. Optional: offer 5 as an explicit opt-in
(«Permitir que Blackwater recupere mi cuenta»), clearly labelled as not end-to-end.

### Design (for options 2 + 3)

- **Account key** (AK): random 256-bit, created on the first device at sign-up. All synced
  records are encrypted with it; it never reaches the server unwrapped.
- **Wrapping**: the server stores AK only encrypted («wrapped») by
  - the **password key**: `Argon2id(password, salt)` split in two with HKDF: one half wraps
    AK, the other becomes the login secret (the server stores a hash of it). The server never
    sees anything that unwraps AK. ⚠️ Login changes: today the server receives the password.
  - the **recovery key**: random, shown once as 24 words, wraps AK a second time.
- **Devices**: Android keeps AK wrapped by an Android Keystore key (like the AI keys today);
  the web keeps it as a non-extractable `CryptoKey` in IndexedDB («recordar este navegador»)
  or asks for the password each session.
- **Records**: each meal, weigh-in, template and the profile is serialised to JSON and
  encrypted with AES-256-GCM (random 96-bit nonce, record id + kind as associated data). The
  server stores `id, user_id, kind, ciphertext, updated_at, deleted` — nothing else.
  ❓ Metadata: keeping `log_date` in clear would allow date-range queries but reveals when the
  user eats; suggested: encrypt it too and let clients download everything (a year of meals is
  a few MB) and filter locally.
- **Password change**: re-wrap AK with the new password key (needs the old password, the
  recovery code or a signed-in device). **Operator reset** (today's admin «cambiar
  contraseña»): can reset the *login*, but the data stays unreadable until the user enters the
  recovery code.
- **Crypto libraries (free software, F-Droid friendly)**: web — WebCrypto (AES-GCM, HKDF) +
  Argon2id from `hash-wasm` (MIT) or `libsodium-wrappers` (ISC); Android — `javax.crypto`
  (AES-GCM) + Argon2id from Bouncy Castle (pure Java, MIT-style). Both sides checked against
  shared **test vectors** (same pattern as `src/lib/core` ⇄ `android/core`).

### What changes in the code ⚠️

This is the largest change since the app exists: it touches the database, the API and both
clients. The server stops understanding the data, so everything it computes today moves to
the clients.

| Area | Today | With E2EE |
|---|---|---|
| Stats (`/api/stats`) | computed on the server | computed on the device (`buildStatsFromData` already runs on Android and in demo mode) |
| Meal totals (`resolved_*` columns) | server | client (`core/nutrition` already shared) |
| Validation (zod on meals, weights) | server | client; server only checks size and envelope |
| CSV export / import routes | server builds / parses | client-side (Android already does it locally) |
| Meal list by date range | SQL `WHERE log_date` | download all, decrypt, filter on the device |
| Web data layer (`lib/api.ts`, cache) | fetch JSON | fetch blobs → decrypt → same DTOs (web becomes local-first like Android) |
| Android sync (`SyncEngine`) | pushes JSON | pushes encrypted blobs; Room stays plaintext on the phone (app sandbox) |
| Admin | sees users | sees users, never their data |
| Login | password to server | derived login secret; sign-up creates and wraps AK |

- **Migration**: existing plaintext accounts are migrated on the next login (the client
  downloads, encrypts, uploads, the server deletes the plaintext). ❓ or: only new premium
  accounts are encrypted, and the current invite accounts are migrated once by hand.
- **Web limitation to state openly**: a web app is downloaded from the server each visit, so
  whoever controls the server could ship code that steals the key. The Android app from
  F-Droid is built independently from the public source, so it is stronger. Mitigations:
  Subresource Integrity, a published build hash; or recommend the Android app for maximum
  protection.
- **Size**, roughly, as separate steps each ending green on the gate:
  1. crypto core + test vectors (TS + Kotlin);
  2. server schema + API for encrypted records and wrapped keys;
  3. web: key handling, sign-up/login/recovery screens, encrypted data layer, client-side stats;
  4. Android: key handling, sync of encrypted records, recovery screens;
  5. migration of existing accounts; 6. security review (external if possible) and docs.
  Doable, but substantial — plan it as its own project, before paid accounts launch.

### Open questions for the owner (Part C)

- ❓ F5: options 2 + 3 (true E2EE), with or without the opt-in escrow (5)?
- ❓ Passkeys (4) from the start, or later?
- ❓ Encrypt `log_date` too (suggested) or keep it for server-side date queries?
- ❓ Migrate the current accounts automatically or by hand?
- ❓ Keep the password on the web each session, or «recordar este navegador»?
