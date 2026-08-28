import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../../core/api/api.dart';
import '../../../core/auth/auth_controller.dart';
import '../../../core/domain/dates.dart';
import '../../../core/domain/models.dart';
import '../../../core/domain/nutrition.dart';
import '../../../i18n/es.dart';
import '../../../shared/utils/numbers.dart';

/// Abre el formulario de comida y devuelve true si se guardó correctamente.
Future<bool> showMealForm(
  BuildContext context, {
  required Meal? meal,
  required String logDate,
}) async {
  final saved = await showDialog<bool>(
    context: context,
    builder: (context) => MealFormDialog(meal: meal, logDate: logDate),
  );
  return saved ?? false;
}

/// Formulario de creación/edición de comida: título, notas, modo de entrada,
/// ingredientes (con totales en vivo) o total manual.
class MealFormDialog extends StatefulWidget {
  const MealFormDialog({super.key, required this.meal, required this.logDate});

  final Meal? meal;
  final String logDate;

  @override
  State<MealFormDialog> createState() => _MealFormDialogState();
}

class _IngredientDraft {
  _IngredientDraft([Ingredient? ingredient])
      : name = TextEditingController(text: ingredient?.name ?? ''),
        quantity = TextEditingController(text: ingredient?.quantity ?? ''),
        calories = TextEditingController(text: _fmt(ingredient?.calories)),
        protein = TextEditingController(text: _fmt(ingredient?.protein)),
        carbs = TextEditingController(text: _fmt(ingredient?.carbs)),
        fat = TextEditingController(text: _fmt(ingredient?.fat));

  final TextEditingController name;
  final TextEditingController quantity;
  final TextEditingController calories;
  final TextEditingController protein;
  final TextEditingController carbs;
  final TextEditingController fat;

  void dispose() {
    name.dispose();
    quantity.dispose();
    calories.dispose();
    protein.dispose();
    carbs.dispose();
    fat.dispose();
  }

  static String? _fmt(double? value) =>
      value == null ? '' : toDecimalInput(value);
}

class _MealFormDialogState extends State<MealFormDialog> {
  static const _modeKey = 'meal_entry_mode';

  late final TextEditingController _title;
  late final TextEditingController _notes;
  late final TextEditingController _totalCalories;
  late final TextEditingController _totalProtein;
  late final TextEditingController _totalCarbs;
  late final TextEditingController _totalFat;
  late EntryMode _entryMode;
  late List<_IngredientDraft> _ingredients;

  bool _pending = false;
  String? _error;

  bool get isEditing => widget.meal != null;

  @override
  void initState() {
    super.initState();
    final meal = widget.meal;
    _title = TextEditingController(text: meal?.title ?? '');
    _notes = TextEditingController(text: meal?.notes ?? '');
    _totalCalories = TextEditingController(text: toDecimalInput(meal?.totalCalories));
    _totalProtein = TextEditingController(text: toDecimalInput(meal?.totalProtein));
    _totalCarbs = TextEditingController(text: toDecimalInput(meal?.totalCarbs));
    _totalFat = TextEditingController(text: toDecimalInput(meal?.totalFat));
    _entryMode = meal?.entryMode ?? EntryMode.totalOnly;
    _ingredients = (meal?.ingredients ?? const <Ingredient>[])
        .map((ingredient) => _IngredientDraft(ingredient))
        .toList();
    _restoreEntryMode();
  }

  Future<void> _restoreEntryMode() async {
    if (widget.meal != null) return;
    final prefs = await SharedPreferences.getInstance();
    final saved = prefs.getString(_modeKey);
    if (saved == null || !mounted) return;
    final mode = EntryMode.values.asNameMap()[saved];
    if (mode != null && mode != _entryMode) {
      setState(() => _entryMode = mode);
    }
  }

  Future<void> _saveEntryMode(EntryMode mode) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_modeKey, mode.name);
  }

  @override
  void dispose() {
    _title.dispose();
    _notes.dispose();
    _totalCalories.dispose();
    _totalProtein.dispose();
    _totalCarbs.dispose();
    _totalFat.dispose();
    for (final draft in _ingredients) {
      draft.dispose();
    }
    super.dispose();
  }

  void _addIngredient() => setState(() => _ingredients.add(_IngredientDraft()));

  void _removeIngredient(int index) {
    setState(() => _ingredients.removeAt(index).dispose());
  }

  Future<void> _submit() async {
    final title = _title.text.trim();
    if (title.isEmpty) {
      setState(() => _error = S.meal.titleLabel);
      return;
    }

    final notes = _notes.text.trim().isEmpty ? null : _notes.text.trim();

    var ingredients = const <Ingredient>[];
    var totalCalories = 0.0;
    var totalProtein = 0.0;
    var totalCarbs = 0.0;
    var totalFat = 0.0;

    if (_entryMode == EntryMode.perIngredient) {
      final parsed = <Ingredient>[];
      for (final draft in _ingredients) {
        final name = draft.name.text.trim();
        if (name.isEmpty) continue;
        parsed.add(Ingredient(
          name: name,
          quantity: draft.quantity.text.trim().isEmpty
              ? null
              : draft.quantity.text.trim(),
          calories: parseDecimal(draft.calories.text),
          protein: parseDecimal(draft.protein.text),
          carbs: parseDecimal(draft.carbs.text),
          fat: parseDecimal(draft.fat.text),
        ));
      }
      if (parsed.isEmpty) {
        setState(() => _error = S.meal.ingredientsLabel);
        return;
      }
      ingredients = List.unmodifiable(parsed);
    } else {
      totalCalories = parseDecimal(_totalCalories.text) ?? 0;
      totalProtein = parseDecimal(_totalProtein.text) ?? 0;
      totalCarbs = parseDecimal(_totalCarbs.text) ?? 0;
      totalFat = parseDecimal(_totalFat.text) ?? 0;
    }

    final payload = Meal.draft(
      logDate: widget.logDate,
      title: title,
      notes: notes,
      entryMode: _entryMode,
      ingredients: ingredients,
      totalCalories: _entryMode == EntryMode.totalOnly ? totalCalories : null,
      totalProtein: _entryMode == EntryMode.totalOnly ? totalProtein : null,
      totalCarbs: _entryMode == EntryMode.totalOnly ? totalCarbs : null,
      totalFat: _entryMode == EntryMode.totalOnly ? totalFat : null,
    );

    setState(() {
      _pending = true;
      _error = null;
    });
    try {
      final api = appAuthController!.api;
      if (isEditing) {
        await api.updateMeal(widget.meal!.id, payload);
      } else {
        await api.createMeal(payload);
      }
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _error = e.message);
    } catch (_) {
      if (!mounted) return;
      setState(() => _error = S.common.errorGeneric);
    } finally {
      if (mounted) {
        setState(() => _pending = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(isEditing ? S.meal.editTitle : S.meal.newTitle),
      content: SizedBox(
        width: 480,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              TextField(
                controller: _title,
                enabled: !_pending,
                decoration: InputDecoration(
                  labelText: S.meal.titleLabel,
                  hintText: S.meal.titlePlaceholder,
                ),
                autofocus: true,
                textCapitalization: TextCapitalization.sentences,
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _notes,
                enabled: !_pending,
                maxLines: 2,
                decoration: InputDecoration(
                  labelText: S.meal.notesLabel,
                  alignLabelWithHint: true,
                ),
              ),
              const SizedBox(height: 16),
              Text(S.meal.modeLabel, style: Theme.of(context).textTheme.bodyMedium),
              const SizedBox(height: 8),
              SegmentedButton<EntryMode>(
                showSelectedIcon: false,
                segments: [
                  ButtonSegment(
                    value: EntryMode.totalOnly,
                    label: Text(S.meal.modeTotalOnly),
                  ),
                  ButtonSegment(
                    value: EntryMode.perIngredient,
                    label: Text(S.meal.modePerIngredient),
                  ),
                ],
                selected: {_entryMode},
                onSelectionChanged: _pending
                    ? null
                    : (selection) {
                        setState(() => _entryMode = selection.first);
                        _saveEntryMode(selection.first);
                      },
              ),
              const SizedBox(height: 8),
              Text(
                _entryMode == EntryMode.perIngredient
                    ? S.meal.modePerIngredientHint
                    : S.meal.modeTotalOnlyHint,
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const SizedBox(height: 16),
              if (_entryMode == EntryMode.perIngredient)
                ..._buildIngredientEditor()
              else
                _buildTotalOnlyEditor(),
              if (_error != null) ...[
                const SizedBox(height: 12),
                Text(
                  _error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ],
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _pending ? null : () => Navigator.of(context).pop(false),
          child: Text(S.meal.cancel),
        ),
        FilledButton(
          onPressed: _pending ? null : _submit,
          child: _pending
              ? const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : Text(S.meal.save),
        ),
      ],
    );
  }

  List<Widget> _buildIngredientEditor() {
    List<Widget> children = [
      Text(S.meal.ingredientsLabel, style: Theme.of(context).textTheme.titleSmall),
      const SizedBox(height: 8),
    ];
    for (var i = 0; i < _ingredients.length; i++) {
      children.add(_IngredientRow(
        key: ObjectKey(_ingredients[i]),
        draft: _ingredients[i],
        enabled: !_pending,
        onRemove: () => _removeIngredient(i),
      ));
      children.add(const SizedBox(height: 8));
    }
    children.add(Align(
      alignment: Alignment.centerLeft,
      child: TextButton.icon(
        onPressed: _pending ? null : _addIngredient,
        icon: const Icon(Icons.add),
        label: Text(S.meal.addIngredient),
      ),
    ));
    children.add(const SizedBox(height: 8));
    children.add(_TotalsPreview(drafts: _ingredients));
    return children;
  }

  Widget _buildTotalOnlyEditor() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(S.meal.mealTotalsLabel, style: Theme.of(context).textTheme.titleSmall),
        const SizedBox(height: 8),
        Row(
          children: [
            Expanded(
              child: TextField(
                controller: _totalCalories,
                enabled: !_pending,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: InputDecoration(
                  labelText: S.hoy.calories,
                  suffixText: S.hoy.kcalUnit,
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: TextField(
                controller: _totalProtein,
                enabled: !_pending,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: InputDecoration(
                  labelText: S.hoy.protein,
                  suffixText: S.hoy.proteinUnit,
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: TextField(
                controller: _totalCarbs,
                enabled: !_pending,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: InputDecoration(
                  labelText: S.hoy.carbs,
                  suffixText: S.hoy.gramUnit,
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: TextField(
                controller: _totalFat,
                enabled: !_pending,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: InputDecoration(
                  labelText: S.hoy.fat,
                  suffixText: S.hoy.gramUnit,
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _IngredientRow extends StatefulWidget {
  const _IngredientRow({
    super.key,
    required this.draft,
    required this.enabled,
    required this.onRemove,
  });

  final _IngredientDraft draft;
  final bool enabled;
  final VoidCallback onRemove;

  @override
  State<_IngredientRow> createState() => _IngredientRowState();
}

class _IngredientRowState extends State<_IngredientRow> {
  @override
  Widget build(BuildContext context) {
    // El estado del draft viene con los controllers ya sincronizados; este
    // widget solo necesita re-renderizarse cuando el padre añade/elimina filas.
    return Card(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          children: [
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: widget.draft.name,
                    enabled: widget.enabled,
                    decoration: InputDecoration(
                      hintText: S.meal.ingredientNamePlaceholder,
                      isDense: true,
                      prefixIcon: Icon(Icons.fastfood_outlined),
                      counterText: '',
                    ),
                  ),
                ),
                IconButton(
                  onPressed: widget.enabled ? widget.onRemove : null,
                  icon: const Icon(Icons.close),
                  tooltip: S.meal.delete,
                ),
              ],
            ),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: widget.draft.quantity,
                    enabled: widget.enabled,
                    decoration: InputDecoration(
                      hintText: S.meal.quantityPlaceholder,
                      isDense: true,
                      counterText: '',
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: TextField(
                    controller: widget.draft.calories,
                    enabled: widget.enabled,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: InputDecoration(
                      hintText: S.meal.caloriesPlaceholder,
                      isDense: true,
                      counterText: '',
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: TextField(
                    controller: widget.draft.protein,
                    enabled: widget.enabled,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: InputDecoration(
                      hintText: S.meal.proteinPlaceholder,
                      isDense: true,
                      counterText: '',
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: widget.draft.carbs,
                    enabled: widget.enabled,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: InputDecoration(
                      hintText: S.meal.carbsPlaceholder,
                      isDense: true,
                      counterText: '',
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: TextField(
                    controller: widget.draft.fat,
                    enabled: widget.enabled,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: InputDecoration(
                      hintText: S.meal.fatPlaceholder,
                      isDense: true,
                      counterText: '',
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                const Expanded(child: SizedBox()),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Total en vivo de los ingredientes introducidos (modo por ingrediente).
class _TotalsPreview extends StatelessWidget {
  const _TotalsPreview({required this.drafts});

  final List<_IngredientDraft> drafts;

  @override
  Widget build(BuildContext context) {
    final ingredients = drafts
        .where((draft) => draft.name.text.trim().isNotEmpty)
        .map((draft) => Ingredient(
              name: draft.name.text,
              calories: parseDecimal(draft.calories.text),
              protein: parseDecimal(draft.protein.text),
              carbs: parseDecimal(draft.carbs.text),
              fat: parseDecimal(draft.fat.text),
            ))
        .toList();
    final totals = resolveMealTotals(EntryMode.perIngredient, ingredients);
    final theme = Theme.of(context);
    return Text(
      '${formatNumberEs(totals.calories, maxDecimals: 0)} kcal · '
      '${formatNumberEs(totals.protein, maxDecimals: 1)} g proteína',
      style: theme.textTheme.bodyMedium!.copyWith(
        color: theme.colorScheme.primary,
        fontWeight: FontWeight.w600,
      ),
    );
  }
}