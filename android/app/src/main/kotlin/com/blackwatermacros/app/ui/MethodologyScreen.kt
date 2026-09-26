package com.blackwatermacros.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MethodologyScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenteredTopAppBar(
                title = "Metodología",
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Cómo se calculan cada una de las métricas que aparecen en Estadísticas, con las fórmulas exactas y las referencias en las que se basan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MethodCard(
                title = "Peso en báscula vs tendencia",
                body = "El «peso actual» es tu última entrada registrada. La «tendencia actual» es el valor más reciente de la media móvil de 7 días. El «cambio total» compara la primera y la última entrada del rango seleccionado. El «mínimo» y el «máximo» son los valores extremos de tus entradas dentro del rango.",
            )
            MethodCard(
                title = "Media móvil de 7 días (tendencia)",
                formula = listOf(
                    "MA(d) = (w₁ + w₂ + … + wₙ) / n",
                    "W(d) = { entradas con fecha en [d − 6, d] }",
                ),
                body = "Se promedian todas tus entradas cuya fecha cae dentro de la ventana de 7 días que termina en d. Los días sin registro no cuentan ni se rellenan con ceros: si solo pesaste 3 veces esa semana, la media es de esas 3 entradas. Esto atenúa el ruido diario (agua, sal, contenido intestinal) manteniendo la señal real de grasa, que cambia despacio. Es la práctica habitual recomendada en la literatura de auto-pesaje y la base del concepto de «peso de tendencia» popularizado por The Hacker's Diet.",
            )
            MethodCard(
                title = "Ritmo semanal (kg/semana)",
                formula = listOf(
                    "β = Σ(xᵢ − x̄)(yᵢ − ȳ) / Σ(xᵢ − x̄)²",
                    "ritmo = β × 7 [kg/semana]",
                ),
                body = "x son los días transcurridos desde la primera entrada del rango e y el peso registrado. La pendiente β se multiplica por 7 para expresarla por semana. La regresión usa tus entradas crudas del rango visible: captura mejor la dirección a largo plazo que comparar solo dos puntos concretos.",
            )
            MethodCard(
                title = "Media semanal del peso",
                body = "Las semanas empiezan el lunes. Se hace la media aritmética de todas las entradas de cada semana; las semanas sin ninguna entrada no aparecen.",
            )
            MethodCard(
                title = "Calorías, proteína, carbohidratos y grasa diarios",
                body = "El total de cada día es la suma de tus comidas de ese día (las calorías, proteína, carbohidratos y grasa se calculan al guardar, ya sea sumando ingredientes o tomando tu total manual). Los días sin comidas cuentan como 0: así los huecos reflejan honestamente la adherencia en lugar de desaparecer. La «media» es la media aritmética del rango, el «día pico» el valor máximo, y la línea de tendencia aplica exactamente la misma media móvil de 7 días descrita arriba.",
            )
            MethodCard(
                title = "Recomendaciones de proteína diaria",
                body = "La cantidad de proteína que necesitas depende de tu objetivo, tu peso y tu composición corporal. La app calcula un rango diario multiplicando tu peso por un factor según tu objetivo.\n\nMantener músculo (1.2–1.6 g/kg/día): suficiente para la mayoría de personas activas. Volumen (1.6–2.0 g/kg/día): por encima de 1.6 g/kg los beneficios adicionales empiezan a disminuir, pero hasta 2.0 cubre la variabilidad individual. Definición (1.6–2.2 g/kg de peso corporal): durante un déficit calórico la proteína ayuda a preservar músculo.\n\nLa recomendación se calcula exclusivamente sobre el peso corporal total.",
            )
            MethodCard(
                title = "Estimación del gasto calórico diario (TDEE)",
                formula = listOf(
                    "Hombres: TMB = (10 × peso_kg) + (6.25 × altura_cm) - (5 × edad) + 5",
                    "Mujeres: TMB = (10 × peso_kg) + (6.25 × altura_cm) - (5 × edad) - 161",
                    "TDEE = TMB × factor de actividad",
                ),
                body = "El gasto calórico total se estima en dos pasos: primero se calcula el metabolismo basal (TMB) con la ecuación de Mifflin-St Jeor (1990), considerada la más precisa para personas no deportistas (Frankenfield et al. 2005, 82% de precisión dentro de ±10%). Luego se multiplica por un factor de actividad basado en la frecuencia del gimnasio, la duración de las sesiones y el tiempo de caminata diario.\n\nLos factores de actividad van de 1.2 (sedentario) a 1.9 (muy activo). La investigación muestra que la gente tiende a sobreestimar su nivel de actividad, por lo que los umbrales se calibran de forma conservadora.\n\nDefinición: TDEE − 400 kcal (rango: −500 a −300). Mantenimiento: TDEE ± 100 kcal. Volumen: TDEE + 300 kcal (rango: +200 a +400).",
            )
            ReferencesCard()
            Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MethodCard(
    title: String,
    body: String? = null,
    formula: List<String>? = null,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (body != null) {
                Spacer(Modifier.height(8.dp))
                body.split("\n\n").forEach { paragraph ->
                    Text(
                        paragraph,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            if (formula != null) {
                FormulaBlock(formula)
            }
        }
    }
}

@Composable
private fun FormulaBlock(lines: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        lines.forEachIndexed { i, line ->
            if (i > 0) Spacer(Modifier.height(4.dp))
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ReferencesCard() {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Referencias", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            val refs = listOf(
                "Zheng Y, Burke LE, Danford CA, Ewing LJ, Terry MA, Sereika SM. «Self-weighing in weight management: a systematic literature review». Obesity Reviews. 2015;16(2):124–139.",
                "Walker J. The Hacker's Diet: How to lose weight and hair through stress and poor nutrition. 3.ª ed. 2005. Disponible en línea (fourmilab.ch).",
                "Montgomery DC, Peck EA, Vining GG. Introduction to Linear Regression Analysis. 6.ª ed. Hoboken (NJ): Wiley; 2021.",
                "Morton RW et al. A systematic review, meta-analysis and meta-regression of the effect of protein supplementation on resistance training-induced gains in muscle mass and strength. Br J Sports Med. 2018;52:376–384.",
                "Nunes EA et al. Systematic review and meta-analysis of protein intake to support muscle mass and function in healthy adults. J Cachexia Sarcopenia Muscle. 2022;13:795–810.",
                "Kokura Y et al. Protein supplementation for improving skeletal muscle mass and function in community-dwelling older adults: a systematic review. Clin Nutr ESPEN. 2024.",
                "Helms ER et al. Evidence-based recommendations for natural bodybuilding contest preparation. Int J Sport Nutr Exerc Metab. 2014;24(2):127–138.",
                "Jäger R et al. International Society of Sports Nutrition position stand: protein and exercise. J Int Soc Sports Nutr. 2017;14:20.",
                "Mifflin MD, St Jeor ST, Hill LA, Scott BJ, Daugherty SA, Koh YO. A new predictive equation for resting energy expenditure in healthy individuals. Am J Clin Nutr. 1990;51(2):241–247.",
                "Frankenfield D, Roth-Yousey L, Compher C. Comparison of predictive equations for resting metabolic rate in healthy nonobese and obese adults. J Am Diet Assoc. 2005;105(5):775–789.",
            )
            Column {
                refs.forEachIndexed { i, ref ->
                    if (i > 0) HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    Text(
                        ref,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}