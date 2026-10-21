# Releasing Blackwater Macros

You push a git tag, and two things happen on their own:

- **GitHub** (`.github/workflows/release.yml`) builds the APK, signs it with your release key
  and publishes it as a GitHub Release. The website's Download button always gives the latest
  one, and the APKs from the website tell their users about it within a day.
- **F-Droid** builds the same tag, signs it with its own key and publishes it a few days later.

The two are signed with different keys, so an app from one can't update an app from the other
(switching means reinstalling; the website says so). Nothing to upload by hand.

- **`dev`**: everyday work. Every push runs CI and deploys the web.
- **`main`**: released code only, with the `vX.Y.Z` tags. The apps read the phone-model catalog
  from here too.

## Still to do (once)

- [ ] **Release key.** On your Mac, outside the repository:
  ```bash
  keytool -genkeypair -v -keystore ~/blackwater-release.jks -alias blackwater -keyalg RSA -keysize 4096 -validity 10000
  ```
  Choose a strong password and keep it in your password manager. **Back up the `.jks` file
  in two places** (e.g. an encrypted USB stick and your password manager): if it is lost, no
  update can ever be installed over the website's APK again.
- [ ] **Its fingerprint in the repository:** `keytool -list -v -keystore ~/blackwater-release.jks
  -alias blackwater` → copy the `SHA256:` line into `android/gradle.properties` as
  `releaseCertSha256=` (it is public; it lets the app know it is the website's build, and the
  workflow refuses an APK signed with another key).
- [ ] **GitHub secrets** (repository → Settings → Secrets and variables → Actions):
  `RELEASE_KEYSTORE_BASE64` (output of `base64 -i ~/blackwater-release.jks`),
  `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` (`blackwater`), `RELEASE_KEY_PASSWORD` (the
  same password unless you chose another).
- [ ] **Local signing** (optional, to build signed APKs on the Mac): `android/keystore.properties`
  with `storeFile`, `storePassword`, `keyAlias`, `keyPassword` (gitignored).
- [ ] **Domains** (in this order, so the app never loses its server). The website is
  `blackwater-macros.jordanmontt.fr`, the web app and the API are
  `app.blackwater-macros.jordanmontt.fr` (the Android app's server address since 1.0.1):
  1. **Vercel** → project → Settings → Domains → add `app.blackwater-macros.jordanmontt.fr`, and
     create the DNS record Vercel shows (a `CNAME` for `app.blackwater-macros`). Wait until
     https://app.blackwater-macros.jordanmontt.fr/ opens the web app.
  2. **GitHub Pages:** repository → Settings → Pages → Source = **GitHub Actions**, Custom domain
     = `blackwater-macros.jordanmontt.fr`. (Optional but recommended: verify `jordanmontt.fr` in
     your GitHub account → Settings → Pages, so nobody else can claim it.)
  3. **DNS:** change the `blackwater-macros` record to `CNAME jordanmontt.github.io`, then remove
     `blackwater-macros.jordanmontt.fr` from the Vercel project. When the certificate is ready,
     tick «Enforce HTTPS» in Pages. Old links to the web app (`/login`, `/progreso`…) are sent to
     `app.` by `website/404.html`; web users log in once more.
- [ ] **Phones with the old test builds** (signed with the Mac's debug key): check that sync is
  green (or export the CSV in Settings), uninstall, install from the website, log in again.

- [ ] **Vercel** → Settings → Git → Production Branch = `dev` (so pushes to `dev` deploy the web).
- [ ] **Merge request to F-Droid**, after `v1.0.1` is released (`v1.0.0` passed `fdroid lint`,
      the scanner and `fdroid build`; update the recipe's `Builds` entry to 1.0.1, versionCode 3
      and the tag's commit first):
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

The **Release** workflow (repository → Actions) publishes the signed APK in a few minutes;
the website and the update notice pick it up by themselves. F-Droid sees the tag and publishes
it within a few days (status: https://monitor.f-droid.org). The tag must match `versionName`
(`v` + version), `versionCode` must be higher than the previous release, and the en-US
changelog for that `versionCode` must exist (the workflow stops otherwise).

**Never move or reuse a published tag**: phones, the website and F-Droid rely on it. A mistake
gets a new version. To rebuild the APK of an existing tag (e.g. the workflow failed), run
«Release» by hand with that tag.

## Occasionally

- **New phone AI model:** edit `android/catalog/local-models.json` on `dev` (TECHNICAL.md
  «Adding a phone model»), run `npm test`, then `git checkout main && git cherry-pick <commit> &&
  git push origin main && git checkout dev && git merge main`. Phones see it next time
  Settings → AI opens; no app release needed.
- **New screenshots:** `scripts/screenshots.sh` (emulator + demo data). **Banner:** edit
  `scripts/store/feature-graphic-<lang>.svg`, then `npm i --no-save @resvg/resvg-js` and
  `node scripts/store/render-svg.mjs <svg> android/fastlane/metadata/android/<locale>/images/featureGraphic.png`.
- **New features:** update the README and `full_description.txt`. The README is the source
  (English, full); `android/fastlane/metadata/android/<locale>/full_description.txt` is its
  short version in the five languages: same facts and order, simple HTML only, ≤ 4000
  characters each.
- **The F-Droid recipe** only needs a merge request when the build itself changes (a new
  Gradle property, CPU type, build tool, anti-feature or licence). Check it locally with
  `brew install fdroidserver`, then `fdroid lint` and `fdroid build -l com.blackwatermacros.app`
  inside a fdroiddata checkout.
