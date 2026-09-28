package com.blackwatermacros.app.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * «Comida eliminada · Deshacer»: a long snackbar (web: a 10 s toast) that runs
 * [onUndo] if tapped. Deletes ask first and then offer this, on both apps.
 */
fun CoroutineScope.offerUndo(host: SnackbarHostState, message: String, undoLabel: String, onUndo: () -> Unit) {
    launch {
        host.currentSnackbarData?.dismiss()
        val result = host.showSnackbar(message = message, actionLabel = undoLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) onUndo()
    }
}
