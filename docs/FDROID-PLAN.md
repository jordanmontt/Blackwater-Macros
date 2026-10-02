# Plan: F-Droid release, accounts and end-to-end encryption

Living plan for what comes after the AI work. **Any session (human or agent) can pick it up
from here.** Read [`TECHNICAL.md`](./TECHNICAL.md) first (architecture, conventions,
verification), then the **Status** table, then the part you are on. Update this file with the
work: tick the boxes, add a line to the **Log**, record every decision in **Decisions**.

Markers: ❓ = needs the owner's answer before that step starts · ⚠️ = changes a lot of code or
is risky · ✅ = decided.

## Status

| # | Part | State |
|---|------|-------|
| A | F-Droid release (repo public, catalog from the repo, metadata, merge request) | ◐ ready; the owner tags v1.0.0 and opens the merge request |
| B | Premium accounts (paid sign-up on the web) | ☐ later |
| C | End-to-end encryption for accounts ⚠️ | ☐ later, before B goes live |

Legend: ☐ todo · ◐ in progress · ☑ done.

## Log

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
| F2 | No «Crear cuenta» for now: the welcome says «Empezar» / «Tengo una cuenta» and the login says accounts are by invitation. Later (Part B) a sign-up page on the web. | ✅ owner, changed 2026-10-02 |
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
- [x] Store listing: `android/fastlane/metadata/android/` in 5 languages (descriptions,
      changelog 2, screenshots en/es, feature graphic, icon).
- [x] Anti-feature `NonFreeNet` and the recipe draft: `docs/fdroid/com.blackwatermacros.app.yml`.
- [x] Process: [RELEASING.md](./RELEASING.md) (every release, catalog, screenshots, first
      submission, moving the current phones).

### Left (owner)

- [ ] Vercel → Settings → Git → Production Branch = `dev`.
- [ ] Merge `dev` into `main`, tag `v1.0.0`, push (RELEASING.md «Every release»).
- [ ] Merge request to fdroiddata (RELEASING.md «First submission»).
- [ ] Optional: Codeberg mirror (F3); a real release key for APKs shared outside F-Droid.
- [ ] After F-Droid publishes: move the three phones (RELEASING.md, last section).

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
