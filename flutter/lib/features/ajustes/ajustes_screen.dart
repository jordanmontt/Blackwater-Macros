import 'dart:async';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/api/api.dart';
import '../../core/auth/auth_controller.dart';
import '../../core/domain/calories.dart';
import '../../core/domain/dates.dart';
import '../../core/domain/models.dart';
import '../../core/domain/protein.dart';
import '../../core/theme/theme_controller.dart';
import '../../i18n/es.dart';
import '../../shared/utils/numbers.dart';

/// Página «Ajustes»: tema, objetivo, perfil calórico, exportación, plantillas
/// y cierre de sesión.
class AjustesScreen extends StatefulWidget {
  const AjustesScreen({super.key});

  @override
  State<AjustesScreen> createState() => _AjustesScreenState();
}

class _AjustesScreenState extends State<AjustesScreen> {
  final Map<String, TextEditingController> _fields = {
    for (final key in const ['birthYear', 'heightCm', 'gymDays', 'gymMinutes', 'walking'])
      key: TextEditingController(),
  };

  CalorieProfile _profile = CalorieProfile.empty;
  double? _latestWeight;
  List<MealTemplate> _templates = [];
  String? _username;
  Timer? _saveTimer;
  Map<String, String> _errors = {};
  bool _saving = false;

  Api get _api => appAuthController!.api;

  @override
  void initState() {
    super.initState();
    final auth = appAuthController!;
    _profile = auth.profile;
    _username = auth.username;
    _syncFields();
    _loadInitialData();
  }

  Future<void> _loadInitialData() async {
    try {
      final session = await _api.session();
      if (!mounted) return;
      setState(() {
        _profile = session.profile;
        _username = session.username;
      });
      _syncFields();
    } catch (_) {}
    try {
      final templates = await _api.listTemplates();
      if (!mounted) return;
      setState(() => _templates = templates);
    } catch (_) {}
    try {
      final weights = await _api.listWeights();
      if (!mounted || weights.isEmpty) return;
      final sorted = List<WeightEntry>.from(weights)
        ..sort((a, b) => a.measuredAt.compareTo(b.measuredAt));
      setState(() => _latestWeight = sorted.last.weightKg);
    } catch (_) {}
  }

  @override
  void dispose() {
    _saveTimer?.cancel();
    for (final controller in _fields.values) {
      controller.dispose();
    }
    super.dispose();
  }

  void _syncFields() {
    _fields['birthYear']!.text = _profile.birthYear?.toString() ?? '';
    _fields['heightCm']!.text = _profile.heightCm == null ? '' : toDecimalInput(_profile.heightCm);
    _fields['gymDays']!.text = _profile.gymDaysPerWeek?.toString() ?? '';
    _fields['gymMinutes']!.text = _profile.gymSessionMinutes?.toString() ?? '';
    _fields['walking']!.text = _profile.walkingMinutesPerDay?.toString() ?? '';
  }

  Map<String, String> validateProfile(CalorieProfile profile) {
    final errors = <String, String>{};
    if (profile.birthYear != null && (profile.birthYear! < 1920 || profile.birthYear! > 2010)) {
      errors['birthYear'] = 'El año debe estar entre 1920 y 2010';
    }
    if (profile.heightCm != null && (profile.heightCm! < 100 || profile.heightCm! > 250)) {
      errors['heightCm'] = 'La altura debe estar entre 100 y 250 cm';
    }
    if (profile.gymDaysPerWeek != null && (profile.gymDaysPerWeek! < 0 || profile.gymDaysPerWeek! > 7)) {
      errors['gymDays'] = 'Los días deben ser entre 0 y 7';
    }
    if (profile.gymSessionMinutes != null && (profile.gymSessionMinutes! < 0 || profile.gymSessionMinutes! > 300)) {
      errors['gymMinutes'] = 'La duración debe ser entre 0 y 300 min';
    }
    if (profile.walkingMinutesPerDay != null && (profile.walkingMinutesPerDay! < 0 || profile.walkingMinutesPerDay! > 480)) {
      errors['walking'] = 'El tiempo debe ser entre 0 y 480 min';
    }
    return errors;
  }

  void _updateProfile(CalorieProfile next) {
    setState(() {
      _profile = next;
      _errors = validateProfile(next);
      _saving = true;
    });
    _saveTimer?.cancel();
    if (_errors.isNotEmpty) {
      setState(() => _saving = false);
      return;
    }
    _saveTimer = Timer(const Duration(milliseconds: 500), () async {
      try {
        final saved = await _api.updateSettings(next);
        if (!mounted) return;
        appAuthController!.updateProfile(saved);
        setState(() => _saving = false);
      } on ApiException catch (error) {
        if (!mounted) return;
        setState(() => _saving = false);
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text(error.message)));
      } catch (_) {
        if (!mounted) return;
        setState(() => _saving = false);
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text(S.common.errorGeneric)));
      }
    });
  }

  void _onFieldChange(String key, String input) {
    final intValue = input.trim().isEmpty ? null : int.tryParse(input.trim());
    final doubleValue = input.trim().isEmpty
        ? null
        : double.tryParse(input.trim().replaceAll(',', '.'));
    final copy = CalorieProfile(
      gender: _profile.gender,
      birthYear: key == 'birthYear' ? intValue : _profile.birthYear,
      heightCm: key == 'heightCm' ? doubleValue : _profile.heightCm,
      gymDaysPerWeek: key == 'gymDays' ? intValue : _profile.gymDaysPerWeek,
      gymSessionMinutes: key == 'gymMinutes' ? intValue : _profile.gymSessionMinutes,
      walkingMinutesPerDay: key == 'walking' ? intValue : _profile.walkingMinutesPerDay,
      calorieGoal: _profile.calorieGoal,
    );
    _updateProfile(copy);
  }

  Future<void> _logout() async {
    try {
      await appAuthController!.logout();
    } on ApiException {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(S.common.errorGeneric)));
    }
  }

  Future<void> _deleteTemplate(MealTemplate template) async {
    try {
      await _api.deleteTemplate(template.id);
      if (!mounted) return;
      setState(() => _templates = _templates.where((item) => item.id != template.id).toList());
    } on ApiException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(error.message)));
    }
  }

  Future<void> _exportCsv(String path, String filename) async {
    try {
      final csv = await _api.client.requestText(path);
      final result = await SharePlus.instance.share(ShareParams(
        subject: filename,
        title: filename,
        text: csv,
      ));
      if (result.status != ShareResultStatus.success && mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text('No se pudo guardar $filename')));
      }
    } on ApiException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(error.message)));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(S.common.errorGeneric)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final calorieRec =
        _latestWeight == null ? null : calculateCalorieRecommendation(_profile, _latestWeight!);
    final proteinRec = _latestWeight == null || _profile.calorieGoal == null
        ? null
        : calculateProteinRecommendation(_latestWeight!, _profile.calorieGoal!);

    return Scaffold(
      appBar: AppBar(
        title: Text(S.ajustes.title),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
        children: [
          _AppearanceCard(),
          const SizedBox(height: 12),
          _GoalCard(
            goal: _profile.calorieGoal,
            saving: _saving,
            onSelect: (goal) => _updateProfile(_copyProfile(calorieGoal: goal)),
          ),
          const SizedBox(height: 12),
          _ProfileCard(
            errors: _errors,
            fields: _fields,
            calorieRec: calorieRec,
            proteinRec: proteinRec,
            gender: _profile.gender,
            onGender: (gender) => _updateProfile(_copyProfile(gender: gender)),
            onFieldChange: _onFieldChange,
          ),
          const SizedBox(height: 12),
          _MethodologyCard(),
          const SizedBox(height: 12),
          _ExportCard(onExport: _exportCsv),
          const SizedBox(height: 12),
          _TemplatesCard(templates: _templates, onDelete: _deleteTemplate),
          const SizedBox(height: 12),
          _SessionCard(username: _username, onLogout: _logout),
        ],
      ),
    );
  }

  CalorieProfile _copyProfile({Goal? calorieGoal, Gender? gender}) => CalorieProfile(
        gender: gender ?? _profile.gender,
        birthYear: _profile.birthYear,
        heightCm: _profile.heightCm,
        gymDaysPerWeek: _profile.gymDaysPerWeek,
        gymSessionMinutes: _profile.gymSessionMinutes,
        walkingMinutesPerDay: _profile.walkingMinutesPerDay,
        calorieGoal: calorieGoal ?? _profile.calorieGoal,
      );
}

class _AppearanceCard extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(S.ajustes.appearance, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 12),
            _Segmented<ThemeMode>(
              options: [
                (value: ThemeMode.light, label: S.ajustes.themeLight),
                (value: ThemeMode.dark, label: S.ajustes.themeDark),
                (value: ThemeMode.system, label: S.ajustes.themeSystem),
              ],
              selected: appThemeController?.mode ?? ThemeMode.system,
              onSelect: (mode) => appThemeController?.setMode(mode),
            ),
          ],
        ),
      ),
    );
  }
}

class _GoalCard extends StatelessWidget {
  const _GoalCard({required this.goal, required this.saving, required this.onSelect});

  final Goal? goal;
  final bool saving;
  final ValueChanged<Goal> onSelect;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(S.ajustes.goalSection, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 4),
            Text(S.ajustes.goalDescription, style: Theme.of(context).textTheme.bodySmall),
            const SizedBox(height: 12),
            _Segmented<Goal>(
              options: [
                (value: Goal.cut, label: S.ajustes.goalCut),
                (value: Goal.maintain, label: S.ajustes.goalMaintainShort),
                (value: Goal.surplus, label: S.ajustes.goalSurplusShort),
              ],
              selected: goal ?? Goal.maintain,
              onSelect: onSelect,
            ),
            if (saving) ...[
              const SizedBox(height: 8),
              const LinearProgressIndicator(minHeight: 2),
            ],
          ],
        ),
      ),
    );
  }
}

class _Segmented<T> extends StatelessWidget {
  const _Segmented({
    required this.options,
    required this.selected,
    required this.onSelect,
  });

  final List<({T value, String label})> options;
  final T selected;
  final ValueChanged<T> onSelect;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: SegmentedButton<T>(
        segments: options
            .map((option) => ButtonSegment(value: option.value, label: Text(option.label)))
            .toList(),
        selected: {selected},
        showSelectedIcon: false,
        onSelectionChanged: (selection) => onSelect(selection.first),
      ),
    );
  }
}

class _ProfileCard extends StatelessWidget {
  const _ProfileCard({
    required this.errors,
    required this.fields,
    required this.calorieRec,
    required this.proteinRec,
    required this.gender,
    required this.onGender,
    required this.onFieldChange,
  });

  final Map<String, String> errors;
  final Map<String, TextEditingController> fields;
  final CalorieRecommendation? calorieRec;
  final ProteinRecommendation? proteinRec;
  final Gender? gender;
  final ValueChanged<Gender> onGender;
  final void Function(String key, String input) onFieldChange;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(S.calorias.recommendationTitle, style: theme.textTheme.titleMedium),
            const SizedBox(height: 4),
            Text(S.calorias.noProfile, style: theme.textTheme.bodySmall),
            const SizedBox(height: 12),
            Text(S.calorias.genderLabel, style: theme.textTheme.bodyMedium),
            const SizedBox(height: 6),
            _GenderPicker(selected: gender, onSelect: onGender),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: fields['birthYear'],
                    keyboardType: TextInputType.number,
                    textInputAction: TextInputAction.next,
                    decoration: _decoration(S.calorias.birthYearLabel, errors['birthYear'], '1990'),
                    onChanged: (value) => onFieldChange('birthYear', value),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: TextField(
                    controller: fields['heightCm'],
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    textInputAction: TextInputAction.next,
                    decoration: _decoration(S.calorias.heightLabel, errors['heightCm'], '175', suffix: 'cm'),
                    onChanged: (value) => onFieldChange('heightCm', value),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: fields['gymDays'],
                    keyboardType: TextInputType.number,
                    textInputAction: TextInputAction.next,
                    decoration: _decoration(S.calorias.gymDaysLabel, errors['gymDays'], '3'),
                    onChanged: (value) => onFieldChange('gymDays', value),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: TextField(
                    controller: fields['gymMinutes'],
                    keyboardType: TextInputType.number,
                    textInputAction: TextInputAction.next,
                    decoration: _decoration(S.calorias.gymSessionLabel, errors['gymMinutes'], '60', suffix: 'min'),
                    onChanged: (value) => onFieldChange('gymMinutes', value),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            TextField(
              controller: fields['walking'],
              keyboardType: TextInputType.number,
              decoration: _decoration(S.calorias.walkingLabel, errors['walking'], '30', suffix: 'min'),
              onChanged: (value) => onFieldChange('walking', value),
            ),
            if (calorieRec != null || proteinRec != null) ...[
              const SizedBox(height: 16),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.5),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (calorieRec != null) ...[
                      Text(
                        '${S.calorias.target}: ${formatNumberEs(calorieRec!.target)} ${S.calorias.perDay}',
                        style: theme.textTheme.titleMedium!.copyWith(
                          fontWeight: FontWeight.w700,
                          color: theme.colorScheme.primary,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '${formatNumberEs(calorieRec!.targetMin)} – ${formatNumberEs(calorieRec!.targetMax)} ${S.calorias.perDay}',
                        style: theme.textTheme.bodySmall!.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text.rich(
                        TextSpan(
                          style: theme.textTheme.bodySmall,
                          children: [
                            TextSpan(
                              text:
                                  'TMB: ${formatNumberEs(calorieRec!.bmr)} · TDEE: ',
                            ),
                            TextSpan(
                              text:
                                  '${formatNumberEs(calorieRec!.tdee)} ${S.calorias.perDay}',
                              style: const TextStyle(fontWeight: FontWeight.w700),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 8),
                    ],
                    if (proteinRec != null) ...[
                      Text(S.ajustes.proteinRecLabel, style: theme.textTheme.titleSmall),
                      const SizedBox(height: 4),
                      Text(
                        '${formatNumberEs(proteinRec!.bwRange.min)} – '
                        '${formatNumberEs(proteinRec!.bwRange.max)} g/día',
                        style: theme.textTheme.bodyMedium!.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      Text(
                        '${formatNumberEs(proteinRec!.bwPerKg.min, maxDecimals: 1)} – '
                        '${formatNumberEs(proteinRec!.bwPerKg.max, maxDecimals: 1)} g/kg',
                        style: theme.textTheme.bodySmall!.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  InputDecoration _decoration(String label, String? error, String placeholder,
      {String? suffix}) {
    return InputDecoration(
      labelText: label,
      hintText: placeholder,
      errorText: error,
      suffixText: suffix,
    );
  }
}

class _GenderPicker extends StatelessWidget {
  const _GenderPicker({required this.selected, required this.onSelect});

  final Gender? selected;
  final ValueChanged<Gender> onSelect;

  @override
  Widget build(BuildContext context) {
    return _Segmented<Gender>(
      options: [
        (value: Gender.male, label: S.calorias.genderMale),
        (value: Gender.female, label: S.calorias.genderFemale),
      ],
      selected: selected ?? Gender.male,
      onSelect: onSelect,
    );
  }
}

class _MethodologyCard extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: const Icon(Icons.info_outline),
        title: Text(S.metodologia.title, style: Theme.of(context).textTheme.titleMedium),
        subtitle: Text(S.ajustes.methodologyLink),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => context.push('/metodologia'),
      ),
    );
  }
}

class _ExportCard extends StatelessWidget {
  const _ExportCard({required this.onExport});

  final Future<void> Function(String path, String filename) onExport;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(S.ajustes.exportSection, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 4),
            Text(S.ajustes.exportHint, style: Theme.of(context).textTheme.bodySmall),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => onExport('/api/export/meals.csv', 'comidas.csv'),
                    icon: const Icon(Icons.download),
                    label: Text(S.ajustes.exportMeals),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => onExport('/api/export/weights.csv', 'peso.csv'),
                    icon: const Icon(Icons.download),
                    label: Text(S.ajustes.exportWeights),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _TemplatesCard extends StatelessWidget {
  const _TemplatesCard({required this.templates, required this.onDelete});

  final List<MealTemplate> templates;
  final ValueChanged<MealTemplate> onDelete;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(S.hoy.templates, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            if (templates.isEmpty)
              Text(S.hoy.noTemplates, style: Theme.of(context).textTheme.bodySmall)
            else
              ...templates.map((template) => ListTile(
                    contentPadding: EdgeInsets.zero,
                    dense: true,
                    title: Text(template.name),
                    subtitle: Text(fill(S.meal.perIngredientSummary,
                        {'n': '${template.ingredients.length}'})),
                    trailing: IconButton(
                      icon: const Icon(Icons.delete_outline),
                      tooltip: S.meal.delete,
                      onPressed: () => onDelete(template),
                    ),
                  )),
          ],
        ),
      ),
    );
  }
}

class _SessionCard extends StatelessWidget {
  const _SessionCard({required this.username, required this.onLogout});

  final String? username;
  final VoidCallback onLogout;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(S.ajustes.session, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 4),
            Text(
              '${S.ajustes.loggedInAs} ${username ?? '…'}',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: 12),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                onPressed: onLogout,
                icon: const Icon(Icons.logout),
                label: Text(S.auth.logout),
              ),
            ),
          ],
        ),
      ),
    );
  }
}