import 'package:flutter/material.dart';

import '../../core/api/api.dart';
import '../../core/auth/auth_controller.dart';
import '../../core/domain/dates.dart';
import '../../core/domain/models.dart';
import '../../core/domain/stats.dart';
import '../../i18n/es.dart';
import '../../shared/utils/numbers.dart';
import '../../shared/widgets/async_view.dart';
import '../../shared/widgets/chart_cards.dart';

/// Página «Peso»: resumen, gráfico de grasa corporal y registro de entradas.
class PesoScreen extends StatefulWidget {
  const PesoScreen({super.key});

  @override
  State<PesoScreen> createState() => _PesoScreenState();
}

class _PesoScreenState extends State<PesoScreen> {
  List<WeightEntry>? _weights;
  Object? _error;

  Api get _api => appAuthController!.api;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _weights = null;
      _error = null;
    });
    try {
      final weights = await _api.listWeights();
      if (!mounted) return;
      setState(() => _weights = weights);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = e);
    }
  }

  Future<void> _openForm([WeightEntry? entry]) async {
    final result = await showDialog<({DateTime measuredAt, double weightKg, double? bodyFatPct, String? note})>(
      context: context,
      builder: (context) => _WeightFormDialog(entry: entry),
    );
    if (result == null || !mounted) return;
    try {
      if (entry != null) {
        await _api.updateWeight(entry.id, WeightEntry.fromJson({
          'id': entry.id,
          'measuredAt': result.measuredAt.toUtc().toIso8601String(),
          'weightKg': result.weightKg,
          if (result.bodyFatPct != null) 'bodyFatPct': result.bodyFatPct,
          if (result.note != null) 'note': result.note,
        }));
      } else {
        await _api.createWeight(WeightEntry.fromJson({
          'id': '',
          'measuredAt': result.measuredAt.toUtc().toIso8601String(),
          'weightKg': result.weightKg,
          if (result.bodyFatPct != null) 'bodyFatPct': result.bodyFatPct,
          if (result.note != null) 'note': result.note,
        }));
      }
      await _load();
    } on ApiException {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(S.common.errorGeneric)));
    }
  }

  Future<void> _confirmDelete(WeightEntry entry) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(S.peso.deleteConfirmTitle),
        content: Text(S.peso.deleteConfirmBody),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text(S.peso.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            style: FilledButton.styleFrom(backgroundColor: Theme.of(context).colorScheme.error),
            child: Text(S.peso.delete),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    try {
      await _api.deleteWeight(entry.id);
      await _load();
    } on ApiException {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(S.common.errorGeneric)));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(S.peso.title),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openForm(),
        icon: const Icon(Icons.add),
        label: Text(S.peso.addTitle),
      ),
      body: AsyncView(
        loading: _weights == null && _error == null,
        error: _error,
        onRetry: _load,
        builder: () {
          final weights = _weights!;
          if (weights.isEmpty) {
            return EmptyState(message: S.peso.emptyList, icon: Icons.monitor_weight_outlined);
          }
          return _PesoBody(weights: weights, onEdit: _openForm, onDelete: _confirmDelete);
        },
      ),
    );
  }
}

class _PesoBody extends StatelessWidget {
  const _PesoBody({required this.weights, required this.onEdit, required this.onDelete});

  final List<WeightEntry> weights;
  final ValueChanged<WeightEntry> onEdit;
  final ValueChanged<WeightEntry> onDelete;

  @override
  Widget build(BuildContext context) {
    final sorted = List<WeightEntry>.from(weights)
      ..sort((a, b) => a.measuredAt.compareTo(b.measuredAt));
    final withFat = sorted.where((entry) => entry.bodyFatPct != null).toList();

    final weightPoints = sorted
        .map((entry) => SeriesPoint(
              date: toDateKey(entry.measuredAt.toLocal()),
              value: entry.weightKg,
            ))
        .toList();
    final weightTrend = movingAverageByDays(
      sorted
          .map((entry) => DataPoint(
                date: toDateKey(entry.measuredAt.toLocal()),
                value: entry.weightKg,
              ))
          .toList(),
      7,
    );
    final weightSeries = List.generate(
      weightPoints.length,
      (index) => SeriesPoint(
        date: weightPoints[index].date,
        value: weightPoints[index].value,
        trend: weightTrend[index],
      ),
    );
    final bodyFatSeries = withFat
        .map((entry) => SeriesPoint(
              date: toDateKey(entry.measuredAt.toLocal()),
              value: entry.bodyFatPct!,
            ))
        .toList();

    final descending = List<WeightEntry>.from(sorted.reversed);
    final fatWindowStart = addDaysToKey(todayKey(), -6);
    final fatInWindow = withFat
        .where((entry) =>
            toDateKey(entry.measuredAt.toLocal()).compareTo(fatWindowStart) >= 0)
        .toList();
    final changeFat = fatInWindow.length >= 2
        ? fatInWindow.last.bodyFatPct! - fatInWindow.first.bodyFatPct!
        : null;

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
      children: [
        _WeightSummary(weights: sorted, changeBodyFat: changeFat),
        const SizedBox(height: 12),
        DualScaleLineChartCard(
          title: S.peso.weightChartTitle,
          primary: weightSeries,
          secondary: bodyFatSeries,
          primaryUnit: 'kg',
          secondaryUnit: S.peso.bodyFatUnit,
          primaryName: S.stats.scaleWeight,
          secondaryName: S.stats.bodyFatSeries,
        ),
        const SizedBox(height: 16),
        Text(
          fill(S.peso.entriesCount, {'n': '${descending.length}'}),
          style: Theme.of(context).textTheme.titleSmall,
        ),
        const SizedBox(height: 8),
        ...descending.map((entry) => _WeightEntryTile(
              entry: entry,
              onEdit: () => onEdit(entry),
              onDelete: () => onDelete(entry),
            )),
      ],
    );
  }
}

class _WeightSummary extends StatelessWidget {
  const _WeightSummary({required this.weights, required this.changeBodyFat});

  final List<WeightEntry> weights;
  final double? changeBodyFat;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final current = weights.last;
    final lastFatEntry = weights.lastWhereOrNull((entry) => entry.bodyFatPct != null);

    Widget metric(String label, String value, {IconData? icon}) => Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: Theme.of(context).textTheme.bodySmall),
            const SizedBox(height: 4),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (icon != null) ...[
                  Icon(icon, size: 16, color: scheme.primary),
                  const SizedBox(width: 4),
                ],
                Text(value, style: Theme.of(context).textTheme.titleMedium),
              ],
            ),
          ],
        );

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: FittedBox(
          fit: BoxFit.scaleDown,
          alignment: Alignment.centerLeft,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              metric(
                S.peso.currentWeight,
                '${formatNumberEs(current.weightKg, maxDecimals: 1)} kg',
                icon: Icons.monitor_weight_outlined,
              ),
              const SizedBox(width: 32),
              metric(
                S.peso.currentBodyFat,
                lastFatEntry?.bodyFatPct == null
                    ? '—'
                    : '${formatNumberEs(lastFatEntry!.bodyFatPct!, maxDecimals: 1)} %',
                icon: Icons.percent,
              ),
              const SizedBox(width: 32),
              metric(
                S.peso.changeBodyFatPeriod,
                changeBodyFat == null
                    ? '—'
                    : '${changeBodyFat! >= 0 ? '+' : ''}${formatNumberEs(changeBodyFat!, maxDecimals: 1)} %',
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _WeightEntryTile extends StatelessWidget {
  const _WeightEntryTile({
    required this.entry,
    required this.onEdit,
    required this.onDelete,
  });

  final WeightEntry entry;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final when = DateTime.parse(entry.measuredAt.toIso8601String()).toLocal();
    final subtitles = <String>[
      '${formatDateKeyShort(toDateKey(when))} ${formatTime(entry.measuredAt.toIso8601String())}',
      if (entry.note != null && entry.note!.isNotEmpty) entry.note!,
    ];
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        leading: const Icon(Icons.monitor_weight_outlined),
        title: Text(
          entry.bodyFatPct == null
              ? '${formatNumberEs(entry.weightKg, maxDecimals: 1)} kg'
              : fill(S.peso.weightWithFat, {
                  'weight': formatNumberEs(entry.weightKg, maxDecimals: 1),
                  'fat': formatNumberEs(entry.bodyFatPct!, maxDecimals: 1),
                }),
          style: const TextStyle(fontWeight: FontWeight.w600),
        ),
        subtitle: Text(subtitles.join('\n')),
        isThreeLine: subtitles.length > 1,
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              onPressed: onEdit,
              icon: const Icon(Icons.edit_outlined),
              tooltip: S.peso.edit,
            ),
            IconButton(
              onPressed: onDelete,
              icon: const Icon(Icons.delete_outline),
              tooltip: S.peso.delete,
            ),
          ],
        ),
      ),
    );
  }
}

class _WeightFormDialog extends StatefulWidget {
  const _WeightFormDialog({this.entry});

  final WeightEntry? entry;

  @override
  State<_WeightFormDialog> createState() => _WeightFormDialogState();
}

class _WeightFormDialogState extends State<_WeightFormDialog> {
  late final TextEditingController _weight;
  late final TextEditingController _bodyFat;
  late final TextEditingController _note;
  late DateTime _measuredAt;
  String? _error;

  @override
  void initState() {
    super.initState();
    final entry = widget.entry;
    _weight = TextEditingController(text: entry == null ? '' : toDecimalInput(entry.weightKg));
    _bodyFat = TextEditingController(text: entry == null ? '' : toDecimalInput(entry.bodyFatPct));
    _note = TextEditingController(text: entry?.note ?? '');
    _measuredAt = entry?.measuredAt ?? DateTime.now();
  }

  @override
  void dispose() {
    _weight.dispose();
    _bodyFat.dispose();
    _note.dispose();
    super.dispose();
  }

  Future<void> _pickDateTime() async {
    final date = await showDatePicker(
      context: context,
      initialDate: _measuredAt,
      firstDate: DateTime(2000),
      lastDate: DateTime.now().add(const Duration(days: 1)),
    );
    if (date == null || !mounted) return;
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(_measuredAt),
    );
    if (time == null) return;
    setState(() => _measuredAt = DateTime(date.year, date.month, date.day, time.hour, time.minute));
  }

  void _submit() {
    final weightKg = parseDecimal(_weight.text);
    if (weightKg == null || weightKg <= 0) {
      setState(() => _error = S.peso.weightLabel);
      return;
    }
    final bodyFat = parseDecimal(_bodyFat.text);
    if (_bodyFat.text.trim().isNotEmpty && bodyFat == null) {
      setState(() => _error = S.peso.bodyFatLabel);
      return;
    }
    final note = _note.text.trim();
    Navigator.of(context).pop((
      measuredAt: _measuredAt,
      weightKg: weightKg,
      bodyFatPct: bodyFat,
      note: note.isEmpty ? null : note,
    ));
  }

  @override
  Widget build(BuildContext context) {
    final now = _measuredAt;
    return AlertDialog(
      title: Text(widget.entry == null ? S.peso.addTitle : S.peso.edit),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.event),
              title: Text('${formatDateKeyLong(toDateKey(now))} · '
                  '${now.hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')}'),
              subtitle: Text(S.peso.datetimeLabel),
              onTap: _pickDateTime,
              trailing: const Icon(Icons.edit_calendar_outlined),
            ),
            TextField(
              controller: _weight,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: InputDecoration(
                labelText: S.peso.weightLabel,
                suffixText: 'kg',
              ),
              autofocus: true,
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _bodyFat,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: InputDecoration(
                labelText: S.peso.bodyFatLabel,
                hintText: S.peso.bodyFatPlaceholder,
                suffixText: '%',
              ),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _note,
              maxLines: 2,
              decoration: InputDecoration(
                labelText: S.peso.noteLabel,
                alignLabelWithHint: true,
              ),
            ),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
            ],
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(S.peso.cancel),
        ),
        FilledButton(
          onPressed: _submit,
          child: Text(S.peso.save),
        ),
      ],
    );
  }
}

extension _LastWhereOrNull<E> on List<E> {
  E? lastWhereOrNull(bool Function(E element) test) {
    for (var i = length - 1; i >= 0; i--) {
      if (test(this[i])) return this[i];
    }
    return null;
  }
}