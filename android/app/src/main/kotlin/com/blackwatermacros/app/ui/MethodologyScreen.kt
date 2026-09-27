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
            MethodCard(R.string.meth_daily_title, R.string.meth_daily_body)
            MethodCard(R.string.meth_protein_title, R.string.meth_protein_body)
            MethodCard(
                R.string.meth_tdee_title,
                R.string.meth_tdee_body,
                formula = listOf(
                    stringResource(R.string.meth_tdee_men),
                    stringResource(R.string.meth_tdee_women),
                    stringResource(R.string.meth_tdee_gym),
                    stringResource(R.string.meth_tdee_pal),
                    stringResource(R.string.meth_tdee_total),
                ),
                afterRes = R.string.meth_tdee_after,
            )
            MethodCard(
                R.string.meth_measured_title,
                R.string.meth_measured_body,
                formula = listOf(
                    stringResource(R.string.meth_measured_window),
                    stringResource(R.string.meth_measured_intake),
                    stringResource(R.string.meth_measured_slope),
                    stringResource(R.string.meth_measured_formula),
                    stringResource(R.string.meth_measured_margin),
                ),
                afterRes = R.string.meth_measured_after,
            )
            MethodCard(R.string.meth_ai_title, R.string.meth_ai_body)
            MethodCard(R.string.meth_sources_title, R.string.meth_sources_body)
            ReferencesCard()
            Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Title, [bodyRes] paragraphs, an optional formula block, then optional [afterRes] paragraphs. */
@Composable
private fun MethodCard(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    formula: List<String>? = null,
    @StringRes afterRes: Int? = null,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Paragraphs(stringResource(bodyRes))
            if (formula != null) {
                FormulaBlock(formula)
            }
            if (afterRes != null) {
                Spacer(Modifier.height(8.dp))
                Paragraphs(stringResource(afterRes))
            }
        }
    }
}

@Composable
private fun Paragraphs(text: String) {
    text.split("\n\n").forEach { paragraph ->
        Text(
            paragraph,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
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
            // Same list and order as REFERENCES in the web src/app/metodologia/page.tsx.
            val refs = listOf(
                "Zheng Y, Klem ML, Sereika SM, Danford CA, Ewing LJ, Burke LE. Self-weighing in weight management: a systematic literature review. Obesity. 2015;23(2):256–265. doi:10.1002/oby.20946",
                "Walker J. The Hacker's Diet: how to lose weight and hair through stress and poor nutrition. fourmilab.ch. 2005.",
                "Montgomery DC, Peck EA, Vining GG. Introduction to Linear Regression Analysis. 6th ed. Wiley; 2021.",
                "Mifflin MD, St Jeor ST, Hill LA, Scott BJ, Daugherty SA, Koh YO. A new predictive equation for resting energy expenditure in healthy individuals. Am J Clin Nutr. 1990;51(2):241–247. doi:10.1093/ajcn/51.2.241",
                "Frankenfield D, Roth-Yousey L, Compher C. Comparison of predictive equations for resting metabolic rate in healthy nonobese and obese adults: a systematic review. J Am Diet Assoc. 2005;105(5):775–789. doi:10.1016/j.jada.2005.02.005",
                "FAO/WHO/UNU. Human energy requirements: report of a joint FAO/WHO/UNU expert consultation. FAO Food and Nutrition Technical Report Series 1. Rome: FAO; 2004.",
                "Herrmann SD, Willis EA, Ainsworth BE, et al. 2024 Adult Compendium of Physical Activities: a third update of the energy costs of human activities. J Sport Health Sci. 2024;13(1):6–12. doi:10.1016/j.jshs.2023.10.010",
                "Prince SA, Adamo KB, Hamel ME, Hardt J, Connor Gorber S, Tremblay M. A comparison of direct versus self-report measures for assessing physical activity in adults: a systematic review. Int J Behav Nutr Phys Act. 2008;5:56. doi:10.1186/1479-5868-5-56",
                "Hall KD. What is the required energy deficit per unit weight loss? Int J Obes. 2008;32(3):573–576. doi:10.1038/sj.ijo.0803720",
                "Jäger R, Kerksick CM, Campbell BI, et al. International Society of Sports Nutrition Position Stand: protein and exercise. J Int Soc Sports Nutr. 2017;14:20. doi:10.1186/s12970-017-0177-8",
                "Morton RW, Murphy KT, McKellar SR, et al. A systematic review, meta-analysis and meta-regression of the effect of protein supplementation on resistance training-induced gains in muscle mass and strength in healthy adults. Br J Sports Med. 2018;52(6):376–384. doi:10.1136/bjsports-2017-097608",
                "Iraki J, Fitschen P, Espinar S, Helms E. Nutrition recommendations for bodybuilders in the off-season: a narrative review. Sports. 2019;7(7):154. doi:10.3390/sports7070154",
                "Helms ER, Aragon AA, Fitschen PJ. Evidence-based recommendations for natural bodybuilding contest preparation: nutrition and supplementation. J Int Soc Sports Nutr. 2014;11:20. doi:10.1186/1550-2783-11-20",
                "Helms ER, Zinn C, Rowlands DS, Brown SR. A systematic review of dietary protein during caloric restriction in resistance trained lean athletes: a case for higher intakes. Int J Sport Nutr Exerc Metab. 2014;24(2):127–138. doi:10.1123/ijsnem.2013-0054",
                "Kokura Y, Ueshima J, Saino Y, Maeda K. Enhanced protein intake on maintaining muscle mass, strength, and physical function in adults with overweight/obesity: a systematic review and meta-analysis. Clin Nutr ESPEN. 2024;63:417–426. doi:10.1016/j.clnesp.2024.06.030",
                "Nunes EA, Colenso-Semple L, McKellar SR, et al. Systematic review and meta-analysis of protein intake to support muscle mass and function in healthy adults. J Cachexia Sarcopenia Muscle. 2022;13(2):795–810. doi:10.1002/jcsm.12922",
                "Gallagher D, Heymsfield SB, Heo M, Jebb SA, Murgatroyd PR, Sakamoto Y. Healthy percentage body fat ranges: an approach for developing guidelines based on body mass index. Am J Clin Nutr. 2000;72(3):694–701. doi:10.1093/ajcn/72.3.694",
                "McClave SA, Taylor BE, Martindale RG, et al. Guidelines for the provision and assessment of nutrition support therapy in the adult critically ill patient (SCCM and A.S.P.E.N.). JPEN J Parenter Enteral Nutr. 2016;40(2):159–211. doi:10.1177/0148607115621863",
                "Byrne NM, Hills AP, Hunter GR, Weinsier RL, Schutz Y. Metabolic equivalent: one size does not fit all. J Appl Physiol. 2005;99(3):1112–1119. doi:10.1152/japplphysiol.00023.2004",
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