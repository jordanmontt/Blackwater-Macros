/// Port de `src/lib/protein.ts`: recomendación de proteína por objetivo
/// (g/kg/día) basada en evidencia.
library;

import 'models.dart';

const _ranges = <Goal, ({double bwMin, double bwMax})>{
  Goal.maintain: (bwMin: 1.2, bwMax: 1.6),
  Goal.surplus: (bwMin: 1.6, bwMax: 2.0),
  Goal.cut: (bwMin: 1.6, bwMax: 2.2),
};

ProteinRecommendation calculateProteinRecommendation(double weightKg, Goal goal) {
  final range = _ranges[goal]!;
  return ProteinRecommendation(
    goal: goal,
    bodyWeightKg: weightKg,
    bwRange: ProteinRange(
      min: (weightKg * range.bwMin).roundToDouble(),
      max: (weightKg * range.bwMax).roundToDouble(),
    ),
    bwPerKg: ProteinRange(
      min: (range.bwMin * 10).roundToDouble() / 10,
      max: (range.bwMax * 10).roundToDouble() / 10,
    ),
  );
}