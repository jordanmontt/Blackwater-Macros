package com.blackwatermacros.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.blackwatermacros.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackwatermacros.app.data.LocalDataSummary

/**
 * Optional account login, pushed from Ajustes → Cuenta. The app never requires
 * it: without an account everything is stored on the phone only.
 */
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onConnected: () -> Unit,
    viewModel: LoginViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is LoginUiState.Connected) onConnected()
    }

    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val busy = state == LoginUiState.Loading

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenteredTopAppBar(
                title = stringResource(R.string.account_title),
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppLogo(size = 88.dp)
            Spacer(Modifier.height(20.dp))
            AppCard(modifier = Modifier.widthIn(max = 400.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.account_login),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.login_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(20.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.login_username)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.login_password)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                    )

                    Spacer(Modifier.height(12.dp))

                    (state as? LoginUiState.Error)?.let {
                        Text(
                            text = loginErrorText(it.error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    Button(
                        onClick = { viewModel.login(username, password) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(stringResource(R.string.login_submit))
                        }
                    }
                }
            }
        }
    }

    (state as? LoginUiState.AskLocalData)?.let { ask ->
        AlertDialog(
            onDismissRequest = viewModel::cancelLocalDataQuestion,
            title = { Text(stringResource(R.string.login_local_data_title)) },
            text = {
                Text(stringResource(R.string.login_local_data_body, describe(ask.login.localData), ask.login.username))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.resolveLocalData(upload = true) }) {
                    Text(stringResource(R.string.login_local_data_upload))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolveLocalData(upload = false) }) {
                    Text(stringResource(R.string.login_local_data_discard), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

/** One line per kind of data, e.g. "• 12 meals". */
@Composable
private fun describe(data: LocalDataSummary): String = listOfNotNull(
    data.meals.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.local_meals, it, it) },
    data.weights.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.local_weights, it, it) },
    data.templates.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.local_templates, it, it) },
).joinToString("\n") { "• $it" }

@Composable
private fun loginErrorText(error: LoginError): String = when (error) {
    LoginError.MissingFields -> stringResource(R.string.login_error_missing)
    LoginError.BadCredentials -> stringResource(R.string.login_error_credentials)
    LoginError.Network -> stringResource(R.string.login_error_network)
    is LoginError.WrongAccount -> stringResource(R.string.login_error_wrong_account, error.expected)
    is LoginError.Server -> stringResource(R.string.login_error_server, error.code)
}
