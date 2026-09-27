# Plan: AI food logging, food databases, Progress tab, Coach and onboarding

Living plan for the next version. **Any session (human or agent) can pick it up from here.**
Read [`TECHNICAL.md`](../TECHNICAL.md) first (architecture, conventions, verification), then
the **Status** table below, then the phase you are on. Update this file in the same PR as the
work: tick the boxes, add a line to the **Log**, record any decision that changed.

## Status

| # | Phase | Web | Android | PR |
|---|-------|-----|---------|----|
| 0 | Plan + decisions confirmed | ☑ | ☑ | #12 |
| 1 | Core foundations (pure TS + Kotlin) | ☑ | ☑ | #12 |
| 2 | Progress tab (merge Peso + Estadísticas) | ☑ | ☑ | #12 |
| 3 | New «Añadir comida» flow: sheet, review form, copy from another day, manual | ☑ | ☑ | #12 |
| 4 | Food search + barcode (Open Food Facts + bundled generic foods) | ☐ | ☐ | |
| 5 | AI settings + AI client (cloud providers, keys, test) | ☐ | ☐ | |
| 6 | Photo logging with AI (+ text estimate) | ☐ | ☐ | |
| 7 | Coach tab | ☐ | ☐ | |
| 8 | Onboarding (first launch) | ☐ | ☐ | |
| 9 | On-device AI (Android: LiteRT-LM + Gemma 4) | — | ☐ | |
| 10 | Local AI on the web (optional, see D9) | ☐ | — | |
| 11 | Docs, F-Droid metadata, release | ☐ | ☐ | |

Legend: ☐ todo · ◐ in progress · ☑ done. **Workflow (user's choice): everything goes on the
single branch `ai-features` and the single PR #12; each phase is one commit** (a phase may be
split into «web» and «Android» commits if a session runs out of budget). Never open another PR.

## Log

- 2026-09-27 — Plan written (branch `ai-features`). Methodology v2 (PR #10) merged before.
- 2026-09-27 — Decisions D6, D7, D9 confirmed by the user (see §2). Checked: Swiss FCDB terms
  allow nutrition-diary apps with attribution; CIQUAL is Etalab 2.0; BEDCA has no reuse licence.
- 2026-09-27 — Phase 1 done on `ai/1-core` (branched from `ai-features`, so its PR also carries
  the plan). Gate green: web 358 tests, core sync OK, Android core/app tests + lint + assemble.
  Next: phase 2 (Progreso tab).
- 2026-09-27 — User asked for **one PR only**: branches consolidated into `ai-features`
  (PR #11 closed, replaced by #12). Phase 2 done as one commit. Next: phase 3, then phase 4
  (the user asked to continue with 4 after this; 4 plugs into 3's add-food flow).
- 2026-09-27 — Phase 3 done (one commit). Verified on the emulator (swipe closes an empty
  form, asks with edits, copy + undo) and in the browser at phone size (drag closes the
  sheet). Next: phase 4, Spanish first (see phase 4 notes).
- 2026-09-27 — User created a free Gemini key at https://aistudio.google.com/api-keys (that is
  the URL the guide must use). The key is entered by the user in the app (phase 5); it must
  never be pasted into chat, committed, or put in fixtures. Tests use recorded/mock responses.

---

## 1. Principles (do not break these)

1. **Simple.** This is not MyFitnessPal. Every screen has one job; every new feature must fit
   the existing «day of meals → recommendations → weight» model. No streaks, social, badges,
   micronutrients, recipes database, etc.
2. **Your data stays yours.** Local-first on Android (works without account, offline). Web
   requires login (unchanged). Nothing new is stored on our server.
3. **API keys never leave the device/browser except to the chosen AI provider.** Not synced,
   not sent to the Blackwater server, excluded from Android backups.
4. **Photos are never stored.** Kept in memory (or a cache file deleted immediately), downscaled,
   sent to the AI, discarded. Never written to the gallery, the DB or the server.
5. **Manual entry stays first-class** — both modes (`per_ingredient` and `total_only`) keep
   working exactly as today. AI, barcode and search only *fill the same form*.
6. **F-Droid clean:** only FOSS dependencies (no Google Play Services, no ML Kit, no Firebase).
7. **Web ⇄ Android parity** as today, same rules as `docs/ANDROID-TEST-SPEC.md`: pure logic
   lives in `src/lib/core` + Kotlin `:core` with mirrored tests.

## 2. Decisions

Defaults chosen by the plan. ✅ = confirmed by the user; ❓ = confirm before implementing that
phase (ask once, record the answer here).

| # | Decision | Default |
|---|----------|---------|
| D1 | DB schema | ✅ **No schema change.** Profile fields exist (`gender`, `birthYear`, `heightCm`, activity, `calorieGoal`); onboarding weight → `weights` table; AI items are ingredients in the existing `ingredients` JSONB (`name`, `quantity`, kcal/P/C/F); keys and onboarding flag are local. |
| D2 | Tabs | ✅ Comidas · Progreso · Coach · Ajustes (Peso + Estadísticas merged into Progreso). |
| D3 | Chat history | ✅ Memory only: lost when the app/tab is closed. «Nueva conversación» clears it. |
| D4 | AI providers (cloud) | Google Gemini (recommended, free key), OpenAI, Anthropic Claude, OpenRouter, «Otro (compatible con OpenAI)» with base URL (covers Ollama / LM Studio). Model name prefilled per provider, editable. Verify current model ids at implementation time. |
| D5 | Where each AI feature runs | Two selectors in Ajustes → IA: **Fotos:** Nube / Dispositivo; **Coach:** Nube / Dispositivo. All four combinations allowed (cheap to support once both engines exist). |
| D6 | On-device model (Android) | Gemma 4 E2B `.litertlm` (~2.6 GB, needs ~6 GB RAM, arm64) via LiteRT-LM, downloaded on demand from Hugging Face (`litert-community/gemma-4-E2B-it-litert-lm`). Same stack as Chompass (on F-Droid). Clear warnings: size, Wi-Fi, RAM, «menos preciso que la nube». ✅ If image input does not work with E2B, on-device = **Coach only** and the photo selector shows «Las fotos requieren la nube». |
| D7 | Food databases | ✅ **Only open databases, the more the better; the app's focus is Spanish.** Online: Open Food Facts (barcode + text search, ODbL, attribution). Bundled offline generic index built from: USDA FoodData Central Foundation + SR Legacy (CC0, en), **CIQUAL** (ANSES, Licence Ouverte/Etalab 2.0, fr/en), **Swiss Food Composition Database** (FSVO: free incl. nutrition-diary apps, source must be acknowledged; de/fr/it/en). **BEDCA (Spain) excluded:** no reuse licence. Because no open DB has Spanish names, the build script adds `name_es` for the most common ~1,000 generic foods (one-time AI translation, committed as reviewable data). Credit every source in the UI and in Metodología. |
| D8 | Averages in Progreso | Averages over **logged days only**, with «N de M días registrados» (consistent with the measured expenditure). Drop «pico» and «Media semanal del peso» cards. |
| D9 | Local AI on the web | ✅ Yes, **low effort**: in-browser model with WebLLM (`@mlc-ai/web-llm`) for the **Coach only**, one small model (~0.5–1 GB, pick from WebLLM's prebuilt list at implementation), shown only when the browser has WebGPU (works on some phones, not all — the user also uses the web from the phone, so it must degrade gracefully to «no disponible en este navegador»). Size warning before download. The «Otro (compatible con OpenAI)» provider also covers Ollama/LM Studio. No time on anything beyond that. |
| D10 | Keys on the web | `localStorage` of that browser (per device; re-enter on another browser). Browser calls the provider directly (no proxy through our server). |
| D11 | Coach data sharing | The Coach sends a compact summary of your data with each question (see §6). Toggle in Ajustes → IA «El coach puede ver mis datos» (default on). |
| D12 | Onboarding | Shown once on first launch (Android) / first login with an incomplete profile (web). «Iniciar sesión» is the first option on Android. |

## 3. Architecture overview

```
                 ┌───────────── src/lib/core  +  android/core (pure, mirrored tests) ─────────────┐
                 │ foods.ts        per-100 g → portion scaling, OFF product parsing, unit parsing │
                 │ ai-schema.ts    parse/validate the AI meal JSON → ingredient drafts           │
                 │ coach.ts        buildCoachContext(), weightProjection(), macroAverages()      │
                 │ progress.ts     macro averages over logged days, calorie series vs target     │
                 └──────────────────────────────────────────────────────────────────────────────────┘
 Web (src/lib/ai/*, src/lib/foods/*)             Android (app/.../ai/*, app/.../foods/*)
  - providers: gemini, openai-compatible,         - same providers with OkHttp/Retrofit (streaming)
    anthropic (fetch, streaming)                  - OnDeviceEngine (LiteRT-LM) behind same interface
  - keys in localStorage                          - keys: Android Keystore AES-GCM in prefs, no backup
  - OFF via fetch, generic index /foods.json      - OFF via OkHttp, generic index in assets
  - barcode: BarcodeDetector / zxing-wasm polyfill- barcode: CameraX + zxing-cpp
```

**AI interface (both platforms):**

```
interface AiEngine {
  estimateMeal(photos: Image[], description: string?, locale): MealEstimate   // JSON result
  estimateText(query: string, locale): MealEstimate                            // "3 bananas"
  chat(system: string, messages: Message[]): Stream<String>                    // Coach
  test(): Result                                                               // settings «Probar»
}
```

`MealEstimate` = `{ title, items: [{ name, grams, calories, protein, carbs, fat }], confidence: low|medium|high, notes }`.
Providers are asked for JSON (Gemini `responseMimeType: application/json` + schema; OpenAI
`response_format: json_object`; Anthropic: instruct + parse). The **core parser**
(`ai-schema.ts`) is the only place that turns model output into ingredients: tolerant of code
fences / extra text, clamps negatives, rounds with `round1`, rejects empty lists, flags items
whose kcal is far from 4P+4C+9F (> 25 % off) so the review form can highlight them.

## 4. UX specification

### 4.1 «Añadir comida» (the main redesign)

One idea: **a meal draft that you fill from any source, then review and save.**

```
[+] (FAB on Comidas)
 └─ Sheet «Añadir comida»                     (drag down / tap outside = close; confirm if dirty)
     ┌──────────┬──────────┬──────────┐
     │ 📷 Foto  │ ▦ Código │ 🔍 Buscar│      big buttons (Foto only if AI configured,
     └──────────┴──────────┴──────────┘       otherwise it shows «Configura la IA»)
     ⎘ Copiar de otro día
     ✎ Escribir a mano
     Plantillas: [Desayuno] [Cena ligera] …   (existing template chips)
```

- **Review form** (= today's `MealForm`, improved): title, list of items (name, quantity,
  kcal/P/C/F, all editable, delete), totals, mode toggle (por ingrediente / solo total),
  «+ Añadir alimento» (opens Foto / Código / Buscar again and appends), «Guardar».
  Everything except «Escribir a mano» arrives here pre-filled.
- **Foto (IA):** tips card (collapsible, remembered once closed): «Haz varias fotos desde
  distintos ángulos», «Incluye un tenedor o tu mano para la escala», «Fotografía también la
  etiqueta nutricional si la hay», «Describe lo que no se ve: aceite, salsas, azúcar».
  Photo strip (camera or gallery, up to 5, remove with ×), optional text field «Describe tu
  comida», button «Estimar macros» → progress → review form with items + a line «Estimación
  de la IA (confianza media): revisa las cantidades». Errors: no key / quota / offline → clear
  message + «Escribir a mano».
- **Código de barras:** full-screen camera scanner → product card (name, brand, per 100 g) →
  portion (grams, default = serving size if known, else 100 g; quick buttons «1 ración»,
  «100 g») → «Añadir». Not found → «No está en Open Food Facts» + «Buscar» / «Escribir a mano».
  Offline → queue nothing, say it needs internet (cache of already-seen products: nice-to-have).
- **Buscar:** search field; results: generic foods first (offline index) then Open Food Facts
  (online, debounced ≥ 400 ms, respect 10 req/min), each «name · brand · kcal/100 g». Tap →
  portion → added. Last item of the list: «Estimar “<query>” con IA» (uses `estimateText`, e.g.
  «3 plátanos»). Recent foods (last 20 picked, local) shown when the field is empty.
- **Copiar de otro día:** day navigator (defaults to yesterday) → that day's meals with
  checkboxes → «Copiar al <día seleccionado>». Copies directly as new meals (no review), undo
  snackbar. Same for web.
- **Escribir a mano:** empty review form, exactly today's behaviour.
- **Bug to fix (current):** the add/edit meal modal does not close when dragged/scrolled down —
  Android: `ModalBottomSheet` with drag-to-dismiss (and `confirmValueChange` → «¿Descartar
  cambios?» if dirty); web: same behaviour on mobile (drawer) + close on outside click/Escape.

### 4.2 Progreso tab (merge Peso + Estadísticas)

Top to bottom, one range selector for the whole screen (7 d · 30 d · 90 d · 1 a · Todo):

1. **Peso** summary card: current weight, trend (7-day MA), rate kg/week, change in range.
2. **Weight chart** (the current one, unchanged — the user likes it).
3. **Calories chart**: daily kcal bars/line + 7-day trend + target band (targetMin–targetMax).
4. **Promedio de macros** card (replaces protein/carbs/fat charts, reuses «Resumen de macros»):
   avg kcal, protein, carbs, fat per logged day vs targets (kcal and protein ranges),
   split % of kcal (P/C/F), «N de M días registrados». No «pico».
5. **Gasto medido** line (from methodology v2) when available.
6. **Registros de peso** list (existing edit/delete), FAB «+» = add weight (existing dialog).

Removed: «Media semanal del peso» card, protein/carbs/fat charts, peak values. Metodología
page updated accordingly (drop the weekly-average section, explain logged-days averages).

### 4.3 Coach tab

- Empty state: title «Coach», one line «Pregunta sobre comida, tu dieta o tu progreso», and
  example chips: «¿Cuál será mi peso en 30 días?», «¿Como demasiado?», «¿Qué como hoy para
  llegar a mis calorías?», «¿Qué ceno?», «¿Cuántos macros tienen 3 plátanos?», «Evalúa mi dieta
  de esta semana». Tap = send.
- Chat list with streaming answers, input bar, «Nueva conversación» (top bar), small footer
  «El coach puede equivocarse. No es consejo médico.» and which engine is used («Gemini ·
  nube» / «Gemma · dispositivo»).
- Not configured → the tab shows a short card «Configura la IA para usar el coach» →
  Ajustes → IA.
- History: memory only (ViewModel scoped to the activity on Android; module state on web).

### 4.4 Ajustes → IA (one card, «Inteligencia artificial»)

```
Proveedor en la nube:  [Google Gemini (gratis) ▾]
Clave API:             [••••••••••]  [Probar]     «Cómo conseguir una clave gratis» (expands)
Modelo:                gemini-…  (editable, advanced)
Modelo en el dispositivo (Android): Gemma 4 E2B · 2,6 GB   [Descargar] / [Eliminar]
   ⚠ Ocupa 2,6 GB, necesita ~6 GB de RAM, usa Wi-Fi. Menos preciso que la nube.
Usar para fotos:  (•) Nube  ( ) Dispositivo
Usar para coach:  (•) Nube  ( ) Dispositivo
[x] El coach puede ver mis datos
```

**Free Google key guide** (onboarding and here): 1) Abre aistudio.google.com/api-keys e inicia
sesión con tu cuenta de Google. 2) Pulsa «Create API key». 3) Cópiala y pégala aquí.
Privacy note (verified 2026-09-27 in the Gemini API terms): on the free tier Google may use
prompts and photos to improve its products and humans may review them — **except** in the EEA,
Switzerland and the UK, where the paid-tier terms apply to free usage too. Say it in one line.

### 4.5 Onboarding (first launch)

Android (4 short steps, «Saltar» always visible from step 2):

1. **Bienvenida** — logo, «Registra tus comidas y tu peso. Tus datos se quedan en tu
   teléfono.» Buttons: **«Ya tengo cuenta — Iniciar sesión»** (existing login; after sync, skip
   step 2 if the profile is complete) and **«Empezar sin cuenta»**.
2. **Tus datos** — sexo (Hombre/Mujer), año de nacimiento, altura, peso actual, objetivo
   (Definición / Mantenimiento / Volumen); collapsible «Actividad» (gym days, minutes, walking).
   Saves profile + first weight entry.
3. **IA (opcional)** — what it is for (fotos, coach), the free Google key guide + «Abrir AI
   Studio», paste + «Probar». «Otras opciones» → note that other providers / on-device are in
   Ajustes. «Ahora no».
4. **Listo** → Comidas.

Web: after login, if the local flag is unset and the profile is incomplete → steps 2–4.
Flag: `AppPreferences.onboardingDone` (Android), `localStorage` (web). «Ver tutorial» in Ajustes.

## 5. Food data details

- **Open Food Facts**
  - Barcode: `GET https://world.openfoodfacts.org/api/v2/product/{code}.json?fields=code,product_name,brands,serving_size,serving_quantity,nutriments`.
    Nutriments: `energy-kcal_100g`, `proteins_100g`, `carbohydrates_100g`, `fat_100g`
    (+ `_serving`). Fallback kcal from `energy_100g` (kJ ÷ 4.184).
  - Text search: Search-a-licious `https://search.openfoodfacts.org/search?q=…&langs=…&page_size=20&fields=…`
    (the v2 API has no full-text search; `/cgi/search.pl` is deprecated). Verify params at
    implementation.
  - Limits: 15 req/min product, 10 req/min search per IP → debounce, cache results in memory.
  - Android sets `User-Agent: BlackwaterMacros/<version> (<contact>)`. Browser can't set it; if
    CORS blocks anything, add a thin pass-through route on our server (no storage).
  - Licence: ODbL → attribution «Datos de Open Food Facts» in the search/barcode UI and in
    Metodología.
- **Generic foods index** (bundled, offline): `scripts/foods/build-generic-index.ts` downloads
  USDA FDC Foundation + SR Legacy, CIQUAL and the Swiss Food Composition Database, keeps
  `{id, source, names: {en, es?, fr?, de?, it?}, kcal, protein, carbs, fat}` per 100 g for a
  curated subset, writes `public/foods/generic.json` (web) and
  `android/app/src/main/assets/foods/generic.json`. Spanish: `names.es` for the ~1,000 most
  common foods, translated once by the script (AI) and committed so it can be reviewed and
  fixed by hand; search matches all name languages, accent-insensitive. Spanish products also
  come from OFF; anything else → «Estimar con IA». Size budget ≤ 2 MB gzip. Licences/credits:
  USDA (CC0), CIQUAL (Etalab 2.0, «Source : Anses, Table Ciqual»), Swiss FCDB («Swiss Food
  Composition Database, FSVO»), OFF (ODbL).
- Pure helpers in core (`foods.ts`): `scalePer100g(n, grams)`, `parseOffProduct(json)`,
  `parseServingGrams("30 g")`, `normalizeQuery()`; mirrored in Kotlin with tests.

## 6. Coach context (what the AI sees)

Built by the pure `buildCoachContext()` (core, mirrored + tested) as compact text, only when the
user sends a message and D11 is on:

- Profile: sex, age, height, goal, activity; targets (kcal range, protein range), BMR, TDEE,
  measured expenditure ± margin if available.
- Today: meals with items and totals; remaining kcal/protein to the target range.
- Last 14 days: daily totals of **logged** days; averages; days logged.
- Weight: last 60 days summary (first, latest, 7-day trend, rate kg/week), latest body fat.
- **Projection computed by the app, not the model:** `weightProjection(days=30)` = trend rate ×
  days with the same uncertainty rules as the measured expenditure; if not enough data, say so.
  The system prompt tells the model to use these numbers and not invent its own.
- System prompt: role «coach de nutrición de Blackwater Macros», answer in the app language,
  short, practical, no medical claims; for food macros questions give per-item estimates.

## 7. Phases (each ends green on the verification gate)

**Verification gate** (every phase):

```bash
npx tsc --noEmit && npm run lint && npm test && npm run build
npm run core:sync-check
(cd android && ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug)
```

Plus a manual check on the emulator (screenshots via `adb exec-out screencap`) and in the
browser (demo mode: «Explorar datos de demo» on /login). New Android strings go to all five
`strings.xml`; paragraphs use escaped `\n` (aapt collapses literal newlines) and strings with
`%` that are not format strings need `formatted="false"`.

### Phase 1 — Core foundations
- [x] `src/lib/core/foods.ts` + `tests/unit/foods.test.ts`; Kotlin `Foods.kt` + `FoodsTest.kt`.
      (`scalePer100g`, `foodToIngredient`, `parseServingGrams`, `normalizeText`,
      `parseOffProduct(Fields)`, `parseOffSearch`, `searchGenericFoods`; types `Per100g`,
      `FoodProduct`, `GenericFood {id, source, names: {es,en,fr,de,it}, per100g}`.)
- [x] `src/lib/core/ai-schema.ts` + tests; Kotlin `AiSchema.kt` + tests (`MEAL_ESTIMATE_SHAPE`
      for prompts, `extractJson`, `parseMealEstimate`, `estimateToIngredients`).
- [x] `src/lib/core/progress.ts` (`macroAverages` over logged days + kcal split) + tests;
      Kotlin mirror. (The calorie chart's target band needs no core code: it is the
      recommendation's `targetMin/targetMax`.)
- [x] `src/lib/core/coach.ts` (`weightProjection`, `buildCoachContext`, `buildCoachSystemPrompt`)
      + tests with a shared fixture (`tests/unit/coach.fixture.ts`) asserting the exact text;
      Kotlin mirror produces the identical text. `expenditure.ts` now exports `fitWeightTrend`
      (+ `Z_95`) so the projection reuses the same statistics.
- [x] Pairs in `android/test-sync/manifest.json`; tables in `docs/ANDROID-TEST-SPEC.md` and
      `src/lib/core/README.md`. `:core` now depends on `kotlinx-serialization-json` (JSON trees).

### Phase 2 — Progreso tab
- [x] Web: `src/app/progreso/page.tsx` (merge `peso` + `estadisticas`), redirects from `/peso`
      and `/estadisticas` (`next.config.ts`), nav in `components/app-nav.tsx`, i18n (unused
      peso/stats keys removed). Shared `components/weight-form-dialog.tsx` and
      `lib/use-recommendations.ts` (also used by the Comidas card). Behavior test
      `progreso-page` replaces `peso-page`; `tests/helpers/repos.ts` got `mealDto`.
- [x] Android: `ProgressScreen.kt` + `ProgressViewModel.kt` (pure `buildProgress`, tested in
      `ProgressLogicTest`) replace `PesoScreen`/`StatsScreen` and their VMs; `BottomNavBar.kt`
      has 3 tabs (Coach tab arrives in phase 7); `TrendChart` got an optional target `band`;
      strings ×5 (unused stats_* removed).
- [x] Metodología (web + Android ×5): weekly-average section removed; weight summary and
      daily-nutrition texts describe the Progreso card, logged-days averages and the kcal split.
- Note: the stats series (`StatsSummary.calories`) has every day of the period with 0 when
  unlogged, so `macroAverages(summary.calories)` is used directly; the calories chart plots
  logged days only (a 0 would drag the trend down).

### Phase 3 — «Añadir comida» flow
- [x] Review form = existing `MealForm` (both entry modes kept) that accepts a pre-filled
      draft: web `MealForm initial` (a `NutritionDraft`), Android `MealFormSheet(initial,
      prefilled = true)`. A pre-filled form counts as unsaved (asks before discarding).
      Phases 4/6 fill it; no separate «draft» store was needed.
- [x] «Añadir comida» sheet: web `components/meals/add-food-sheet.tsx` (on the new
      `components/ui/sheet.tsx`), Android `AddFoodSheet.kt`. Sources now: Escribir a mano,
      Copiar de otro día, templates (moved off the Comidas page). Phase 4 adds Buscar/Código
      at the top, phase 6 Foto.
- [x] Copy from another day (web + Android): pick a day, tick meals, copy to the day being
      viewed; Undo (web toast action, Android snackbar). Shared content → request helpers:
      web `lib/meal-payload.ts` `copyMealPayload`, Android `MealFormValue.toCopyRequest`;
      `AppRepository.saveMeal` now returns the id.
- [x] Dismiss bug fixed: sheets close by dragging down / outside / Back-Esc; the meal form
      asks «¿Descartar los cambios?» when it has edits (Android no longer blocks `Hidden`).
- [x] Tests: `today-page` (sheet, templates, manual, copy, discard confirmation);
      Android `ValidationTest` (`toCopyRequest`), `OfflineSyncTest` (saveMeal returns id).

### Phase 4 — Search + barcode

**Spanish first (user, 2026-09-27): most users will be Spanish speakers.** So: every
bundled generic food gets a Spanish name (not only the top 1,000), Spanish names rank first
when the app language is Spanish, common Spain/Latin-America variants are matched as
synonyms (plátano/banana, patata/papa, judías/frijoles/porotos, melocotón/durazno,
zumo/jugo, maíz/choclo, gambas/camarones…), Open Food Facts is queried with `langs=es` and
Spanish product names preferred, and the review form shows the Spanish name.

- [ ] Generic index build script + generated files (committed) + attribution.
- [ ] OFF client (web fetch; Android OkHttp with User-Agent), rate-limit/debounce, in-memory
      cache.
- [ ] Search screen with portion step; recent foods (local).
- [ ] Barcode: Android CameraX + zxing-cpp (`CAMERA` permission, requested on first use);
      web `BarcodeDetector` with zxing-wasm polyfill. Portion step shared with search.
- [ ] Tests: parsing with fixtures (core), MockWebServer for OFF (Android), mocked fetch (web).

### Phase 5 — AI settings + client
- [ ] Provider abstraction + Gemini, OpenAI-compatible, Anthropic implementations
      (non-streaming JSON for estimates, streaming for chat). Android: OkHttp SSE; web: fetch
      streams (Anthropic browser header `anthropic-dangerous-direct-browser-access: true`).
- [ ] Key storage: Android Keystore-backed encrypted prefs, excluded in
      `data_extraction_rules.xml`/`backup_rules.xml`; web `localStorage`.
- [ ] Ajustes → IA card (§4.4) with «Probar» and the free-key guide. Android strings ×5.
- [ ] Tests: request building + response parsing per provider against recorded fixtures
      (MockWebServer / mocked fetch). Never call real providers in tests.

### Phase 6 — Photo logging
- [ ] Photo screen (§4.1): camera (Android `TakePicture` into cache file, deleted after
      reading; or CameraX) + Photo Picker; web `<input type=file accept=image/* multiple
      capture>`; downscale to max 1024 px JPEG ~80 %; nothing persisted.
- [ ] Prompt + JSON schema; result → review form; confidence line; error states.
- [ ] «Estimar con IA» from search (text only).
- [ ] Tests: prompt building, image downscale util, error mapping; UI test on web with mocked
      engine.

### Phase 7 — Coach
- [ ] Coach tab UI (§4.3), in-memory chat state, streaming, examples, not-configured state.
- [ ] Context from `buildCoachContext`; D11 toggle.
- [ ] Tests: context content (core), chat reducer, mocked engine streaming.

### Phase 8 — Onboarding
- [ ] Android: 4-step flow (§4.5), shown when `onboardingDone` is false; login path reuses
      `LoginScreen` logic; profile + first weight saved through `AppRepository`.
- [ ] Web: steps 2–4 after first login with incomplete profile.
- [ ] «Ver tutorial» in Ajustes. Tests for the gating logic.

### Phase 9 — On-device AI (Android)
- [ ] Upgrade Kotlin to ≥ 2.2 (LiteRT-LM requirement; project is on 2.1.20) — separate
      commit, full gate.
- [ ] `com.google.ai.edge.litertlm:litertlm-android`; `OnDeviceEngine` implementing
      `AiEngine`; model download via WorkManager (foreground, resumable, progress, Wi-Fi
      default), checksum, delete. arm64 only: hide the option on unsupported devices / < 6 GB
      RAM.
- [ ] Verify multimodal (D6); JSON output reliability → stricter prompt + core parser retries
      once.
- [ ] F-Droid: confirm LiteRT-LM is acceptable (Chompass precedent) before merging.

### Phase 10 — Local AI on the web (low effort, D9)
- [ ] WebLLM (`@mlc-ai/web-llm`, loaded lazily so it does not weigh on the normal bundle),
      `navigator.gpu` check, one small model, download with progress + size warning, Coach
      only. Hidden/disabled with a one-line reason when WebGPU is missing (common on phones).
      Timebox: one session; if it fights back, ship without it and note it here.

### Phase 11 — Docs & release
- [ ] TECHNICAL.md (new modules, AI section, Progress tab), README features, api.md unchanged
      (no API change), ANDROID-TEST-SPEC tables, Metodología (AI estimates are estimates; data
      sources + ODbL attribution).
- [ ] F-Droid: `CAMERA` permission, anti-feature **NonFreeNet** (optional cloud AI providers),
      fastlane metadata/screenshots, version bump.

## 8. Risks and mitigations

| Risk | Mitigation |
|------|------------|
| AI returns malformed JSON / wrong numbers | Core parser (tolerant + validation), always land in the editable review form, never auto-save. |
| OFF rate limits / CORS | Debounce, cache, generic index offline; optional thin proxy route. |
| No open DB with Spanish names | One-time translated `names.es` for common generic foods + OFF (has Spanish products) + «Estimar con IA». |
| On-device model too big/slow | Optional, warnings, feature-level selector, cloud default. |
| Keys leaking | Never logged, never synced, excluded from backup, masked in UI; web warns it's stored in this browser. |
| Scope creep | Principles §1; each phase ships alone; stop and ask before adding anything not listed. |

## 9. Sources checked (2026-09-27)

- Chompass (F-Droid `app.chompass`): zxing-cpp barcode, Open Food Facts + offline USDA/Swiss
  indexes, LiteRT-LM with Gemma 4 E2B (~2.4–2.6 GB) / E4B, BYO cloud key (Google AI Studio free).
  https://github.com/fitguyfitguy/Chompass · https://f-droid.org/en/packages/app.chompass/
- Open Food Facts API (v2 product, Search-a-licious, rate limits, User-Agent, ODbL):
  https://openfoodfacts.github.io/openfoodfacts-server/api/
- Gemini API terms (unpaid data use; EEA/CH/UK exception): https://ai.google.dev/gemini-api/terms
- LiteRT-LM Android (Kotlin ≥ 2.2, `.litertlm` models): https://ai.google.dev/edge/litert-lm/android
- Swiss Food Composition Database terms: https://naehrwertdaten.ch/en/downloads/ ·
  https://opendata.swiss/en/dataset/naehrwerte_lebensmittel
- BEDCA has no reuse licence (open-data request): https://datos.gob.es/en/solicitud-de-datos/base-de-datos-bedca
