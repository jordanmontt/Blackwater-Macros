# Releasing Blackwater Macros

F-Droid builds the app from this repository and signs it with its own key. You only push a
git tag; F-Droid notices it and publishes the new version within a few days. Nothing to
upload, nothing to sign.

- **`dev`**: everyday work. Every push runs CI and deploys the web.
- **`main`**: released code only. Tags `vX.Y.Z` live there. The phone-model catalog the apps
  read comes from `main` too.

## Every release

1. **On `dev`, everything green** (TECHNICAL.md §11):
   ```bash
   npx tsc --noEmit && npm run lint && npm test && npm run build && npm run core:sync-check
   (cd android && ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug \
       :app:assembleRelease -PreleaseAbi=arm64-v8a)
   ```
   Install the release APK on your phone and try the main screens once.
2. **Version** in `android/app/build.gradle.kts`: `versionCode` +1 (never reuse one) and
   `versionName` (`1.0.0` → `1.0.1` for fixes, `1.1.0` for features).
3. **What's new**, one file per language (≤ 500 characters, plain text):
   `android/fastlane/metadata/android/{en-US,es-ES,fr-FR,it-IT,de-DE}/changelogs/<versionCode>.txt`.
   If screens changed a lot, redo the screenshots (below). If features changed, update
   `full_description.txt` (≤ 4000 characters) and the README.
4. **Commit** on `dev` («Release 1.0.1»), then merge into `main` and tag:
   ```bash
   git checkout main && git merge --ff-only dev
   git tag v1.0.1 && git push origin main v1.0.1
   git checkout dev
   ```
   If `--ff-only` refuses, `main` has a catalog-only commit: `git checkout dev && git merge main`
   first, then repeat.
5. **Done.** F-Droid checks the tags, builds `v1.0.1` and publishes it (usually 1–5 days).
   Follow it on the app's page or in the build log at https://monitor.f-droid.org.

The tag must point at a commit where `versionName` = the tag without the `v`, and
`versionCode` is higher than the last release. That's all F-Droid needs.

## Publishing a phone AI model (no app release)

The apps read `android/catalog/local-models.json` from **`main`** on GitHub when Settings → AI
opens. To offer a new model: edit the file on `dev` following TECHNICAL.md «Adding a phone
model», run `npm test`, then bring just that commit to `main`:

```bash
git checkout main && git cherry-pick <commit> && git push origin main
git checkout dev && git merge main
```

Phones see it the next time Settings → AI opens. To retire a model, set `"hidden": true`.

## Screenshots and store images

- **Screenshots** (English and Spanish; other languages show the English ones):
  `scripts/screenshots.sh`. It boots the emulator, loads the demo dataset into a debug build
  and saves `phoneScreenshots/*.png`. Look at every image before committing.
  One-time setup of the emulator (Android SDK installed):
  ```bash
  sdkmanager "emulator" "system-images;android-36;default;arm64-v8a"
  ```
  The script creates the emulator (`bw-shots`, Pixel-sized) the first time.
- **Feature graphic** (1024×500): edit `scripts/store/feature-graphic-<lang>.svg`, then
  ```bash
  npm i --no-save @resvg/resvg-js
  node scripts/store/render-svg.mjs scripts/store/feature-graphic-en.svg \
       android/fastlane/metadata/android/en-US/images/featureGraphic.png
  ```
  (same for `es` → `es-ES`).
- **Icon:** `images/icon.png` is `public/icon-512x512.png` (from `scripts/logo/render-icons.sh`).

## When the F-Droid recipe needs a change

The recipe lives in fdroiddata (`metadata/com.blackwatermacros.app.yml`, copy in
`docs/fdroid/`). New versions don't touch it. Open a merge request there only if the build
itself changes: a new Gradle property, another CPU type (`releaseAbi`), a new build tool
(e.g. Node), a new anti-feature (a new online service), or a new licence.

## First submission (once)

1. Make sure the release `v1.0.0` exists (steps above, with `versionCode 2`).
2. Create an account on gitlab.com, fork https://gitlab.com/fdroid/fdroiddata and add
   `metadata/com.blackwatermacros.app.yml` from `docs/fdroid/`.
3. Optional but recommended: check it locally with fdroidserver
   (`fdroid lint com.blackwatermacros.app` and `fdroid build -v -l com.blackwatermacros.app`).
4. Open the merge request («New app: Blackwater Macros»). In the description, say:
   - Anti-feature `NonFreeNet`: optional cloud AI with the user's own key; on-device models
     from Hugging Face. Everything else works offline, without an account.
   - Bundled food data: Ciqual 2020 (Licence Ouverte / Etalab 2.0) and the Swiss Food
     Composition Database (FSVO; free use, including in apps, with attribution — credited in
     the app). Open Food Facts is only queried online (ODbL).
   - LiteRT-LM (Apache-2.0) comes from Google's Maven repository as in `app.chompass`.
5. Answer the reviewers' questions; after the merge, the first build appears within days.

## Moving the current phones to the F-Droid version

Builds installed by hand were signed with a different key, so Android won't update them
from F-Droid. On each phone: Settings → Account → Sync now; uninstall the app; install it
from F-Droid; «I have an account» and log in. The data comes back from the account. Without
an account, export a CSV first (Settings → Your data) and import it afterwards.
