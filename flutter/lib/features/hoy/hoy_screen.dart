import 'package:flutter/material.dart';

import '../../core/api/api.dart';
import '../../core/auth/auth_controller.dart';
import '../../core/domain/dates.dart';
import '../../core/domain/models.dart';
import '../../i18n/es.dart';
import '../../shared/widgets/async_view.dart';
import 'widgets/daily_totals_card.dart';
import 'widgets/day_navigator.dart';
import 'widgets/goal_recommendation_cards.dart';
import 'widgets/meal_card.dart';
import 'widgets/meal_form_dialog.dart';

/// Página «Hoy»: navegación entre días, totales, plantillas y lista
/// reordenable de comidas.
class HoyScreen extends StatefulWidget {
  const HoyScreen({super.key});

  @override
  State<HoyScreen> createState() => _HoyScreenState();
}

class _HoyScreenState extends State<HoyScreen> {
  late String _day;
  List<Meal>? _meals;
  List<MealTemplate>? _templates;
  double? _latestWeightKg;
  Object? _error;
  bool _loadingTemplates = true;
  String? _applyingTemplateId;

  Api get _api => appAuthController!.api;

  @override
  void initState() {
    super.initState();
    _day = todayKey();
    _loadTemplates();
    _loadDay();
    _loadLatestWeight();
  }

  Future<void> _loadLatestWeight() async {
    try {
      final weights = await _api.listWeights();
      if (!mounted || weights.isEmpty) return;
      final sorted = List<WeightEntry>.from(weights)
        ..sort((a, b) => a.measuredAt.compareTo(b.measuredAt));
      setState(() => _latestWeightKg = sorted.last.weightKg);
    } catch (_) {
      // Las tarjetas de objetivo simplemente no se pueden calcular.
    }
  }

  Future<void> _loadMeals() async {
    try {
      final meals = await _api.listMeals(_day, _day);
      if (!mounted) return;
      setState(() {
        _meals = meals;
        _error = null;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = e);
    }
  }

  Future<void> _loadDay() async {
    setState(() => _error = null);
    await _loadMeals();
  }

  Future<void> _loadTemplates() async {
    try {
      final templates = await _api.listTemplates();
      if (!mounted) return;
      setState(() {
        _templates = templates;
        _loadingTemplates = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _loadingTemplates = false);
    }
  }

  Future<void> _selectDay(String day) async {
    if (_day == day) return;
    setState(() {
      _day = day;
      _meals = null;
    });
    await _loadMeals();
  }

  Future<void> _openForm([Meal? meal]) async {
    final saved = await showMealForm(context, meal: meal, logDate: _day);
    if (saved && mounted) {
      await _loadMeals();
    }
  }

  Future<void> _confirmDelete(Meal meal) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(S.meal.deleteConfirmTitle),
        content: Text(S.meal.deleteConfirmBody),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text(S.meal.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
            ),
            child: Text(S.meal.delete),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    try {
      await _api.deleteMeal(meal.id);
      await _loadMeals();
    } on ApiException {
      if (!mounted) return;
      _showError(S.common.errorGeneric);
    }
  }

  Future<void> _applyTemplate(MealTemplate template) async {
    setState(() => _applyingTemplateId = template.id);
    try {
      await _api.createMeal(Meal.draft(
        logDate: _day,
        title: template.title,
        notes: template.notes,
        entryMode: EntryMode.perIngredient,
        ingredients: template.ingredients,
      ));
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(
          content: Text(fill(S.hoy.templateApplied, {'name': template.name})),
        ));
      await _loadMeals();
    } on ApiException {
      if (!mounted) return;
      _showError(S.common.errorGeneric);
    } finally {
      if (mounted) {
        setState(() => _applyingTemplateId = null);
      }
    }
  }

  Future<void> _handleReorder(int oldIndex, int newIndex) async {
    final meals = List<Meal>.from(_meals ?? const []);
    final moved = meals.removeAt(oldIndex);
    meals.insert(newIndex, moved);
    setState(() => _meals = meals);
    try {
      await _api.reorderMeals(meals.map((meal) => meal.id).toList());
    } on ApiException {
      if (!mounted) return;
      _showError(S.common.errorGeneric);
    }
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  double _dayCalories(List<Meal> meals) =>
      meals.fold(0.0, (sum, meal) => sum + meal.resolvedCalories);

  double _dayProtein(List<Meal> meals) =>
      meals.fold(0.0, (sum, meal) => sum + meal.resolvedProtein);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(S.hoy.title),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openForm(),
        icon: const Icon(Icons.add),
        label: Text(S.hoy.addMeal),
      ),
      body: Column(
        children: [
          DayNavigator(
            selectedDay: _day,
            onSelect: _selectDay,
            onToday: () => _selectDay(todayKey()),
          ),
          if (_meals != null) ...[
            DailyTotalsCard(meals: _meals!),
            const SizedBox(height: 8),
            if (_day == todayKey())
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: HoyGoalCards(
                  profile: appAuthController!.profile,
                  latestWeightKg: _latestWeightKg,
                  dailyCalories: _dayCalories(_meals!),
                  dailyProtein: _dayProtein(_meals!),
                ),
              ),
          ],
          _TemplateStrip(
            templates: _templates,
            loading: _loadingTemplates,
            applyingId: _applyingTemplateId,
            onApply: _applyTemplate,
          ),
          Expanded(child: _buildMealsList()),
        ],
      ),
    );
  }

  Widget _buildMealsList() {
    if (_error != null) {
      return ErrorView(onRetry: _loadDay, message: S.common.errorGeneric);
    }
    final meals = _meals;
    if (meals == null) {
      return const Center(child: CircularProgressIndicator());
    }
    if (meals.isEmpty) {
      return EmptyState(message: S.hoy.emptyDay, icon: Icons.restaurant_menu);
    }
    return ReorderableListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
      buildDefaultDragHandles: false,
      itemCount: meals.length,
      onReorderItem: _handleReorder,
      itemBuilder: (context, index) {
        final meal = meals[index];
        return Padding(
          key: ValueKey(meal.id),
          padding: const EdgeInsets.only(bottom: 8),
          child: MealCard(
            meal: meal,
            onEdit: () => _openForm(meal),
            onDelete: () => _confirmDelete(meal),
            dragHandle: ReorderableDragStartListener(
              index: index,
              child: const Padding(
                padding: EdgeInsets.only(top: 4),
                child: Icon(Icons.drag_handle, color: Colors.grey),
              ),
            ),
          ),
        );
      },
    );
  }
}

class _TemplateStrip extends StatelessWidget {
  const _TemplateStrip({
    required this.templates,
    required this.loading,
    required this.applyingId,
    required this.onApply,
  });

  final List<MealTemplate>? templates;
  final bool loading;
  final String? applyingId;
  final ValueChanged<MealTemplate> onApply;

  @override
  Widget build(BuildContext context) {
    final list = templates;
    if (loading) {
      return const SizedBox(height: 56, child: Center(child: CircularProgressIndicator(strokeWidth: 2)));
    }
    if (list == null || list.isEmpty) {
      return const SizedBox(height: 8);
    }
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(S.hoy.templates, style: Theme.of(context).textTheme.labelMedium),
          ),
          const SizedBox(height: 4),
          SizedBox(
            height: 40,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              itemCount: list.length,
              separatorBuilder: (context, index) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final template = list[index];
                final applying = applyingId == template.id;
                return ActionChip(
                  avatar: applying
                      ? const SizedBox(
                          width: 14,
                          height: 14,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : null,
                  label: Text(template.name),
                  onPressed: applying ? null : () => onApply(template),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}