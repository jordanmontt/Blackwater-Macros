import 'package:flutter/material.dart';

import '../../../core/domain/calories.dart';
import '../../../core/domain/dates.dart';
import '../../../core/domain/models.dart';
import '../../../core/domain/protein.dart';
import '../../../i18n/es.dart';

/// Tarjetas de objetivo alcanzado del día (proteína y calorías), espejo de
/// `src/components/protein-recommendation.tsx` y `calorie-recommendation.tsx`.
class HoyGoalCards extends StatelessWidget {
  const HoyGoalCards({
    super.key,
    required this.profile,
    required this.latestWeightKg,
    required this.dailyCalories,
    required this.dailyProtein,
  });

  final CalorieProfile profile;
  final double? latestWeightKg;
  final double dailyCalories;
  final double dailyProtein;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final weight = latestWeightKg;

    if (weight == null) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: Text(
            S.protein.noWeight,
            textAlign: TextAlign.center,
            style: theme.textTheme.bodySmall,
          ),
        ),
      );
    }

    final sections = <Widget>[
      if (profile.calorieGoal != null)
        _ProteinSection(
          weightKg: weight,
          goal: profile.calorieGoal!,
          dailyProtein: dailyProtein,
        ),
      if (calculateCalorieRecommendation(profile, weight) case final rec?)
        _CalorieSection(
          goal: rec.goal,
          target: rec.target,
          targetMin: rec.targetMin,
          targetMax: rec.targetMax,
          weightKg: weight,
          dailyCalories: dailyCalories,
        ),
    ];

    if (sections.isEmpty) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: Text(
            S.calorias.noProfile,
            textAlign: TextAlign.center,
            style: theme.textTheme.bodySmall,
          ),
        ),
      );
    }

    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            for (var index = 0; index < sections.length; index++) ...[
              if (index > 0) const Divider(height: 20),
              sections[index],
            ],
          ],
        ),
      ),
    );
  }
}

String _goalLabel(Goal goal) => switch (goal) {
      Goal.cut => S.ajustes.goalCut,
      Goal.maintain => S.ajustes.goalMaintain,
      Goal.surplus => S.ajustes.goalSurplus,
    };

/// Línea «Ingesta de hoy: X g por debajo/en rango…» con el estado coloreado.
class _ConsumoLine extends StatelessWidget {
  const _ConsumoLine({
    required this.value,
    required this.status,
    required this.statusColor,
  });

  final String value;
  final String status;
  final Color statusColor;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Text.rich(
      TextSpan(
        style: theme.textTheme.bodySmall,
        children: [
          TextSpan(text: '${S.protein.currentIntake}: '),
          TextSpan(
            text: value,
            style: const TextStyle(fontWeight: FontWeight.w600),
          ),
          TextSpan(text: ' · ', style: TextStyle(color: theme.colorScheme.onSurfaceVariant)),
          TextSpan(text: status, style: TextStyle(color: statusColor)),
        ],
      ),
    );
  }
}

class _ProteinSection extends StatelessWidget {
  const _ProteinSection({
    required this.weightKg,
    required this.goal,
    required this.dailyProtein,
  });

  final double weightKg;
  final Goal goal;
  final double dailyProtein;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final rec = calculateProteinRecommendation(weightKg, goal);

    final inRange =
        dailyProtein >= rec.bwRange.min && dailyProtein <= rec.bwRange.max;
    final status = inRange
        ? S.protein.inRange
        : dailyProtein < rec.bwRange.min
            ? S.protein.belowRange
            : S.protein.aboveRange;
    final statusColor = inRange
        ? Colors.green.shade600
        : dailyProtein < rec.bwRange.min
            ? Colors.amber.shade700
            : Colors.orange.shade700;
    final barProgress = rec.bwRange.max > 0
        ? (dailyProtein / rec.bwRange.max).clamp(0.0, 1.0)
        : 0.0;
    final markerFraction =
        rec.bwRange.max > 0 ? rec.bwRange.min / rec.bwRange.max : 0.0;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(Icons.fitness_center, size: 18, color: theme.colorScheme.primary),
            const SizedBox(width: 6),
            Expanded(
              child: Text(
                S.protein.recommendationTitle,
                style: theme.textTheme.titleSmall,
              ),
            ),
          ],
        ),
        const SizedBox(height: 6),
        Text(
          '${S.protein.goalLabel}: ${_goalLabel(goal)} · '
          '${formatNumberEs(weightKg, maxDecimals: 1)} kg',
          style: theme.textTheme.bodySmall!.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          'Rango: ${formatNumberEs(rec.bwRange.min)} – '
          '${formatNumberEs(rec.bwRange.max)} g/día '
          '${fill(S.protein.perKg, {'min': formatNumberEs(rec.bwPerKg.min, maxDecimals: 1), 'max': formatNumberEs(rec.bwPerKg.max, maxDecimals: 1)})}',
          style: theme.textTheme.bodySmall,
        ),
        const SizedBox(height: 4),
        _ConsumoLine(
          value: '${formatNumberEs(dailyProtein, maxDecimals: 1)} g',
          status: status,
          statusColor: statusColor,
        ),
        const SizedBox(height: 8),
        _GoalBar(progress: barProgress, markerFraction: markerFraction),
      ],
    );
  }
}

class _CalorieSection extends StatelessWidget {
  const _CalorieSection({
    required this.goal,
    required this.target,
    required this.targetMin,
    required this.targetMax,
    required this.weightKg,
    required this.dailyCalories,
  });

  final Goal goal;
  final double target;
  final double targetMin;
  final double targetMax;
  final double weightKg;
  final double dailyCalories;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    final inRange = dailyCalories >= targetMin && dailyCalories <= targetMax;
    final status = inRange
        ? S.calorias.inRange
        : dailyCalories < targetMin
            ? S.calorias.belowRange
            : S.calorias.aboveRange;
    final statusColor = inRange
        ? Colors.green.shade600
        : dailyCalories < targetMin
            ? Colors.amber.shade700
            : Colors.orange.shade700;
    final barProgress =
        targetMax > 0 ? (dailyCalories / targetMax).clamp(0.0, 1.0) : 0.0;
    final markerFraction =
        targetMax > 0 ? targetMin / targetMax : 0.0;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(
              Icons.local_fire_department,
              size: 18,
              color: theme.colorScheme.primary,
            ),
            const SizedBox(width: 6),
            Expanded(
              child: Text(
                S.calorias.recommendationTitle,
                style: theme.textTheme.titleSmall,
              ),
            ),
          ],
        ),
        const SizedBox(height: 6),
        Text(
          'Objetivo: ${_goalLabel(goal)} · '
          '${formatNumberEs(weightKg, maxDecimals: 1)} kg',
          style: theme.textTheme.bodySmall!.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
          maxLines: 1,
        ),
        const SizedBox(height: 4),
        Text(
          'Rango: ${formatNumberEs(targetMin)} – ${formatNumberEs(targetMax)} '
          '${S.calorias.perDay} (objetivo ${formatNumberEs(target)})',
          style: theme.textTheme.bodySmall,
        ),
        const SizedBox(height: 4),
        _ConsumoLine(
          value: '${formatNumberEs(dailyCalories)} kcal',
          status: status,
          statusColor: statusColor,
        ),
        const SizedBox(height: 8),
        _GoalBar(progress: barProgress, markerFraction: markerFraction),
      ],
    );
  }
}

/// Barra de progreso frente al objetivo con una marca en el mínimo del rango.
class _GoalBar extends StatelessWidget {
  const _GoalBar({required this.progress, required this.markerFraction});

  final double progress;
  final double markerFraction;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return SizedBox(
      height: 6,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final width = constraints.maxWidth;
          return Stack(
            clipBehavior: Clip.none,
            children: [
              Container(
                decoration: BoxDecoration(
                  color: scheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(999),
                ),
              ),
              FractionallySizedBox(
                alignment: Alignment.centerLeft,
                widthFactor: progress,
                child: Container(
                  decoration: BoxDecoration(
                    color: scheme.primary,
                    borderRadius: BorderRadius.circular(999),
                  ),
                ),
              ),
              Positioned(
                left: (markerFraction * width - 1).clamp(0.0, width - 2),
                top: 0,
                bottom: 0,
                child: Container(
                  width: 2,
                  color: scheme.onSurfaceVariant.withValues(alpha: 0.6),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}