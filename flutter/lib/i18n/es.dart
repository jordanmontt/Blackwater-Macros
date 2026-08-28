/// TODO el texto visible de la app (español), espejo tipado de `src/i18n/es.ts`.
library;

/// Sustituye `{clave}` por los valores dados: `fill("Hola {n}", {'n': '3'})`.
String fill(String pattern, Map<String, Object> values) {
  var result = pattern;
  values.forEach((key, value) {
    result = result.replaceAll('{$key}', value.toString());
  });
  return result;
}

class S {
  static const appName = 'Blackwater Macros';

  static const nav = NavStrings();
  static const auth = AuthStrings();
  static const hoy = HoyStrings();
  static const meal = MealStrings();
  static const peso = PesoNames();
  static const stats = StatsStrings();
  static const protein = ProteinStrings();
  static const calorias = CaloriasStrings();
  static const metodologia = MetodologiaStrings();
  static const ajustes = AjustesStrings();
  static const common = CommonStrings();
}

class NavStrings {
  const NavStrings();
  final hoy = 'Comidas';
  final peso = 'Peso';
  final estadisticas = 'Estadísticas';
  final ajustes = 'Ajustes';
}

class AuthStrings {
  const AuthStrings();
  final loginTitle = 'Iniciar sesión';
  final loginDescription = 'Accede a tu registro de comidas y peso.';
  final username = 'Usuario';
  final password = 'Contraseña';
  final submit = 'Entrar';
  final invalidCredentials = 'Usuario o contraseña incorrectos.';
  final userNotFound = 'El usuario no existe.';
  final invalidPassword = 'Contraseña incorrecta.';
  final genericError = 'No se pudo iniciar sesión. Inténtalo de nuevo.';
  final logout = 'Cerrar sesión';
}

class HoyStrings {
  const HoyStrings();
  final title = 'Comidas';
  final addMeal = 'Añadir comida';
  final emptyDay = 'Todavía no has registrado ninguna comida este día.';
  final dailyTotals = 'Totales del día';
  final calories = 'Calorías';
  final protein = 'Proteína';
  final carbs = 'Carbohidratos';
  final fat = 'Grasa';
  final kcalUnit = 'kcal';
  final proteinUnit = 'g';
  final proteinUnitLabel = 'g proteína';
  final gramUnit = 'g';
  final applyTemplate = 'Aplicar plantilla';
  final applyingTemplate = 'Añadiendo…';
  final templateApplied = '«{name}» añadido a este día';
  final noTemplates = 'Aún no tienes plantillas guardadas.';
  final templates = 'Plantillas';
}

class MealStrings {
  const MealStrings();
  final newTitle = 'Nueva comida';
  final editTitle = 'Editar comida';
  final titleLabel = 'Título';
  final titlePlaceholder = 'Desayuno';
  final notesLabel = 'Notas (opcional)';
  final modeLabel = '¿Cómo mides la nutrición?';
  final modePerIngredient = 'Por ingredientes';
  final modePerIngredientHint =
      'Introduce las calorías, proteína, carbohidratos y grasa de cada ingrediente y se sumarán solas.';
  final modeTotalOnly = 'Total manual';
  final modeTotalOnlyHint =
      'Introduce únicamente el total de calorías, proteína, carbohidratos y grasa de la comida.';
  final ingredientsLabel = 'Ingredientes';
  final ingredientNamePlaceholder = '4 huevos';
  final quantityPlaceholder = 'Cantidad (ej. 30-40 g)';
  final caloriesPlaceholder = 'kcal';
  final proteinPlaceholder = 'g proteína';
  final carbsPlaceholder = 'g carbohidratos';
  final fatPlaceholder = 'g grasa';
  final addIngredient = 'Añadir ingrediente';
  final mealTotalsLabel = 'Totales de la comida';
  final save = 'Guardar';
  final cancel = 'Cancelar';
  final saveAsTemplate = 'Guardar como plantilla';
  final templateNameLabel = 'Nombre de la plantilla';
  final delete = 'Eliminar';
  final edit = 'Editar';
  final reorder = 'Reordenar comida';
  final deleteConfirmTitle = '¿Eliminar comida?';
  final deleteConfirmBody =
      'Se borrará esta comida y sus ingredientes. Esta acción no se puede deshacer.';
  final perIngredientSummary = '{n} ingredientes';
  final totalOnlyBadge = 'Total manual';
}

class PesoNames {
  const PesoNames();
  final title = 'Peso';
  final addTitle = 'Registrar peso';
  final weightLabel = 'Peso (kg)';
  final bodyFatLabel = 'Grasa corporal (%)';
  final bodyFatPlaceholder = 'Ej. 15';
  final datetimeLabel = 'Fecha y hora';
  final nowButton = 'Ahora';
  final noteLabel = 'Nota (opcional)';
  final save = 'Guardar';
  final cancel = 'Cancelar';
  final edit = 'Editar';
  final delete = 'Eliminar';
  final emptyList = 'Todavía no has registrado ningún peso.';
  final deleteConfirmTitle = '¿Eliminar registro?';
  final deleteConfirmBody = 'Este registro de peso se borrará permanentemente.';
  final currentWeight = 'Peso actual';
  final currentBodyFat = 'Grasa actual';
  final changeBodyFat = 'Cambio grasa';
  final changeBodyFatPeriod = 'Cambio grasa (7 días)';
  final bodyFatChartTitle = 'Grasa corporal';
  final weightChartTitle = 'Evolución del peso';
  final bodyFatUnit = '%';
  final entriesCount = '{n} registros';
  final weightWithFat = '{weight} kg · {fat}% grasa';
}

class StatsStrings {
  const StatsStrings();
  final title = 'Estadísticas';
  final range7 = '7 días';
  final range30 = '30 días';
  final range90 = '90 días';
  final rangeAll = 'Todo';
  final caloriesChartTitle = 'Calorías diarias';
  final proteinChartTitle = 'Proteína diaria';
  final carbsChartTitle = 'Carbohidratos diarios';
  final fatChartTitle = 'Grasa diaria';
  final dailyIntake = 'Registro diario';
  final weightChartTitle = 'Evolución del peso y grasa corporal';
  final weightWeeklyAvgTitle = 'Media semanal del peso';
  final trendLine = 'Tendencia (media 7 días)';
  final scaleWeight = 'Peso en báscula';
  final bodyFatSeries = 'Grasa corporal';
  final weightAndBodyFatTitle = 'Peso y grasa corporal';
  final legendWeight = 'Peso';
  final legendTrend = 'Tendencia';
  final tooltipAverage = 'Media (rango)';
  final avgShort = 'Media';
  final peakShort = 'Día pico';
  final macroCalories = 'Calorías';
  final macroProtein = 'Proteína';
  final macroCarbs = 'Carbohidratos';
  final macroFat = 'Grasa';
  final caloriesAvg = 'Media de calorías';
  final caloriesMax = 'Día pico';
  final proteinAvg = 'Media de proteína';
  final proteinMax = 'Día pico';
  final carbsAvg = 'Media de carbohidratos';
  final carbsMax = 'Día pico';
  final fatAvg = 'Media de grasa';
  final fatMax = 'Día pico';
  final currentWeight = 'Peso actual';
  final currentTrend = 'Tendencia actual';
  final changeSinceStart = 'Cambio total';
  final ratePerWeek = 'Ritmo semanal';
  final minWeight = 'Mínimo';
  final maxWeight = 'Máximo';
  final minBodyFat = 'Grasa mín';
  final maxBodyFat = 'Grasa máx';
  final perWeek = 'kg/semana';
  final bodyFatChartTitle = 'Evolución de grasa corporal';
  final leanMassChartTitle = 'Evolución de masa libre de grasa';
  final currentBodyFat = 'Grasa actual';
  final changeBodyFat = 'Cambio grasa';
  final currentLeanMass = 'Masa libre actual';
  final changeLeanMass = 'Cambio masa libre';
  final noData = 'Sin datos todavía en este periodo.';
  final weekStart = 'Semana del';
}

class ProteinStrings {
  const ProteinStrings();
  final recommendationTitle = 'Recomendación de proteína';
  final goalLabel = 'Objetivo actual';
  final bwRange = 'Por peso corporal';
  final perKg = '({min} – {max} g/kg)';
  final currentIntake = 'Ingesta de hoy';
  final inRange = 'en rango';
  final belowRange = 'por debajo';
  final aboveRange = 'por encima';
  final noWeight = 'Registra tu peso para ver recomendaciones';
}

class CaloriasStrings {
  const CaloriasStrings();
  final recommendationTitle = 'Recomendación de calorías';
  final noProfile = 'Introduce tus datos para conocer las calorías óptimas';
  final bmr = 'Metabolismo basal (TMB)';
  final tdee = 'Gasto calórico diario estimado (TDEE)';
  final target = 'Consumo objetivo';
  final goalCut = 'Cortar / definir';
  final goalMaintain = 'Mantenimiento';
  final goalSurplus = 'Abultar / superávit';
  final genderLabel = 'Género';
  final genderMale = 'Hombre';
  final genderFemale = 'Mujer';
  final birthYearLabel = 'Año de nacimiento';
  final heightLabel = 'Altura (cm)';
  final gymDaysLabel = 'Días de gimnasio por semana';
  final gymSessionLabel = 'Duración media de la sesión (min)';
  final walkingLabel = 'Tiempo de caminata diario (min)';
  final calorieGoalLabel = 'Objetivo calórico';
  final perDay = 'kcal/día';
  final range = '{min} – {max} kcal';
  final currentIntake = 'Ingesta de hoy';
  final inRange = 'en rango';
  final belowRange = 'por debajo';
  final aboveRange = 'por encima';
}

class MetodologiaStrings {
  const MetodologiaStrings();
  final title = 'Metodología';
  final intro =
      'Cómo se calculan cada una de las métricas que aparecen en Estadísticas, con las fórmulas exactas y las referencias en las que se basan.';
  final scaleVsTrendTitle = 'Peso en báscula vs tendencia';
  final scaleVsTrendBody =
      'El «peso actual» es tu última entrada registrada. La «tendencia actual» es el valor más reciente de la media móvil de 7 días. El «cambio total» compara la primera y la última entrada del rango seleccionado. El «mínimo» y el «máximo» son los valores extremos de tus entradas dentro del rango.';
  final movingAvgTitle = 'Media móvil de 7 días (tendencia)';
  final movingAvgFormulaLabel = 'Para cada día d:';
  final movingAvgBody =
      'Se promedian todas tus entradas cuya fecha cae dentro de la ventana de 7 días que termina en d. Los días sin registro no cuentan ni se rellenan con ceros: si solo pesaste 3 veces esa semana, la media es de esas 3 entradas. Esto atenúa el ruido diario (agua, sal, contenido intestinal) manteniendo la señal real de grasa, que cambia despacio. Es la práctica habitual recomendada en la literatura de auto-pesaje y la base del concepto de «peso de tendencia» popularizado por The Hacker’s Diet.';
  final rateTitle = 'Ritmo semanal (kg/semana)';
  final rateFormulaLabel = 'Pendiente por mínimos cuadrados ordinarios:';
  final rateBody =
      'x son los días transcurridos desde la primera entrada del rango e y el peso registrado. La pendiente β se multiplica por 7 para expresarla por semana. La regresión usa tus entradas crudas del rango visible: captura mejor la dirección a largo plazo que comparar solo dos puntos concretos.';
  final weeklyAvgTitle = 'Media semanal del peso';
  final weeklyAvgBody =
      'Las semanas empiezan el lunes. Se hace la media aritmética de todas las entradas de cada semana; las semanas sin ninguna entrada no aparecen.';
  final nutritionTitle = 'Calorías, proteína, carbohidratos y grasa diarios';
  final nutritionBody =
      'El total de cada día es la suma de tus comidas de ese día (las calorías, proteína, carbohidratos y grasa se calculan al guardar, ya sea sumando ingredientes o tomando tu total manual). Los días sin comidas cuentan como 0: así los huecos reflejan honestamente la adherencia en lugar de desaparecer. La «media» es la media aritmética del rango, el «día pico» el valor máximo, y la línea de tendencia aplica exactamente la misma media móvil de 7 días descrita arriba.';
  final proteinRecTitle = 'Recomendaciones de proteína diaria';
  final proteinRecBody =
      'La cantidad de proteína que necesitas depende de tu objetivo, tu peso y tu composición corporal. La app calcula un rango diario multiplicando tu peso por un factor según tu objetivo.';
  final proteinRecRanges =
      'Mantener músculo (1.2–1.6 g/kg/día): suficiente para la mayoría de personas activas. Abultar / superávit (1.6–2.0 g/kg/día): por encima de 1.6 g/kg los beneficios adicionales empiezan a disminuir, pero hasta 2.0 cubre la variabilidad individual. Cortar / definir (1.6–2.2 g/kg de peso corporal): durante un déficit calórico la proteína ayuda a preservar músculo.';
  final proteinRecFfm = 'La recomendación se calcula exclusivamente sobre el peso corporal total.';
  final tdeeTitle = 'Estimación del gasto calórico diario (TDEE)';
  final tdeeBody =
      'El gasto calórico total se estima en dos pasos: primero se calcula el metabolismo basal (TMB) con la ecuación de Mifflin-St Jeor (1990), considerada la más precisa para personas no deportistas (Frankenfield et al. 2005, 82% de precisión dentro de ±10%). Luego se multiplica por un factor de actividad basado en la frecuencia del gimnasio, la duración de las sesiones y el tiempo de caminata diario.';
  final tdeeFormula =
      'Hombres: TMB = (10 × peso_kg) + (6.25 × altura_cm) − (5 × edad) + 5\nMujeres: TMB = (10 × peso_kg) + (6.25 × altura_cm) − (5 × edad) − 161\nTDEE = TMB × factor de actividad';
  final tdeeActivity =
      'Los factores de actividad van de 1.2 (sedentario) a 1.9 (muy activo). La investigación muestra que la gente tiende a sobreestimar su nivel de actividad, por lo que los umbrales se calibran de forma conservadora.';
  final tdeeGoals =
      'Cortar / definir: TDEE − 400 kcal (rango: −500 a −300). Mantenimiento: TDEE ± 100 kcal. Abultar / superávit: TDEE + 300 kcal (rango: +200 a +400).';
  final referencesTitle = 'Referencias';
}

class AjustesStrings {
  const AjustesStrings();
  final title = 'Ajustes';
  final appearance = 'Apariencia';
  final themeLight = 'Claro';
  final themeDark = 'Oscuro';
  final themeSystem = 'Sistema';
  final exportSection = 'Exportar datos';
  final exportMeals = 'Exportar comidas (CSV)';
  final exportWeights = 'Exportar peso (CSV)';
  final exportHint = 'Descarga todos tus datos en formato CSV.';
  final exportDemoDisabled = 'La exportación está desactivada en el modo demo.';
  final methodologyLink = 'Cómo se calculan las métricas';
  final goalSection = 'Objetivo deportivo';
  final goalDescription =
      'Define tu objetivo deportivo para calcular los rangos de proteína y calorías recomendadas.';
  final goalCut = 'Definir';
  final goalMaintain = 'Mantenimiento';
  final goalSurplus = 'Abultar / superávit';
  final goalMaintainShort = 'Mantener';
  final goalSurplusShort = 'Abultar';
  final proteinRecLabel = 'Proteína recomendada';
  final session = 'Sesión';
  final loggedInAs = 'Sesión iniciada como';
}

class CommonStrings {
  const CommonStrings();
  final today = 'Hoy';
  final loading = 'Cargando…';
  final retry = 'Reintentar';
  final errorGeneric = 'Algo salió mal. Inténtalo de nuevo.';
  final optional = 'opcional';
}