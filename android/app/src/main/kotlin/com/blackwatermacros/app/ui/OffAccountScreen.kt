package com.blackwatermacros.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.BuildConfig
import com.blackwatermacros.app.R
import com.blackwatermacros.app.data.foods.AccountCheck
import com.blackwatermacros.app.data.foods.OffAccount
import com.blackwatermacros.app.data.foods.OwnProducts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OffAccountCheck { IDLE, CHECKING, WRONG, OFFLINE, REQUIRED }

/** Ajustes → Open Food Facts (web `/ajustes/open-food-facts`). */
class OffAccountViewModel(private val products: OwnProducts = AppGraph.ownProducts) : ViewModel() {
    val account: StateFlow<OffAccount?> = products.account.account

    private val _check = MutableStateFlow(OffAccountCheck.IDLE)
    val check: StateFlow<OffAccountCheck> = _check.asStateFlow()

    /** Checks the username and password with Open Food Facts; true when they were saved. */
    fun save(userId: String, password: String, onSaved: () -> Unit) {
        val user = userId.trim()
        if (user.isEmpty() || password.isEmpty()) {
            _check.value = OffAccountCheck.REQUIRED
            return
        }
        _check.value = OffAccountCheck.CHECKING
        viewModelScope.launch {
            when (products.checkAccount(user, password)) {
                AccountCheck.OK -> {
                    products.setAccount(OffAccount(user, password))
                    _check.value = OffAccountCheck.IDLE
                    onSaved()
                }
                AccountCheck.WRONG -> _check.value = OffAccountCheck.WRONG
                AccountCheck.OFFLINE -> _check.value = OffAccountCheck.OFFLINE
            }
        }
    }

    fun signOut() = products.setAccount(null)
}

/**
 * Products added from a label go to Open Food Facts as Blackwater Macros, or
 * with the user's own account: checked with Open Food Facts, then kept on this
 * phone only (password encrypted, not backed up), like the AI keys.
 */
@Composable
fun OffAccountScreen(onBack: () -> Unit, viewModel: OffAccountViewModel = viewModel()) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val check by viewModel.check.collectAsStateWithLifecycle()
    var userId by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackTopAppBar(title = stringResource(R.string.off_account_title), onBack = onBack) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsCard(null) { CardDescription(stringResource(R.string.off_account_description)) }
            SettingsCard(null) {
                val current = account
                if (current != null) {
                    Text(stringResource(R.string.off_account_signed_in, current.userId), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = viewModel::signOut) { Text(stringResource(R.string.off_account_sign_out)) }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldLabel(stringResource(R.string.off_account_username))
                        CompactField(
                            value = userId,
                            onValueChange = { userId = it },
                            placeholder = "",
                            autoCorrect = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FieldLabel(stringResource(R.string.off_account_password))
                        CompactField(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = "",
                            secret = !showPassword,
                            trailingIcon = { PasswordVisibilityToggle(showPassword, onToggle = { showPassword = !showPassword }) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.off_account_kept),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val error = when (check) {
                            OffAccountCheck.WRONG -> R.string.off_account_wrong
                            OffAccountCheck.OFFLINE -> R.string.off_account_offline
                            OffAccountCheck.REQUIRED -> R.string.off_account_required
                            else -> null
                        }
                        if (error != null) {
                            Text(stringResource(error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = {
                                viewModel.save(userId, password) {
                                    userId = ""
                                    password = ""
                                }
                            },
                            enabled = check != OffAccountCheck.CHECKING,
                        ) {
                            if (check == OffAccountCheck.CHECKING) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.off_account_checking))
                            } else {
                                Text(stringResource(R.string.off_account_save))
                            }
                        }
                        TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "${BuildConfig.OFF_SERVER}/cgi/user.pl".toUri())) }
                        }) {
                            Text(stringResource(R.string.off_account_create))
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, Modifier.size(16.dp))
                        }
                    }
                }
            }
            SettingsCard(null) { CardDescription(stringResource(R.string.off_account_photos_note)) }
        }
    }
}
