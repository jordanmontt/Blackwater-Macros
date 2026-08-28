/// Port de `src/lib/nutrition.ts`: totales de comida y redondeo.
library;

import 'models.dart';

class NutritionTotals {
  const NutritionTotals({this.calories = 0, this.protein = 0, this.carbs = 0, this.fat = 0});

  final double calories;
  final double protein;
  final double carbs;
  final double fat;
}

/// Suma la nutrición introducida por ingrediente (los vacíos aportan 0).
NutritionTotals sumIngredientNutrition(List<Ingredient> ingredients) {
  var calories = 0.0, protein = 0.0, carbs = 0.0, fat = 0.0;
  for (final ingredient in ingredients) {
    calories += ingredient.calories ?? 0;
    protein += ingredient.protein ?? 0;
    carbs += ingredient.carbs ?? 0;
    fat += ingredient.fat ?? 0;
  }
  return NutritionTotals(calories: calories, protein: protein, carbs: carbs, fat: fat);
}

/// Resuelve los totales según el modo: "total_only" usa los valores manuales;
/// "per_ingredient" suma lo introducido por ingrediente.
NutritionTotals resolveMealTotals(
  EntryMode entryMode,
  List<Ingredient> ingredients, {
  double? manualTotalCalories,
  double? manualTotalProtein,
  double? manualTotalCarbs,
  double? manualTotalFat,
}) {
  if (entryMode == EntryMode.totalOnly) {
    return NutritionTotals(
      calories: round2(manualTotalCalories ?? 0),
      protein: round2(manualTotalProtein ?? 0),
      carbs: round2(manualTotalCarbs ?? 0),
      fat: round2(manualTotalFat ?? 0),
    );
  }
  final summed = sumIngredientNutrition(ingredients);
  return NutritionTotals(
    calories: round2(summed.calories),
    protein: round2(summed.protein),
    carbs: round2(summed.carbs),
    fat: round2(summed.fat),
  );
}

double round1(double value) => (value * 10).roundToDouble() / 10;

double round2(double value) => (value * 100).roundToDouble() / 100;