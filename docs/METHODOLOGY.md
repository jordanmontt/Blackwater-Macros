# Blackwater Macros — Scientific methodology

How the app turns a profile, weigh-ins and logged meals into calorie and protein targets,
a measured expenditure and a weight projection: every formula, why it was chosen, where
it comes from and where it stops being reliable.

- **Code:** `src/lib/core/calories.ts`, `protein.ts`, `expenditure.ts`, `stats.ts`,
  `stats-builder.ts`, `progress.ts`, `coach.ts`, `food-questions/` (web), mirrored 1:1 in Kotlin
  `android/core` (see [TECHNICAL.md](./TECHNICAL.md) §8 and §11.1).
- **User-facing text:** the Metodología page (web `metodologia.*` in `src/i18n/*.json` plus
  the formula blocks and references in `src/app/metodologia/page.tsx`; Android `meth_*`
  strings — a few generated from the same JSON — and `MethodologyScreen.kt`).
- **Rule:** a change to any number here changes the code (both platforms), the tests (both
  platforms), the Metodología texts (5 languages × 2 platforms) and this file, together.

The numbers are for healthy adults. They are estimates, not medical advice: pregnancy,
breastfeeding, kidney disease, eating disorders, adolescents and competitive athletes
need individual guidance.

---

## 0. Principles

1. **Published, validated equations over home-made ones.** Every constant has a source
   (§8). Where the app had to choose inside a published range, the choice and its
   reason are written down here.
2. **Conservative when unsure.** Exercise energy is counted low, deficits and surpluses
   are moderate, the target never goes below the basal metabolic rate.
3. **The formula starts the target; your own data take over.** Once there is a measured
   expenditure, the target is built on the formula's and the measured expenditure combined
   by how precise each one is (§3.2), recalculated once a week so it does not jump with
   the scale's noise. The screen always says what the target is built on.
4. **Show the uncertainty, hide what the data cannot support.** The measured expenditure
   carries a 95 % margin and is hidden when that margin is too wide.
5. **"Not logged" is not "zero".** Days without meals are left out of averages, never
   counted as 0 kcal.

## Summary

| Quantity | Method | Main source |
|---|---|---|
| Basal metabolic rate (BMR) | Mifflin-St Jeor | Mifflin 1990; Frankenfield 2005 |
| Activity factor (PAL) | Factorial method from gym and walking minutes | FAO/WHO/UNU 2004; Compendium 2024 |
| Calorie target | TDEE − 400 / ± 100 / + 300 kcal, never below BMR | Hall 2008; Helms 2014 (JISSN); Iraki 2019 |
| Past days | Each day keeps the profile and weigh-in it had; changes count from today | FAO/WHO/UNU 2004 (PAL as habitual average) |
| Protein | g/kg by goal (maintain 1.6–2.2 with strength training); lean mass when cutting with body fat; weight at BMI 25 above BMI 25; above the range is not a warning | Jäger 2017; Morton 2018; Nunes 2022; Helms 2014 (IJSNEM); McClave 2016; Kokura 2024; Gallagher 2000; Devries 2018; Antonio 2016 |
| Strength training | With gym days, every goal also aims at muscle; a flat weight in a deficit can be fat lost and muscle gained | Barakat 2020; Murphy & Koehler 2022; Garthe 2011; Hall 2008 |
| Measured expenditure | Energy balance over the 28 days before Monday, weight slope compared only within weigh-ins taken alike (fasted or not, same time zone), 95 % margin | Hall 2008; Montgomery 2021 |
| Adaptive target | Formula (σ = 10 %) and measured (σ = margin ÷ 1.96) combined by inverse-variance weighting, weekly | Frankenfield 2005; Bajunaid 2025 |
| Profile check | Measured 95 % range entirely outside profile ± 10 %, on two weekly measurements; says whether the profile fits, the target already follows the data | Frankenfield 2005; Schoeller 1995; Lichtman 1992 |
| Weight trend | 7-day moving average | Zheng 2015; Walker 2005 |
| Weekly rate | Slope within weigh-ins taken alike × 7 | Montgomery 2021 |
| Coach projection | Trend line extended 30 days with its 95 % confidence band | Montgomery 2021 |
| Questions about your meals | The model only reads the question; the app counts over logged days (≥ 7, a hint below 14), amounts only in g or ml, food vs weight as an association | §3 rules for the weight trend |
| Macro energy split | 4 / 4 / 9 kcal per g | Atwater general factors |
| Fat share | Period's average outside 20–35 % of calories, ≥ 7 logged days: one line in Progreso, no target | IOM 2005; WHO 2023; Hooper 2020; Whittaker & Wu 2021; Hall & Guo 2017; Gardner 2018 |

---

## 1. Energy expenditure from the profile (`calories.ts`)

### 1.1 Basal metabolic rate: Mifflin-St Jeor

```
Men:   BMR = 10 × weight_kg + 6.25 × height_cm − 5 × age + 5
Women: BMR = 10 × weight_kg + 6.25 × height_cm − 5 × age − 161
```

**Why this one.** The systematic review by Frankenfield et al. (2005) compared the common
predictive equations against measured resting metabolic rate. Mifflin-St Jeor was the
most reliable: within ±10 % of measured in more non-obese *and* obese adults than any
other. It needs only sex, weight, height and age, which the profile already has.

**Alternatives not used.**
- *Harris-Benedict (1919/1984):* older reference population, tends to overestimate.
- *Katch-McArdle / Cunningham (lean-mass based):* more accurate when lean mass is known
  precisely, but the body fat people log comes mostly from home bioimpedance scales,
  which can be off by several percentage points. An error there would move BMR more than
  Mifflin's own error. We would also lose the estimate for everyone who logs no body fat.

**Limits.**
- It is a population equation: roughly 1 person in 4 to 1 in 5 falls outside ±10 %. The
  measured expenditure (§3) exists for exactly this reason.
- It was derived for adults aged about 19–78. The profile accepts birth years 1920–2010.
- Age is `current year − birth year`, so it can be one year high for part of the year.
  That costs at most 5 kcal/day.
- Mifflin predicts *resting* metabolic rate, a little above the strict basal rate that
  FAO's PAL values are defined against, so BMR × PAL comes out slightly high. The
  difference is small and partly offsets the conservative choices below, which count
  activity low.

### 1.2 Activity factor (PAL): the factorial method

```
gym     = gym days × minutes per session / 7            [average min/day]
walking = walking minutes per day
PAL     = ((1440 − gym − walking) × 1.4 + gym × 4.0 + walking × 3.5) / 1440
TDEE    = round(BMR × PAL)
```

This is the **factorial method** of the FAO/WHO/UNU expert consultation (2004):

- The 1440 minutes of the day are split into blocks, and each block counts at its
  intensity as a multiple of BMR.
- Exercise minutes *replace* ordinary minutes instead of being added on top, so the
  resting energy of those minutes is never counted twice.
- Every extra minute of training or walking raises PAL. There are no jumps between
  categories.

**The three intensities.**

| Block | × BMR | Why |
|---|---|---|
| Everything else (sleep, sitting, desk work, light chores) | **1.4** | The low end of FAO's "sedentary or light activity" band, PAL 1.40–1.69. FAO's own worked example of a sedentary office worker comes to 1.53 *including* an hour of walking at 3.2 × BMR. Without that hour it is ≈ 1.45, so 1.4 sits just below it. |
| Gym (resistance training, including rests) | **4.0** | 2024 Compendium: 3.5 MET (multiple exercises, 8–15 reps), 5.0 (a general gym visit), 5.0–6.0 (heavy or vigorous lifting). A real session includes rests between sets, so the low half of the range. |
| Walking | **3.5** | 2024 Compendium: 3.0 MET at 4 km/h, 3.5 walking for pleasure, 3.8 at 4.5–5.5 km/h. |

**METs as multiples of BMR.** A MET is defined as 3.5 ml O₂/kg/min, which is usually
*higher* than a person's real resting oxygen uptake (Byrne et al. 2005). Using the MET
value directly as a multiple of BMR therefore undercounts exercise energy slightly. We
accept this because it errs on the conservative side.

**Why not a "sedentary … very active" scale.**
- Self-reported activity agrees poorly with measured activity. Against accelerometers
  it is usually higher (60 % of the comparisons), and the gap is largest for vigorous
  activity (Prince et al. 2008).
- The app therefore asks only for *times* (days, minutes, walking) and fixes the
  intensities itself. Nobody has to judge whether their life is "active" or
  "very active".
- This is still self-report, since minutes can be overestimated too. That residual
  error is what the measured expenditure catches.

**Worked example.** 3 sessions of 60 min a week and 30 min of walking a day:
`gym = 25.7`, PAL = ((1440 − 25.7 − 30) × 1.4 + 25.7 × 4.0 + 30 × 3.5) / 1440 = **1.49**.
With no exercise, PAL is 1.40. The profile limits (7 days × 300 min of gym, 480 min of
walking) cap PAL at ≈ 2.64.

**What it leaves out.** Physical work, sports other than the gym, and everyday movement
beyond walking (NEAT). People with active jobs will be underestimated; the measured
expenditure shows by how much.

### 1.3 Calorie target

The offsets apply to the expenditure the target is built on: the formula's TDEE until there
is a measured expenditure, then the two combined (§3.2).

| Goal | Target | Range | Expected change (÷ 7700 kcal/kg) |
|---|---|---|---|
| Cut | TDEE − 400 | − 500 … − 300 | ≈ 0.36 kg/week (0.27–0.45) |
| Maintain | TDEE | ± 100 | — |
| Surplus | TDEE + 300 | + 200 … + 400 | ≈ 0.27 kg/week (0.18–0.36) |

**Why these offsets.**
- **Cut.** Helms, Aragon & Fitschen (2014, JISSN) recommend losing 0.5–1 % of body
  weight per week to keep muscle, which is 0.35–0.7 kg for 70 kg. −400 kcal/day
  (≈ 0.36 kg/week) is exactly 0.5 % a week at about 73 kg. For lighter people it is the
  gentle end of that range; for heavier people it is below it. That is deliberate: a
  slower loss is easier to sustain, and the range allows −500.
- **Surplus.** Iraki et al. (2019) propose gaining 0.25–0.5 % of body weight per week in
  a lean bulk (0.18–0.35 kg for 70 kg). +300 kcal/day lands inside that range.
- **Maintain.** ±100 kcal is a band, not a target. The daily noise of logging is larger
  than that.
- **Why fixed kilocalories rather than a percentage.** They are predictable and easy to
  reason about. A percentage-of-TDEE rule gives large, fast deficits to large and very
  active people, which is the opposite of conservative.

**The 7700 kcal/kg factor** (Hall 2008) is the classic energy content of 1 kg of weight
change when it is mostly fat. The true value depends on how much of the change is fat and
how much is lean tissue and water. It is also a *static* rule: over months, expenditure
adapts and a fixed deficit slows down. Here it is used only for two things:
- translating the offsets into the rates above, as guidance;
- the 4-week measured expenditure (§3), which uses your actual weight trend and
  therefore includes any adaptation that has already happened. Since the target now
  follows it (§3.2), a deficit that slows down is caught at the next weekly update.

**Floor.** The target, its minimum and its maximum never go below the BMR. Only a cut for
a small, sedentary person reaches it (e.g. TDEE 1540 − 500 = 1040 against a BMR of
1100). This is a safety convention, not a published threshold: eating below your BMR is
a very-low-calorie diet and is better done with supervision.

### 1.4 Past days keep their targets (`profile-history.ts`)

**Rule.** A day's targets use the profile in effect that day, the last weigh-in on or
before it (before the first weigh-in, the first one) and the expenditure measured on that
week's Monday (§3.2). Editing the activity, the goal or any
other profile field counts from today: the app keeps the old profile for the days up to
yesterday. Several edits on the same day are one change.

**Why.** The energy you burned on a day depends on what you did that day. Judging last
week's intake with this week's activity (or goal, or weight) moves the goalposts. The
same holds for weight: BMR goes up and down with it, so each day uses the weigh-in known
then. MyFitnessPal works this way; Cronometer rewrites past days, and its users have asked
for years to change that.

**Proportion.** The profile's PAL describes a habitual week (FAO/WHO/UNU 2004 define PAL as
a 24-hour average over a typical period), not one week. With a BMR of about 1500 kcal:
- one 60-minute gym day less per week is ≈ −25 kcal/day (60/7 min × (4.0 − 1.4) / 1440 × 1500);
- 30 minutes less walking per day is ≈ −65 kcal/day (30 × (3.5 − 1.4) / 1440 × 1500).

Both are below the formula's own uncertainty (±10 %, about ±200 kcal; Frankenfield 2005).
So this rule is about consistency, not precision, and one atypical week is not a reason
to edit the profile.

**Corrections.** The app cannot tell a change of routine from a correction (activity that
was always higher than entered). Past days keep the targets computed with the old numbers,
as MyFitnessPal does; asking on every edit was rejected as one more decision most people
cannot answer. The measured expenditure (§3) uses only intake and weight, so it is the
reference for what was really burned, whatever the profile said.

**Not affected.** The measured expenditure does not use the profile, and the profile check
(§3.1) compares it with the current profile, which is the one it judges.

---

## 2. Protein (`protein.ts`)

The number shown is the midpoint of a range, in grams per day:
`range = basis_kg × (min…max g/kg)`.

### 2.1 Ranges by goal

| Goal | g/kg | Basis | Source and reasoning |
|---|---|---|---|
| Maintain, strength training (gym days > 0) | 1.6–2.2 | body weight | The surplus range. At maintenance someone who lifts loses fat and gains muscle slowly (§2.4), and the muscle gained from more protein keeps growing up to ~1.6 g/kg (Morton et al. 2018). In Nunes et al. (2022, 74 RCTs) extra protein added lean mass in adults under 65 doing resistance exercise when the total reached ≥ 1.6 g/kg. 1.4 is below that point. |
| Maintain, no strength training | 1.4–2.0 | body weight | ISSN position stand for people who exercise (Jäger et al. 2017). Well above the 0.8 g/kg RDA, which is a minimum for sedentary people, not an optimum for people who train. |
| Surplus | 1.6–2.2 | body weight | Meta-analysis of 49 RCTs (Morton et al. 2018): the benefit to muscle gain plateaus at ~1.6 g/kg, with the upper 95 % confidence limit at 2.2. Iraki et al. (2019) recommend exactly 1.6–2.2 for a surplus. |
| Cut, no body fat logged | 1.8–2.7 | body weight | In a deficit more protein is needed to keep muscle. This is the per-body-weight equivalent of 2.3–3.1 g/kg of lean mass at typical body fat: 2.3 × 0.78 ≈ 1.8 (22 % fat) and 3.1 × 0.87 ≈ 2.7 (13 % fat). |
| Cut, body fat logged | 2.3–3.1 | lean mass = weight × (1 − fat %) | Helms, Zinn et al. (2014, IJSNEM), a systematic review in lean, resistance-trained people in a deficit; adopted by Jäger et al. (2017). |

**Why not higher for recomposition.** Barakat et al. (2020), a narrative review of
recomposition in trained people, suggest 2.6–3.5 g/kg of lean mass, from small studies.
In one of them, trained people eating 3.4 g/kg lost more fat than at 2.3 g/kg but gained
no more lean mass (Antonio et al. 2015). The meta-analyses above put the plateau for
muscle at ~1.6 g/kg with an upper limit near 2.2, so the app stays at 1.6–2.2 outside a
cut. The cut's 2.3–3.1 g/kg of lean mass is already close to Barakat's range.

**Above the range is not a warning.** The top of each range is where the evidence stops
seeing extra benefit for muscle, not a safety limit, so the app says «X g above the range»
in a neutral grey and keeps the bar's colour (calories over the range stay orange). In
healthy adults a meta-analysis of 28 studies found no difference in the change of kidney
function between high-protein diets (≥ 1.5 g/kg, ≥ 20 % of energy or ≥ 100 g/day) and
normal ones (Devries et al. 2018). In a one-year crossover study in 14 resistance-trained
men, 2.5–3.3 g/kg/day had no harmful effect on blood lipids or liver and kidney markers
(Antonio et al. 2016); it is a small study, so it supports, not proves, the point. People
with kidney disease are outside the scope of these numbers (see the note at the top).

**Why lean mass only when cutting.** Each range is applied on the basis it was derived
for. The deficit studies express protein per kg of lean mass. The maintenance and surplus
studies, including the Morton meta-analysis, express it per kg of body weight.

**Which body fat.** The most recent weigh-in that has a body fat value. Home bioimpedance
scales can be off by several percentage points, so the lean-mass rule is only as good as
that number.

### 2.2 Above BMI 25: the weight at BMI 25

```
reference_kg = 25 × height_m²     (used instead of actual weight when weight > reference_kg)
```

**Why.**
- The g/kg ranges come mostly from normal-weight, trained people.
- Fat tissue barely raises protein needs, so applying them to a high body weight
  overshoots. At 110 kg and 1.80 m, a cut would call for 198–297 g/day.
- Clinical nutrition doses protein on an ideal or reference weight in obesity (McClave
  et al. 2016).

**Check against outcomes.** Kokura et al. (2024) is a meta-analysis of weight loss in
adults with overweight or obesity. It found that more than 1.3 g/kg of *actual* weight
preserves muscle mass, and that less than 1.0 g/kg raises the risk of losing it; strength
and physical function did not improve. With the reference weight, the example uses 81 kg
and a cut gives 146–219 g/day. That is 1.33–1.99 g/kg of actual weight, just above
Kokura's line.

**Severe obesity.** Above BMI ≈ 35 the low end of the cut range falls below 1.3 g/kg of
actual weight, while the midpoint stays near it:
- 130 kg at 1.80 m (BMI 40): 146–219 g/day, target 182 g = 1.40 g/kg.
- 146 kg at 1.80 m (BMI 45): the same grams, target = 1.25 g/kg.

At that size, dosing per actual weight is exactly what clinical guidance avoids. McClave
et al. give 2.0 g/kg of ideal weight at BMI 30–40 and up to 2.5 at BMI ≥ 40, and the
1.8–2.7 g/kg of reference weight used here matches that.

**Why BMI 25.** It is the upper limit of the normal range. The usual "ideal body weight"
formulas give less: Hamwi gives ≈ 77 kg at 1.80 m for men. BMI 25 is the more generous
of the common choices.

The lean-mass rule for a cut with logged body fat takes precedence: lean mass already
removes the fat.

### 2.3 The muscle exception: body fat below what goes with BMI 25

A high BMI can be muscle. If the logged body fat is **below the average body fat at BMI 25
for the person's sex and age**, the extra weight is lean mass and the actual weight is
used.

Thresholds are from Gallagher et al. (2000), Table 4 (African-American and white adults,
predicted body fat at BMI 25):

| Age | Men | Women |
|---|---|---|
| 20–39 | 20 % | 33 % |
| 40–59 | 22 % | 34 % |
| 60+ (study: 60–79) | 25 % | 36 % |

- **Under 20, or no birth year:** the 20–39 band, which is the strictest.
- **Age** is `current year − birth year`, as for Mifflin.
- **Asian adults** have more body fat at the same BMI (Gallagher Table 5: men 23/24/24 %,
  women 35/35/36 % at BMI 25). The Table 4 values are therefore slightly strict for them:
  a muscular person just above the threshold may get the reference weight and a somewhat
  lower range. Logging body fat while cutting avoids the question, since lean mass is then
  used.
- **Correction (2026-09-28).** Until then the thresholds were 25 % for men and 33 % for
  women at every age. But 25 % is Gallagher's value for BMI **30** in men aged 20–39, so
  young men with 20–25 % body fat and a BMI above 25 kept their full weight as the
  protein basis. The result was a higher range than the method intends: not harmful, but
  not what the method says.

### 2.4 Strength training: muscle in every goal (`doesStrengthTraining`)

**Rule.** With gym days and minutes above 0, the app assumes the user also wants to gain
muscle, whatever the goal. The goal only sets the pace: how fast fat goes, how fast
muscle comes and how much fat is accepted. Without strength training, food alone does
not build muscle, so with 0 gym days the app does not assume it. Walking does not count.

| Goal | With strength training | Without |
|---|---|---|
| Cut | Lose fat fastest while keeping muscle; gaining some is possible, more likely for beginners, people coming back after a break or with more fat to lose | Lose fat |
| Maintain | Lose fat and gain muscle slowly; the weight may barely change | Keep the weight |
| Surplus | Gain muscle fastest, with a little fat | Gain weight; without strength training most of it is fat |

**The evidence.**
- *Recomposition is real* with resistance training and enough protein, in untrained and
  trained people (Barakat et al. 2020), most easily in beginners, people returning to
  training and people with more fat.
- *A deficit slows muscle gain, more the larger it is.* In Murphy & Koehler (2022), lean
  mass gains were smaller in a deficit than at maintenance (strength gains were not), and
  in their meta-regression a deficit of ~500 kcal/day prevented them. The 500 figure has
  been questioned, so it is a guide, not a line.
- *A slow cut leaves room for gains even in trained people.* Elite athletes who lifted 4×
  a week gained 2.1 % lean mass while losing 0.7 % of body weight a week, and none at
  1.4 % a week (Garthe et al. 2011). The app's cut (−400 kcal, ≈ 0.5 %/week at 73 kg, §1.3)
  is slower than that.
- *Not for everyone.* For lean people who have trained for years, keeping muscle is the
  realistic result of a cut: physique competitors near contest leanness mostly do not show
  recomposition (Barakat et al. 2020). Gains in a cut are small, often about a kilo over
  several weeks, close to the error of bioimpedance and even DXA.

**What it means for the scale.** Weight is fat plus lean tissue, and the two store very
different energy: about 1800 kcal per kg of lean tissue against about 7700 for the usual
weight loss, mostly fat (Hall 2008: 7.6 vs 39.5 MJ per kg of lean and fat tissue).
Losing 0.2 kg of fat and gaining 0.2 kg of muscle a week leaves the weight flat while the
real deficit is (0.2 × 7700 − 0.2 × 1800) / 7 ≈ 170 kcal/day. So:
- a flat weight in a deficit is not always a stall;
- the measured expenditure (§3), which counts every kilo at 7700 kcal, comes out somewhat
  low in that case.

**Where the app says it.** Only with strength training:
- Perfil, under the goal: the descriptions in the table above.
- The profile check (§3.1): a «you burn less» notice adds «part of it can be muscle you're
  gaining», and the reasons list adds this cause with the example.
- The coach: the goal text and a note that a flat weight in a deficit can be
  recomposition, so it suggests strength, waist size or photos besides the scale.

**What it does not do.** It changes no calorie target, and it does not try to split the
weight into fat and muscle: body fat is optional and most people do not log it.

---

## 3. Measured expenditure (`expenditure.ts`)

The formula in §1 is a population estimate: two people with the same profile can differ
by several hundred kcal/day. When there is enough data, the app measures the real value
from **energy balance**, as adaptive apps such as MacroFactor do. If weight is stable,
you burn what you eat; if it moves, the difference is energy stored or released.

```
Window:  the 28 days before this week's Monday (the current week is still being logged)
intake = mean kcal of the days with meals logged
β      = slope of the daily-mean weights, only within groups of weigh-ins taken alike [kg/day]
       = Σ_g Σᵢ(xᵢ − x̄_g)(yᵢ − ȳ_g) / Σ_g Σᵢ(xᵢ − x̄_g)²
TDEE   = intake − β × 7700
margin = 1.96 × σ / √Sxx × 7700,   Sxx = Σ_g Σᵢ(xᵢ − x̄_g)²       [kcal/day, 95 %]
σ      = max( √(Σ residuals² / (n − G − 1)), 0.5 kg )            G = number of groups
```

With a single group (every weigh-in taken the same way, which is also how older data
counts) this is the ordinary least-squares line, with n − 2 degrees of freedom.

**Data rules, and why.**

| Rule | Reason |
|---|---|
| ≥ 21 logged days of 28 | Weight reflects *every* day, logged or not. The intake average must cover nearly all of them, or unlogged (often larger) days are missing from it. |
| Days without meals are excluded, never 0 kcal | Counting them as zero would make you look like you burn far less. |
| ≥ 4 weigh-in days spanning ≥ 14 days | A slope over a few days is dominated by water and glycogen swings. |
| One point per group and day (the mean of that day's weigh-ins in the group) | Two weigh-ins taken alike on one day are not independent measurements of the trend, so counting them twice would shrink the error falsely. |
| The trend compares only weigh-ins taken alike: fasted or not, and in the same time zone | A weigh-in after eating or training reads 0.5–1 kg higher, and another scale reads differently. Comparing across them turns a change of habit into a fake loss or gain (below, «Weigh-ins taken alike»). |
| No largest margin | A wide margin is shown with its ±, counts less in the target (§3.2) and needs a bigger gap before the check speaks (§3.1). A cut-off made the target jump (§3.2, «Why no cut-off»). |

**The error model.**
- The slope's standard error is `σ / √Sxx`, from ordinary least squares (Montgomery et
  al. 2021).
- σ is estimated from the residuals with n − G − 1 degrees of freedom (n − 2 with one
  group), but never below
  **0.5 kg**, a realistic day-to-day scatter for body weight. A handful of weigh-ins can
  fall on a straight line by chance, and without the floor they would look precise.
- `Sxx` (within groups) grows with both the number of weigh-ins and how spread out they
  are; splitting into groups lowers it a little, which is the cost of a split.

Worked margins, at the 0.5 kg floor and 28-day window:

| Weighing | Sxx | Margin | Data's share, 2600 kcal profile (§3.2) |
|---|---|---|---|
| Daily | 1827 | ± 177 kcal/day | 89 % |
| 3 × a week | 767 | ± 272 | 78 % |
| 2 × a week | 508 | ± 335 | 70 % |
| Weekly | 245 | ± 482 | 53 % |

**z (1.96) rather than Student's t.**
- With the noise floored, σ is treated as a known lower bound of the scale's noise, and
  for a known σ the normal quantile is the right one.
- When the residual scatter is *above* the floor, σ is estimated. Then t(n − 2) would be
  stricter: 2.06 instead of 1.96 for daily weighing (+5 %), and 2.23 for 3 weigh-ins a
  week (+14 %), which would lower the data's share by a few points.
- We keep z: the floor already makes the typical case conservative, and the margin is
  shown as an approximate 95 %. (Until 2026-11, z also kept 3 weigh-ins a week under the
  ±300 cut-off; there is no cut-off now.)

**What the margin does not cover.**
- *Logging error:* under-logging makes expenditure look lower, one for one.
- *The 7700 kcal/kg approximation.*
- *Water and glycogen shifts:* in the first weeks of a new diet, and across the menstrual
  cycle.

A **constant** logging error does not hurt the target, though: if you always log 10 % less
than you eat, the measured expenditure is low by the same amount, and a target built on it
is in the same units as your logging. The 4-week window, the margin, the weekly update
and the formula's share in §3.2 soften the rest.

**Weigh-ins taken alike.**

*In plain words.* The app only compares weigh-ins taken in the same conditions: fasted
with fasted, after eating with after eating, and in the same time zone. Changing how you
weigh yourself then never counts as weight lost or gained.

*Why.* Weight rises 0.5–1 kg through the day with food and drink, and another scale can
read a kilo apart. The trend is a line through 4 weeks of weigh-ins. A 1 kg jump in the
middle of the window, from a change of habit, reads as 1 × 98 / 1827 × 7700 ≈ **410
kcal/day** of extra expenditure, and the ± margin cannot see it: it only measures scatter.
Two real exports showed it (2026-11-24):
- one person switched from after-breakfast and after-gym weigh-ins to fasted ones on
  waking, and the measurement read 2506–2711 while the fasted weigh-ins alone said ≈ 2000–2300;
- another moved across time zones (and scales), weighed at midday for two weeks, then
  went back to waking weigh-ins, and the measurement read 3000–3450 for six weeks while
  the stretches of waking weigh-ins said 2665 ± 137 and 2605 ± 204 (formula ≈ 2650).

*How.*
- The weight form has an «En ayunas» switch (on waking, before eating or drinking). It
  starts from the latest weigh-in's value, so it is only touched when the habit changes.
- Each new weigh-in stores the device's time zone. A weigh-in in a different zone from the
  last known one starts a new period: a move almost always means another scale and travel
  water shifts. Summer time keeps the zone's name, and an unknown zone (older weigh-ins)
  never splits.
- Groups = (fasted or not, time-zone period). The trend has one level per group and one
  common slope (a fixed-effects or ANCOVA fit with indicator variables; Montgomery et al.
  2021). The jump between groups is absorbed by their levels, so only the change *within*
  each group counts. Groups with a single day carry no slope and are left out.
- The groups come only from the switch and the time zone, never from the weights. A real
  change of body weight, including fat lost while muscle is gained (§2.4), is therefore
  never taken for a change of conditions.

*Why no time-of-day rule.* An automatic split «when the time of weighing moves by N hours»
was considered and dropped: any N would be tuned on a few people, weekend lie-ins and
summer time would have to fall under it, and it still could not tell «after breakfast at
8» from «on waking at 7». Fasted or not is what changes the weight; the time zone is
recorded exactly.

*Older weigh-ins* count as fasted, with no time zone: the app always asked for weigh-ins on
waking, and this way no one's target moves on the day of the update. They can be corrected
by editing them.

*What it costs, checked by simulation.* A person with a true expenditure of 2600 kcal/day,
eating 2600 ± 400 a day and logging 25 of 28 days, scale noise 0.5 kg, 1000 windows each:

| Daily weigh-ins | Before: bias / 95 % range holds 2600 | After: bias / holds 2600 |
|---|---|---|
| Nothing changes | −4 / 94 % | identical (1000 of 1000 windows) |
| After breakfast (+0.8 kg) for 14 days, then fasted | **+326** / 9 % | −5 / 95 % (margin ± 196 → ± 374) |
| Time-zone move with a new scale (−1.5 kg) at day 10 | **+565** / 0 % | −1 / 95 % (margin ± 230 → ± 336) |
| Fasted switch set at random, no real change | −4 / 93 % | −5 / 93 % (margin ± 187 → ± 190) |

A split removes the bias and widens the margin only while there are few weigh-ins of each
kind, so the data count a little less in the target for those weeks (§3.2). A wrong or
needless split costs almost nothing. Weighing 4 days a week gives the same picture with
wider margins.

*Checked on the two real exports*, with the switch set from each person's own notes («after
the gym», «after breakfast», «on waking») and the zone change from the times of the
weigh-ins. In every week where the waking weigh-ins alone could be measured (7 of 10 and 2
of 2), the new measurement agreed with them; before, it was up to 630 kcal/day above. One
stretch stayed high in both methods (≈ 3440 ± 730 from waking weigh-ins alone over 10 days):
the fix removes changes of conditions, not real changes or short-window noise.

*What it cannot do.* It needs the switch to be right: an unmarked weigh-in after eating
behaves as before. Another scale without a change of time zone is not detected. And when
day-to-day noise carries over from one day to the next (water retention), the 95 % margin
is somewhat too narrow with or without this split (≈ 85 % instead of 95 % in the
simulation with a day-to-day correlation of 0.5).

**Worked example.** Over the 4 weeks before Monday you logged 25 days averaging 2300 kcal. The weight
trend fell 0.1 kg/week (β = −0.0143 kg/day), so `TDEE = 2300 + 0.0143 × 7700 ≈ 2410`.

### 3.1 Checking the profile against the data (`checkExpenditure`)

The target already follows the measured value (§3.2). The check answers a different
question: **does the profile fit the person?** When the formula and the data **clearly**
disagree, the app says so, explains why with the user's own numbers, and lists what can
cause it, so the user can fix the activity minutes or the logging.

```
profile  = BMR × PAL                                  (§1)
normal   = profile × (1 ± 0.10)
measured = TDEE ± margin                               (§3, 95 %)
higher:    measured − margin > profile × 1.10
lower:     measured + margin < profile × 0.90
measured on this week's Monday; confirmed: the same verdict on the previous Monday
```

**Rules, and why.**

| Rule | Reason |
|---|---|
| ±10 % around the profile is "normal" | Mifflin-St Jeor predicts the basal rate within about ±10 % of the measured value in most healthy adults, more often than any other equation (Frankenfield 2005). A gap inside that band is the formula's normal error for one person. The activity factor's own error is not added to the band: it is what the check is meant to reveal. |
| The **whole** 95 % range must lie outside the band | Then neither the formula's normal error nor the scale's noise explains the gap. With daily weigh-ins (±177) the gap must be ≳ 10 % + 177 kcal, about 400 kcal/day for a 2400 kcal profile. Strict on purpose: the check speaks rarely, and is then very unlikely to be noise. |
| Confirmed on the window a week earlier | One odd week (a holiday, the scale on another floor) cannot trigger it. The two windows share 21 days, so this adds stability, not statistical confidence. |
| Today only when confirmed; «Not now» holds until the verdict changes or 28 days pass | Do not repeat what the user already read; a new full window is new data. |
| The profile used is the one shown (current weight) | Over 4 weeks the weight changes a few kg at most: < 50 kcal/day of BMR × PAL. Using the same number the user sees keeps the explanation checkable. |

**What it cannot tell apart.** Energy balance measures the total, not where the gap comes
from, so the app lists the possible causes for each direction instead of naming one:

- *Measured lower than the profile:* unlogged food is the most common cause. Self-reported
  intake usually falls short of what doubly labelled water shows people really burn
  (Schoeller 1995); in people who could not lose weight on what they reported, intake was
  under-reported by 47 % and exercise over-reported by 51 % (Lichtman et al. 1992). Also:
  activity minutes set too high, a basal rate below average, water retention, or adaptive
  thermogenesis during a long diet (Rosenbaum & Leibel 2010).
- *Measured higher than the profile:* activity the profile does not count (physical work,
  other sports, more walking, §1.2), the fast loss of water and glycogen in the first weeks
  of a diet (Kreitzman et al. 1992), over-logging, or a basal rate above average.

**Implied activity factor.** The «Why?» also shows `measured ÷ BMR`, the activity factor the
data correspond to. It is only a reference: it assumes the basal formula is right for the
person, which is exactly one of the things the check cannot know.

**When there is not enough data,** Perfil lists what is missing (logged days out of 21,
weigh-in days out of 4, or span out of 14 days). When meals are
logged but weigh-ins are what is missing, Today says so once.

**Worked example.** Profile: man, 36, 180 cm, 80 kg → BMR 1750; 3 × 60 min of gym and
30 min of walking → PAL 1.49; profile 2608 kcal/day, normal 2347–2869. Five weeks on
2600 kcal/day losing 0.7 kg/week, weighed daily: measured `2600 + 0.1 × 7700 = 3370 ± 177`.
The low end, 3193, is above 2869, and it was a week earlier too: «you burn about 750 kcal/day
more than your profile estimates». His data correspond to a PAL of 3370 / 1750 ≈ 1.93.

### 3.2 The target follows the data (`combineExpenditure`, `weeklyExpenditure`)

Until 2026-10 the measured value was only shown next to the formula. A real case showed
why that is not enough: a man training 5 × 80 min a week, on «maintain», ate at the top of
his range for weeks and lost 0.35–0.5 kg/week. His measured expenditure was 400–480 kcal
above the formula's 2608 for two weeks, but the check (§3.1) still said «matches», because
it only speaks when the whole 95 % range is beyond ±10 % (≈ 470 kcal for him). A gap the
size of a whole cut was invisible, and his «maintenance» was a cut.

*Later finding (2026-11-24).* Those windows straddled a move across time zones (and scales)
and two weeks of midday weigh-ins. Comparing only weigh-ins taken alike (§3, «Weigh-ins
taken alike»), the same Mondays read 2862–2909 instead of 3020–3137: still above the
formula, by less. The case overstated the gap; the reason for the design (one person's
expenditure can differ from the formula by several hundred kcal, Bajunaid et al. 2025)
stands on its own.

```
σ_profile  = 10 % × profile's expenditure (BMR × PAL)
σ_measured = margin ÷ 1.96                     (the margin is the 95 % one, §3)
share      = σ_profile² ÷ (σ_profile² + σ_measured²)
expenditure = profile + share × (measured − profile)
target     = expenditure − 400 / ± 100 / + 300 (§1.3), never below the BMR
```

**Why combine rather than replace.** Inverse-variance weighting is the standard way to
combine two estimates of the same quantity: each counts by its precision, and the result
is more precise than either. With little data the formula leads; with good data the data
lead. It also makes the change gradual instead of a switch on the day the measurement
appears.

**Why trust the data at all.** Prediction equations are right on average and poor for one
person: an equation built from 6497 doubly-labelled-water measurements (weight, age, sex)
has 95 % predictive limits of about −40 % / +60 % for an individual (Bajunaid et al.
2025). The profile adds activity minutes and does better, but its intensities are fixed
guesses (§1.2). The measured value is the person's own, in the units of their own
logging. Adaptive apps do the same: MacroFactor uses the formula only at the start and
then sets targets from the data-driven expenditure at a weekly check-in with smoothing;
Carbon adjusts each week from logged intake and the weight trend.

**Why σ = 10 %.** Mifflin-St Jeor is within ±10 % of measured BMR in about 82 % of
non-obese adults (Frankenfield 2005), an SD of ≈ 7.5 % if the errors are normal; the
activity factor adds its own error, so 10 % for BMR × PAL. Resulting shares for a
2600 kcal profile: daily weigh-ins (±177) → 89 %; 3 a week (±272) → 78 %; the widest
weekly weigh-ins (±482) → 53 %. The share is shown on screen («85 % from your data»).

**Why weekly.** The measurement is taken on Monday from the 4 weeks before
(`weekStartOf`), and every day of that week uses it: the target changes once a week, not
with every weigh-in (MacroFactor and Carbon update at a weekly check-in too). The check
(§3.1) uses the same measurement and confirms on the previous Monday's. A past day keeps
the target of its own week, computed from the data before that Monday.

**Safeguards kept.** The measurement's rules (21 logged days of 28, ≥ 4 weigh-in days
over ≥ 14 days) and the BMR floor. Without a measurement the target is exactly the
formula's.

**Why no cut-off on the margin.** Until 2026-11 a measurement only counted with a margin of
±300 or less. A real case showed the cost: a woman on «maintain», weighing 4–5 times a
week at varying times of day, measured 2711 ± 287 one Monday and 2506 ± 317 the next,
against a profile of about 1975. The second was dropped for being 17 kcal over the cut-off,
and her target fell from 2453 to 1972 overnight, although her data still said she burned
about 500 kcal/day more than the profile. The weighting already gives a noisy measurement
less say (60 % for her at ±317; for a 2600 kcal profile, 72 % at ±317 and 53 % at ±482);
the cut-off only turned that gradual decline into a jump. Without it her target went
2453 → 2291. *Later finding (2026-11-24):* within those windows she had switched from
after-breakfast and after-gym weigh-ins to fasted ones on waking; comparing only weigh-ins
taken alike, the two Mondays read 2381 ± 374 and 2148 ± 359. The argument against the
cut-off (a gradual weight instead of a jump) still holds. The same holds for
the check (§3.1): it needs the whole 95 % range outside the profile's ± 10 %, so a wide
margin already makes it harder to trigger.

**Worked example (the case above).** Profile 2608 (BMR 1671 × 1.56). On Monday
2026-10-26 the measurement was 3060 ± 214: share = 260.8² ÷ (260.8² + 109.2²) = 0.85,
expenditure = 2608 + 0.85 × 452 = 2993, maintenance 2893–3093 kcal/day instead of
2508–2708.

**Limits.** Same as §3: water and glycogen at the start of a diet, the menstrual cycle,
and the 7700 kcal/kg approximation. During recomposition the scale understates fat loss,
so the measured value comes out somewhat low (§2.4): the target errs on the low side.

---

## 4. Progress statistics (`stats.ts`, `stats-builder.ts`, `progress.ts`)

- **Current weight:** the latest weigh-in in the selected period, as the scale read it.
- **Usual weigh-ins.** The trend and the change use only the weigh-ins taken the way most
  of the period's were, fasted or not (a tie counts as fasted). Research weighs people
  the same way every time (morning, after voiding, before eating, light clothing, the same
  scale); trial protocols reschedule a visit rather than use a non-fasted weight. A
  weigh-in after a meal is not random noise but a steady extra amount (the weight rises
  about 0.5–1 kg through the day with food and drink), so averaging it with the others
  does not cancel it. The two accepted answers are to analyse only the standard
  measurements, or to give each condition its own level and compare within it. The
  trend and the change take the first: with only a few weigh-ins of the other kind their
  level is too noisy to estimate, and a corrected line would show weights the scale never
  read. The rate takes the second (§3). On the chart the others are hollow dots, with one
  line saying so.
- **Body fat.** The same rule, counted among the weigh-ins that have a body fat value
  (someone who measures it only in the evening still gets a trend): a home scale
  estimates body fat from the body's electrical resistance, which moves with the water
  and food in it, so a reading after a meal is not comparable with a fasted one. The
  chart draws the 7-day trend of the usual readings, the others hollow with their own
  line under the chart; the value shown is still the latest reading, and the body fat
  change in the summary is the last minus the first usual reading.
- **Change:** the last minus the first usual weigh-in of the period.
- **Trend (7-day moving average):** for each weigh-in on day d, the mean of the usual
  entries dated d − 6 … d. Days without entries are neither counted nor filled with zeros.
  The other weigh-ins also get that value, so the line carries on past them.
  - Regular self-weighing is associated with more weight loss and no negative
    psychological effects (Zheng et al. 2015). The average turns those weigh-ins into a
    trend that does not jump with each day's number.
  - The Hacker's Diet (Walker 2005) uses an exponential moving average. A simple 7-day
    mean is easier to read and covers one full weekly cycle of eating and water.
- **Weekly rate:** the slope of the weigh-ins in the visible range, compared only within
  weigh-ins taken alike (§3), × 7. It captures the long-term direction better than two
  single points, and a switch to fasted weigh-ins doesn't show as a loss.
- **Raw entries versus daily means.** The moving average uses every usual entry, so a day
  weighed twice that way counts twice. That is harmless for a display. The rate, the measured
  expenditure and the coach projection first average same-day weigh-ins taken alike into
  one point.
- **Macro averages:** the mean over the days with food logged (calories > 0), shown with
  "N of M days logged". The energy split uses the general Atwater factors: 4 kcal/g
  protein, 4 carbs, 9 fat. Alcohol and fibre are not separated, so the split is
  approximate.

### 4.1 Fat as a share of calories (`checkFatShare`)

The app recommends only calories and protein; carbs and fat share the rest. That is a
deliberate choice: for **weight**, the split matters little. In controlled feeding
studies, diets with the same calories and different carb/fat ratios change body fat by
amounts too small to matter (Hall & Guo 2017), and in DIETFITS 609 adults lost about the
same over 12 months on a healthy low-fat or a healthy low-carb diet (Gardner et al. 2018).

For **health** there are limits, and fat's are the useful ones:

| Limit | Why | Source |
|---|---|---|
| Fat ≥ 20 % of calories | Below it, essential fatty acids (omega-3, omega-6) and the fat-soluble vitamins A, D, E and K are hard to cover; in men, low-fat diets lower testosterone | Institute of Medicine 2005 (AMDR 20–35 %); Whittaker & Wu 2021 |
| Fat ≤ 35 % of calories | Above it, saturated fat tends to be high; WHO advises < 10 % of calories, and replacing it with unsaturated fat lowers cardiovascular events | Institute of Medicine 2005; WHO 2023; Hooper et al. 2020 |

Carbs get no line of their own: with calories and protein fixed, they are the remainder,
so they move opposite to fat. The app records totals only, not saturated fat, sugar or
fibre, so the high case cannot tell good fat from bad and says what to favour instead
of warning.

**Rule.** Over the Progreso period, with **≥ 7 logged days** (a single heavy or light day
says nothing), the share `fat g × 9 ÷ (protein × 4 + carbs × 4 + fat × 9)` is compared
with 20–35 %. It uses the rounded share the card shows, so «20 %» is never called low.
Outside the band, one line under the split («¿Por qué?» → the user's numbers, the band on
a 0–100 % bar, the reasons and what to change → Metodología `#fat`). Information only:
no target changes, nothing to dismiss, and it goes away when the average is back inside.

**Worked example.** 7 days averaging 135 g protein, 265 g carbs and 35 g fat:
540 + 1060 + 315 = 1915 kcal from macros; 315 ÷ 1915 = 16 % → «low». A tablespoon of olive
oil a day instead of a portion of bread (~+14 g fat, −25 g carbs) brings it to about 23 %.

## 5. Coach weight projection (`coach.ts`)

- **Fit:** the same trend as the measured expenditure (§3): daily-mean weights of the last
  28 days, today included, compared only within weigh-ins taken alike. It needs ≥ 4
  weigh-in days spanning ≥ 14 days.
- **Projection:** the line is extended 30 days from the level of the latest weigh-ins'
  group, with the **95 % confidence band of the trend line**:

```
margin(x) = 1.96 × σ × √(1/n_g + (x − x̄_g)² / Sxx)    (n_g, x̄_g: the latest group; σ floored at 0.5 kg, as in §3)
```

- **What the band means.** It is the uncertainty of *where the trend will be*, not of a
  single weigh-in on that day. A prediction interval for one weigh-in adds 1 inside the
  root. With daily weighing that is about ±1.4 kg instead of ±1.0 kg at 30 days ahead.
- **What it assumes.** Nothing changes. A linear extrapolation ignores the slowing of
  weight loss as expenditure adapts (Hall 2008), so beyond a month it overstates the
  change.
- **How the coach may use it.** The coach receives it as "projection computed by the app
  if this trend continues", and must quote it instead of computing its own.

## 6. AI estimates

- **What the model does.** The language model turns photos or text into foods, grams and
  macros. It is an estimate, not a measurement.
- **Consistency check.** The app checks the energy from the macros with the same 4/4/9
  factors. An item is flagged when its stated kcal differs by more than 25 % from
  `4P + 4C + 9F` (for items of 20 kcal or more).
- **Review.** Every estimate opens the review form before anything is saved.

### 6.1 Questions about your meals (`food-questions/`)

The coach answers questions about the food log («How much chocolate milk do I drink?»,
«Do I eat more at the weekend?», «Did my weight change since I stopped beer?»). Language
models add up dozens of entries badly and present a wrong average as a fact, so **the
model never computes a number here**:

1. The model turns the question into a request from a fixed list (how much and how often,
   top foods, what was eaten on some days, a comparison, a food and the weight trend):
   which foods (search words) and which period.
2. The app computes it from the log on the device (TECHNICAL.md §8.7).
3. The model explains the result, which travels with the question as text it must quote.
   With nothing to compute (no match, too few days, an unreadable request), the app
   answers itself, and under every answer «How it was worked out» says what was counted.

- **Matching.** Names are compared without accents or capitals; every searched word must
  start a word of the logged name («leche choco» finds «Leche chocolatada»). «Total only»
  meals match by their title, with the meal's totals. The answer lists the names counted,
  so a wrong match is visible.
- **Averages over logged days.** A day counts as logged when it has meals with calories.
  Unlogged days are not zero (principle 5, §0): «per logged day» is the divisor, and «about N days a
  week» is days eaten ÷ logged days × 7.
- **How many days.** Below **7 logged days** there is no average (the app says how many
  there are); **7–13** are given as a rough hint; 14 covers two weekly cycles. Without a
  period: the last **28 days**; weekdays vs weekend and food vs weight: **56 days**
  (28 days have only 8 weekend days, too few once some go unlogged).
- **Amounts.** Only quantities in g or ml with a single number are added up («200 ml»,
  «1,5 kg», «33 cl»); «1 cup (250 ml)», «2 x 30 g» or «2 eggs» are left out rather than
  guessed, and the answer says how many entries were. Grams and millilitres are reported
  separately (no density is assumed).
- **Comparisons.** Each side needs ≥ 7 logged days. The difference is second minus first,
  in the metric's unit per logged day, with its percentage of the first.
- **A food and the weight.** The period is split on the day the user names; otherwise on
  the day after the food was last logged (if that is ≥ 14 days before the end: stopped),
  on the first day it was logged (if ≥ 14 days after the start: started), or in the
  middle. Each side needs ≥ 21 days and ≥ 7 logged days. Each side's weight trend uses
  the measured expenditure's rules and fit (§3: ≥ 4 weigh-in days spanning ≥ 14 days,
  compared only within weigh-ins taken alike), with its 95 % margin. Total calories per
  logged day are shown next to it, and the text says it is an association, not a cause:
  weight follows total energy balance, which usually changes with the food.
- **What leaves the device.** With a cloud model, the question goes to the provider in
  step 1, and in step 3 the result: food names and amounts. With a local model nothing
  leaves the device. «The coach can see what I eat» turns it off.

## 7. Rounding

- **Energy:** BMR, TDEE and targets are rounded to whole kcal. The margin is also a whole
  kcal.
- **Protein:** grams are rounded to whole grams, and g/kg factors to 0.1.
- **Weights:** 2 decimals, and trends 1 decimal.
- **Where rounding meets a rule:** the BMR floor is the rounded BMR, and the check's
  verdict uses the rounded margin. Both are well below the precision of the estimates.

---

## 8. Review log

- **2026-11-25, questions about your meals (§6.1).**
  - *Added:* the coach answers questions about the food log with numbers the app computes
    (the model only chooses what to compute and explains the result).
  - *Rules chosen:* averages over logged days; no average below 7 logged days, a hint below
    14; 28 days by default, 56 for weekdays vs weekend and food vs weight; amounts only in
    g or ml; the weight trend with §3's rules on each side of the split; food vs weight
    said as an association, with total calories alongside.

- **2026-09-28, second review.**
  - *Checked against the sources:* every constant, formula and worked example above.
    This includes the Mifflin coefficients, FAO PAL bands and the sedentary example,
    Compendium 2024 walking and resistance METs, the protein ranges, Kokura's 1.3 g/kg,
    the margin figures and the projection band.
  - *Changed:*
    1. Muscle-exception thresholds now follow Gallagher et al. 2000 by sex **and age**:
       men 20/22/25 %, women 33/34/36 %. Before, men were at 25 % at every age.
    2. The Prince et al. 2008 wording now says what the review found: self-report is
       usually above accelerometer data and the review found it both higher and lower
       overall. It no longer says that asking for times "avoids" the bias.
    3. The Zheng et al. 2015 wording now cites what the review found: regular
       self-weighing goes with more weight loss and no psychological harm. The review
       says nothing about trends.
  - *Kept, with the reasoning above:* z instead of t for the margin, raw entries for the
    Progreso trend and rate, and fixed kcal offsets.

- **2026-10-26, strength training in every goal (§2.4).**
  - *Changed:* maintenance protein with strength training 1.4–2.0 → 1.6–2.2 g/kg (Morton
    2018; Nunes 2022). Goal descriptions, the coach's goal text and the «you burn less»
    notice now say that with strength training muscle can come in every goal, and that
    the scale cannot tell muscle from fat.
  - *Checked, not adopted:* 2.6–3.5 g/kg of lean mass for recomposition (Barakat 2020),
    see §2.1.
  - *Kept:* the calorie offsets; the cut's pace and protein already match the evidence on
    gaining muscle in a deficit (Murphy & Koehler 2022; Garthe 2011).

- **2026-10-30, the target follows the data (§3.2); protein above the range (§2.1).**
  - *Changed:* the calorie target is built on the formula and the measured expenditure
    combined by precision, measured every Monday; the check now says whether the profile
    fits, not whether to change the target. Morning weigh-ins asked for. Protein above the
    range is said in neutral grey, not as a warning.
  - *Why:* a user's data (above) showed the strict check hiding a cut-sized gap at
    «maintain». Sources: Frankenfield 2005, Bajunaid 2025, Devries 2018, Antonio 2016;
    MacroFactor and Carbon help pages for what adaptive apps do.
  - *Kept:* the check's thresholds (±10 %, whole 95 % range, two weeks), now only for the
    profile.

- **2026-11-17, no cut-off on the measured margin (§3, §3.2); fat share (§4.1).**
  - *Changed:* the measured expenditure no longer needs a margin of ±300 or less; a wide
    margin only counts less. Progreso says when fat is outside 20–35 % of calories.
  - *Why:* a user's target fell ~480 kcal overnight when her margin crossed ±300 (§3.2).
    The fat line answers «do carbs and fat matter?» without adding a target. Sources:
    IOM 2005, WHO 2023, Hooper 2020, Whittaker & Wu 2021, Hall & Guo 2017, Gardner 2018,
    checked against Crossref.
  - *Kept:* calories and protein as the only targets; z for the margin.

- **2026-11-24, third review.**
  - *Checked:* every formula and worked example again, the web and Kotlin constants side
    by side, and the measured expenditure on two users' real exports.
  - *Corrected:*
    1. §1.1: resting rather than basal rate makes BMR × PAL slightly high, which partly
       offsets the conservative choices; it said they went in the same direction.
    2. §3.2: 60 % at ±317 was her share; for a 2600 kcal profile it is 72 %.
    3. The coach's context said the measured expenditure counts «because it is the more
       precise of the two», also when it weighs less than half. It now says both count by
       their precision.

- **2026-11-24, weigh-ins taken alike (§3).**
  - *Changed:* the weight trend (measured expenditure, Progreso's rate, the coach's
    projection) compares only weigh-ins taken in the same conditions: fasted or not (a
    switch on the weight form) and the time zone (saved with each weigh-in). One level per
    group, one common slope.
  - *Why:* two users' real exports showed changes of weighing habit or scale reading as
    300–800 kcal/day of extra expenditure for weeks, beyond the ± margin.
  - *Checked before building it:* a simulation (bias removed, 95 % range holds the true
    value in 95 % of windows, nothing changes when the weigh-ins are alike) and both
    exports (agreement with the waking weigh-ins in every week that could be compared).
  - *Considered, not adopted:* an automatic split when the time of weighing moves by N
    hours (N would be tuned on a few people and can't tell after breakfast from on waking);
    a «new scale» checkbox (rare; a move is detected from the time zone).
  - *Open:* the margin assumes independent day-to-day noise; with water retention that
    carries over, it is somewhat narrow (≈ 85 % instead of 95 % in the simulation).

## 9. References

1. Antonio J, Ellerbroek A, Silver T, et al. A high protein diet (3.4 g/kg/d) combined with a heavy resistance training program improves body composition in healthy trained men and women – a follow-up investigation. *J Int Soc Sports Nutr*. 2015;12:39. doi:10.1186/s12970-015-0100-0
2. Antonio J, Ellerbroek A, Silver T, et al. A high protein diet has no harmful effects: a one-year crossover study in resistance-trained males. *J Nutr Metab*. 2016;2016:9104792. doi:10.1155/2016/9104792
3. Bajunaid R, Niu C, Hambly C, et al. Predictive equation derived from 6,497 doubly labelled water measurements enables the detection of erroneous self-reported energy intake. *Nat Food*. 2025;6(1):58–71. doi:10.1038/s43016-024-01089-5
4. Barakat C, Pearson J, Escalante G, Campbell B, De Souza EO. Body recomposition: can trained individuals build muscle and lose fat at the same time? *Strength Cond J*. 2020;42(5):7–21. doi:10.1519/SSC.0000000000000584
5. Byrne NM, Hills AP, Hunter GR, Weinsier RL, Schutz Y. Metabolic equivalent: one size does not fit all. *J Appl Physiol*. 2005;99(3):1112–1119. doi:10.1152/japplphysiol.00023.2004
6. Devries MC, Sithamparapillai A, Brimble KS, Banfield L, Morton RW, Phillips SM. Changes in kidney function do not differ between healthy adults consuming higher- compared with lower- or normal-protein diets: a systematic review and meta-analysis. *J Nutr*. 2018;148(11):1760–1775. doi:10.1093/jn/nxy197
7. FAO/WHO/UNU. Human energy requirements: report of a joint FAO/WHO/UNU expert consultation. *FAO Food and Nutrition Technical Report Series 1*. Rome: FAO; 2004.
8. Frankenfield D, Roth-Yousey L, Compher C. Comparison of predictive equations for resting metabolic rate in healthy nonobese and obese adults: a systematic review. *J Am Diet Assoc*. 2005;105(5):775–789. doi:10.1016/j.jada.2005.02.005
9. Gallagher D, Heymsfield SB, Heo M, Jebb SA, Murgatroyd PR, Sakamoto Y. Healthy percentage body fat ranges: an approach for developing guidelines based on body mass index. *Am J Clin Nutr*. 2000;72(3):694–701. doi:10.1093/ajcn/72.3.694
10. Gardner CD, Trepanowski JF, Del Gobbo LC, et al. Effect of low-fat vs low-carbohydrate diet on 12-month weight loss in overweight adults and the association with genotype pattern or insulin secretion: the DIETFITS randomized clinical trial. *JAMA*. 2018;319(7):667–679. doi:10.1001/jama.2018.0245
11. Garthe I, Raastad T, Refsnes PE, Koivisto A, Sundgot-Borgen J. Effect of two different weight-loss rates on body composition and strength and power-related performance in elite athletes. *Int J Sport Nutr Exerc Metab*. 2011;21(2):97–104. doi:10.1123/ijsnem.21.2.97
12. Hall KD, Guo J. Obesity energetics: body weight regulation and the effects of diet composition. *Gastroenterology*. 2017;152(7):1718–1727.e3. doi:10.1053/j.gastro.2017.01.052
13. Hall KD. What is the required energy deficit per unit weight loss? *Int J Obes*. 2008;32(3):573–576. doi:10.1038/sj.ijo.0803720
14. Helms ER, Aragon AA, Fitschen PJ. Evidence-based recommendations for natural bodybuilding contest preparation: nutrition and supplementation. *J Int Soc Sports Nutr*. 2014;11:20. doi:10.1186/1550-2783-11-20
15. Helms ER, Zinn C, Rowlands DS, Brown SR. A systematic review of dietary protein during caloric restriction in resistance trained lean athletes: a case for higher intakes. *Int J Sport Nutr Exerc Metab*. 2014;24(2):127–138. doi:10.1123/ijsnem.2013-0054
16. Herrmann SD, Willis EA, Ainsworth BE, et al. 2024 Adult Compendium of Physical Activities: a third update of the energy costs of human activities. *J Sport Health Sci*. 2024;13(1):6–12. doi:10.1016/j.jshs.2023.10.010
17. Hooper L, Martin N, Jimoh OF, Kirk C, Foster E, Abdelhamid AS. Reduction in saturated fat intake for cardiovascular disease. *Cochrane Database Syst Rev*. 2020;8:CD011737. doi:10.1002/14651858.CD011737.pub3
18. Institute of Medicine. *Dietary Reference Intakes for Energy, Carbohydrate, Fiber, Fat, Fatty Acids, Cholesterol, Protein, and Amino Acids*. Washington, DC: National Academies Press; 2005. doi:10.17226/10490
19. Iraki J, Fitschen P, Espinar S, Helms E. Nutrition recommendations for bodybuilders in the off-season: a narrative review. *Sports*. 2019;7(7):154. doi:10.3390/sports7070154
20. Jäger R, Kerksick CM, Campbell BI, et al. International Society of Sports Nutrition Position Stand: protein and exercise. *J Int Soc Sports Nutr*. 2017;14:20. doi:10.1186/s12970-017-0177-8
21. Kokura Y, Ueshima J, Saino Y, Maeda K. Enhanced protein intake on maintaining muscle mass, strength, and physical function in adults with overweight/obesity: a systematic review and meta-analysis. *Clin Nutr ESPEN*. 2024;63:417–426. doi:10.1016/j.clnesp.2024.06.030
22. Kreitzman SN, Coxon AY, Szaz KF. Glycogen storage: illusions of easy weight loss, excessive weight regain, and distortions in estimates of body composition. *Am J Clin Nutr*. 1992;56(1 Suppl):292S–293S. doi:10.1093/ajcn/56.1.292S
23. Lichtman SW, Pisarska K, Berman ER, et al. Discrepancy between self-reported and actual caloric intake and exercise in obese subjects. *N Engl J Med*. 1992;327(27):1893–1898. doi:10.1056/NEJM199212313272701
24. McClave SA, Taylor BE, Martindale RG, et al. Guidelines for the provision and assessment of nutrition support therapy in the adult critically ill patient (SCCM and A.S.P.E.N.). *JPEN J Parenter Enteral Nutr*. 2016;40(2):159–211. doi:10.1177/0148607115621863
25. Mifflin MD, St Jeor ST, Hill LA, Scott BJ, Daugherty SA, Koh YO. A new predictive equation for resting energy expenditure in healthy individuals. *Am J Clin Nutr*. 1990;51(2):241–247. doi:10.1093/ajcn/51.2.241
26. Montgomery DC, Peck EA, Vining GG. *Introduction to Linear Regression Analysis*. 6th ed. Wiley; 2021.
27. Morton RW, Murphy KT, McKellar SR, et al. A systematic review, meta-analysis and meta-regression of the effect of protein supplementation on resistance training-induced gains in muscle mass and strength in healthy adults. *Br J Sports Med*. 2018;52(6):376–384. doi:10.1136/bjsports-2017-097608
28. Murphy C, Koehler K. Energy deficiency impairs resistance training gains in lean mass but not strength: a meta-analysis and meta-regression. *Scand J Med Sci Sports*. 2022;32(1):125–137. doi:10.1111/sms.14075
29. Nunes EA, Colenso-Semple L, McKellar SR, et al. Systematic review and meta-analysis of protein intake to support muscle mass and function in healthy adults. *J Cachexia Sarcopenia Muscle*. 2022;13(2):795–810. doi:10.1002/jcsm.12922
30. Prince SA, Adamo KB, Hamel ME, Hardt J, Connor Gorber S, Tremblay M. A comparison of direct versus self-report measures for assessing physical activity in adults: a systematic review. *Int J Behav Nutr Phys Act*. 2008;5:56. doi:10.1186/1479-5868-5-56
31. Rosenbaum M, Leibel RL. Adaptive thermogenesis in humans. *Int J Obes*. 2010;34(Suppl 1):S47–S55. doi:10.1038/ijo.2010.184
32. Schoeller DA. Limitations in the assessment of dietary energy intake by self-report. *Metabolism*. 1995;44(2 Suppl 2):18–22. doi:10.1016/0026-0495(95)90204-X
33. Walker J. *The Hacker's Diet: how to lose weight and hair through stress and poor nutrition*. fourmilab.ch; 2005.
34. Whittaker J, Wu K. Low-fat diets and testosterone in men: systematic review and meta-analysis of intervention studies. *J Steroid Biochem Mol Biol*. 2021;210:105878. doi:10.1016/j.jsbmb.2021.105878
35. World Health Organization. *Saturated fatty acid and trans-fatty acid intake for adults and children: WHO guideline*. Geneva: WHO; 2023.
36. Zheng Y, Klem ML, Sereika SM, Danford CA, Ewing LJ, Burke LE. Self-weighing in weight management: a systematic literature review. *Obesity*. 2015;23(2):256–265. doi:10.1002/oby.20946
