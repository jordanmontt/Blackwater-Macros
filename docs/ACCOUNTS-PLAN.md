# Plan: premium accounts

A feature for later: a paid sign-up on the web, so anyone can get an account (today accounts
are by invitation). **Any session (human or agent) can pick it up from here.** Read
[`TECHNICAL.md`](./TECHNICAL.md) first (architecture, conventions, verification). Update this
file with the work and record every decision in it.

Markers: ❓ = needs the owner's answer before that step starts · ⚠️ = changes a lot of code or
is risky · ✅ = decided.

**Status:** ☐ later (moved out of FDROID-PLAN.md, where it was Part B, on 26 Nov 2026). It needs
end-to-end encryption first: [E2EE-PLAN.md](./E2EE-PLAN.md).

## Steps

- [ ] Sign-up page on the web (`/cuenta/nueva`): choose username/password (or passkey, see
      [E2EE-PLAN.md](./E2EE-PLAN.md)), pay, account created. ❓ payment provider (Stripe, Paddle, Lemon Squeezy — card payments
      and EU VAT handling differ), price, monthly/yearly.
- [ ] Subscription state on the server (`users.plan`, `paid_until`); webhook from the payment
      provider. ❓ What happens when it expires: read-only? sync stops but the phone keeps
      working locally (suggested — local-first stays)?
- [ ] «Crear cuenta» (not on the welcome screen yet: FDROID-PLAN.md, decision F2) opens that page; after paying, the user logs in from the app.
- [ ] Self-hosting stays possible (the code is public): document running your own server, which
      is also what keeps the `NonFreeNet` question simple.
- ⚠️ Do end-to-end encryption ([E2EE-PLAN.md](./E2EE-PLAN.md)) **before** selling accounts:
  migrating paying users' data to encryption later is harder than starting encrypted.
