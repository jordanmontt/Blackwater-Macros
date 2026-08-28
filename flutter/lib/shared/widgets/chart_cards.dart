import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../../core/domain/dates.dart';
import '../../i18n/es.dart';

/// Punto etiquetado de una serie de gráfico; el eje X usa el índice.
class ChartPoint {
  const ChartPoint({required this.label, required this.value});

  final String label;
  final double value;
}

/// Serie con etiquetas + tendencia opcional (misma longitud que [points]).
/// Los valores null de la tendencia (días sin datos al arrancar) se omiten.
class ChartSeries {
  const ChartSeries({required this.points, this.trendValues});

  final List<ChartPoint> points;
  final List<double?>? trendValues;
}

/// Punto crudo (con clave de fecha) para el gráfico combinado de dos series.
class SeriesPoint {
  const SeriesPoint({required this.date, required this.value, this.trend});

  final String date;
  final double value;
  final double? trend;
}

int _labelStep(int count) => (count / 6).ceil().clamp(1, 1 << 30);

/// Crea los items de tooltip de línea: fecha, valor, tendencia (si existe) y
/// media del rango. Devuelve un item por spot (el painter lo exige).
List<LineTooltipItem> _lineTooltipItems(
  List<LineBarSpot> touchedSpots,
  ChartSeries series,
  String unit,
  String seriesName,
  int decimals,
  ThemeData theme,
) {
  final scheme = theme.colorScheme;
  final baseStyle = theme.textTheme.bodySmall!;
  final mutedStyle = baseStyle.copyWith(color: scheme.onSurfaceVariant);
  final avg = series.points.isEmpty
      ? null
      : series.points.map((p) => p.value).reduce((a, b) => a + b) /
          series.points.length;

  String fmt(double v) => formatNumberEs(v, maxDecimals: decimals);

  final items = <LineTooltipItem>[];
  for (final spot in touchedSpots) {
    final index = spot.x.round();
    if (index < 0 || index >= series.points.length) {
      items.add(LineTooltipItem('', baseStyle));
      continue;
    }
    final point = series.points[index];
    if (spot.barIndex == 0) {
      items.add(LineTooltipItem(
        '',
        baseStyle,
        children: [
          TextSpan(
            text: '${point.label}\n',
            style: baseStyle.copyWith(
              fontWeight: FontWeight.w700,
              color: scheme.onSurface,
            ),
          ),
          TextSpan(
            text: '$seriesName: ${fmt(point.value)} $unit\n',
            style: baseStyle,
          ),
          if (avg != null)
            TextSpan(
              text: '${S.stats.tooltipAverage}: ${fmt(avg)} $unit',
              style: mutedStyle,
            ),
        ],
      ));
    } else {
      final value = series.trendValues?[index] ?? point.value;
      items.add(LineTooltipItem(
        '${S.stats.trendLine}: ${fmt(value)} $unit',
        baseStyle.copyWith(color: scheme.tertiary),
      ));
    }
  }
  return items;
}

LineTouchTooltipData _lineTooltipData(
  ChartSeries series,
  String unit,
  String seriesName,
  int decimals,
  ThemeData theme,
) {
  final scheme = theme.colorScheme;
  return LineTouchTooltipData(
    tooltipBorderRadius: BorderRadius.circular(10),
    tooltipBorder: BorderSide(color: scheme.outlineVariant),
    getTooltipColor: (_) => scheme.surface,
    maxContentWidth: 240,
    fitInsideHorizontally: true,
    fitInsideVertically: true,
    getTooltipItems: (spots) =>
        _lineTooltipItems(spots, series, unit, seriesName, decimals, theme),
  );
}

/// Tarjeta con un gráfico de líneas + línea de tendencia opcional. Muestra
/// tooltip (hover/click) con fecha, valor, tendencia y media del rango.
class LineChartCard extends StatelessWidget {
  const LineChartCard({
    super.key,
    required this.title,
    required this.series,
    this.showTrend = false,
    this.maxDecimals = 0,
    this.unit = '',
    this.seriesName,
  });

  final String title;
  final ChartSeries series;
  final bool showTrend;
  final int maxDecimals;
  final String unit;
  final String? seriesName;

  @override
  Widget build(BuildContext context) {
    if (series.points.isEmpty) {
      return _EmptyChartCard(title: title, height: 180);
    }
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final style = theme.textTheme.labelSmall!;

    final spots = List.generate(
      series.points.length,
      (index) => FlSpot(index.toDouble(), series.points[index].value),
    );
    final bars = <LineChartBarData>[
      LineChartBarData(
        spots: spots,
        isCurved: false,
        color: scheme.primary,
        barWidth: 2,
        dotData: const FlDotData(show: false),
      ),
      if (showTrend && series.trendValues != null)
        LineChartBarData(
          spots: series.trendValues!
              .asMap()
              .entries
              .where((entry) => entry.value != null)
              .map((entry) => FlSpot(entry.key.toDouble(), entry.value!))
              .toList(),
          isCurved: false,
          color: scheme.tertiary,
          barWidth: 2,
          dashArray: [6, 4],
          dotData: const FlDotData(show: false),
        ),
    ];

    final step = _labelStep(series.points.length);
    final name = seriesName ?? title;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: theme.textTheme.titleMedium),
            const SizedBox(height: 16),
            SizedBox(
              height: 180,
              child: LineChart(
                LineChartData(
                  minY: _minY(spots),
                  maxY: _maxY(spots),
                  gridData: FlGridData(
                    show: true,
                    drawVerticalLine: false,
                    getDrawingHorizontalLine: (value) => FlLine(
                      color: scheme.outlineVariant.withValues(alpha: 0.4),
                      strokeWidth: 1,
                    ),
                  ),
                  borderData: FlBorderData(show: false),
                  titlesData: FlTitlesData(
                    topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    leftTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    bottomTitles: AxisTitles(
                      sideTitles: SideTitles(
                        showTitles: true,
                        interval: 1,
                        getTitlesWidget: (value, meta) {
                          final index = value.toInt();
                          if (index < 0 ||
                              index >= series.points.length ||
                              index % step != 0) {
                            return const SizedBox.shrink();
                          }
                          return Padding(
                            padding: const EdgeInsets.only(top: 4),
                            child: Text(series.points[index].label, style: style),
                          );
                        },
                      ),
                    ),
                  ),
                  lineTouchData: LineTouchData(
                    touchTooltipData:
                        _lineTooltipData(series, unit, name, maxDecimals, theme),
                  ),
                  lineBarsData: bars,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Tarjeta con un gráfico de barras (p. ej. medias semanales de peso) y
/// tooltip con la etiqueta y el valor de cada barra.
class BarChartCard extends StatelessWidget {
  const BarChartCard({
    super.key,
    required this.title,
    required this.values,
    this.unit = '',
  });

  final String title;
  final List<ChartPoint> values;
  final String unit;

  @override
  Widget build(BuildContext context) {
    if (values.isEmpty) {
      return _EmptyChartCard(title: title);
    }
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final style = theme.textTheme.labelSmall!;
    final step = _labelStep(values.length);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: theme.textTheme.titleMedium),
            const SizedBox(height: 16),
            SizedBox(
              height: 180,
              child: BarChart(
                BarChartData(
                  alignment: BarChartAlignment.spaceAround,
                  barTouchData: BarTouchData(
                    enabled: true,
                    touchTooltipData: BarTouchTooltipData(
                      tooltipBorderRadius: BorderRadius.circular(10),
                      tooltipBorder: BorderSide(color: scheme.outlineVariant),
                      getTooltipColor: (_) => scheme.surface,
                      maxContentWidth: 180,
                      fitInsideHorizontally: true,
                      fitInsideVertically: true,
                      getTooltipItem: (group, groupIndex, rod, rodIndex) {
                        final index = group.x.toInt();
                        if (index < 0 || index >= values.length) {
                          return null;
                        }
                        final point = values[index];
                        final value = formatNumberEs(point.value,
                            maxDecimals: unit == 'kg' ? 1 : 0);
                        return BarTooltipItem(
                          '',
                          theme.textTheme.bodySmall!,
                          children: [
                            TextSpan(
                              text: '${point.label}\n',
                              style: theme.textTheme.bodySmall!.copyWith(
                                fontWeight: FontWeight.w700,
                                color: scheme.onSurface,
                              ),
                            ),
                            TextSpan(
                              text: '$value $unit',
                              style: theme.textTheme.bodySmall!,
                            ),
                          ],
                        );
                      },
                    ),
                  ),
                  gridData: FlGridData(
                    show: true,
                    drawVerticalLine: false,
                    getDrawingHorizontalLine: (value) => FlLine(
                      color: scheme.outlineVariant.withValues(alpha: 0.4),
                      strokeWidth: 1,
                    ),
                  ),
                  borderData: FlBorderData(show: false),
                  titlesData: FlTitlesData(
                    topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    leftTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    bottomTitles: AxisTitles(
                      sideTitles: SideTitles(
                        showTitles: true,
                        getTitlesWidget: (value, meta) {
                          final index = value.toInt();
                          if (index < 0 || index >= values.length || index % step != 0) {
                            return const SizedBox.shrink();
                          }
                          return Padding(
                            padding: const EdgeInsets.only(top: 4),
                            child: Text(values[index].label, style: style),
                          );
                        },
                      ),
                    ),
                  ),
                  barGroups: List.generate(
                    values.length,
                    (index) => BarChartGroupData(
                      x: index,
                      barRods: [
                        BarChartRodData(
                          toY: values[index].value,
                          color: scheme.primary,
                          width: 18,
                          borderRadius: BorderRadius.circular(4),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Gráfico con dos series de unidades distintas (p. ej. peso en kg a la
/// izquierda y grasa corporal en % a la derecha) en la misma tarjeta.
class DualScaleLineChartCard extends StatelessWidget {
  const DualScaleLineChartCard({
    super.key,
    required this.title,
    required this.primary,
    required this.secondary,
    this.primaryUnit = '',
    this.secondaryUnit = '',
    this.primaryName,
    this.secondaryName,
  });

  final String title;
  final List<SeriesPoint> primary;
  final List<SeriesPoint> secondary;
  final String primaryUnit;
  final String secondaryUnit;
  final String? primaryName;
  final String? secondaryName;

  @override
  Widget build(BuildContext context) {
    if (primary.isEmpty && secondary.isEmpty) {
      return _EmptyChartCard(title: title, height: 180);
    }
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final style = theme.textTheme.labelSmall!;
    final isDark = theme.brightness == Brightness.dark;
    final trendColor = isDark ? Colors.blue.shade300 : Colors.blue.shade700;
    final bodyFatColor = isDark ? Colors.red.shade300 : Colors.red.shade600;

    final dates = <String, int>{};
    for (final point in [...primary, ...secondary]) {
      dates.putIfAbsent(point.date, () => dates.length);
    }
    final orderedDates = dates.keys.toList();

    final primarySpots = primary
        .map((point) => FlSpot(dates[point.date]!.toDouble(), point.value))
        .toList();
    final primaryTrendSpots = primary
        .where((point) => point.trend != null)
        .map((point) => FlSpot(dates[point.date]!.toDouble(), point.trend!))
        .toList();

    final wValues = primary.map((point) => point.value).toList();
    final bandValues = wValues.isNotEmpty
        ? wValues
        : secondary.map((point) => point.value).toList();
    var plotMin = bandValues.reduce((a, b) => a < b ? a : b);
    var plotMax = bandValues.reduce((a, b) => a > b ? a : b);
    final pad = (plotMax - plotMin) * 0.1;
    plotMin -= pad;
    plotMax += pad;
    if (plotMin >= plotMax) {
      plotMin = plotMax - 1;
      plotMax = plotMax + 1;
    }
    final plotSpan = plotMax - plotMin;

    final bValues = secondary.map((point) => point.value).toList();
    var bMin = bValues.isEmpty ? null : bValues.reduce((a, b) => a < b ? a : b);
    var bMax = bValues.isEmpty ? null : bValues.reduce((a, b) => a > b ? a : b);

    double? toPlot(double v) {
      if (bMin == null || bMax == null) return null;
      if (bMax == bMin) return (plotMin + plotMax) / 2;
      return plotMin + ((v - bMin) * plotSpan) / (bMax - bMin);
    }

    double? fromPlot(double y) {
      if (bMin == null || bMax == null) return null;
      if (bMax == bMin) return bMin;
      return bMin + ((y - plotMin) * (bMax - bMin)) / plotSpan;
    }

    final secondarySpots = secondary
        .map((point) => FlSpot(dates[point.date]!.toDouble(), toPlot(point.value)!))
        .toList();

    final bars = <LineChartBarData>[
      LineChartBarData(
        spots: primarySpots,
        isCurved: false,
        color: scheme.primary,
        barWidth: 2,
        dotData: const FlDotData(show: false),
      ),
      if (primaryTrendSpots.isNotEmpty)
        LineChartBarData(
          spots: primaryTrendSpots,
          isCurved: false,
          color: trendColor,
          barWidth: 2,
          dashArray: [6, 4],
          dotData: const FlDotData(show: false),
        ),
      if (secondarySpots.isNotEmpty)
        LineChartBarData(
          spots: secondarySpots,
          isCurved: false,
          color: bodyFatColor,
          barWidth: 2,
          dotData: const FlDotData(show: false),
        ),
    ];

    final step = _labelStep(orderedDates.length);
    final axisInterval = plotSpan / 4;

    String withUnit(String name, String unit) =>
        unit.isEmpty ? name : '$name ($unit)';

    final legendItems = <_LegendItemData>[
      _LegendItemData(
        color: scheme.primary,
        label: withUnit(S.stats.legendWeight, primaryUnit),
      ),
      if (primaryTrendSpots.isNotEmpty)
        _LegendItemData(
          color: trendColor,
          label: S.stats.legendTrend,
          dashed: true,
        ),
      if (secondarySpots.isNotEmpty)
        _LegendItemData(
          color: bodyFatColor,
          label: withUnit(secondaryName ?? S.stats.bodyFatSeries, secondaryUnit),
        ),
    ];

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: theme.textTheme.titleMedium),
            const SizedBox(height: 8),
            _ChartLegend(items: legendItems),
            const SizedBox(height: 8),
            SizedBox(
              height: 180,
              child: LineChart(
                LineChartData(
                  minY: plotMin,
                  maxY: plotMax,
                  gridData: FlGridData(
                    show: true,
                    drawVerticalLine: false,
                    getDrawingHorizontalLine: (value) => FlLine(
                      color: scheme.outlineVariant.withValues(alpha: 0.4),
                      strokeWidth: 1,
                    ),
                  ),
                  borderData: FlBorderData(show: false),
                  titlesData: FlTitlesData(
                    topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    rightTitles: AxisTitles(
                      sideTitles: SideTitles(
                        showTitles: secondary.isNotEmpty,
                        interval: axisInterval,
                        reservedSize: 40,
                        getTitlesWidget: (value, meta) {
                          final inverted = fromPlot(value.toDouble());
                          if (inverted == null) return const SizedBox.shrink();
                          return Padding(
                            padding: const EdgeInsets.only(left: 4),
                            child: Text(
                              '${formatNumberEs(inverted, maxDecimals: 1)}$secondaryUnit',
                              style: style,
                            ),
                          );
                        },
                      ),
                    ),
                    leftTitles: AxisTitles(
                      sideTitles: SideTitles(
                        showTitles: true,
                        interval: axisInterval,
                        reservedSize: 40,
                        getTitlesWidget: (value, meta) => Padding(
                          padding: const EdgeInsets.only(right: 4),
                          child: Text(
                            '${formatNumberEs(value.toDouble(), maxDecimals: 1)}$primaryUnit',
                            style: style,
                          ),
                        ),
                      ),
                    ),
                    bottomTitles: AxisTitles(
                      sideTitles: SideTitles(
                        showTitles: true,
                        interval: 1,
                        getTitlesWidget: (value, meta) {
                          final index = value.toInt();
                          if (index < 0 ||
                              index >= orderedDates.length ||
                              index % step != 0) {
                            return const SizedBox.shrink();
                          }
                          return Padding(
                            padding: const EdgeInsets.only(top: 4),
                            child: Text(
                              formatDateKeyShort(orderedDates[index]),
                              style: style,
                            ),
                          );
                        },
                      ),
                    ),
                  ),
                  lineTouchData: LineTouchData(
                    touchTooltipData: _dualTooltipData(
                      dates,
                      primary,
                      secondary,
                      primaryUnit,
                      secondaryUnit,
                      primaryName ?? S.stats.scaleWeight,
                      secondaryName ?? S.stats.bodyFatSeries,
                      theme,
                    ),
                  ),
                  lineBarsData: bars,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  LineTouchTooltipData _dualTooltipData(
    Map<String, int> dates,
    List<SeriesPoint> primary,
    List<SeriesPoint> secondary,
    String primaryUnit,
    String secondaryUnit,
    String primaryName,
    String secondaryName,
    ThemeData theme,
  ) {
    final scheme = theme.colorScheme;
    final baseStyle = theme.textTheme.bodySmall!;
    final fillerStyle = baseStyle.copyWith(fontSize: 1, height: 0.2);

    String fmt(double v) => formatNumberEs(v, maxDecimals: 1);

    return LineTouchTooltipData(
      tooltipBorderRadius: BorderRadius.circular(10),
      tooltipBorder: BorderSide(color: scheme.outlineVariant),
      getTooltipColor: (_) => scheme.surface,
      maxContentWidth: 220,
      fitInsideHorizontally: true,
      fitInsideVertically: true,
      getTooltipItems: (spots) {
        final primaryByIndex = <int, SeriesPoint>{
          for (final point in primary) dates[point.date]!: point,
        };
        final secondaryByIndex = <int, SeriesPoint>{
          for (final point in secondary) dates[point.date]!: point,
        };
        final items = <LineTooltipItem>[];
        var headerShown = false;
        for (final spot in spots) {
          final index = spot.x.round();
          if (spot.barIndex < 2) {
            final point = primaryByIndex[index];
            if (point == null) {
              items.add(LineTooltipItem(' ', fillerStyle));
              continue;
            }
            if (!headerShown) {
              headerShown = true;
              items.add(LineTooltipItem(
                '',
                baseStyle,
                children: [
                  TextSpan(
                    text: '${formatDateKeyShort(point.date)}\n',
                    style: baseStyle.copyWith(
                      fontWeight: FontWeight.w700,
                      color: scheme.onSurface,
                    ),
                  ),
                  TextSpan(
                    text: '$primaryName: ${fmt(point.value)} $primaryUnit',
                    style: baseStyle,
                  ),
                ],
              ));
            } else {
              items.add(LineTooltipItem(' ', fillerStyle));
            }
          } else {
            final point = secondaryByIndex[index];
            items.add(point == null
                ? LineTooltipItem(' ', fillerStyle)
                : LineTooltipItem(
                    '',
                    baseStyle,
                    children: [
                      TextSpan(
                        text:
                            '$secondaryName: ${fmt(point.value)} $secondaryUnit',
                        style: baseStyle,
                      ),
                    ],
                  ));
          }
        }
        return items;
      },
    );
  }
}

class _LegendItemData {
  const _LegendItemData({required this.color, required this.label, this.dashed = false});

  final Color color;
  final String label;
  final bool dashed;
}

class _ChartLegend extends StatelessWidget {
  const _ChartLegend({required this.items});

  final List<_LegendItemData> items;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Wrap(
      spacing: 16,
      runSpacing: 4,
      children: [
        for (final item in items)
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              _LegendSwatch(color: item.color, dashed: item.dashed),
              const SizedBox(width: 6),
              Text(
                item.label,
                style: theme.textTheme.bodySmall!.copyWith(
                  color: item.color,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
      ],
    );
  }
}

class _LegendSwatch extends StatelessWidget {
  const _LegendSwatch({required this.color, required this.dashed});

  final Color color;
  final bool dashed;

  @override
  Widget build(BuildContext context) {
    if (!dashed) {
      return Container(width: 18, height: 3, color: color);
    }
    return SizedBox(
      width: 18,
      height: 3,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final segmentWidth = (constraints.maxWidth - 2) / 3;
          return Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              for (var i = 0; i < 3; i++)
                Container(width: segmentWidth, height: 3, color: color),
            ],
          );
        },
      ),
    );
  }
}

class _EmptyChartCard extends StatelessWidget {
  const _EmptyChartCard({required this.title, this.height = 90});

  final String title;
  final double height;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 16),
            SizedBox(
              height: height,
              child: Center(
                child: Text(
                  S.stats.noData,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

double _minY(List<FlSpot> spots) {
  final ys = spots.map((spot) => spot.y).toList();
  final rawMin = ys.reduce((a, b) => a < b ? a : b);
  final rawMax = ys.reduce((a, b) => a > b ? a : b);
  final pad = (rawMax - rawMin) * 0.1;
  var minY = (rawMin - pad).floorToDouble();
  final maxY = (rawMax + pad).ceilToDouble();
  if (minY >= maxY) minY = maxY - 1;
  return minY;
}

double _maxY(List<FlSpot> spots) {
  final ys = spots.map((spot) => spot.y).toList();
  final rawMin = ys.reduce((a, b) => a < b ? a : b);
  final rawMax = ys.reduce((a, b) => a > b ? a : b);
  final pad = (rawMax - rawMin) * 0.1;
  final minY = (rawMin - pad).floorToDouble();
  return (rawMax + pad).ceilToDouble().clamp(minY + 1, double.infinity).toDouble();
}