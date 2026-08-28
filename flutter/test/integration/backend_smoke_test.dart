// Smoke de integración: valida el contrato Flutter → backend Hono en local.
// Requiere el backend efímero en memoria con el usuario 'smoke' (desde la raíz:
//   npx tsx backend/smoke.ts, puerto 8787/8788) y ejecutarse explícitamente:
//   flutter test test/integration/backend_smoke_test.dart \
//     --dart-define=RUN_INTEGRATION=true \
//     [--platform chrome] [--dart-define=API_BASE_URL=http://localhost:8788]
// Se omite en `flutter test` normal (no debe tocar un backend real).
library;

import 'package:flutter_test/flutter_test.dart';

import 'package:blackwater_macros/core/api/api.dart';
import 'package:blackwater_macros/core/domain/dates.dart';
import 'package:blackwater_macros/core/domain/models.dart';

void main() {
  String token = '';
  final client = ApiClient(
    tokenProvider: () async => token.isEmpty ? null : token,
  );
  final api = Api(client: client);

  test('flujo completo contra el backend local', () async {
    const enabled = bool.fromEnvironment('RUN_INTEGRATION');
    if (!enabled) {
      markTestSkipped('E2E explícito: añade --dart-define=RUN_INTEGRATION=true');
      return;
    }
    try {
      token = await api.login('smoke', 'smoke1234');
    } on ApiException catch (e) {
      if (e.isNetwork) {
        markTestSkipped('Backend no disponible en localhost:8787');
        return;
      }
      rethrow;
    }
    expect(token, isNotEmpty);

    final session = await api.session();
    expect(session.username, 'smoke');

    final today = todayKey();
    // Limpieza para que el humo sea re-ejecutable contra un backend persistente.
    for (final meal in await api.listMeals(today, today)) {
      await api.deleteMeal(meal.id);
    }
    expect(await api.listMeals(today, today), isEmpty);

    final draft = Meal.draft(
      logDate: today,
      title: 'Desayuno smoke',
      notes: 'nota de prueba',
      entryMode: EntryMode.perIngredient,
      ingredients: const [
        Ingredient(name: '4 huevos', calories: 300, protein: 24, carbs: 2, fat: 20),
      ],
    );
    final created = await api.createMeal(draft);
    expect(created.id, isNotEmpty);
    expect(created.resolvedCalories, closeTo(300, 0.001));
    expect(created.resolvedProtein, closeTo(24, 0.001));

    final listed = await api.listMeals(today, today);
    expect(listed.map((meal) => meal.id), contains(created.id));

    final updated = await api.updateMeal(created.id, Meal.draft(
      logDate: today,
      title: 'Desayuno editado',
      entryMode: EntryMode.perIngredient,
      ingredients: const [Ingredient(name: 'avena', calories: 150, protein: 5)],
    ));
    expect(updated.title, 'Desayuno editado');
    expect(updated.resolvedCalories, closeTo(150, 0.001));

    final total = await api.createMeal(Meal.draft(
      logDate: today,
      title: 'Comida manual',
      entryMode: EntryMode.totalOnly,
      totalCalories: 800,
      totalProtein: 40,
      totalCarbs: 90,
      totalFat: 20,
    ));
    expect(total.resolvedCalories, closeTo(800, 0.001));

    await api.reorderMeals([total.id, updated.id]);

    final template = await api.createTemplate(
      name: 'Plantilla smoke',
      title: 'Plantilla título',
      notes: 'n',
      ingredients: const [Ingredient(name: 'x', calories: 100)],
    );
    final templates = await api.listTemplates();
    expect(templates.map((item) => item.id), contains(template.id));
    await api.deleteTemplate(template.id);

    final weight = await api.createWeight(WeightEntry.fromJson({
      'id': '',
      'measuredAt': DateTime.now().toUtc().toIso8601String(),
      'weightKg': 80.5,
      'bodyFatPct': 18,
    }));
    final weights = await api.listWeights();
    expect(weights.map((item) => item.id), contains(weight.id));

    final statsRange = await api.stats(StatsRange.d30, today);
    final lastDay = statsRange.calories.lastWhere(
      (point) => point.date == today,
      orElse: () => statsRange.calories.isEmpty
          ? DailyNutritionPoint(date: today, calories: 0, protein: 0, carbs: 0, fat: 0)
          : statsRange.calories.last,
    );
    expect(lastDay.calories, closeTo(950, 0.001));
    expect(statsRange.weight.minKg, isNotNull);

    final profile = await api.updateSettings(const CalorieProfile(
      gender: Gender.male,
      birthYear: 1990,
      heightCm: 180,
      gymDaysPerWeek: 3,
      gymSessionMinutes: 60,
      walkingMinutesPerDay: 30,
      calorieGoal: Goal.maintain,
    ));
    // El fake en memoria del backend solo persiste calorieGoal.
    expect(profile.calorieGoal, Goal.maintain);

    final csv = await client.requestText('/api/export/meals.csv');
    expect(csv, contains('fecha'));

    await api.deleteMeal(total.id);
    final afterDelete = await api.listMeals(today, today);
    expect(afterDelete.map((meal) => meal.id), isNot(contains(total.id)));

    // Sesión sin token (o con token inválido) recibe 401.
    token = '';
    await expectLater(
      api.listMeals(today, today),
      throwsA(isA<ApiException>().having((e) => e.isUnauthorized, 'isUnauthorized', true)),
    );

    await api.logout();
  });
}