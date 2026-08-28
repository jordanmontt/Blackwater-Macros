import 'package:flutter/material.dart';

import '../../../core/domain/models.dart';
import '../../../i18n/es.dart';

/// Tarjeta de una comida dentro de la lista del día.
class MealCard extends StatelessWidget {
  const MealCard({
    super.key,
    required this.meal,
    required this.onEdit,
    required this.onDelete,
    this.dragHandle,
  });

  final Meal meal;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  /// Divisor/sensor de arrastre para reordenar la lista.
  final Widget? dragHandle;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 8, 12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (dragHandle != null) ...[
              dragHandle!,
              const SizedBox(width: 4),
            ],
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Flexible(
                        child: Text(
                          meal.title,
                          style: theme.textTheme.titleMedium,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      if (meal.entryMode == EntryMode.totalOnly) ...[
                        const SizedBox(width: 8),
                        _Badge(text: S.meal.totalOnlyBadge),
                      ],
                    ],
                  ),
                  if (meal.notes != null && meal.notes!.isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Text(
                      meal.notes!,
                      style: theme.textTheme.bodySmall,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                  if (meal.entryMode == EntryMode.perIngredient) ...[
                    const SizedBox(height: 8),
                    Text(
                      fill(S.meal.perIngredientSummary, {
                        'n': '${meal.ingredients.length}',
                      }),
                      style: theme.textTheme.bodySmall,
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(width: 8),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(
                  '${_fmt(meal.resolvedCalories)} ${S.hoy.kcalUnit}',
                  style: theme.textTheme.titleMedium!.copyWith(
                    color: theme.colorScheme.primary,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                Text(
                  '${_fmt(meal.resolvedProtein)} ${S.hoy.proteinUnit}',
                  style: theme.textTheme.bodySmall,
                ),
              ],
            ),
            IconButton(
              tooltip: S.meal.edit,
              onPressed: onEdit,
              icon: const Icon(Icons.edit_outlined),
            ),
            IconButton(
              tooltip: S.meal.delete,
              onPressed: onDelete,
              icon: const Icon(Icons.delete_outline),
              color: theme.colorScheme.error,
            ),
          ],
        ),
      ),
    );
  }

  String _fmt(double value) => value % 1 == 0
      ? value.toInt().toString()
      : value.toStringAsFixed(1).replaceAll('.', ',');
}

class _Badge extends StatelessWidget {
  const _Badge({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: scheme.secondaryContainer,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(text, style: Theme.of(context).textTheme.labelSmall),
    );
  }
}