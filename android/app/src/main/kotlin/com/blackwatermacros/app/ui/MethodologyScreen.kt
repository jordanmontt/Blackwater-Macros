package com.blackwatermacros.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.blackwatermacros.app.R

@Composable
fun MethodologyScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenteredTopAppBar(
                title = stringResource(R.string.methodology),
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
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
                stringResource(R.string.meth_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MethodCard(R.string.meth_scale_title, R.string.meth_scale_body)
            MethodCard(
                R.string.meth_ma_title,
                R.string.meth_ma_body,
                formula = listOf("MA(d) = (w₁ + w₂ + … + wₙ) / n", stringResource(R.string.meth_ma_window)),
            )
            MethodCard(
                R.string.meth_rate_title,
                R.string.meth_rate_body,
                formula = listOf("β = Σ(xᵢ − x̄)(yᵢ − ȳ) / Σ(xᵢ − x̄)²", stringResource(R.string.meth_rate_formula)),
            )
            MethodCard(R.string.meth_weekly_title, R.string.meth_weekly_body)
            MethodCard(R.string.meth_daily_title, R.string.meth_daily_body)
            MethodCard(R.string.meth_protein_title, R.string.meth_protein_body)
            MethodCard(
                R.string.meth_tdee_title,
                R.string.meth_tdee_body,
                formula = listOf(
                    stringResource(R.string.meth_tdee_men),
                    stringResource(R.string.meth_tdee_women),
                    stringResource(R.string.meth_tdee_total),
                ),
            )
            ReferencesCard()
            Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MethodCard(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    formula: List<String>? = null,
) {
    val title = stringResource(titleRes)
    val body: String? = stringResource(bodyRes)
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
            Text(stringResource(R.string.meth_references), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            val refs = listOf(
                "Zheng Y, Burke LE, Danford CA, Ewing LJ, Terry MA, Sereika SM. «Self-weighing in weight management: a systematic literature review». Obesity Reviews. 2015;16(2):124–139.",
                "Walker J. The Hacker's Diet: How to lose weight and hair through stress and poor nutrition. 3rd ed. 2005. Available online (fourmilab.ch).",
                "Montgomery DC, Peck EA, Vining GG. Introduction to Linear Regression Analysis. 6th ed. Hoboken (NJ): Wiley; 2021.",
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