package com.blackwatermacros.app.ui.foods

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.core.AiImage
import com.blackwatermacros.app.core.MealEstimate
import com.blackwatermacros.app.data.AppPreferences
import com.blackwatermacros.app.data.ai.AiException
import com.blackwatermacros.app.data.ai.AiFailure
import com.blackwatermacros.app.data.ai.AiSettingsStore
import com.blackwatermacros.app.data.ai.MAX_PHOTOS
import com.blackwatermacros.app.data.ai.MealEstimator
import com.blackwatermacros.app.data.ai.PhotoCodec
import com.blackwatermacros.app.data.ai.aiLanguageName
import com.blackwatermacros.app.ui.appLocale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MealPhoto(val id: Long, val image: AiImage, val preview: ImageBitmap)

sealed interface PhotoEstimateState {
    data object Idle : PhotoEstimateState
    data object Estimating : PhotoEstimateState
    /** Nothing to estimate yet: no photo and no description. */
    data object NeedInput : PhotoEstimateState
    data object PhotoError : PhotoEstimateState
    data class Failed(val failure: AiFailure) : PhotoEstimateState
    data class Done(val estimate: MealEstimate) : PhotoEstimateState
}

/**
 * «Foto» (web `PhotoEstimate`): up to five photos held only in memory as small
 * JPEGs, an optional description, and the AI's estimate. [reset] (the sheet
 * opening again) drops everything.
 */
class PhotoEstimateViewModel(
    private val estimator: MealEstimator = AppGraph.mealEstimator,
    settings: AiSettingsStore = AppGraph.aiSettings,
    private val preferences: AppPreferences = AppGraph.preferences,
) : ViewModel() {

    val aiReady: StateFlow<Boolean> = settings.settings.map { it.ready }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.current.ready)

    private val _photos = MutableStateFlow<List<MealPhoto>>(emptyList())
    val photos: StateFlow<List<MealPhoto>> = _photos.asStateFlow()

    val description = MutableStateFlow("")

    private val _state = MutableStateFlow<PhotoEstimateState>(PhotoEstimateState.Idle)
    val state: StateFlow<PhotoEstimateState> = _state.asStateFlow()

    private val _tipsVisible = MutableStateFlow(!preferences.photoTipsHidden)
    val tipsVisible: StateFlow<Boolean> = _tipsVisible.asStateFlow()

    private var nextId = 1L
    private var job: Job? = null

    fun reset(autoDescription: String? = null) {
        job?.cancel()
        _photos.value = emptyList()
        description.value = autoDescription.orEmpty()
        _state.value = PhotoEstimateState.Idle
        if (autoDescription != null && aiReady.value) estimate()
    }

    fun hideTips() {
        preferences.photoTipsHidden = true
        _tipsVisible.value = false
    }

    fun addPhotos(context: Context, uris: List<Uri>) {
        val room = MAX_PHOTOS - _photos.value.size
        if (room <= 0 || uris.isEmpty()) return
        viewModelScope.launch {
            for (uri in uris.take(room)) addOne(context, uri, deleteAfter = null)
        }
    }

    /** A camera photo: read into memory, then the file is deleted at once. */
    fun addCameraFile(context: Context, file: File) {
        viewModelScope.launch { addOne(context, Uri.fromFile(file), deleteAfter = file) }
    }

    private suspend fun addOne(context: Context, uri: Uri, deleteAfter: File?) {
        val scaled = withContext(Dispatchers.IO) {
            try {
                runCatching { PhotoCodec.downscale(context.applicationContext, uri) }
                    .onFailure { Log.w("PhotoEstimate", "Could not read a photo", it) }
                    .getOrNull()
            } finally {
                deleteAfter?.delete()
            }
        }
        if (scaled == null) {
            _state.value = PhotoEstimateState.PhotoError
            return
        }
        if (_photos.value.size >= MAX_PHOTOS) return
        _photos.value = _photos.value + MealPhoto(nextId++, scaled.image, scaled.preview.asImageBitmap())
        if (_state.value == PhotoEstimateState.NeedInput || _state.value == PhotoEstimateState.PhotoError) {
            _state.value = PhotoEstimateState.Idle
        }
    }

    fun remove(id: Long) {
        _photos.value = _photos.value.filterNot { it.id == id }
    }

    fun estimate() {
        val photos = _photos.value.map { it.image }
        val text = description.value
        if (photos.isEmpty() && text.isBlank()) {
            _state.value = PhotoEstimateState.NeedInput
            return
        }
        job?.cancel()
        _state.value = PhotoEstimateState.Estimating
        job = viewModelScope.launch {
            _state.value = try {
                PhotoEstimateState.Done(estimator.estimate(text, photos, aiLanguageName(appLocale().language)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                PhotoEstimateState.Failed(e.failure)
            } catch (e: Exception) {
                PhotoEstimateState.Failed(AiFailure.PROVIDER)
            }
        }
    }

    /** The estimate went to the review form: drop the photos. */
    fun consumed() {
        _photos.value = emptyList()
        description.value = ""
        _state.value = PhotoEstimateState.Idle
    }
}
