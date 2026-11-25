package com.blackwatermacros.app.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blackwatermacros.app.R

/**
 * A bottom sheet that holds something the user typed (the meal form, «Añadir
 * comida»). With nothing to lose it behaves like any sheet: swipe down, tap
 * outside or Back closes it. While [hasUnsavedInput], the content never moves
 * the sheet — scrolling a long form only scrolls it — and every way of closing
 * (tap outside, Back, a swipe on the handle, the content's Cancel through
 * `requestDismiss`) asks «Discard changes?» first.
 *
 * Why not just veto the swipe with `confirmValueChange`: the sheet still follows
 * the content's scroll, gets vetoed every frame and springs back while the finger
 * keeps pulling, which made a long form jump up and down. Turning the sheet's
 * gestures off while there is input avoids that fight altogether.
 *
 * The same while [contentScrolls] (content taller than the screen): a drag on the
 * form went partly to its scroll and partly to the sheet, which lagged the finger
 * and shook up and down (Nov 2026). Then the form only scrolls, and the handle closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardedBottomSheet(
    hasUnsavedInput: Boolean,
    /** What the dialog says will be lost. */
    discardBody: String,
    onDismiss: () -> Unit,
    /** The content is taller than the sheet and scrolls: only the handle drags it closed. */
    contentScrolls: Boolean = false,
    content: @Composable ColumnScope.(requestDismiss: () -> Unit) -> Unit,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    val currentUnsaved by rememberUpdatedState(hasUnsavedInput)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // Reached by a tap outside and by Back (gestures are off while there is input).
        confirmValueChange = { value ->
            if (value == SheetValue.Hidden && currentUnsaved) {
                confirmDiscard = true
                false
            } else {
                true
            }
        },
    )
    val requestDismiss: () -> Unit = {
        if (currentUnsaved) {
            confirmDiscard = true
        } else {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = !hasUnsavedInput && !contentScrolls,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { GuardedDragHandle(guarded = hasUnsavedInput || contentScrolls, onSwipeDown = requestDismiss) },
    ) {
        content(requestDismiss)
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            text = discardBody,
            confirmLabel = stringResource(R.string.discard),
            cancelLabel = stringResource(R.string.keep_editing),
            onConfirm = {
                confirmDiscard = false
                onDismiss()
            },
            onCancel = { confirmDiscard = false },
        )
    }
}

/**
 * The usual handle. While [guarded] the sheet itself does not drag, so a swipe
 * down on the handle closes it instead (asking first if there is input, as on iOS).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuardedDragHandle(guarded: Boolean, onSwipeDown: () -> Unit) {
    val threshold = with(LocalDensity.current) { 24.dp.toPx() }
    var dragged by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta -> dragged += delta }
    Box(
        Modifier
            .fillMaxWidth()
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                enabled = guarded,
                onDragStarted = { dragged = 0f },
                onDragStopped = { velocity -> if (dragged > threshold || (dragged > 0f && velocity > 0f)) onSwipeDown() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BottomSheetDefaults.DragHandle()
    }
}
