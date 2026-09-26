package com.blackwatermacros.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackwatermacros.app.data.AdminUserDTO

@Composable
fun AdminScreen(
    onBack: () -> Unit,
    viewModel: AdminViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var createOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AdminUserDTO?>(null) }
    var deleting by remember { mutableStateOf<AdminUserDTO?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenteredTopAppBar(
                title = "Administración",
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver a Ajustes",
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
        when (state) {
            AdminUiState.Loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is AdminUiState.Error -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    (state as AdminUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            is AdminUiState.Loaded -> {
                val loaded = state as AdminUiState.Loaded
                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Usuarios",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            androidx.compose.material3.Button(
                                onClick = { createOpen = true },
                            ) {
                                Text("Nuevo usuario")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Crea y gestiona las cuentas de la aplicación.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (loaded.users.isEmpty()) {
                            Text(
                                "Todavía no hay usuarios.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            loaded.users.forEachIndexed { i, user ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                UserRow(
                                    user = user,
                                    isCurrent = user.username == loaded.currentUsername,
                                    onEdit = { editing = user },
                                    onDelete = { deleting = user },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (createOpen) {
        AdminUserDialog(
            title = "Nuevo usuario",
            description = "La cuenta podrá iniciar sesión inmediatamente.",
            initialUsername = "",
            initialIsAdmin = false,
            isNew = true,
            onDismiss = { createOpen = false },
            onSubmit = { username, password, _ ->
                viewModel.create(username, password) { error ->
                    if (error == null) createOpen = false
                }
            },
        )
    }

    editing?.let { user ->
        AdminUserDialog(
            title = "Editar usuario",
            description = "Cambia el nombre, la contraseña o el rol del usuario.",
            initialUsername = user.username,
            initialIsAdmin = user.isAdmin,
            isNew = false,
            editingSelf = user.username == (state as? AdminUiState.Loaded)?.currentUsername,
            onDismiss = { editing = null },
            onSubmit = { username, password, isAdmin ->
                viewModel.update(user, username, password, isAdmin) { error ->
                    if (error == null) editing = null
                }
            },
        )
    }

    deleting?.let { user ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("¿Eliminar usuario?") },
            text = {
                Text("Se borrarán sus comidas, pesajes, plantillas y sesiones. Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(user.id) { error -> if (error == null) deleting = null }
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun UserRow(
    user: AdminUserDTO,
    isCurrent: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.username, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (isCurrent) {
                    Text(
                        " (tú)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (user.isAdmin) {
                    Spacer(Modifier.height(0.dp).size(0.dp))
                    Text(
                        "admin",
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(50),
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "Desde ${formatMemberSince(user.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Editar ${user.username}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        if (!isCurrent) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Eliminar ${user.username}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun AdminUserDialog(
    title: String,
    description: String,
    initialUsername: String,
    initialIsAdmin: Boolean,
    isNew: Boolean,
    editingSelf: Boolean = false,
    onDismiss: () -> Unit,
    onSubmit: (username: String, password: String, isAdmin: Boolean?) -> Unit,
) {
    var username by remember(initialUsername) { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    var isAdmin by remember(initialIsAdmin) { mutableStateOf(initialIsAdmin) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = {
            Column(
                Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.fillMaxWidth()) {
                    FieldLabel("Usuario")
                    Spacer(Modifier.height(4.dp))
                    CompactField(
                        value = username,
                        onValueChange = { username = it },
                        placeholder = "",
                        enabled = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(Modifier.fillMaxWidth()) {
                    FieldLabel(if (isNew) "Contraseña" else "Nueva contraseña (opcional)")
                    Spacer(Modifier.height(4.dp))
                    CompactField(
                        value = password,
                        onValueChange = {
                            val sanitized = it.filterNot { c -> c == '\u0000' }
                            password = sanitized
                        },
                        placeholder = "Mínimo 8 caracteres",
                        enabled = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Es administrador", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = isAdmin,
                        enabled = !editingSelf,
                        onCheckedChange = { isAdmin = it },
                    )
                }
                if (error != null) {
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (username.isBlank()) {
                    error = "El usuario es obligatorio."
                    return@TextButton
                }
                if (isNew && password.length < 8) {
                    error = "La contraseña debe tener al menos 8 caracteres."
                    return@TextButton
                }
                error = null
                onSubmit(username.trim(), password, if (editingSelf) null else isAdmin)
            }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

private fun formatMemberSince(iso: String): String {
    return try {
        val odt = java.time.OffsetDateTime.parse(iso)
        java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale("es", "ES"))
            .format(odt)
    } catch (_: Exception) {
        iso.take(10)
    }
}