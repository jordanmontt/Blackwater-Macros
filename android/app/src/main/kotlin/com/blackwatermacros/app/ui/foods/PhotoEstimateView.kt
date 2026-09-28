package com.blackwatermacros.app.ui.foods

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blackwatermacros.app.R
import com.blackwatermacros.app.core.EstimateConfidence
import com.blackwatermacros.app.core.MealEstimate
import com.blackwatermacros.app.data.ai.MAX_PHOTOS
import com.blackwatermacros.app.ui.CompactTextArea
import com.blackwatermacros.app.ui.FieldLabel
import com.blackwatermacros.app.ui.messageRes

/**
 * «Foto» in «Añadir comida» (§4.1): tips, up to five photos from the camera or
 * the gallery, a description, «Estimar macros». The estimate goes to the
 * review form through the sheet ([PhotoEstimateState.Done]).
 */
@Composable
fun PhotoEstimateView(
    viewModel: PhotoEstimateViewModel,
    onOpenAiSettings: () -> Unit,
    onManual: (() -> Unit)?,
) {
    val context = LocalContext.current
    val ready by viewModel.aiReady.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onDevice by viewModel.onDevice.collectAsStateWithLifecycle()

    if (!ready) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.photo_not_configured), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenAiSettings) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.photo_configure))
                }
                if (onManual != null) {
                    OutlinedButton(onClick = onManual) { Text(stringResource(R.string.add_food_manual)) }
                }
            }
        }
        return
    }

    val pickers = rememberPhotoPickers(
        onCameraFile = { viewModel.addCameraFile(context, it) },
        onGallery = { viewModel.addPhotos(context, it) },
    )
    val busy = state == PhotoEstimateState.Estimating
    val full = photos.size >= MAX_PHOTOS

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                .padding(12.dp),
        ) {
            Text(stringResource(R.string.photo_tips_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.photo_tips), style = MaterialTheme.typography.bodySmall)
        }

        if (photos.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                photos.forEachIndexed { index, photo ->
                    Box(Modifier.padding(top = 6.dp, end = 6.dp)) {
                        Image(
                            photo.preview,
                            contentDescription = stringResource(R.string.photo_alt, index + 1),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(start = 0.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable(enabled = !busy) { viewModel.remove(photo.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.photo_remove, index + 1),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = pickers.openCamera,
                enabled = !full && !busy,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.photo_camera))
            }
            OutlinedButton(
                onClick = pickers.openGallery,
                enabled = !full && !busy,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.photo_gallery))
            }
        }
        Text(
            stringResource(if (onDevice) R.string.photo_max_device else R.string.photo_max, MAX_PHOTOS),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column {
            FieldLabel(stringResource(R.string.photo_describe))
            Spacer(Modifier.height(6.dp))
            CompactTextArea(
                description,
                { viewModel.description.value = it },
                Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.photo_describe_placeholder),
            )
        }

        val error = when (val current = state) {
            PhotoEstimateState.NeedInput -> stringResource(R.string.photo_need_input)
            PhotoEstimateState.PhotoError -> stringResource(R.string.photo_error)
            is PhotoEstimateState.Failed -> stringResource(current.failure.messageRes())
            else -> null
        }
        if (error != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                val detail = (state as? PhotoEstimateState.Failed)?.detail.orEmpty()
                if (detail.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onManual != null && state is PhotoEstimateState.Failed) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onManual) { Text(stringResource(R.string.add_food_manual)) }
                }
            }
        }

        Button(onClick = viewModel::estimate, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (busy) R.string.photo_estimating else R.string.photo_estimate))
        }
    }
}

/** The line above the review form for an AI estimate (web `estimateNotice`). */
@Composable
fun estimateNotice(estimate: MealEstimate): String {
    val base = when (estimate.confidence) {
        null -> stringResource(R.string.photo_notice_plain)
        else -> stringResource(
            R.string.photo_notice,
            stringResource(
                when (estimate.confidence) {
                    EstimateConfidence.LOW -> R.string.photo_confidence_low
                    EstimateConfidence.MEDIUM -> R.string.photo_confidence_medium
                    else -> R.string.photo_confidence_high
                },
            ),
        )
    }
    return estimate.notes?.let { "$base $it" } ?: base
}
