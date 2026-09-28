package com.blackwatermacros.app.ui.foods

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.blackwatermacros.app.PHOTO_CACHE_DIR
import com.blackwatermacros.app.data.ai.MAX_PHOTOS
import java.io.File

/** Opens the camera or the system Photo Picker (see [rememberPhotoPickers]). */
class PhotoPickers(val openCamera: () -> Unit, val openGallery: () -> Unit)

/**
 * The camera (into a temporary cache file that [onCameraFile] reads and
 * deletes) and the system Photo Picker (no storage permission). Shared by
 * «Foto» in «Añadir comida» and the coach.
 */
@Composable
fun rememberPhotoPickers(onCameraFile: (File) -> Unit, onGallery: (List<Uri>) -> Unit): PhotoPickers {
    val context = LocalContext.current
    // The camera writes into a temporary cache file; its path survives rotation.
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingPath?.let(::File)
        pendingPath = null
        if (file == null) return@rememberLauncherForActivityResult
        if (saved) onCameraFile(file) else file.delete()
    }
    fun launchCamera() {
        val dir = File(context.cacheDir, PHOTO_CACHE_DIR).apply { mkdirs() }
        val file = File.createTempFile("meal", ".jpg", dir)
        pendingPath = file.absolutePath
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            file.delete()
            pendingPath = null
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris ->
        onGallery(uris)
    }
    return PhotoPickers(
        openCamera = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
        },
        openGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )
}
