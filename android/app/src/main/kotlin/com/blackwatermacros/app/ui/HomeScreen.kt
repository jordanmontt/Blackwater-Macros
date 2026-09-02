package com.blackwatermacros.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Logged-in landing screen. Confirms the session and offers logout. The real
 * feature screens (comidas, peso, estadísticas…) arrive in later milestones.
 */
@Composable
fun HomeScreen(
    onLoggedOut: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Blackwater Macros", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        when (state) {
            HomeUiState.Loading -> CircularProgressIndicator()
            is HomeUiState.Error -> Text(
                text = (state as HomeUiState.Error).message,
                color = MaterialTheme.colorScheme.error,
            )
            is HomeUiState.Loaded -> {
                val s = state as HomeUiState.Loaded
                Text("Sesión iniciada: ${s.username}", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = if (s.isAdmin) "Usuario administrador" else "Usuario normal",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            HomeUiState.LoggedOut -> Unit
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Próximamente: comidas, peso y estadísticas.",
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                viewModel.logout()
                onLoggedOut()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cerrar sesión")
        }
    }
}