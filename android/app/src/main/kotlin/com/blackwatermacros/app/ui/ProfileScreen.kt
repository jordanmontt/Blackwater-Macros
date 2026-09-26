package com.blackwatermacros.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackwatermacros.app.R
import com.blackwatermacros.app.core.CalorieProfile
import com.blackwatermacros.app.core.Gender
import com.blackwatermacros.app.core.Goal

/**
 * "Perfil" (Ajustes → Perfil): fitness goal, body data and the calorie/protein
 * recommendations derived from them. Same split as the web `/ajustes/perfil`.
 */
@Composable
fun ProfileScreen(onBack: () -> Unit, viewModel: ProfileViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenteredTopAppBar(
                title = stringResource(R.string.profile_title),
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
        Column(Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
            when (val loaded = state) {
                ProfileUiState.Loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is ProfileUiState.Loaded -> {
                    GoalCard(goal = loaded.profile.calorieGoal, onGoalChange = viewModel::updateCalorieGoal)
                    ProfileCard(profile = loaded.profile, onProfileChange = viewModel::updateProfile)
                    RecommendationsCard(loaded)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// --- Goal & profile ---

@Composable
private fun GoalCard(goal: Goal?, onGoalChange: (Goal) -> Unit) {
    SettingsCard(stringResource(R.string.goal_title)) {
        CardDescription(stringResource(R.string.goal_description))
        Spacer(Modifier.height(12.dp))
        SegmentedChoice(
            options = listOf(
                Goal.CUT to stringResource(R.string.goal_cut),
                Goal.MAINTAIN to stringResource(R.string.goal_maintain),
                Goal.SURPLUS to stringResource(R.string.goal_surplus),
            ),
            selected = goal,
            onSelect = onGoalChange,
        )
    }
}

@Composable
private fun ProfileCard(profile: CalorieProfile, onProfileChange: (CalorieProfile) -> Unit) {
    SettingsCard(stringResource(R.string.profile_body_title)) {
        CardDescription(stringResource(R.string.profile_description))
        Spacer(Modifier.height(12.dp))

        FieldLabel(stringResource(R.string.profile_gender))
        Spacer(Modifier.height(6.dp))
        SegmentedChoice(
            options = listOf(
                Gender.MALE to stringResource(R.string.gender_male),
                Gender.FEMALE to stringResource(R.string.gender_female),
            ),
            selected = profile.gender,
            onSelect = { onProfileChange(profile.copy(gender = it)) },
        )
        Spacer(Modifier.height(12.dp))

        ProfileRow {
            NumberField(
                label = stringResource(R.string.profile_birth_year),
                value = profile.birthYear?.toString().orEmpty(),
                placeholder = "1990",
                error = stringResource(R.string.error_birth_year).takeUnless { profile.birthYear.okIn(1920, 2010) },
                onValue = { onProfileChange(profile.copy(birthYear = it.trim().toIntOrNull())) },
            )
            NumberField(
                label = stringResource(R.string.profile_height),
                value = profile.heightCm?.let(::toDecimalInput).orEmpty(),
                error = stringResource(R.string.error_height).takeUnless { profile.heightCm.okIn(100.0, 250.0) },
                onValue = { onProfileChange(profile.copy(heightCm = parseDecimal(it))) },
            )
        }
        ProfileRow {
            NumberField(
                label = stringResource(R.string.profile_gym_days),
                value = profile.gymDaysPerWeek?.toString().orEmpty(),
                error = stringResource(R.string.error_gym_days).takeUnless { profile.gymDaysPerWeek.okIn(0, 7) },
                onValue = { onProfileChange(profile.copy(gymDaysPerWeek = it.trim().toIntOrNull())) },
            )
            NumberField(
                label = stringResource(R.string.profile_gym_minutes),
                value = profile.gymSessionMinutes?.toString().orEmpty(),
                error = stringResource(R.string.error_gym_minutes).takeUnless { profile.gymSessionMinutes.okIn(0, 300) },
                onValue = { onProfileChange(profile.copy(gymSessionMinutes = it.trim().toIntOrNull())) },
            )
        }
        ProfileRow {
            NumberField(
                label = stringResource(R.string.profile_walking_minutes),
                value = profile.walkingMinutesPerDay?.toString().orEmpty(),
                error = stringResource(R.string.error_walking_minutes).takeUnless { profile.walkingMinutesPerDay.okIn(0, 480) },
                onValue = { onProfileChange(profile.copy(walkingMinutesPerDay = it.trim().toIntOrNull())) },
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

private fun <T : Comparable<T>> T?.okIn(min: T, max: T): Boolean = this == null || this in min..max

@Composable
private fun ProfileRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun RowScope.NumberField(
    label: String,
    value: String,
    error: String?,
    placeholder: String = "",
    onValue: (String) -> Unit,
) {
    Column(Modifier.weight(1f).padding(bottom = 12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        CompactField(value = value, onValueChange = onValue, placeholder = placeholder, decimal = true, modifier = Modifier.fillMaxWidth())
        if (error != null) {
            Spacer(Modifier.height(2.dp))
            Text(error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun RecommendationsCard(loaded: ProfileUiState.Loaded) {
    val calorie = loaded.calorieRec
    val protein = loaded.proteinRec
    if (calorie == null && protein == null) return
    val kcalDay = stringResource(R.string.unit_kcal_day)
    val gDay = stringResource(R.string.unit_g_day)
    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (calorie != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RecommendationSection(
                        title = stringResource(R.string.rec_calories_title),
                        icon = Icons.Filled.Whatshot,
                        goal = calorie.goal,
                        rangeValue = "${formatNumber(calorie.targetMin)} – ${formatNumber(calorie.targetMax)}",
                        rangeUnit = kcalDay,
                        detail = stringResource(R.string.rec_estimated_average, "${formatNumber(calorie.target)} $kcalDay"),
                        current = 0.0,
                        rangeMin = calorie.targetMin,
                        rangeMax = calorie.targetMax,
                        showBar = false,
                    )
                    CardDescription(stringResource(R.string.rec_bmr, formatNumber(calorie.bmr)))
                    CardDescription(stringResource(R.string.rec_tdee, formatNumber(calorie.tdee)))
                }
            }
            if (protein != null) {
                RecommendationSection(
                    title = stringResource(R.string.rec_protein_title),
                    icon = Icons.Filled.FitnessCenter,
                    goal = protein.goal,
                    rangeValue = "${formatNumber(protein.bwRange.min)} – ${formatNumber(protein.bwRange.max)}",
                    rangeUnit = gDay,
                    detail = stringResource(R.string.rec_estimated_average, "${formatNumber(protein.target)} $gDay") +
                        " · ${formatNumber(protein.bwPerKg.min, 1)} – ${formatNumber(protein.bwPerKg.max, 1)} g/kg",
                    current = 0.0,
                    rangeMin = protein.bwRange.min,
                    rangeMax = protein.bwRange.max,
                    showBar = false,
                )
            }
        }
    }
}

