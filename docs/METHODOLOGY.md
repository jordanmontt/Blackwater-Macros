# Blackwater Macros — Scientific methodology

How the app turns a profile, weigh-ins and logged meals into calorie and protein targets,
a measured expenditure and a weight projection: every formula, why it was chosen, where
it comes from and where it stops being reliable.

- **Code:** `src/lib/core/calories.ts`, `protein.ts`, `expenditure.ts`, `stats.ts`,
  `stats-builder.ts`, `progress.ts`, `coach.ts` (web), mirrored 1:1 in Kotlin
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
3. **The formula sets the target; your own data checks it.** The measured expenditure is
   shown next to the formula, never silently replaces it, so the target does not jump
   with the scale's noise.
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
| Protein | g/kg by goal (maintain 1.6–2.2 with strength training); lean mass when cutting with body fat; weight at BMI 25 above BMI 25 | Jäger 2017; Morton 2018; Nunes 2022; Helms 2014 (IJSNEM); McClave 2016; Kokura 2024; Gallagher 2000 |
| Strength training | With gym days, every goal also aims at muscle; a flat weight in a deficit can be fat lost and muscle gained | Barakat 2020; Murphy & Koehler 2022; Garthe 2011; Hall 2008 |
| Measured expenditure | Energy balance over 28 days, least-squares weight slope, 95 % margin | Hall 2008; Montgomery 2021 |
| Profile check | Measured 95 % range entirely outside profile ± 10 %, on two weekly windows | Frankenfield 2005; Schoeller 1995; Lichtman 1992 |
| Weight trend | 7-day moving average | Zheng 2015; Walker 2005 |
| Weekly rate | Least-squares slope × 7 | Montgomery 2021 |
| Coach projection | Trend line extended 30 days with its 95 % confidence band | Montgomery 2021 |
| Macro energy split | 4 / 4 / 9 kcal per g | Atwater general factors |

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
  FAO's PAL values are defined against. The difference is small and in the same
  direction as the conservative choices below.

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
  therefore includes any adaptation that has already happened.

**Floor.** The target, its minimum and its maximum never go below the BMR. Only a cut for
a small, sedentary person reaches it (e.g. TDEE 1540 − 500 = 1040 against a BMR of
1100). This is a safety convention, not a published threshold: eating below your BMR is
a very-low-calorie diet and is better done with supervision.

### 1.4 Past days keep their targets (`profile-history.ts`)

**Rule.** A day's targets use the profile in effect that day and the last weigh-in on or
before it (before the first weigh-in, the first one). Editing the activity, the goal or any
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
Window:  the 28 days before today (today is excluded: it is still being logged)
intake = mean kcal of the days with meals logged
β      = least-squares slope of the daily-mean weights [kg/day]
TDEE   = intake − β × 7700
margin = 1.96 × σ / √Σ(xᵢ − x̄)² × 7700          [kcal/day, 95 %]
σ      = max( √(Σ residuals² / (n − 2)), 0.5 kg )
```

**Data rules, and why.**

| Rule | Reason |
|---|---|
| ≥ 21 logged days of 28 | Weight reflects *every* day, logged or not. The intake average must cover nearly all of them, or unlogged (often larger) days are missing from it. |
| Days without meals are excluded, never 0 kcal | Counting them as zero would make you look like you burn far less. |
| ≥ 4 weigh-in days spanning ≥ 14 days | A slope over a few days is dominated by water and glycogen swings. |
| One point per day (the mean of that day's weigh-ins) | Morning and evening weigh-ins can differ by a kilo. They are not independent measurements of the trend, so counting them twice would shrink the error falsely. |
| Shown only when margin ≤ ±300 kcal/day | Beyond that the number would not be useful as a check. |

**The error model.**
- The slope's standard error is `σ / √Sxx`, from ordinary least squares (Montgomery et
  al. 2021).
- σ is estimated from the residuals with n − 2 degrees of freedom, but never below
  **0.5 kg**, a realistic day-to-day scatter for body weight. A handful of weigh-ins can
  fall on a straight line by chance, and without the floor they would look precise.
- `Sxx = Σ(xᵢ − x̄)²` grows with both the number of weigh-ins and how spread out they are.

Worked margins, at the 0.5 kg floor and 28-day window:

| Weighing | Sxx | Margin | Shown? |
|---|---|---|---|
| Daily | 1827 | ± 177 kcal/day | yes |
| 3 × a week | 767 | ± 272 | yes |
| 2 × a week | 508 | ± 335 | no |
| Weekly | 245 | ± 482 | no |

**z (1.96) rather than Student's t.**
- With the noise floored, σ is treated as a known lower bound of the scale's noise, and
  for a known σ the normal quantile is the right one.
- When the residual scatter is *above* the floor, σ is estimated. Then t(n − 2) would be
  stricter: 2.06 instead of 1.96 for daily weighing (+5 %), and 2.23 for 3 weigh-ins a
  week (+14 %). The 3-a-week example would read ±310 and be hidden.
- We keep z so that ~3 weigh-ins a week is enough. The floor already makes the typical
  case conservative, and the margin is shown as an approximate 95 %.

**What the margin does not cover.**
- *Logging error:* under-logging makes expenditure look lower, one for one.
- *The 7700 kcal/kg approximation.*
- *Water and glycogen shifts:* in the first weeks of a new diet, and across the menstrual
  cycle.

For those reasons, and so that the target does not move with the scale, the measured
value is **shown next to the formula and never replaces the target**. If the two disagree
for several weeks, the measured value is the better description of *your* expenditure.

**Worked example.** Over 4 weeks you logged 25 days averaging 2300 kcal. The weight
trend fell 0.1 kg/week (β = −0.0143 kg/day), so `TDEE = 2300 + 0.0143 × 7700 ≈ 2410`.

### 3.1 Checking the profile against the data (`checkExpenditure`)

The measured value is shown next to the formula (principle 3). When the two **clearly**
disagree, the app also says so, explains why with the user's own numbers, and lists what
can cause it. The target does not change.

```
profile  = BMR × PAL                                  (§1)
normal   = profile × (1 ± 0.10)
measured = TDEE ± margin                               (§3, 95 %)
higher:    measured − margin > profile × 1.10
lower:     measured + margin < profile × 0.90
confirmed: the same verdict on the 28-day window that ended 7 days earlier
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
weigh-in days out of 4, span out of 14 days, or the margin against ±300). When meals are
logged but weigh-ins are what is missing, Today says so once.

**Worked example.** Profile: man, 36, 180 cm, 80 kg → BMR 1750; 3 × 60 min of gym and
30 min of walking → PAL 1.49; profile 2608 kcal/day, normal 2347–2869. Five weeks on
2600 kcal/day losing 0.7 kg/week, weighed daily: measured `2600 + 0.1 × 7700 = 3370 ± 177`.
The low end, 3193, is above 2869, and it was a week earlier too: «you burn about 750 kcal/day
more than your profile estimates». His data correspond to a PAL of 3370 / 1750 ≈ 1.93.

---

## 4. Progress statistics (`stats.ts`, `stats-builder.ts`, `progress.ts`)

- **Current weight:** the latest weigh-in in the selected period.
- **Change:** the last minus the first weigh-in of the period.
- **Trend (7-day moving average):** for each weigh-in on day d, the mean of all entries
  dated d − 6 … d. Days without entries are neither counted nor filled with zeros.
  - Regular self-weighing is associated with more weight loss and no negative
    psychological effects (Zheng et al. 2015). The average turns those weigh-ins into a
    trend that does not jump with each day's number.
  - The Hacker's Diet (Walker 2005) uses an exponential moving average. A simple 7-day
    mean is easier to read and covers one full weekly cycle of eating and water.
- **Weekly rate:** the ordinary-least-squares slope of the weigh-ins in the visible range,
  × 7. It captures the long-term direction better than two single points.
- **Raw entries versus daily means.** The moving average and the rate use every entry, so
  a day weighed twice counts twice. That is harmless for a display. Where the statistical
  error matters (the measured expenditure and the coach projection), same-day weigh-ins
  are first averaged into one point.
- **Macro averages:** the mean over the days with food logged (calories > 0), shown with
  "N of M days logged". The energy split uses the general Atwater factors: 4 kcal/g
  protein, 4 carbs, 9 fat. Alcohol and fibre are not separated, so the split is
  approximate.

## 5. Coach weight projection (`coach.ts`)

- **Fit:** a least-squares line through the daily-mean weights of the last 28 days,
  today included. It needs ≥ 4 weigh-in days spanning ≥ 14 days.
- **Projection:** the line is extended 30 days, with the **95 % confidence band of the
  trend line**:

```
margin(x) = 1.96 × σ × √(1/n + (x − x̄)² / Sxx)        (σ floored at 0.5 kg, as in §3)
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

## 7. Rounding

- **Energy:** BMR, TDEE and targets are rounded to whole kcal. The margin is also a whole
  kcal.
- **Protein:** grams are rounded to whole grams, and g/kg factors to 0.1.
- **Weights:** 2 decimals, and trends 1 decimal.
- **Where rounding meets a rule:** the BMR floor is the rounded BMR. The ±300 kcal check
  uses the rounded margin, so a margin of 300.4 is shown. Both are well below the
  precision of the estimates.

---

## 8. Review log

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

## 9. References

1. Antonio J, Ellerbroek A, Silver T, et al. A high protein diet (3.4 g/kg/d) combined with a heavy resistance training program improves body composition in healthy trained men and women – a follow-up investigation. *J Int Soc Sports Nutr*. 2015;12:39. doi:10.1186/s12970-015-0100-0
2. Barakat C, Pearson J, Escalante G, Campbell B, De Souza EO. Body recomposition: can trained individuals build muscle and lose fat at the same time? *Strength Cond J*. 2020;42(5):7–21. doi:10.1519/SSC.0000000000000584
3. Byrne NM, Hills AP, Hunter GR, Weinsier RL, Schutz Y. Metabolic equivalent: one size does not fit all. *J Appl Physiol*. 2005;99(3):1112–1119. doi:10.1152/japplphysiol.00023.2004
4. FAO/WHO/UNU. Human energy requirements: report of a joint FAO/WHO/UNU expert consultation. *FAO Food and Nutrition Technical Report Series 1*. Rome: FAO; 2004.
5. Frankenfield D, Roth-Yousey L, Compher C. Comparison of predictive equations for resting metabolic rate in healthy nonobese and obese adults: a systematic review. *J Am Diet Assoc*. 2005;105(5):775–789. doi:10.1016/j.jada.2005.02.005
6. Gallagher D, Heymsfield SB, Heo M, Jebb SA, Murgatroyd PR, Sakamoto Y. Healthy percentage body fat ranges: an approach for developing guidelines based on body mass index. *Am J Clin Nutr*. 2000;72(3):694–701. doi:10.1093/ajcn/72.3.694
7. Garthe I, Raastad T, Refsnes PE, Koivisto A, Sundgot-Borgen J. Effect of two different weight-loss rates on body composition and strength and power-related performance in elite athletes. *Int J Sport Nutr Exerc Metab*. 2011;21(2):97–104. doi:10.1123/ijsnem.21.2.97
8. Hall KD. What is the required energy deficit per unit weight loss? *Int J Obes*. 2008;32(3):573–576. doi:10.1038/sj.ijo.0803720
9. Helms ER, Aragon AA, Fitschen PJ. Evidence-based recommendations for natural bodybuilding contest preparation: nutrition and supplementation. *J Int Soc Sports Nutr*. 2014;11:20. doi:10.1186/1550-2783-11-20
10. Helms ER, Zinn C, Rowlands DS, Brown SR. A systematic review of dietary protein during caloric restriction in resistance trained lean athletes: a case for higher intakes. *Int J Sport Nutr Exerc Metab*. 2014;24(2):127–138. doi:10.1123/ijsnem.2013-0054
11. Herrmann SD, Willis EA, Ainsworth BE, et al. 2024 Adult Compendium of Physical Activities: a third update of the energy costs of human activities. *J Sport Health Sci*. 2024;13(1):6–12. doi:10.1016/j.jshs.2023.10.010
12. Iraki J, Fitschen P, Espinar S, Helms E. Nutrition recommendations for bodybuilders in the off-season: a narrative review. *Sports*. 2019;7(7):154. doi:10.3390/sports7070154
13. Jäger R, Kerksick CM, Campbell BI, et al. International Society of Sports Nutrition Position Stand: protein and exercise. *J Int Soc Sports Nutr*. 2017;14:20. doi:10.1186/s12970-017-0177-8
14. Kokura Y, Ueshima J, Saino Y, Maeda K. Enhanced protein intake on maintaining muscle mass, strength, and physical function in adults with overweight/obesity: a systematic review and meta-analysis. *Clin Nutr ESPEN*. 2024;63:417–426. doi:10.1016/j.clnesp.2024.06.030
15. Kreitzman SN, Coxon AY, Szaz KF. Glycogen storage: illusions of easy weight loss, excessive weight regain, and distortions in estimates of body composition. *Am J Clin Nutr*. 1992;56(1 Suppl):292S–293S. doi:10.1093/ajcn/56.1.292S
16. Lichtman SW, Pisarska K, Berman ER, et al. Discrepancy between self-reported and actual caloric intake and exercise in obese subjects. *N Engl J Med*. 1992;327(27):1893–1898. doi:10.1056/NEJM199212313272701
17. McClave SA, Taylor BE, Martindale RG, et al. Guidelines for the provision and assessment of nutrition support therapy in the adult critically ill patient (SCCM and A.S.P.E.N.). *JPEN J Parenter Enteral Nutr*. 2016;40(2):159–211. doi:10.1177/0148607115621863
18. Mifflin MD, St Jeor ST, Hill LA, Scott BJ, Daugherty SA, Koh YO. A new predictive equation for resting energy expenditure in healthy individuals. *Am J Clin Nutr*. 1990;51(2):241–247. doi:10.1093/ajcn/51.2.241
19. Montgomery DC, Peck EA, Vining GG. *Introduction to Linear Regression Analysis*. 6th ed. Wiley; 2021.
20. Morton RW, Murphy KT, McKellar SR, et al. A systematic review, meta-analysis and meta-regression of the effect of protein supplementation on resistance training-induced gains in muscle mass and strength in healthy adults. *Br J Sports Med*. 2018;52(6):376–384. doi:10.1136/bjsports-2017-097608
21. Murphy C, Koehler K. Energy deficiency impairs resistance training gains in lean mass but not strength: a meta-analysis and meta-regression. *Scand J Med Sci Sports*. 2022;32(1):125–137. doi:10.1111/sms.14075
22. Nunes EA, Colenso-Semple L, McKellar SR, et al. Systematic review and meta-analysis of protein intake to support muscle mass and function in healthy adults. *J Cachexia Sarcopenia Muscle*. 2022;13(2):795–810. doi:10.1002/jcsm.12922
23. Prince SA, Adamo KB, Hamel ME, Hardt J, Connor Gorber S, Tremblay M. A comparison of direct versus self-report measures for assessing physical activity in adults: a systematic review. *Int J Behav Nutr Phys Act*. 2008;5:56. doi:10.1186/1479-5868-5-56
24. Rosenbaum M, Leibel RL. Adaptive thermogenesis in humans. *Int J Obes*. 2010;34(Suppl 1):S47–S55. doi:10.1038/ijo.2010.184
25. Schoeller DA. Limitations in the assessment of dietary energy intake by self-report. *Metabolism*. 1995;44(2 Suppl 2):18–22. doi:10.1016/0026-0495(95)90204-X
26. Walker J. *The Hacker's Diet: how to lose weight and hair through stress and poor nutrition*. fourmilab.ch; 2005.
27. Zheng Y, Klem ML, Sereika SM, Danford CA, Ewing LJ, Burke LE. Self-weighing in weight management: a systematic literature review. *Obesity*. 2015;23(2):256–265. doi:10.1002/oby.20946
