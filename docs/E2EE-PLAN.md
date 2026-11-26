# Plan: end-to-end encryption for accounts

A feature for later: it comes **before paid accounts go live**
([ACCOUNTS-PLAN.md](./ACCOUNTS-PLAN.md)), because migrating paying users' data to encryption later is
harder than starting encrypted. **Any session (human or agent) can pick it up from here.** Read
[`TECHNICAL.md`](./TECHNICAL.md) first (architecture, conventions, verification). Update this
file with the work and record every decision in **Decisions**.

Markers: ❓ = needs the owner's answer before that step starts · ⚠️ = changes a lot of code or
is risky · ✅ = decided.

**Status:** ☐ later ⚠️ (moved out of FDROID-PLAN.md, where it was Part C, on 26 Nov 2026).

## Decisions

| # | Decision | State |
|---|----------|-------|
| E1 | End-to-end encryption and password recovery (a truly end-to-end design cannot let the operator restore data alone; see «The honest answer»). Was F5. | ❓ |

## What was asked

Premium data end-to-end encrypted; usable from Android **and** the web; no data lost if the
phone is lost; and no data lost if the password is lost — «I can change the password and they
have everything».

## The honest answer

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

## Design (for options 2 + 3)

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

## What changes in the code ⚠️

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

## Open questions for the owner

- ❓ E1: options 2 + 3 (true E2EE), with or without the opt-in escrow (5)?
- ❓ Passkeys (4) from the start, or later?
- ❓ Encrypt `log_date` too (suggested) or keep it for server-side date queries?
- ❓ Migrate the current accounts automatically or by hand?
- ❓ Keep the password on the web each session, or «recordar este navegador»?
