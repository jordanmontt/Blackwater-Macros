# Releasing Blackwater Macros

F-Droid builds the app from this repository and signs it. You push a git tag; F-Droid
publishes it a few days later. Nothing to upload, nothing to sign.

- **`dev`**: everyday work. Every push runs CI and deploys the web.
- **`main`**: released code only, with the `vX.Y.Z` tags. The apps read the phone-model catalog
  from here too.

## Still to do (once)

- [ ] **Vercel** → Settings → Git → Production Branch = `dev` (so pushes to `dev` deploy the web).
- [ ] **Merge request to F-Droid** (`v1.0.0` is tagged and passed `fdroid lint`, the scanner
      and `fdroid build`):
  1. Create an account on gitlab.com and fork https://gitlab.com/fdroid/fdroiddata.
  2. Add `metadata/com.blackwatermacros.app.yml`: copy `docs/fdroid/com.blackwatermacros.app.yml`
     as is (it is already in F-Droid's format).
  3. Open the merge request «New app: Blackwater Macros» and paste in the description:
     - Anti-feature `NonFreeNet`: optional cloud AI with the user's own key; on-device models
       from Hugging Face. Everything else works offline.
     - Optional sync with an account (invite-only) on our own server; its code is in the same
       AGPL repository and the app works fully without it.
     - Bundled food data: Ciqual 2020 (Licence Ouverte / Etalab 2.0) and the Swiss Food
       Composition Database (FSVO; free use with attribution, credited in the app). Open Food
       Facts is only queried online (ODbL).
     - LiteRT-LM (Apache-2.0) comes from Google's Maven repository.
  4. Answer the reviewers. If they ask for the `TetheredNet` anti-feature (because of the
     sync server), accept it. After the merge, the app appears within days.
- [ ] **Once it is on F-Droid:** in the README, replace «Coming soon to F-Droid» with the
      F-Droid badge and link (`https://f-droid.org/packages/com.blackwatermacros.app/`).

## Every release

1. **On `dev`, everything green:**
   ```bash
   npx tsc --noEmit && npm run lint && npm test && npm run build && npm run core:sync-check
   (cd android && ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug \
       :app:assembleRelease -PreleaseAbi=arm64-v8a)
   ```
2. **Version** in `android/app/build.gradle.kts`: `versionCode` +1 (never reuse one) and
   `versionName` (`1.0.1` for fixes, `1.1.0` for features).
3. **What's new**: one file per language, `changelogs/<versionCode>.txt` in
   `android/fastlane/metadata/android/{en-US,es-ES,fr-FR,it-IT,de-DE}/` (≤ 500 characters).
4. **Publish:**
   ```bash
   git checkout main && git merge --ff-only dev
   git tag v1.0.1 && git push origin main v1.0.1
   git checkout dev
   ```
   (If `--ff-only` refuses: `git checkout dev && git merge main`, then repeat.)

F-Droid sees the tag and publishes it within a few days (status: https://monitor.f-droid.org).
The tag must match `versionName` (`v` + version) and `versionCode` must be higher than the
previous release.

## Occasionally

- **New phone AI model:** edit `android/catalog/local-models.json` on `dev` (TECHNICAL.md
  «Adding a phone model»), run `npm test`, then `git checkout main && git cherry-pick <commit> &&
  git push origin main && git checkout dev && git merge main`. Phones see it next time
  Settings → AI opens; no app release needed.
- **New screenshots:** `scripts/screenshots.sh` (emulator + demo data). **Banner:** edit
  `scripts/store/feature-graphic-<lang>.svg`, then `npm i --no-save @resvg/resvg-js` and
  `node scripts/store/render-svg.mjs <svg> android/fastlane/metadata/android/<locale>/images/featureGraphic.png`.
- **New features:** also update `full_description.txt` (≤ 4000 characters) and the README.
- **The F-Droid recipe** only needs a merge request when the build itself changes (a new
  Gradle property, CPU type, build tool, anti-feature or licence). Check it locally with
  `brew install fdroidserver`, then `fdroid lint` and `fdroid build -l com.blackwatermacros.app`
  inside a fdroiddata checkout.
