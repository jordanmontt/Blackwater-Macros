import 'package:flutter/material.dart';

import '../../../core/domain/dates.dart';
import '../../../core/domain/models.dart';
import '../../../i18n/es.dart';

/// Tarjetas con los totales del día (calorías, proteína, carbohidratos, grasa).
class DailyTotalsCard extends StatelessWidget {
  const DailyTotalsCard({super.key, required this.meals});

  final List<Meal> meals;

  @override
  Widget build(BuildContext context) {
    var calories = 0.0, protein = 0.0, carbs = 0.0, fat = 0.0;
    for (final meal in meals) {
      calories += meal.resolvedCalories;
      protein += meal.resolvedProtein;
      carbs += meal.resolvedCarbs;
      fat += meal.resolvedFat;
    }

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                S.hoy.dailyTotals,
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  _TotalItem(value: calories, unit: S.hoy.kcalUnit, label: S.hoy.calories),
                  _TotalItem(value: protein, unit: S.hoy.proteinUnit, label: S.hoy.protein),
                  _TotalItem(value: carbs, unit: S.hoy.gramUnit, label: S.hoy.carbs),
                  _TotalItem(value: fat, unit: S.hoy.gramUnit, label: S.hoy.fat),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _TotalItem extends StatelessWidget {
  const _TotalItem({required this.value, required this.unit, required this.label});

  final double value;
  final String unit;
  final String label;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Expanded(
      child: Column(
        children: [
          Text.rich(
            TextSpan(
              children: [
                TextSpan(
                  text: formatNumberEs(value, maxDecimals: 0),
                  style: theme.textTheme.titleLarge!.copyWith(
                    color: theme.colorScheme.primary,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                TextSpan(
                  text: ' $unit',
                  style: theme.textTheme.bodySmall!.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: theme.textTheme.bodySmall,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }
}