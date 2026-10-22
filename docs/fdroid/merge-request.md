## Abstract for new app inclusion

### Please introduce the functions and usage of your app in summary, and also attach some screenshots.

Blackwater Macros is a simple, private calorie and macro tracker. It tracks what you eat (calories, protein, carbs, fat) and your weight, and tells you how much to eat for your goal.

- Log meals from a built-in offline food database (Ciqual and the Swiss Food Composition Database), Open Food Facts search, barcode scanning, by hand, or optionally from a photo or a description with AI.
- Calorie and protein targets from published methods (Mifflin-St Jeor, the FAO/WHO factorial method for activity, sports-nutrition protein ranges). Every formula, its sources and its limits are explained inside the app.
- A weight trend, progress charts, and after a few weeks the expenditure measured from your own intake and weight.
- An optional coach chat about your diet and progress.
- English, Spanish, French, Italian and German.

It is local-first: it opens straight on your meals, with no sign-up, and everything is stored on the phone. AI is optional and uses either the user's own key for the provider they choose or a model downloaded to the phone.

<img src="https://raw.githubusercontent.com/jordanmontt/Blackwater-Macros/main/fastlane/metadata/android/en-US/images/phoneScreenshots/1_meals.png" width="180"> <img src="https://raw.githubusercontent.com/jordanmontt/Blackwater-Macros/main/fastlane/metadata/android/en-US/images/phoneScreenshots/2_add_food.png" width="180"> <img src="https://raw.githubusercontent.com/jordanmontt/Blackwater-Macros/main/fastlane/metadata/android/en-US/images/phoneScreenshots/5_progress.png" width="180"> <img src="https://raw.githubusercontent.com/jordanmontt/Blackwater-Macros/main/fastlane/metadata/android/en-US/images/phoneScreenshots/7_profile.png" width="180">

### Please elaborate why you wrote this app. Please highlight the unique functions it provides over other similar apps in F-Droid.

I wanted a tracker that does only meals, macros and weight, with targets I can trust and check by hand: no streaks, badges, social feed or ads. What it adds over the trackers already on F-Droid:

- The targets are fully transparent: the methodology is in the app and in the repository, and each number on screen can be recomputed.
- The activity level comes from what you actually do (gym days, session length, minutes walked a day) instead of a self-rated "active" choice.
- After about four weeks it measures your real expenditure from your logs, with its margin of error, and tells you only when your profile is clearly off.
- Past days keep the targets they had when you change your profile.

Chompass is the closest app on F-Droid and inspired the photo logging. Blackwater Macros is the simpler take, focused on the numbers.

### Notes for reviewers

- **Anti-feature `NonFreeNet`**: the optional AI features use the cloud provider the user chooses with their own key (Gemini, OpenAI, Anthropic, OpenRouter, or any OpenAI-compatible server), and on-device models are downloaded from Hugging Face. Logging, targets and statistics work fully without them.
- **Optional account sync**: invitation-only accounts on our own server, whose code (Next.js) is in the same AGPL repository. Without an account the app never contacts it, because a request interceptor refuses it before it leaves the phone. If you prefer the `TetheredNet` anti-feature for this, that's fine with me.
- **Network**: every host the app can reach is listed in the README («Private by design»), and a unit test (`NetworkHostsTest`) fails if the code ever learns another one.
- **Bundled food data**: Ciqual 2020 (Licence Ouverte / Etalab 2.0) and the Swiss Food Composition Database (FSVO, free use with attribution, credited in the app). Open Food Facts (ODbL) is only queried online.
- **Dependencies**: LiteRT-LM (Apache-2.0) for on-device models comes from Google's Maven repository. No Google Play Services, Firebase or analytics. `dependenciesInfo` is disabled.
- **Reproducible builds**: not enabled. The APK on GitHub is signed with my own key, and only an APK signed with that key checks GitHub (once a day) for new versions. F-Droid's build is signed with your key, so it never runs that check and F-Droid stays in charge of its updates. I'm fine with F-Droid signing with its key.
- **ABI**: only `arm64-v8a` is built (`-PreleaseAbi=arm64-v8a`), which covers the phones the on-device AI runs on. The APK is about 15 MB.
- The full pipeline passes on my fork, including `fdroid build` and `check apk`: https://gitlab.com/jordanmontt/fdroiddata/-/pipelines/2970401880

## Checklist

### Policy

* [x] The app complies with the [inclusion criteria](https://f-droid.org/docs/Inclusion_Policy).
* [x] The original app author has been notified (and does not oppose the inclusion). I am the author.
* [x] The upstream app source code repo contains the app metadata in a [Fastlane](https://gitlab.com/snippets/1895688) folder structure (`fastlane/metadata/android/`, five locales including `en-US`, with images, icon and changelogs).

### Docs

* [x] Please read [the guide](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md) first if this is your first contribution.
* [x] Please make sure your metadata follows the best practice in [our templates](https://gitlab.com/fdroid/fdroiddata/tree/master/templates).
* [x] Please read the [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/) and make sure your metadata is valid.
* [x] Please read the [Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/).

### Merge Request Setup

* [x] The title of this merge request should follow "New app: app name" format.
* [x] Please make sure your fdroiddata fork is public and your branch is not protected.
* [x] Please read [our Git guide](https://gitlab.com/fdroid/wiki/-/wikis/Tips-for-fdroiddata-contributors/Git-Usage) if you don't know how to rebase your branch. Don't rebase your branch if there is no conflict.
* [x] All related [fdroiddata](https://gitlab.com/fdroid/fdroiddata/issues) and [RFP issues](https://gitlab.com/fdroid/rfp/issues) have been referenced in this merge request (there are none).
* [x] Please only submit one app in one MR.

### Metadata

* [x] Metadata must be put in `metadata/<applicationId>.yml`.
* [x] Metadata must be a valid YAML file.
* [x] Metadata must use LF as line ending.
* [x] Don't add summary/description/changelog/images or anything that should be provided in upstream repo.
* [x] Releases are tagged and auto update is enabled unless there is a special reason.
* [x] There is an issue tracker and contact info of the author so that we can report bugs and contact the author.
* [x] An AuthorName must be added.
* [x] External repos are added as git submodules instead of srclibs (there are none).
* [ ] Enable [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds). Not enabled, see the notes above.
* [x] Setup abi split if the APK is large and the splitted ones can be much smaller.
* [x] Only the latest versions should be kept in the metadata before it's merged.
* [x] Don't add any disabled versions in the metadata.
* [x] The `commit` field should be the full hash.

### Pipeline

* [x] All pipelines should pass.
* [x] All warnings and errors in the Reports tab should be fixed or explained.

---------------------

/label ~"New App"
