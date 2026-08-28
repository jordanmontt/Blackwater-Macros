import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/api/api.dart';
import '../../core/auth/auth_controller.dart';
import '../../core/domain/dates.dart';
import '../../core/domain/models.dart';
import '../../core/domain/stats.dart';
import '../../i18n/es.dart';
import '../../shared/widgets/async_view.dart';
import '../../shared/widgets/chart_cards.dart';

/// Página «Estadísticas»: métricas agregadas y gráficos por rango.
class EstadisticasScreen extends StatefulWidget {
  const EstadisticasScreen({super.key});

  @override
  State<EstadisticasScreen> createState() => _EstadisticasScreenState();
}

class _EstadisticasScreenState extends State<EstadisticasScreen> {
  StatsRange _range = StatsRange.d30;
  StatsSummary? _summary;
  Object? _error;

  Api get _api => appAuthController!.api;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _summary = null;
      _error = null;
    });
    try {
      final summary = await _api.stats(_range, todayKey());
      if (!mounted) return;
      setState(() => _summary = summary);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = e);
    }
  }

  void _selectRange(StatsRange range) {
    if (_range == range) return;
    setState(() => _range = range);
    _load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(S.stats.title),
        actions: [
          IconButton(
            tooltip: S.metodologia.title,
            icon: const Icon(Icons.info_outline),
            onPressed: () => context.push('/metodologia'),
          ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
            child: SegmentedButton<StatsRange>(
              segments: [
                ButtonSegment(value: StatsRange.d7, label: Text(S.stats.range7)),
                ButtonSegment(value: StatsRange.d30, label: Text(S.stats.range30)),
                ButtonSegment(value: StatsRange.d90, label: Text(S.stats.range90)),
                ButtonSegment(value: StatsRange.all, label: Text(S.stats.rangeAll)),
              ],
              selected: {_range},
              showSelectedIcon: false,
              onSelectionChanged: (selection) => _selectRange(selection.first),
            ),
          ),
          Expanded(
            child: AsyncView(
              loading: _summary == null && _error == null,
              error: _error,
              onRetry: _load,
              builder: () => _StatsBody(summary: _summary!),
            ),
          ),
        ],
      ),
    );
  }
}

class _StatsBody extends StatelessWidget {
  const _StatsBody({required this.summary});

  final StatsSummary summary;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
      children: [
        _CompositionSection(summary: summary),
        const SizedBox(height: 12),
        _NutritionSection(summary: summary),
      ],
    );
  }
}

class _CompositionSection extends StatelessWidget {
  const _CompositionSection({required this.summary});

  final StatsSummary summary;

  @override
  Widget build(BuildContext context) {
    final weights = summary.weights;
    final w = summary.weight;

    String fmt(double? value, {int decimals = 1, bool sign = false}) {
      if (value == null) return '—';
      final prefix = sign && value > 0 ? '+' : '';
      return '$prefix${formatNumberEs(value, maxDecimals: decimals)}';
    }

    if (weights.isEmpty) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const _NoDataMessage(),
          const SizedBox(height: 12),
          const DualScaleLineChartCard(
            title: '',
            primary: [],
            secondary: [],
            primaryUnit: 'kg',
            secondaryUnit: '%',
          ),
        ],
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _MiniStatGrid(items: [
          _MiniStatData(label: S.stats.currentWeight, value: fmt(w.currentWeightKg), unit: 'kg'),
          _MiniStatData(label: S.stats.currentTrend, value: fmt(w.currentTrendKg), unit: 'kg'),
          if (summary.bodyFat.isNotEmpty)
            _MiniStatData(label: S.stats.currentBodyFat, value: fmt(w.currentBodyFatPct), unit: '%'),
          _MiniStatData(
            label: S.stats.changeSinceStart,
            value: fmt(w.changeSinceStartKg, sign: true),
            unit: 'kg',
          ),
          _MiniStatData(
            label: S.stats.ratePerWeek,
            value: fmt(w.ratePerWeekKg, decimals: 2, sign: true),
            unit: S.stats.perWeek,
          ),
          if (summary.bodyFat.isNotEmpty)
            _MiniStatData(
              label: S.stats.changeBodyFat,
              value: fmt(w.changeBodyFatPct, sign: true),
              unit: '%',
            ),
        ]),
        const SizedBox(height: 12),
        DualScaleLineChartCard(
          title: S.stats.weightChartTitle,
          primary: _toSeriesPoints(weights),
          secondary: _toSeriesPoints(summary.bodyFat),
          primaryUnit: 'kg',
          secondaryUnit: '%',
        ),
        if (summary.weeklyWeightAvg.isNotEmpty) ...[
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(S.stats.weightWeeklyAvgTitle,
                      style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 8),
                  ...summary.weeklyWeightAvg.map(
                    (week) => Padding(
                      padding: const EdgeInsets.symmetric(vertical: 4),
                      child: Row(
                        children: [
                          Expanded(
                            child: Text(
                              formatDateKeyShort(week.weekStart),
                              style: Theme.of(context).textTheme.bodyMedium,
                            ),
                          ),
                          Text(
                            '${formatNumberEs(week.avg, maxDecimals: 1)} kg',
                            style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                                  fontWeight: FontWeight.w600,
                                ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
        const SizedBox(height: 12),
        SizedBox(
          width: double.infinity,
          child: Text(
            '${S.stats.minWeight}: ${fmt(w.minKg)} kg · ${S.stats.maxWeight}: ${fmt(w.maxKg)} kg'
            '${summary.bodyFat.isEmpty ? '' : ' · ${S.stats.minBodyFat}: ${fmt(w.minBodyFatPct)} % · ${S.stats.maxBodyFat}: ${fmt(w.maxBodyFatPct)} %'}',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ),
      ],
    );
  }
}

class _NutritionSection extends StatelessWidget {
  const _NutritionSection({required this.summary});

  final StatsSummary summary;

  @override
  Widget build(BuildContext context) {
    final hasNutrition = summary.calories.any(
      (point) => point.calories > 0 || point.protein > 0 || point.carbs > 0 || point.fat > 0,
    );

    if (!hasNutrition) {
      return const _NoDataMessage();
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _NutritionSummaryTable(summary: summary),
        const SizedBox(height: 12),
        LineChartCard(
          title: S.stats.caloriesChartTitle,
          series: _nutritionSeries(summary.calories, (point) => point.calories),
          showTrend: true,
          unit: S.hoy.kcalUnit,
          seriesName: S.stats.dailyIntake,
        ),
        const SizedBox(height: 12),
        LineChartCard(
          title: S.stats.proteinChartTitle,
          series: _nutritionSeries(summary.protein, (point) => point.protein),
          showTrend: true,
          maxDecimals: 1,
          unit: S.hoy.gramUnit,
          seriesName: S.stats.dailyIntake,
        ),
        const SizedBox(height: 12),
        LineChartCard(
          title: S.stats.carbsChartTitle,
          series: _nutritionSeries(summary.carbs, (point) => point.carbs),
          showTrend: true,
          maxDecimals: 1,
          unit: S.hoy.gramUnit,
          seriesName: S.stats.dailyIntake,
        ),
        const SizedBox(height: 12),
        LineChartCard(
          title: S.stats.fatChartTitle,
          series: _nutritionSeries(summary.fat, (point) => point.fat),
          showTrend: true,
          maxDecimals: 1,
          unit: S.hoy.gramUnit,
          seriesName: S.stats.dailyIntake,
        ),
      ],
    );
  }
}

class _NutritionSummaryTable extends StatelessWidget {
  const _NutritionSummaryTable({required this.summary});

  final StatsSummary summary;

  static const _right = TextStyle(
    fontWeight: FontWeight.w600,
    fontFeatures: [FontFeature.tabularFigures()],
  );

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final mutedStyle =
        theme.textTheme.bodySmall!.copyWith(color: scheme.onSurfaceVariant);
    final valueStyle = theme.textTheme.bodyMedium!.merge(_right);

    String fmt(double? value, int decimals) =>
        value == null ? '—' : formatNumberEs(value, maxDecimals: decimals);

    Widget cell(String text, TextStyle style,
            {TextAlign align = TextAlign.right}) =>
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 6),
          child: Text(text, style: style, textAlign: align),
        );

    TableRow headerRow() => TableRow(
          children: [
            cell('', mutedStyle, align: TextAlign.left),
            cell(S.stats.avgShort, mutedStyle),
            cell(S.stats.peakShort, mutedStyle),
            cell('', mutedStyle, align: TextAlign.left),
          ],
        );

    TableRow statRow(String label, String avg, String peak, String unit) {
      return TableRow(
        children: [
          cell(label, theme.textTheme.bodyMedium!, align: TextAlign.left),
          cell(avg, valueStyle),
          cell(peak, valueStyle),
          cell(unit, mutedStyle, align: TextAlign.left),
        ],
      );
    }

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Table(
          defaultVerticalAlignment: TableCellVerticalAlignment.middle,
          columnWidths: const {
            0: FlexColumnWidth(2),
            1: FlexColumnWidth(1.2),
            2: FlexColumnWidth(1.2),
            3: FlexColumnWidth(1),
          },
          children: [
            headerRow(),
            statRow(
              S.stats.macroCalories,
              fmt(summary.caloriesAvg, 0),
              fmt(summary.caloriesMaxDay?.calories, 0),
              S.hoy.kcalUnit,
            ),
            statRow(
              S.stats.macroProtein,
              fmt(summary.proteinAvg, 1),
              fmt(summary.proteinMaxDay?.protein, 1),
              S.hoy.gramUnit,
            ),
            statRow(
              S.stats.macroCarbs,
              fmt(summary.carbsAvg, 1),
              fmt(summary.carbsMaxDay?.carbs, 1),
              S.hoy.gramUnit,
            ),
            statRow(
              S.stats.macroFat,
              fmt(summary.fatAvg, 1),
              fmt(summary.fatMaxDay?.fat, 1),
              S.hoy.gramUnit,
            ),
          ],
        ),
      ),
    );
  }
}

class _MiniStatData {
  const _MiniStatData({required this.label, required this.value, required this.unit});

  final String label;
  final String value;
  final String unit;
}

/// Rejilla responsive de MiniStat (2 columnas en móvil, más en pantallas anchas).
class _MiniStatGrid extends StatelessWidget {
  const _MiniStatGrid({required this.items});

  final List<_MiniStatData> items;

  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
        maxCrossAxisExtent: 160,
        mainAxisExtent: 72,
        crossAxisSpacing: 8,
        mainAxisSpacing: 8,
      ),
      itemCount: items.length,
      itemBuilder: (context, index) {
        final item = items[index];
        return Card(
          margin: EdgeInsets.zero,
          child: Padding(
            padding: const EdgeInsets.all(10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  item.label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: 4),
                Text(
                  '${item.value} ${item.unit}'.trim(),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleMedium!.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _NoDataMessage extends StatelessWidget {
  const _NoDataMessage();

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 24),
        child: Text(
          S.stats.noData,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.bodyMedium,
        ),
      ),
    );
  }
}

ChartSeries _nutritionSeries(
  List<DailyNutritionPoint> points,
  double Function(DailyNutritionPoint) pick,
) {
  final chartPoints = points
      .map((point) => ChartPoint(label: formatDateKeyShort(point.date), value: pick(point)))
      .toList();
  final trend = movingAverageByDays(
    points.map((point) => DataPoint(date: point.date, value: pick(point))).toList(),
    7,
  );
  return ChartSeries(points: chartPoints, trendValues: trend);
}

List<SeriesPoint> _toSeriesPoints(List<TrendPoint> points) {
  final allServerTrend =
      points.isNotEmpty && points.every((point) => point.trend != null);
  final trends = allServerTrend
      ? points.map((point) => point.trend).toList()
      : movingAverageByDays(
          points
              .map((point) => DataPoint(date: point.date, value: point.value))
              .toList(),
          7,
        );
  return List.generate(
    points.length,
    (index) => SeriesPoint(
      date: points[index].date,
      value: points[index].value,
      trend: trends[index],
    ),
  );
}