package com.blackwatermacros.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackwatermacros.app.R
import com.blackwatermacros.app.core.AiProvider
import com.blackwatermacros.app.core.Gender
import com.blackwatermacros.app.core.Goal

/**
 * First launch: welcome with «Iniciar sesión» first,
 * your data, the optional AI key, done. «Saltar» from the second step on.
 */
@Composable
fun OnboardingScreen(
    onLogin: () -> Unit,
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(),
    ai: AiSettingsViewModel = viewModel(),
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    // Back from «Iniciar sesión»: continue after the login.
    LaunchedEffect(Unit) { viewModel.afterLogin() }
    fun finish() {
        viewModel.finish()
        onFinished()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            if (step != OnboardingStep.WELCOME) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.onboarding_step, step.ordinal, OnboardingStep.entries.size - 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (step != OnboardingStep.DONE) {
                        TextButton(onClick = ::finish) { Text(stringResource(R.string.onboarding_skip)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            when (step) {
                OnboardingStep.WELCOME -> WelcomeStep(onLogin = onLogin, onStart = { viewModel.goTo(OnboardingStep.DATA) })
                OnboardingStep.DATA -> DataStep(viewModel)
                OnboardingStep.AI -> AiStep(ai, onBack = { viewModel.goTo(OnboardingStep.DATA) }, onNext = { viewModel.goTo(OnboardingStep.DONE) })
                OnboardingStep.DONE -> DoneStep(aiReady = ai.settings.collectAsStateWithLifecycle().value.ready, onStart = ::finish)
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun WelcomeStep(onLogin: () -> Unit, onStart: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.logo_plate), contentDescription = null, modifier = Modifier.size(112.dp))
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.onboarding_login))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_no_account))
        }
        Spacer(Modifier.height(12.dp))
        CardDescription(stringResource(R.string.onboarding_no_account_hint))
    }
}

@Composable
private fun DataStep(viewModel: OnboardingViewModel) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    var activityOpen by rememberSaveable { mutableStateOf(false) }

    StepTitle(stringResource(R.string.onboarding_data_title), stringResource(R.string.onboarding_data_intro))
    FieldLabel(stringResource(R.string.profile_gender))
    Spacer(Modifier.height(6.dp))
    SegmentedChoice(
        listOf(Gender.MALE to stringResource(R.string.gender_male), Gender.FEMALE to stringResource(R.string.gender_female)),
        form.gender,
    ) { choice -> viewModel.update { it.copy(gender = choice) } }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberInput(stringResource(R.string.profile_birth_year), form.birthYear, "1990", Modifier.weight(1f)) { v -> viewModel.update { it.copy(birthYear = v) } }
        NumberInput(stringResource(R.string.profile_height), form.height, "170", Modifier.weight(1f)) { v -> viewModel.update { it.copy(height = v) } }
        NumberInput(stringResource(R.string.weight_kg), form.weight, "70", Modifier.weight(1f), decimal = true) { v -> viewModel.update { it.copy(weight = v) } }
    }
    Spacer(Modifier.height(16.dp))
    FieldLabel(stringResource(R.string.onboarding_goal))
    Spacer(Modifier.height(6.dp))
    SegmentedChoice(
        listOf(
            Goal.CUT to stringResource(R.string.goal_cut),
            Goal.MAINTAIN to stringResource(R.string.goal_maintain),
            Goal.SURPLUS to stringResource(R.string.goal_surplus),
        ),
        form.goal,
    ) { choice -> viewModel.update { it.copy(goal = choice) } }
    Spacer(Modifier.height(16.dp))
    Row(
        Modifier.fillMaxWidth().clickable { activityOpen = !activityOpen }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.onboarding_activity), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            CardDescription(stringResource(R.string.onboarding_activity_summary, form.gymDays, form.walkingMinutes))
        }
        Icon(if (activityOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
    }
    if (activityOpen) {
        Spacer(Modifier.height(8.dp))
        NumberInput(stringResource(R.string.profile_gym_days), form.gymDays, "0", Modifier.fillMaxWidth()) { v -> viewModel.update { it.copy(gymDays = v) } }
        Spacer(Modifier.height(8.dp))
        NumberInput(stringResource(R.string.profile_gym_minutes), form.sessionMinutes, "60", Modifier.fillMaxWidth()) { v -> viewModel.update { it.copy(sessionMinutes = v) } }
        Spacer(Modifier.height(8.dp))
        NumberInput(stringResource(R.string.profile_walking_minutes), form.walkingMinutes, "30", Modifier.fillMaxWidth()) { v -> viewModel.update { it.copy(walkingMinutes = v) } }
    }
    val valid = form.parsed() != null
    if (form.filled && !valid) {
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onboarding_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(20.dp))
    Button(onClick = viewModel::saveData, enabled = valid && !saving, modifier = Modifier.fillMaxWidth()) {
        if (saving) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(stringResource(R.string.onboarding_next))
    }
    Spacer(Modifier.navigationBarsPadding())
}

@Composable
private fun NumberInput(
    label: String,
    value: String,
    placeholder: String,
    modifier: Modifier,
    decimal: Boolean = false,
    onValue: (String) -> Unit,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, minLines = 2)
        Spacer(Modifier.height(4.dp))
        CompactField(
            value = value,
            onValueChange = onValue,
            placeholder = placeholder,
            decimal = decimal,
            keyboardType = if (decimal) null else KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AiStep(ai: AiSettingsViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val settings by ai.settings.collectAsStateWithLifecycle()
    val test by ai.test.collectAsStateWithLifecycle()
    val key = settings.apiKeys[AiProvider.GEMINI].orEmpty()

    StepTitle(stringResource(R.string.onboarding_ai_title), stringResource(R.string.onboarding_ai_intro))
    FreeKeyGuide(initiallyOpen = true)
    Spacer(Modifier.height(16.dp))
    FieldLabel(stringResource(R.string.onboarding_ai_key))
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CompactField(
            value = key,
            onValueChange = { value ->
                if (settings.provider != AiProvider.GEMINI) ai.setProvider(AiProvider.GEMINI)
                ai.setApiKey(value)
            },
            placeholder = "",
            secret = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = ai::runTest, enabled = key.isNotBlank() && test != AiTestState.Testing) {
            Text(stringResource(if (test == AiTestState.Testing) R.string.ai_testing else R.string.ai_test))
        }
    }
    Spacer(Modifier.height(6.dp))
    when (val state = test) {
        AiTestState.Ok -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ai_test_ok), style = MaterialTheme.typography.bodySmall)
        }
        is AiTestState.Failed -> Text(
            stringResource(state.failure.messageRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        else -> Unit
    }
    Spacer(Modifier.height(12.dp))
    CardDescription(stringResource(R.string.onboarding_ai_other))
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.onboarding_back)) }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onNext) { Text(stringResource(R.string.onboarding_not_now)) }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onNext, enabled = settings.ready) { Text(stringResource(R.string.onboarding_next)) }
    }
}

@Composable
private fun DoneStep(aiReady: Boolean, onStart: () -> Unit) {
    StepTitle(stringResource(R.string.onboarding_done_title), stringResource(R.string.onboarding_done_body))
    if (aiReady) {
        Text(stringResource(R.string.onboarding_done_ai), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
    }
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_start)) }
}
