import 'package:flutter/material.dart';

import '../../../core/domain/dates.dart';
import '../../../i18n/es.dart';

/// Barra de navegación entre días (‹ día ›) con botón de "Hoy".
class DayNavigator extends StatelessWidget {
  const DayNavigator({
    super.key,
    required this.selectedDay,
    required this.onSelect,
    this.onToday,
  });

  final String selectedDay;
  final ValueChanged<String> onSelect;
  final VoidCallback? onToday;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        children: [
          IconButton(
            tooltip: S.peso.nowButton,
            onPressed: () => onSelect(addDaysToKey(selectedDay, -1)),
            icon: const Icon(Icons.chevron_left),
          ),
          Expanded(
            child: GestureDetector(
              onTap: onToday,
              child: Column(
                children: [
                  Text(
                    formatDateKeyLong(selectedDay),
                    style: theme.textTheme.titleMedium,
                    textAlign: TextAlign.center,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    formatDateKeyShort(selectedDay),
                    style: theme.textTheme.bodySmall,
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            ),
          ),
          IconButton(
            tooltip: S.peso.nowButton,
            onPressed: () => onSelect(addDaysToKey(selectedDay, 1)),
            icon: const Icon(Icons.chevron_right),
          ),
        ],
      ),
    );
  }
}