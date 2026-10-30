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

- [x] **Release key.** On your Mac, outside the repository:
  ```bash
  keytool -genkeypair -v -keystore ~/blackwater-release.jks -alias blackwater -keyalg RSA -keysize 4096 -validity 10000
  ```
  Choose a strong password and keep it in your password manager. **Back up the `.jks` file
  in two places** (e.g. an encrypted USB stick and your password manager): if it is lost, no
  update can ever be installed over the website's APK again.
- [x] **Its fingerprint in the repository:** `keytool -list -v -keystore ~/blackwater-release.jks
  -alias blackwater` → copy the `SHA256:` line into `android/gradle.properties` as
  `releaseCertSha256=` (it is public; it lets the app know it is the website's build, and the
  workflow refuses an APK signed with another key).
- [x] **GitHub secrets** (repository → Settings → Secrets and variables → Actions):
  `RELEASE_KEYSTORE_BASE64` (output of `base64 -i ~/blackwater-release.jks`),
  `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` (`blackwater`), `RELEASE_KEY_PASSWORD` (the
  same password unless you chose another).
- [ ] **Local signing** (optional, not needed: GitHub signs the releases; only to build signed APKs on the Mac): `android/keystore.properties`
  with `storeFile`, `storePassword`, `keyAlias`, `keyPassword` (gitignored).
- [x] **Domains** (in this order, so the app never loses its server). The website is
  `blackwater-macros.jordanmontt.fr`, the web app and the API are
  `app.blackwater-macros.jordanmontt.fr` (the Android app's server address since 1.0.1):
  1. **Vercel** → project → Settings → Domains → add `app.blackwater-macros.jordanmontt.fr`, and
     create the record Vercel shows in the OVH DNS zone of `jordanmontt.fr` (Web Cloud → Domain
     names → DNS zone: `CNAME`, subdomain `app.blackwater-macros`, target with a final dot). Wait until
     https://app.blackwater-macros.jordanmontt.fr/ opens the web app.
  2. **GitHub Pages:** repository → Settings → Pages → Source = **GitHub Actions**, Custom domain
     = `blackwater-macros.jordanmontt.fr`. (Optional but recommended: verify `jordanmontt.fr` in
     your GitHub account → Settings → Pages, so nobody else can claim it.)
  3. **OVH DNS:** change the `blackwater-macros` record to `CNAME jordanmontt.github.io.` (no A
     records left for it), then remove
     `blackwater-macros.jordanmontt.fr` from the Vercel project. When the certificate is ready,
     tick «Enforce HTTPS» in Pages (still to check). Old links to the web app (`/login`, `/progreso`…) are sent to
     `app.` by `website/404.html`; web users log in once more.
- [ ] **Phones with the old test builds** (signed with the Mac's debug key): check that sync is
  green (or export your data in Settings), uninstall, install from the website, log in again.
  1.0.0 still syncs with the old address (now the website), so its changes wait on the phone
  and uninstalling would lose them. First install a newer build over it, which keeps
  everything: on this Mac `cd android && ./gradlew :app:assembleRelease -PreleaseAbi=arm64-v8a`
  (without `keystore.properties` it is signed with the same debug key), then
  `adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk`. Open each account
  until it shows synced; then switch to the website APK as above. (Girlfriend's phone: done
  2026-10-21, the build stays installed for now.)

- [ ] **Vercel** → Settings → Git → Production Branch = `dev` (so pushes to `dev` deploy the web).
- [ ] **Google and Bing** (so the website shows up in searches). The website already has
  `robots.txt` and `sitemap.xml`; nothing to change in the repository.
  1. **Google:** https://search.google.com/search-console → Add property → **Domain** →
     `blackwater-macros.jordanmontt.fr`. Google shows a `TXT` record (`google-site-verification=…`):
     add it in the OVH DNS zone of `jordanmontt.fr` (Web Cloud → Domain names → DNS zone → Add an
     entry → `TXT`, subdomain `blackwater-macros`, value as shown). Wait a few minutes, click
     **Verify** (DNS can take up to a day; retry later if it fails).
  2. In that property: **Sitemaps** → submit `https://blackwater-macros.jordanmontt.fr/sitemap.xml`.
     Then **URL inspection** → `https://blackwater-macros.jordanmontt.fr/` → **Request indexing**.
  3. **Bing:** https://www.bing.com/webmasters → sign in → **Import from Google Search Console**
     (it takes the site and the sitemap, no second verification). Bing also feeds DuckDuckGo
     and Ecosia.
  4. A few days later, check **Pages** (Google) and **Site Explorer** (Bing) show the page as
     indexed. Leave the TXT record in place: removing it un-verifies the site.
- [x] **Merge request to F-Droid:** [fdroid/fdroiddata!53565](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/53565)
      (from the fork https://gitlab.com/jordanmontt/fdroiddata, branch `com.blackwatermacros.app`;
      text in [fdroid/merge-request.md](./fdroid/merge-request.md)). GitLab emails you when a
      reviewer comments; answer there. If they ask for the `TetheredNet` anti-feature (because
      of the sync server), accept it. After the merge the app appears within days.
- [ ] **Reopen the F-Droid merge request on or after 24 November 2026.** It was closed on
      2026-10-30: F-Droid wants [3 months of active development](https://f-droid.org/docs/Inclusion_Policy/#quality-control),
      and the repository started on 2026-08-24. Before reopening: in the fork, update `master`
      from upstream and rebase the branch `com.blackwatermacros.app` on it, put the latest tag
      in its `metadata/com.blackwatermacros.app.yml` (copy `docs/fdroid/com.blackwatermacros.app.yml`),
      wait for the pipeline to be green, then reopen !53565 with a short comment: actively
      developed since 24 Aug 2026, with the number of releases since.
- [ ] **Once it is on F-Droid:** replace «Coming soon to F-Droid» with the F-Droid badge and
      link (`https://f-droid.org/packages/com.blackwatermacros.app/`) in the README and in
      `website/index.html` (the line under the Download button), then push `main` so the
      website redeploys.

## Every release

Example for a release `1.2.0`; use your own numbers.

1. **On `dev`, everything green:**
   ```bash
   npx tsc --noEmit && npm run lint && npm test && npm run build && npm run core:sync-check
   (cd android && ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug \
       :app:assembleRelease -PreleaseAbi=arm64-v8a)
   ```
2. **Version** in `android/app/build.gradle.kts`: `versionCode` +1 (never reuse one) and
   `versionName`: the last number +1 for fixes (`1.1.0` → `1.1.1`), the middle one for
   features (`1.1.0` → `1.2.0`).
3. **What's new**: one file per language, `changelogs/<versionCode>.txt` in
   `fastlane/metadata/android/{en-US,es-ES,fr-FR,it-IT,de-DE}/` (≤ 500 characters
   each). The en-US one is also the text of the GitHub Release.
4. **Commit** on `dev` and push. Wait until CI is green (repository → Actions).
5. **Publish** (the tag is `v` + `versionName`):
   ```bash
   git checkout main && git merge --ff-only dev && git push origin main
   git tag v1.2.0 && git push origin v1.2.0
   git checkout dev
   ```
   (If `--ff-only` refuses: `git checkout dev && git merge main`, then repeat.)
6. **Check:** repository → Actions → **Release** is green (a few minutes), and
   https://github.com/jordanmontt/Blackwater-Macros/releases/latest shows the new version with
   `blackwater-macros.apk`. The website's Download button and the in-app notice (within a day)
   point to it by themselves.

Once the app is merged on F-Droid, F-Droid needs nothing from you: its bot sees the new tag,
adds it to the recipe in fdroiddata and publishes the build within a few days (status:
https://monitor.f-droid.org). **Until then** (the merge request is closed until 24 Nov 2026,
see above), after the tag update `docs/fdroid/com.blackwatermacros.app.yml` only; the fork
catches up when the merge request is reopened. **While the merge request is open**, the bot
doesn't run: after the tag, change the `Builds` entry (`versionName`, `versionCode`, `commit` = the full hash
from `git rev-parse v1.2.0`) and `CurrentVersion`/`CurrentVersionCode` in the fork's
`metadata/com.blackwatermacros.app.yml` (branch `com.blackwatermacros.app`) and in
`docs/fdroid/com.blackwatermacros.app.yml`. Only the newest version stays in the recipe. The
merge request's pipeline runs again; wait for it to be green. The Release workflow stops if the tag doesn't match
`versionName` or the en-US changelog for that `versionCode` is missing. A `versionCode` that
isn't higher than the last one is not caught by the workflow, but phones refuse it as an
update and F-Droid ignores it, so always raise it.

**Never move or reuse a published tag**: phones, the website and F-Droid rely on it. A mistake
gets a new version. To rebuild the APK of an existing tag (e.g. the workflow failed for a
reason outside the code), run «Release» by hand with that tag (Actions → Release → Run
workflow).

## Occasionally

- **New phone AI model:** edit `android/catalog/local-models.json` on `dev` (TECHNICAL.md
  «Adding a phone model»), run `npm test`, then `git checkout main && git cherry-pick <commit> &&
  git push origin main && git checkout dev && git merge main`. Phones see it next time
  Settings → AI opens; no app release needed.
- **New screenshots:** `scripts/screenshots.sh` (emulator + demo data; it waits for each screen to settle, and with a phone plugged in too, run it with `ANDROID_SERIAL=emulator-5554`). **Banner:** edit
  `scripts/store/feature-graphic-<lang>.svg`, then `npm i --no-save @resvg/resvg-js` and
  `node scripts/store/render-svg.mjs <svg> fastlane/metadata/android/<locale>/images/featureGraphic.png`.
- **Store listing** (`fastlane/metadata/android/`) stays at the root of the repository: F-Droid
  reads it only there, from the commit it builds. In
  `android/` it found nothing («Fastlane/Triple-T is not found») and the page would be empty.
- **New features:** update the README, `full_description.txt` and the website. The README is
  the source (English, full); `fastlane/metadata/android/<locale>/full_description.txt`
  is its short version in the five languages (same facts and order, simple HTML only, ≤ 4000
  characters each); `website/index.html` has the main ones (English). The website redeploys
  when `website/` changes on `main`, and takes the screenshots from the en-US store images.
- **The F-Droid recipe** only needs a new merge request when the build itself changes (a new
  Gradle property, CPU type, build tool, anti-feature or licence). In the fork: update `master`
  from upstream, make a new branch, edit `metadata/com.blackwatermacros.app.yml` (start from
  F-Droid's current file, which their bot keeps up to date), open the merge request, and keep
  `docs/fdroid/com.blackwatermacros.app.yml` in sync. F-Droid's CI runs `fdroid rewritemeta`
  with a newer fdroidserver than Homebrew's: if it fails, use the formatting its log shows.
