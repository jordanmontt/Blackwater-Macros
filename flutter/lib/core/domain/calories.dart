/// Port de `src/lib/calories.ts`: ecuación Mifflin-St Jeor + multiplicador de
/// actividad + ajuste por objetivo.
library;

import 'models.dart';

double calculateBmr(Gender gender, double weightKg, double heightCm, int age) {
  final base = 10 * weightKg + 6.25 * heightCm - 5 * age;
  return gender == Gender.male ? base + 5 : base - 161;
}

double getActivityMultiplier(
  int gymDays,
  int gymMinutes,
  int walkingMinutes,
) {
  final weeklyGymMinutes = gymDays * gymMinutes;

  if (weeklyGymMinutes == 0 && walkingMinutes < 30) return 1.2;
  if (weeklyGymMinutes == 0 && walkingMinutes >= 30) return 1.375;
  if (weeklyGymMinutes > 0 && weeklyGymMinutes <= 150 && walkingMinutes < 30) return 1.375;
  if (weeklyGymMinutes > 0 && weeklyGymMinutes <= 150 && walkingMinutes >= 30) return 1.55;
  if (weeklyGymMinutes > 150 && weeklyGymMinutes <= 360 && walkingMinutes >= 30) return 1.55;
  if (weeklyGymMinutes > 150 && weeklyGymMinutes <= 360 && walkingMinutes < 30) return 1.375;
  if (weeklyGymMinutes > 360 && weeklyGymMinutes <= 540) return 1.725;
  return 1.9;
}

const _offsets = <Goal, ({double target, double min, double max})>{
  Goal.cut: (target: -400, min: -500, max: -300),
  Goal.maintain: (target: 0, min: -100, max: 100),
  Goal.surplus: (target: 300, min: 200, max: 400),
};

int getCurrentAge(int birthYear) => DateTime.now().year - birthYear;

CalorieRecommendation? calculateCalorieRecommendation(
  CalorieProfile profile,
  double weightKg,
) {
  if (!profile.isComplete) return null;

  final age = getCurrentAge(profile.birthYear!);
  final bmr = calculateBmr(profile.gender!, weightKg, profile.heightCm!, age);
  final multiplier = getActivityMultiplier(
    profile.gymDaysPerWeek!,
    profile.gymSessionMinutes!,
    profile.walkingMinutesPerDay!,
  );
  final tdee = (bmr * multiplier).roundToDouble();
  final offsets = _offsets[profile.calorieGoal!]!;

  return CalorieRecommendation(
    bmr: bmr.roundToDouble(),
    tdee: tdee,
    target: tdee + offsets.target,
    targetMin: tdee + offsets.min,
    targetMax: tdee + offsets.max,
    goal: profile.calorieGoal!,
  );
}