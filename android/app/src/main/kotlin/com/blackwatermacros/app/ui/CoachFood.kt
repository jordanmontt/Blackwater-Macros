package com.blackwatermacros.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackwatermacros.app.R
import com.blackwatermacros.app.core.MIN_WEIGHT_SPAN_DAYS
import com.blackwatermacros.app.core.MIN_WEIGH_INS
import com.blackwatermacros.app.core.foodquestions.AnalysisId
import com.blackwatermacros.app.core.foodquestions.FoodAnswer
import com.blackwatermacros.app.core.foodquestions.FoodDetails
import com.blackwatermacros.app.core.foodquestions.FoodQuestionsSpec
import com.blackwatermacros.app.core.foodquestions.GroupLabel
import com.blackwatermacros.app.core.foodquestions.NotEnoughReason
import com.blackwatermacros.app.core.foodquestions.SplitReason

// Food questions on the Coach screen (web: `lib/ai/food-questions.ts` and the panel in `app/coach/page.tsx`).

/** The Methodology section that explains food questions (web `#food-questions`). */
const val FOOD_QUESTIONS_SECTION = "food-questions"

@Composable
private fun day(key: String): String = formatDateWithYear(key)

@Composable
private fun quoted(text: String): String = stringResource(R.string.coach_food_quoted, text)

/** An example question about one of the user's own foods; a general one without any. */
@Composable
internal fun foodExampleQuestion(example: FoodExample): String =
    example.food?.let { stringResource(R.string.coach_food_example, it) } ?: stringResource(R.string.coach_food_example_generic)

/** What the app says itself when there is nothing to compute (web `replyText` / `unclearText`). */
@Composable
internal fun foodReplyText(reply: FoodReply): String = when (reply) {
    is FoodReply.Unclear -> stringResource(R.string.coach_food_unclear, foodExampleQuestion(FoodExample(reply.exampleFood)))
    is FoodReply.Answer -> when (val answer = reply.answer) {
        is FoodAnswer.NoMatch -> buildList {
            add(stringResource(R.string.coach_food_no_match, answer.foods.map { quoted(it) }.joinToString(", "), day(answer.range.from), day(answer.range.to)))
            answer.lastSeen?.let { add(stringResource(R.string.coach_food_last_seen, day(it))) }
            if (answer.similar.isNotEmpty()) add(stringResource(R.string.coach_food_similar, answer.similar.map { quoted(it) }.joinToString(", ")))
        }.joinToString(" ")
        is FoodAnswer.NotEnough -> notEnoughText(answer)
        is FoodAnswer.Result -> ""
    }
}

@Composable
private fun notEnoughText(answer: FoodAnswer.NotEnough): String {
    val rules = FoodQuestionsSpec.rules
    val groups = answer.details.groups
    val at = answer.details.split?.at
    return when {
        answer.reason == NotEnoughReason.SIDE_DAYS && at != null -> stringResource(R.string.coach_food_not_enough_sides, rules.minSideDays, day(at))
        answer.reason == NotEnoughReason.WEIGH_INS && at != null ->
            stringResource(R.string.coach_food_not_enough_weigh_ins, day(at), MIN_WEIGH_INS, MIN_WEIGHT_SPAN_DAYS)
        groups.size == 1 ->
            stringResource(R.string.coach_food_not_enough, rules.minLoggedDays, day(groups[0].from), day(groups[0].to), groups[0].loggedDays)
        else -> stringResource(
            R.string.coach_food_not_enough_groups,
            rules.minLoggedDays,
            groups.map { stringResource(R.string.coach_food_group_count, stringResource(it.label.labelRes()), it.loggedDays) }.joinToString(", "),
        )
    }
}

private fun AnalysisId.titleRes(): Int = when (this) {
    AnalysisId.FOOD_STATS -> R.string.coach_food_analysis_food_stats
    AnalysisId.RANKING -> R.string.coach_food_analysis_ranking
    AnalysisId.DAY_LOG -> R.string.coach_food_analysis_day_log
    AnalysisId.COMPARE -> R.string.coach_food_analysis_compare
    AnalysisId.FOOD_VS_WEIGHT -> R.string.coach_food_analysis_food_vs_weight
}

private fun GroupLabel.labelRes(): Int = when (this) {
    GroupLabel.PERIOD -> R.string.coach_food_label_period
    GroupLabel.WEEKDAYS -> R.string.coach_food_label_weekdays
    GroupLabel.WEEKEND -> R.string.coach_food_label_weekend
    GroupLabel.FIRST -> R.string.coach_food_label_first
    GroupLabel.SECOND -> R.string.coach_food_label_second
    GroupLabel.BEFORE -> R.string.coach_food_label_before
    GroupLabel.AFTER -> R.string.coach_food_label_after
}

private fun SplitReason.textRes(): Int = when (this) {
    SplitReason.ASKED -> R.string.coach_food_split_asked
    SplitReason.STOPPED -> R.string.coach_food_split_stopped
    SplitReason.STARTED -> R.string.coach_food_split_started
    SplitReason.HALVES -> R.string.coach_food_split_halves
}

/** The lines of «Cómo se calculó», written by the app, never by the model (web `foodDetailsLines`). */
@Composable
internal fun foodDetailsLines(details: FoodDetails): List<String> = buildList {
    add(stringResource(details.analysis.titleRes()))
    for (group in details.groups) {
        add(stringResource(R.string.coach_food_group, stringResource(group.label.labelRes()), day(group.from), day(group.to), group.loggedDays, group.totalDays))
    }
    details.split?.let { add(stringResource(it.reason.textRes(), day(it.at))) }
    if (details.counted.isNotEmpty()) {
        val names = details.counted.joinToString(", ")
        add(
            if (details.moreCounted > 0) stringResource(R.string.coach_food_counted_more, names, details.moreCounted)
            else stringResource(R.string.coach_food_counted, names),
        )
    }
    if (details.withoutAmount > 0 && details.analysis == AnalysisId.FOOD_STATS) add(stringResource(R.string.coach_food_without_amount, details.withoutAmount))
    if (details.few) add(stringResource(R.string.coach_food_few))
    if (details.analysis != AnalysisId.DAY_LOG) add(stringResource(R.string.coach_food_unlogged))
    if (details.analysis == AnalysisId.FOOD_VS_WEIGHT) add(stringResource(R.string.coach_food_association))
}

/** «Cómo se calculó» under an answer about the food log: closed until tapped, with the methodology one tap away. */
@Composable
internal fun FoodDetailsPanel(details: FoodDetails, onOpenMethodology: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.coach_food_how_title), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = muted)
            Spacer(Modifier.width(4.dp))
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = muted)
        }
        if (open) {
            foodDetailsLines(details).forEach { line ->
                Text(line, style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.padding(bottom = 4.dp))
            }
            TextButton(onClick = onOpenMethodology) { Text(stringResource(R.string.coach_food_methodology)) }
        }
    }
}
