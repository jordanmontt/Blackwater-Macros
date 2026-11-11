package com.blackwatermacros.app.ui.foods

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blackwatermacros.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ZOOMED = 2.5f
private const val MAX_ZOOM = 5f
private val OnDark = Color(0xFFF5F5F5)
private val OnDarkMuted = Color(0xFFB4B2A9)

/**
 * The label photo at full size, to check it before saving: Open Food Facts' one
 * matches the package, or yours is sharp. Labels are small print, so it zooms
 * (pinch, a double tap or the button) and pans. From here the photo can also be
 * changed, or removed when it is yours ([onRemove]). Back or «Listo» closes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LabelPhotoViewer(
    jpeg: ByteArray?,
    preview: ImageBitmap,
    source: String,
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    // The thumbnail's bitmap is small: decode the photo itself (≤ 1024 px) for reading the print.
    val full by produceState<ImageBitmap?>(null, jpeg) {
        value = jpeg?.let { bytes ->
            withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var stage by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(value: Offset, zoom: Float): Offset {
        val maxX = stage.width * (zoom - 1f) / 2f
        val maxY = stage.height * (zoom - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    fun toggleZoom() {
        scale = if (scale > 1f) 1f else ZOOMED
        offset = Offset.Zero
    }

    fun then(action: () -> Unit) {
        onDismiss()
        action()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close), tint = OnDark)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.new_product_viewer_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = OnDark,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(source, style = MaterialTheme.typography.bodySmall, color = OnDarkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = ::toggleZoom) {
                    Icon(
                        if (scale > 1f) Icons.Filled.ZoomOut else Icons.Filled.ZoomIn,
                        contentDescription = stringResource(if (scale > 1f) R.string.new_product_zoom_out else R.string.new_product_zoom_in),
                        tint = OnDark,
                    )
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .onSizeChanged { stage = it }
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { toggleZoom() }) }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val next = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                            scale = next
                            offset = clamp(offset + pan, next)
                        }
                    },
            ) {
                Image(
                    full ?: preview,
                    contentDescription = stringResource(R.string.new_product_label_photo_alt),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            }
            val outlined = ButtonDefaults.outlinedButtonColors(contentColor = OnDark)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) {
                OutlinedButton(onClick = { then(onCamera) }, colors = outlined) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.photo_camera))
                }
                OutlinedButton(onClick = { then(onGallery) }, colors = outlined) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.photo_gallery))
                }
                if (onRemove != null) {
                    TextButton(onClick = { then(onRemove) }, colors = ButtonDefaults.textButtonColors(contentColor = OnDarkMuted)) {
                        Icon(Icons.Filled.Close, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.new_product_remove_photo))
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = OnDark, contentColor = Color.Black)) {
                    Text(stringResource(R.string.new_product_done))
                }
            }
        }
    }
}
