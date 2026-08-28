/// Tipos del dominio compartidos entre la UI, el cliente API y la lógica pura.
/// Nombres JSON idénticos al contrato del backend (Hono + Next.js).
library;

/// Tipos de entrada de una comida.
enum EntryMode {
  perIngredient('per_ingredient'),
  totalOnly('total_only');

  const EntryMode(this.json);
  final String json;

  static EntryMode fromJson(String value) => EntryMode.values.firstWhere(
        (mode) => mode.json == value,
        orElse: () => throw FormatException('EntryMode desconocido: $value'),
      );
}

enum Gender {
  male('male'),
  female('female');

  const Gender(this.json);
  final String json;

  static Gender? fromJson(String? value) {
    if (value == null) return null;
    return Gender.values.firstWhere(
      (gender) => gender.json == value,
      orElse: () => throw FormatException('Gender desconocido: $value'),
    );
  }
}

enum Goal {
  cut('cut'),
  maintain('maintain'),
  surplus('surplus');

  const Goal(this.json);
  final String json;

  static Goal? fromJson(String? value) {
    if (value == null) return null;
    return Goal.values.firstWhere(
      (goal) => goal.json == value,
      orElse: () => throw FormatException('Goal desconocido: $value'),
    );
  }
}

enum StatsRange {
  d7('7d'),
  d30('30d'),
  d90('90d'),
  all('all');

  const StatsRange(this.json);
  final String json;

  static StatsRange fromJson(String value) => StatsRange.values.firstWhere(
        (range) => range.json == value,
        orElse: () => throw FormatException('StatsRange desconocido: $value'),
      );
}

class Ingredient {
  const Ingredient({
    required this.name,
    this.quantity,
    this.calories,
    this.protein,
    this.carbs,
    this.fat,
  });

  final String name;
  final String? quantity;
  final double? calories;
  final double? protein;
  final double? carbs;
  final double? fat;

  factory Ingredient.fromJson(Map<String, dynamic> json) => Ingredient(
        name: json['name'] as String,
        quantity: json['quantity'] as String?,
        calories: _asDouble(json['calories']),
        protein: _asDouble(json['protein']),
        carbs: _asDouble(json['carbs']),
        fat: _asDouble(json['fat']),
      );

  Map<String, dynamic> toJson() => {
        'name': name,
        if (quantity != null) 'quantity': quantity,
        if (calories != null) 'calories': calories,
        if (protein != null) 'protein': protein,
        if (carbs != null) 'carbs': carbs,
        if (fat != null) 'fat': fat,
      };
}

class Meal {
  const Meal({
    required this.id,
    required this.logDate,
    required this.title,
    this.notes,
    required this.entryMode,
    required this.ingredients,
    this.totalCalories,
    this.totalProtein,
    this.totalCarbs,
    this.totalFat,
    required this.resolvedCalories,
    required this.resolvedProtein,
    this.resolvedCarbs = 0,
    this.resolvedFat = 0,
  });

  final String id;
  final String logDate;
  final String title;
  final String? notes;
  final EntryMode entryMode;
  final List<Ingredient> ingredients;
  final double? totalCalories;
  final double? totalProtein;
  final double? totalCarbs;
  final double? totalFat;
  final double resolvedCalories;
  final double resolvedProtein;
  final double resolvedCarbs;
  final double resolvedFat;

  factory Meal.fromJson(Map<String, dynamic> json) => Meal(
        id: json['id'] as String,
        logDate: json['logDate'] as String,
        title: json['title'] as String,
        notes: json['notes'] as String?,
        entryMode: EntryMode.fromJson(json['entryMode'] as String),
        ingredients: (json['ingredients'] as List<dynamic>)
            .map((item) => Ingredient.fromJson(item as Map<String, dynamic>))
            .toList(),
        totalCalories: _asDouble(json['totalCalories']),
        totalProtein: _asDouble(json['totalProtein']),
        totalCarbs: _asDouble(json['totalCarbs']),
        totalFat: _asDouble(json['totalFat']),
        resolvedCalories: _asDouble(json['resolvedCalories']) ?? 0,
        resolvedProtein: _asDouble(json['resolvedProtein']) ?? 0,
        resolvedCarbs: _asDouble(json['resolvedCarbs']) ?? 0,
        resolvedFat: _asDouble(json['resolvedFat']) ?? 0,
      );

  /// Borrador sin id (aún no guardado); útil para construir payloads de
  /// creación/actualización. `toPayload()` ignora el id.
  const Meal.draft({
    required this.logDate,
    required this.title,
    this.notes,
    this.entryMode = EntryMode.perIngredient,
    this.ingredients = const [],
    this.totalCalories,
    this.totalProtein,
    this.totalCarbs,
    this.totalFat,
  })  : id = '',
        resolvedCalories = 0,
        resolvedProtein = 0,
        resolvedCarbs = 0,
        resolvedFat = 0;

  /// Payload de creación/actualización que espera el backend.
  Map<String, dynamic> toPayload() => {
        'logDate': logDate,
        'title': title,
        if (notes != null) 'notes': notes,
        'entryMode': entryMode.json,
        'ingredients': ingredients.map((ingredient) => ingredient.toJson()).toList(),
        if (totalCalories != null) 'totalCalories': totalCalories,
        if (totalProtein != null) 'totalProtein': totalProtein,
        if (totalCarbs != null) 'totalCarbs': totalCarbs,
        if (totalFat != null) 'totalFat': totalFat,
      };
}

class MealTemplate {
  const MealTemplate({
    required this.id,
    required this.name,
    required this.title,
    this.notes,
    required this.ingredients,
  });

  final String id;
  final String name;
  final String title;
  final String? notes;
  final List<Ingredient> ingredients;

  factory MealTemplate.fromJson(Map<String, dynamic> json) => MealTemplate(
        id: json['id'] as String,
        name: json['name'] as String,
        title: json['title'] as String,
        notes: json['notes'] as String?,
        ingredients: (json['ingredients'] as List<dynamic>)
            .map((item) => Ingredient.fromJson(item as Map<String, dynamic>))
            .toList(),
      );
}

class WeightEntry {
  const WeightEntry({
    required this.id,
    required this.measuredAt,
    required this.weightKg,
    this.bodyFatPct,
    this.note,
  });

  final String id;
  final DateTime measuredAt;
  final double weightKg;
  final double? bodyFatPct;
  final String? note;

  factory WeightEntry.fromJson(Map<String, dynamic> json) => WeightEntry(
        id: json['id'] as String,
        measuredAt: DateTime.parse(json['measuredAt'] as String),
        weightKg: _asDouble(json['weightKg']) ?? 0,
        bodyFatPct: _asDouble(json['bodyFatPct']),
        note: json['note'] as String?,
      );

  Map<String, dynamic> toPayload() => {
        'measuredAt': measuredAt.toUtc().toIso8601String(),
        'weightKg': weightKg,
        if (bodyFatPct != null) 'bodyFatPct': bodyFatPct,
        if (note != null) 'note': note,
      };
}

class CalorieProfile {
  const CalorieProfile({
    this.gender,
    this.birthYear,
    this.heightCm,
    this.gymDaysPerWeek,
    this.gymSessionMinutes,
    this.walkingMinutesPerDay,
    this.calorieGoal,
  });

  final Gender? gender;
  final int? birthYear;
  final double? heightCm;
  final int? gymDaysPerWeek;
  final int? gymSessionMinutes;
  final int? walkingMinutesPerDay;
  final Goal? calorieGoal;

  static const CalorieProfile empty = CalorieProfile();

  bool get isComplete =>
      gender != null &&
      birthYear != null &&
      heightCm != null &&
      gymDaysPerWeek != null &&
      gymSessionMinutes != null &&
      walkingMinutesPerDay != null &&
      calorieGoal != null;

  factory CalorieProfile.fromJson(Map<String, dynamic> json) => CalorieProfile(
        gender: Gender.fromJson(json['gender'] as String?),
        birthYear: json['birthYear'] as int?,
        heightCm: _asDouble(json['heightCm']),
        gymDaysPerWeek: json['gymDaysPerWeek'] as int?,
        gymSessionMinutes: json['gymSessionMinutes'] as int?,
        walkingMinutesPerDay: json['walkingMinutesPerDay'] as int?,
        calorieGoal: Goal.fromJson(json['calorieGoal'] as String?),
      );

  Map<String, dynamic> toJson() => {
        'gender': gender?.json,
        'birthYear': birthYear,
        'heightCm': heightCm,
        'gymDaysPerWeek': gymDaysPerWeek,
        'gymSessionMinutes': gymSessionMinutes,
        'walkingMinutesPerDay': walkingMinutesPerDay,
        'calorieGoal': calorieGoal?.json,
      };
}

class DailyNutritionPoint {
  const DailyNutritionPoint({
    required this.date,
    required this.calories,
    required this.protein,
    required this.carbs,
    required this.fat,
  });

  final String date;
  final double calories;
  final double protein;
  final double carbs;
  final double fat;

  factory DailyNutritionPoint.fromJson(Map<String, dynamic> json) => DailyNutritionPoint(
        date: json['date'] as String,
        calories: _asDouble(json['calories']) ?? 0,
        protein: _asDouble(json['protein']) ?? 0,
        carbs: _asDouble(json['carbs']) ?? 0,
        fat: _asDouble(json['fat']) ?? 0,
      );
}

class TrendPoint {
  const TrendPoint({required this.date, required this.value, this.trend});

  final String date;
  final double value;
  final double? trend;
}

class CompositionStats {
  const CompositionStats({
    this.currentWeightKg,
    this.currentTrendKg,
    this.changeSinceStartKg,
    this.ratePerWeekKg,
    this.minKg,
    this.maxKg,
    this.currentBodyFatPct,
    this.changeBodyFatPct,
    this.minBodyFatPct,
    this.maxBodyFatPct,
    this.currentLeanMassKg,
    this.changeLeanMassKg,
  });

  final double? currentWeightKg;
  final double? currentTrendKg;
  final double? changeSinceStartKg;
  final double? ratePerWeekKg;
  final double? minKg;
  final double? maxKg;
  final double? currentBodyFatPct;
  final double? changeBodyFatPct;
  final double? minBodyFatPct;
  final double? maxBodyFatPct;
  final double? currentLeanMassKg;
  final double? changeLeanMassKg;

  static const empty = CompositionStats();

  factory CompositionStats.fromJson(Map<String, dynamic> json) => CompositionStats(
        currentWeightKg: _asDouble(json['currentWeightKg']),
        currentTrendKg: _asDouble(json['currentTrendKg']),
        changeSinceStartKg: _asDouble(json['changeSinceStartKg']),
        ratePerWeekKg: _asDouble(json['ratePerWeekKg']),
        minKg: _asDouble(json['minKg']),
        maxKg: _asDouble(json['maxKg']),
        currentBodyFatPct: _asDouble(json['currentBodyFatPct']),
        changeBodyFatPct: _asDouble(json['changeBodyFatPct']),
        minBodyFatPct: _asDouble(json['minBodyFatPct']),
        maxBodyFatPct: _asDouble(json['maxBodyFatPct']),
        currentLeanMassKg: _asDouble(json['currentLeanMassKg']),
        changeLeanMassKg: _asDouble(json['changeLeanMassKg']),
      );
}

class WeeklyWeightAverage {
  const WeeklyWeightAverage({required this.weekStart, required this.avg});

  final String weekStart;
  final double avg;
}

class StatsSummary {
  const StatsSummary({
    required this.calories,
    required this.protein,
    required this.carbs,
    required this.fat,
    required this.weights,
    required this.bodyFat,
    required this.leanMass,
    this.caloriesAvg,
    this.caloriesMaxDay,
    this.proteinAvg,
    this.proteinMaxDay,
    this.carbsAvg,
    this.carbsMaxDay,
    this.fatAvg,
    this.fatMaxDay,
    required this.weight,
    required this.weeklyWeightAvg,
  });

  final List<DailyNutritionPoint> calories;
  final List<DailyNutritionPoint> protein;
  final List<DailyNutritionPoint> carbs;
  final List<DailyNutritionPoint> fat;
  final List<TrendPoint> weights;
  final List<TrendPoint> bodyFat;
  final List<TrendPoint> leanMass;
  final double? caloriesAvg;
  final DailyNutritionPoint? caloriesMaxDay;
  final double? proteinAvg;
  final DailyNutritionPoint? proteinMaxDay;
  final double? carbsAvg;
  final DailyNutritionPoint? carbsMaxDay;
  final double? fatAvg;
  final DailyNutritionPoint? fatMaxDay;
  final CompositionStats weight;
  final List<WeeklyWeightAverage> weeklyWeightAvg;

  static const empty = StatsSummary(
    calories: [],
    protein: [],
    carbs: [],
    fat: [],
    weights: [],
    bodyFat: [],
    leanMass: [],
    weight: CompositionStats.empty,
    weeklyWeightAvg: [],
  );

  factory StatsSummary.fromJson(Map<String, dynamic> json) {
    List<DailyNutritionPoint> nutrition(List<dynamic> items) => items
        .map((item) => DailyNutritionPoint.fromJson(item as Map<String, dynamic>))
        .toList();

    List<TrendPoint> trend(List<dynamic> items, String valueKey) => items
        .map((item) {
          final map = item as Map<String, dynamic>;
          return TrendPoint(
            date: map['date'] as String,
            value: _asDouble(map[valueKey]) ?? 0,
            trend: _asDouble(map['trend']),
          );
        })
        .toList();

    return StatsSummary(
      calories: nutrition(json['calories'] as List<dynamic>),
      protein: nutrition(json['protein'] as List<dynamic>),
      carbs: nutrition(json['carbs'] as List<dynamic>),
      fat: nutrition(json['fat'] as List<dynamic>),
      weights: trend(json['weights'] as List<dynamic>, 'weight'),
      bodyFat: trend(json['bodyFat'] as List<dynamic>, 'bodyFatPct'),
      leanMass: trend(json['leanMass'] as List<dynamic>, 'leanMassKg'),
      caloriesAvg: _asDouble(json['caloriesAvg']),
      caloriesMaxDay: json['caloriesMaxDay'] == null
          ? null
          : DailyNutritionPoint.fromJson(json['caloriesMaxDay'] as Map<String, dynamic>),
      proteinAvg: _asDouble(json['proteinAvg']),
      proteinMaxDay: json['proteinMaxDay'] == null
          ? null
          : DailyNutritionPoint.fromJson(json['proteinMaxDay'] as Map<String, dynamic>),
      carbsAvg: _asDouble(json['carbsAvg']),
      carbsMaxDay: json['carbsMaxDay'] == null
          ? null
          : DailyNutritionPoint.fromJson(json['carbsMaxDay'] as Map<String, dynamic>),
      fatAvg: _asDouble(json['fatAvg']),
      fatMaxDay: json['fatMaxDay'] == null
          ? null
          : DailyNutritionPoint.fromJson(json['fatMaxDay'] as Map<String, dynamic>),
      weight: CompositionStats.fromJson(json['weight'] as Map<String, dynamic>),
      weeklyWeightAvg: (json['weeklyWeightAvg'] as List<dynamic>)
          .map((item) => WeeklyWeightAverage(
                weekStart: (item as Map<String, dynamic>)['weekStart'] as String,
                avg: _asDouble(item['avg']) ?? 0,
              ))
          .toList(),
    );
  }
}

class ProteinRange {
  const ProteinRange({required this.min, required this.max});

  final double min;
  final double max;
}

class ProteinRecommendation {
  const ProteinRecommendation({
    required this.goal,
    required this.bodyWeightKg,
    required this.bwRange,
    required this.bwPerKg,
  });

  final Goal goal;
  final double bodyWeightKg;
  final ProteinRange bwRange;
  final ProteinRange bwPerKg;
}

class CalorieRecommendation {
  const CalorieRecommendation({
    required this.bmr,
    required this.tdee,
    required this.target,
    required this.targetMin,
    required this.targetMax,
    required this.goal,
  });

  final double bmr;
  final double tdee;
  final double target;
  final double targetMin;
  final double targetMax;
  final Goal goal;
}

/// El backend serializa números (p. ej. "calorieGoal" como double en deps) y
/// a veces llegan como int. Normalizamos a double de forma segura.
double? _asDouble(dynamic value) {
  if (value == null) return null;
  if (value is num) return value.toDouble();
  if (value is String) return double.tryParse(value);
  return null;
}