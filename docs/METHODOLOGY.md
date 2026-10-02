# Blackwater Macros — Scientific methodology

How the app turns a profile, weigh-ins and logged meals into calorie and protein targets,
a measured expenditure and a weight projection: every formula, why it was chosen, where
it comes from and where it stops being reliable.

- **Code:** `src/lib/core/calories.ts`, `protein.ts`, `expenditure.ts`, `stats.ts`,
  `stats-builder.ts`, `progress.ts`, `coach.ts` (web), mirrored 1:1 in Kotlin
  `android/core` (see [TECHNICAL.md](./TECHNICAL.md) §8 and §11.1).
- **User-facing text:** the Metodología page (web `metodologia.*` in `src/i18n/*.ts` plus
  the formula blocks and references in `src/app/metodologia/page.tsx`; Android `meth_*`
  strings and `MethodologyScreen.kt`).
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
| Protein | g/kg by goal; lean mass when cutting with body fat; weight at BMI 25 above BMI 25 | Jäger 2017; Morton 2018; Helms 2014 (IJSNEM); McClave 2016; Kokura 2024; Gallagher 2000 |
| Measured expenditure | Energy balance over 28 days, least-squares weight slope, 95 % margin | Hall 2008; Montgomery 2021 |
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

---

## 2. Protein (`protein.ts`)

The number shown is the midpoint of a range, in grams per day:
`range = basis_kg × (min…max g/kg)`.

### 2.1 Ranges by goal

| Goal | g/kg | Basis | Source and reasoning |
|---|---|---|---|
| Maintain | 1.4–2.0 | body weight | ISSN position stand for people who exercise (Jäger et al. 2017). Well above the 0.8 g/kg RDA, which is a minimum for sedentary people, not an optimum for people who train. |
| Surplus | 1.6–2.2 | body weight | Meta-analysis of 49 RCTs (Morton et al. 2018): the benefit to muscle gain plateaus at ~1.6 g/kg, with the upper 95 % confidence limit at 2.2. Iraki et al. (2019) recommend exactly 1.6–2.2 for a surplus. |
| Cut, no body fat logged | 1.8–2.7 | body weight | In a deficit more protein is needed to keep muscle. This is the per-body-weight equivalent of 2.3–3.1 g/kg of lean mass at typical body fat: 2.3 × 0.78 ≈ 1.8 (22 % fat) and 3.1 × 0.87 ≈ 2.7 (13 % fat). |
| Cut, body fat logged | 2.3–3.1 | lean mass = weight × (1 − fat %) | Helms, Zinn et al. (2014, IJSNEM), a systematic review in lean, resistance-trained people in a deficit; adopted by Jäger et al. (2017). |

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

## 9. References

1. Byrne NM, Hills AP, Hunter GR, Weinsier RL, Schutz Y. Metabolic equivalent: one size does not fit all. *J Appl Physiol*. 2005;99(3):1112–1119. doi:10.1152/japplphysiol.00023.2004
2. FAO/WHO/UNU. Human energy requirements: report of a joint FAO/WHO/UNU expert consultation. *FAO Food and Nutrition Technical Report Series 1*. Rome: FAO; 2004.
3. Frankenfield D, Roth-Yousey L, Compher C. Comparison of predictive equations for resting metabolic rate in healthy nonobese and obese adults: a systematic review. *J Am Diet Assoc*. 2005;105(5):775–789. doi:10.1016/j.jada.2005.02.005
4. Gallagher D, Heymsfield SB, Heo M, Jebb SA, Murgatroyd PR, Sakamoto Y. Healthy percentage body fat ranges: an approach for developing guidelines based on body mass index. *Am J Clin Nutr*. 2000;72(3):694–701. doi:10.1093/ajcn/72.3.694
5. Hall KD. What is the required energy deficit per unit weight loss? *Int J Obes*. 2008;32(3):573–576. doi:10.1038/sj.ijo.0803720
6. Helms ER, Aragon AA, Fitschen PJ. Evidence-based recommendations for natural bodybuilding contest preparation: nutrition and supplementation. *J Int Soc Sports Nutr*. 2014;11:20. doi:10.1186/1550-2783-11-20
7. Helms ER, Zinn C, Rowlands DS, Brown SR. A systematic review of dietary protein during caloric restriction in resistance trained lean athletes: a case for higher intakes. *Int J Sport Nutr Exerc Metab*. 2014;24(2):127–138. doi:10.1123/ijsnem.2013-0054
8. Herrmann SD, Willis EA, Ainsworth BE, et al. 2024 Adult Compendium of Physical Activities: a third update of the energy costs of human activities. *J Sport Health Sci*. 2024;13(1):6–12. doi:10.1016/j.jshs.2023.10.010
9. Iraki J, Fitschen P, Espinar S, Helms E. Nutrition recommendations for bodybuilders in the off-season: a narrative review. *Sports*. 2019;7(7):154. doi:10.3390/sports7070154
10. Jäger R, Kerksick CM, Campbell BI, et al. International Society of Sports Nutrition Position Stand: protein and exercise. *J Int Soc Sports Nutr*. 2017;14:20. doi:10.1186/s12970-017-0177-8
11. Kokura Y, Ueshima J, Saino Y, Maeda K. Enhanced protein intake on maintaining muscle mass, strength, and physical function in adults with overweight/obesity: a systematic review and meta-analysis. *Clin Nutr ESPEN*. 2024;63:417–426. doi:10.1016/j.clnesp.2024.06.030
12. McClave SA, Taylor BE, Martindale RG, et al. Guidelines for the provision and assessment of nutrition support therapy in the adult critically ill patient (SCCM and A.S.P.E.N.). *JPEN J Parenter Enteral Nutr*. 2016;40(2):159–211. doi:10.1177/0148607115621863
13. Mifflin MD, St Jeor ST, Hill LA, Scott BJ, Daugherty SA, Koh YO. A new predictive equation for resting energy expenditure in healthy individuals. *Am J Clin Nutr*. 1990;51(2):241–247. doi:10.1093/ajcn/51.2.241
14. Montgomery DC, Peck EA, Vining GG. *Introduction to Linear Regression Analysis*. 6th ed. Wiley; 2021.
15. Morton RW, Murphy KT, McKellar SR, et al. A systematic review, meta-analysis and meta-regression of the effect of protein supplementation on resistance training-induced gains in muscle mass and strength in healthy adults. *Br J Sports Med*. 2018;52(6):376–384. doi:10.1136/bjsports-2017-097608
16. Nunes EA, Colenso-Semple L, McKellar SR, et al. Systematic review and meta-analysis of protein intake to support muscle mass and function in healthy adults. *J Cachexia Sarcopenia Muscle*. 2022;13(2):795–810. doi:10.1002/jcsm.12922
17. Prince SA, Adamo KB, Hamel ME, Hardt J, Connor Gorber S, Tremblay M. A comparison of direct versus self-report measures for assessing physical activity in adults: a systematic review. *Int J Behav Nutr Phys Act*. 2008;5:56. doi:10.1186/1479-5868-5-56
18. Walker J. *The Hacker's Diet: how to lose weight and hair through stress and poor nutrition*. fourmilab.ch; 2005.
19. Zheng Y, Klem ML, Sereika SM, Danford CA, Ewing LJ, Burke LE. Self-weighing in weight management: a systematic literature review. *Obesity*. 2015;23(2):256–265. doi:10.1002/oby.20946
