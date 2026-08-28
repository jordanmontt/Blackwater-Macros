import 'package:flutter/material.dart';

import '../../i18n/es.dart';

/// Página «Metodología»: documentación estática de las fórmulas y referencias.
class MetodologiaScreen extends StatelessWidget {
  const MetodologiaScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(S.metodologia.title)),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
        children: [
          Text(S.metodologia.intro, style: Theme.of(context).textTheme.bodyMedium),
          const SizedBox(height: 12),
          _MethodologyCard(
            icon: Icons.scale_outlined,
            title: S.metodologia.scaleVsTrendTitle,
            children: _paragraph(context, S.metodologia.scaleVsTrendBody),
          ),
          const SizedBox(height: 12),
          _MethodologyCard(
            icon: Icons.data_object,
            title: S.metodologia.movingAvgTitle,
            children: [
              _formulaTitle(context, S.metodologia.movingAvgFormulaLabel),
              const _FormulaBlock(
                children: [
                  Text('MA(d) = (w1 + w2 + … + wn) / n'),
                  Text('W(d) = { entradas con fecha en [d − 6, d] }'),
                ],
              ),
              ..._paragraph(context, S.metodologia.movingAvgBody),
            ],
          ),
          const SizedBox(height: 12),
          _MethodologyCard(
            icon: Icons.trending_up,
            title: S.metodologia.rateTitle,
            children: [
              _formulaTitle(context, S.metodologia.rateFormulaLabel),
              const _FormulaBlock(
                children: [
                  Text('β = Σ(xi − x̄)(yi − ȳ) / Σ(xi − x̄)²'),
                  Text('ritmo = β × 7 [kg/semana]'),
                ],
              ),
              ..._paragraph(context, S.metodologia.rateBody),
            ],
          ),
          const SizedBox(height: 12),
          _MethodologyCard(
            icon: Icons.calendar_view_week_outlined,
            title: S.metodologia.weeklyAvgTitle,
            children: _paragraph(context, S.metodologia.weeklyAvgBody),
          ),
          const SizedBox(height: 12),
          _MethodologyCard(
            icon: Icons.restaurant_outlined,
            title: S.metodologia.nutritionTitle,
            children: _paragraph(context, S.metodologia.nutritionBody),
          ),
          const SizedBox(height: 12),
          _MethodologyCard(
            icon: Icons.fitness_center_outlined,
            title: S.metodologia.proteinRecTitle,
            children: [
              ..._paragraph(context, S.metodologia.proteinRecBody),
              ..._paragraph(context, S.metodologia.proteinRecRanges),
              ..._paragraph(context, S.metodologia.proteinRecFfm),
            ],
          ),
          const SizedBox(height: 12),
          _MethodologyCard(
            icon: Icons.local_fire_department_outlined,
            title: S.metodologia.tdeeTitle,
            children: [
              ..._paragraph(context, S.metodologia.tdeeBody),
              _FormulaBlock(
                children: S.metodologia.tdeeFormula
                    .split('\n')
                    .map((line) => Text(line))
                    .toList(),
              ),
              ..._paragraph(context, S.metodologia.tdeeActivity),
              ..._paragraph(context, S.metodologia.tdeeGoals),
            ],
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(S.metodologia.referencesTitle,
                      style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 8),
                  ..._references(context),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  List<Widget> _paragraph(BuildContext context, String content) {
    return [
      Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Text(
          content,
          style: Theme.of(context).textTheme.bodyMedium!.copyWith(height: 1.5),
        ),
      ),
    ];
  }

  Widget _formulaTitle(BuildContext context, String label) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(label, style: Theme.of(context).textTheme.bodyMedium!.copyWith(fontWeight: FontWeight.w600)),
    );
  }

  List<Widget> _references(BuildContext context) {
    final style = Theme.of(context).textTheme.bodyMedium!.copyWith(height: 1.4);
    List<Widget> item(String text) => [
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('•  ', style: style),
                Expanded(child: Text(text, style: style)),
              ],
            ),
          ),
        ];

    return [
      ...item(
          'Zheng Y, Burke LE, Danford CA, Ewing LJ, Terry MA, Sereika SM. '
          '«Self-weighing in weight management: a systematic literature review». '
          'Obesity Reviews. 2015;16(2):124–139.'),
      ...item(
          'Walker J. «The Hacker\u2019s Diet: How to lose weight and hair through stress '
          'and poor nutrition». 3.ª ed. 2005. Disponible en línea (fourmilab.ch).'),
      ...item(
          'Montgomery DC, Peck EA, Vining GG. «Introduction to Linear Regression Analysis». '
          '6.ª ed. Hoboken (NJ): Wiley; 2021.'),
      ...item(
          'Morton RW et al. A systematic review, meta-analysis and meta-regression of the '
          'effect of protein supplementation on resistance training-induced gains in muscle '
          'mass and strength. Br J Sports Med. 2018;52:376–384. '
          'doi:10.1136/bjsports-2017-097608'),
      ...item(
          'Nunes EA et al. Systematic review and meta-analysis of protein intake to support '
          'muscle mass and function in healthy adults. J Cachexia Sarcopenia Muscle. '
          '2022;13:795–810. doi:10.1002/jcsm.12924'),
      ...item(
          'Kokura Y et al. Enhanced protein intake on maintaining muscle mass, strength, and '
          'physical function in adults with overweight/obesity. Clin Nutr ESPEN. 2024. '
          'doi:10.1016/j.clnesp.2024.01.003'),
      ...item(
          'Helms ER et al. A systematic review of dietary protein during caloric restriction '
          'in resistance trained lean athletes: a case for higher intakes. Int J Sport Nutr '
          'Exerc Metab. 2014;24(2):127–138. doi:10.1123/ijsnem.2013-0054'),
      ...item(
          'Jäger R et al. International Society of Sports Nutrition Position Stand: protein '
          'and exercise. J Int Soc Sports Nutr. 2017;14:20. doi:10.1186/s12970-017-0177-8'),
      ...item(
          'Mifflin MD, St Jeor ST, Hill LA, Scott BJ, Daugherty SA, Koh YO. A new predictive '
          'equation for resting energy expenditure in healthy individuals. Am J Clin Nutr. '
          '1990;51(2):241–247. doi:10.1093/ajcn/51.2.241'),
      ...item(
          'Frankenfield D, Roth-Yousey L, Compher C. Comparison of predictive equations for '
          'resting metabolic rate in healthy nonobese and obese adults: a systematic review. '
          'J Am Diet Assoc. 2005;105(5):775–789. doi:10.1016/j.jada.2005.02.005'),
    ];
  }
}

class _MethodologyCard extends StatelessWidget {
  const _MethodologyCard({
    required this.icon,
    required this.title,
    required this.children,
  });

  final IconData icon;
  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(icon, size: 18, color: scheme.primary),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(title, style: Theme.of(context).textTheme.titleMedium),
                ),
              ],
            ),
            const SizedBox(height: 12),
            ...children,
          ],
        ),
      ),
    );
  }
}

class _FormulaBlock extends StatelessWidget {
  const _FormulaBlock({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.4),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: children
            .map((child) => DefaultTextStyle.merge(
                  style: theme.textTheme.bodySmall!.copyWith(
                    fontFamily: 'monospace',
                    height: 1.6,
                  ),
                  child: child,
                ))
            .toList(),
      ),
    );
  }
}