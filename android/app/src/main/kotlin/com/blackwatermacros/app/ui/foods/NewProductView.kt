package com.blackwatermacros.app.ui.foods

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.blackwatermacros.app.data.foods.OwnProductStore
import com.blackwatermacros.app.data.foods.OpenFoodFactsClient
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.blackwatermacros.app.AppGraph
import com.blackwatermacros.app.R
import com.blackwatermacros.app.core.AiImage
import com.blackwatermacros.app.core.LabelBasis
import com.blackwatermacros.app.core.LabelReading
import com.blackwatermacros.app.core.NewProduct
import com.blackwatermacros.app.core.NewProductProblem
import com.blackwatermacros.app.core.LabelNutrient
import com.blackwatermacros.app.core.OffCorrection
import com.blackwatermacros.app.core.OffKnownProduct
import com.blackwatermacros.app.core.correctionBaseline
import com.blackwatermacros.app.core.correctionChanges
import com.blackwatermacros.app.core.newProductPer100g
import com.blackwatermacros.app.core.newProductProblems
import com.blackwatermacros.app.core.value
import com.blackwatermacros.app.data.ai.AiException
import com.blackwatermacros.app.data.ai.AiFailure
import com.blackwatermacros.app.data.ai.AiSettingsStore
import com.blackwatermacros.app.data.ai.LabelReader
import com.blackwatermacros.app.data.ai.PhotoCodec
import com.blackwatermacros.app.data.ai.aiLanguageName
import com.blackwatermacros.app.data.ai.usableEngine
import com.blackwatermacros.app.data.ai.local.LocalModelState
import com.blackwatermacros.app.ui.CompactField
import com.blackwatermacros.app.ui.FieldLabel
import com.blackwatermacros.app.ui.appLocale
import com.blackwatermacros.app.ui.formatNumber
import com.blackwatermacros.app.ui.messageRes
import com.blackwatermacros.app.ui.toDecimalInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.runtime.remember
import com.blackwatermacros.app.core.PACKAGE_LANGS
import java.util.Locale

/**
 * «Añadir este producto» (from a barcode miss), «Editar» or «Corregir valores»
 * (from the portion screen; [correction]: what Open Food Facts has).
 */
data class ProductDraft(
    val code: String,
    val existing: Boolean,
    val known: OffKnownProduct = OffKnownProduct(null, null),
    val initial: NewProduct? = null,
    val correction: OffCorrection? = null,
    /** Open Food Facts' label photo (URL), when it was fetched with the correction. */
    val labelImage: String? = null,
    /** The package's language when it is known (Open Food Facts' or the one saved before). */
    val lang: String? = null,
)

class LabelPhoto(val image: AiImage, val preview: ImageBitmap)

/** A label photo the form didn't take: one waiting on the phone, or the one Open Food Facts shows. */
class SavedLabelPhoto(val preview: ImageBitmap, val fromDevice: Boolean)

sealed interface LabelReadState {
    data object Idle : LabelReadState
    data object Reading : LabelReadState
    data object Done : LabelReadState
    data class Failed(val failure: AiFailure?) : LabelReadState
}

/** The form as typed: numbers as text with a decimal comma, like every number field. */
data class NewProductFields(
    val name: String = "",
    val brand: String = "",
    val basis: LabelBasis = LabelBasis.PER_100G,
    val serving: String = "",
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    /** «Idioma del envase»: Open Food Facts keeps names and label photos per language. */
    val lang: String = "",
    /** Save was tapped: show what is missing. */
    val tried: Boolean = false,
) {
    /**
     * What the AI read; a name or brand already typed stays, and an existing
     * product ([keepLang]) keeps the language Open Food Facts has for it.
     */
    fun withReading(reading: LabelReading, keepLang: Boolean = false) = copy(
        name = name.ifBlank { reading.name.orEmpty() },
        brand = brand.ifBlank { reading.brand.orEmpty() },
        lang = if (keepLang) lang else reading.language ?: lang,
        basis = reading.basis,
        serving = reading.servingSize.orEmpty(),
        calories = text(reading.calories),
        protein = text(reading.protein),
        carbs = text(reading.carbs),
        fat = text(reading.fat),
    )

    fun toProduct(code: String) = NewProduct(
        code = code,
        name = name,
        brand = brand.ifBlank { null },
        basis = basis,
        servingSize = serving.trim().ifEmpty { null },
        calories = number(calories),
        protein = number(protein),
        carbs = number(carbs),
        fat = number(fat),
    )

    companion object {
        private fun number(text: String): Double? = text.trim().replace(",", ".").toDoubleOrNull()?.takeIf { it.isFinite() }
        private fun text(value: Double?): String = value?.let(::toDecimalInput).orEmpty()

        fun of(draft: ProductDraft, appLang: String = currentFoodLang().code): NewProductFields {
            val lang = draft.lang ?: appLang
            val initial = draft.initial ?: draft.correction?.base
                ?: return NewProductFields(name = draft.known.name.orEmpty(), brand = draft.known.brand.orEmpty(), lang = lang)
            return NewProductFields(
                lang = lang,
                name = initial.name,
                brand = initial.brand.orEmpty(),
                basis = initial.basis,
                serving = initial.servingSize.orEmpty(),
                calories = text(initial.calories),
                protein = text(initial.protein),
                carbs = text(initial.carbs),
                fat = text(initial.fat),
            )
        }
    }
}

/**
 * «Nuevo producto» (web `NewProductForm`): the label typed by hand, or read by
 * the AI from a photo when asked («Rellenar con la IA», since the model on the
 * phone can take a while). The photo lives in memory until saving, then waits
 * on the phone only until Open Food Facts has it; Save asks once for it when
 * there is none ([askPhoto]).
 */
class NewProductViewModel(
    private val reader: LabelReader = AppGraph.labelReader,
    settings: AiSettingsStore = AppGraph.aiSettings,
    localModel: StateFlow<LocalModelState> = AppGraph.localModels.state,
    private val ownProducts: OwnProductStore = AppGraph.ownProducts.store,
    private val openFoodFacts: OpenFoodFactsClient = AppGraph.openFoodFacts,
) : ViewModel() {

    val aiReady: StateFlow<Boolean> = combine(settings.settings, localModel) { current, model ->
        usableEngine(current.photoEngine, current.ready, model == LocalModelState.Ready) != null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, settings.current.ready)

    private val _fields = MutableStateFlow(NewProductFields())
    val fields: StateFlow<NewProductFields> = _fields.asStateFlow()

    private val _photo = MutableStateFlow<LabelPhoto?>(null)
    val photo: StateFlow<LabelPhoto?> = _photo.asStateFlow()

    private val _reading = MutableStateFlow<LabelReadState>(LabelReadState.Idle)
    val reading: StateFlow<LabelReadState> = _reading.asStateFlow()

    /** Shown until a new photo replaces it. */
    private val _savedPhoto = MutableStateFlow<SavedLabelPhoto?>(null)
    val savedPhoto: StateFlow<SavedLabelPhoto?> = _savedPhoto.asStateFlow()

    /** The saved photo as the AI takes it (its JPEG is already small). */
    private var savedImage: AiImage? = null

    /** Save was tapped without a label photo: «Sin foto…» with «Añadir foto» / «Guardar sin foto». */
    private val _askPhoto = MutableStateFlow(false)
    val askPhoto: StateFlow<Boolean> = _askPhoto.asStateFlow()

    private var draftCode: String? = null
    private var draftExisting = false
    private var job: Job? = null
    private var savedJob: Job? = null

    /** A new form for [draft] (the same draft again keeps what was typed, e.g. after rotating). */
    fun start(draft: ProductDraft) {
        if (draftCode == draft.code && _fields.value != NewProductFields()) return
        draftCode = draft.code
        draftExisting = draft.existing
        job?.cancel()
        _fields.value = NewProductFields.of(draft)
        _photo.value = null
        _reading.value = LabelReadState.Idle
        _askPhoto.value = false
        loadSavedPhoto(draft)
    }

    /**
     * The photo waiting on the phone for this product, or else (editing or
     * correcting) the one Open Food Facts has; nothing for a new product.
     */
    private fun loadSavedPhoto(draft: ProductDraft) {
        savedJob?.cancel()
        _savedPhoto.value = null
        savedImage = null
        savedJob = viewModelScope.launch {
            val waiting = withContext(Dispatchers.IO) { ownProducts.photo(draft.code) }
            val bytes = waiting ?: run {
                if (draft.initial == null && draft.correction == null) return@launch
                val url = draft.labelImage ?: openFoodFacts.labelImage(draft.code, currentFoodLang()) ?: return@launch
                openFoodFacts.image(url)
            } ?: return@launch
            val preview = withContext(Dispatchers.Default) { decodePreview(bytes) } ?: return@launch
            savedImage = AiImage("image/jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP))
            _savedPhoto.value = SavedLabelPhoto(preview, fromDevice = waiting != null)
        }
    }

    fun edit(change: (NewProductFields) -> NewProductFields) = _fields.update(change)

    fun addPhoto(context: Context, uri: Uri, deleteAfter: File? = null) {
        job?.cancel()
        _reading.value = LabelReadState.Idle
        job = viewModelScope.launch {
            val scaled = PhotoCodec.read(context, uri, deleteAfter)
            if (scaled == null) {
                _reading.value = LabelReadState.Failed(null)
                return@launch
            }
            _photo.value = LabelPhoto(scaled.image, scaled.preview.asImageBitmap())
            _askPhoto.value = false
        }
    }

    /** «Rellenar con la IA»: the new photo, or else the saved one. */
    fun fillWithAi() {
        val image = _photo.value?.image ?: savedImage ?: return
        job?.cancel()
        job = viewModelScope.launch {
            _reading.value = LabelReadState.Reading
            _reading.value = try {
                val read = reader.read(image, aiLanguageName(appLocale().language))
                _fields.update { it.withReading(read, keepLang = draftExisting) }
                LabelReadState.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                LabelReadState.Failed(e.failure)
            } catch (e: Exception) {
                LabelReadState.Failed(AiFailure.PROVIDER)
            }
        }
    }

    fun cancelReading() {
        job?.cancel()
        _reading.value = LabelReadState.Idle
    }

    /** The shown photo's JPEG (≤ 1024 px), for the viewer: the new one, or else the saved one. */
    fun photoJpeg(): ByteArray? =
        (_photo.value?.image ?: savedImage)?.data?.let { Base64.decode(it, Base64.NO_WRAP) }

    fun removePhoto() {
        job?.cancel()
        _photo.value = null
        _reading.value = LabelReadState.Idle
    }

    /**
     * The product, the photo (JPEG) and the package's language to save, or null
     * with the problems shown. A correction that changes nothing and brings no
     * photo would only repeat what Open Food Facts has. Without any label photo
     * it asks once ([askPhoto]) unless [askForPhoto] is false (editing) or the
     * answer was «Guardar sin foto» ([withoutPhoto]).
     */
    fun submit(
        code: String,
        correction: OffCorrection? = null,
        askForPhoto: Boolean = true,
        withoutPhoto: Boolean = false,
    ): Triple<NewProduct, ByteArray?, String>? {
        val product = _fields.value.toProduct(code)
        val unchanged = correction != null && correctionChanges(correction, product).isEmpty() && _photo.value == null
        if (newProductProblems(product, correction?.per).isNotEmpty() || unchanged || _reading.value == LabelReadState.Reading) {
            _fields.update { it.copy(tried = true) }
            return null
        }
        if (askForPhoto && !withoutPhoto && _photo.value == null && _savedPhoto.value == null) {
            _askPhoto.value = true
            return null
        }
        val jpeg = _photo.value?.image?.data?.let { Base64.decode(it, Base64.NO_WRAP) }
        draftCode = null
        return Triple(product, jpeg, _fields.value.lang)
    }
}

/** «Español», in the app's language. */
private fun languageName(code: String): String =
    Locale.forLanguageTag(code).getDisplayLanguage(appLocale()).replaceFirstChar { it.titlecase(appLocale()) }.ifBlank { code }

/** «Idioma del envase»: the common languages, plus the current one if it is another. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackageLangField(lang: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val langs = if (lang in PACKAGE_LANGS) PACKAGE_LANGS else listOf(lang) + PACKAGE_LANGS
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(stringResource(R.string.new_product_package_lang))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            val label = languageName(lang)
            CompactField(
                value = label,
                onValueChange = {},
                placeholder = label,
                readOnly = true,
                trailingIcon = {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                langs.forEach { code ->
                    DropdownMenuItem(
                        text = { Text(languageName(code)) },
                        onClick = {
                            onChange(code)
                            expanded = false
                        },
                    )
                }
            }
        }
        Text(
            stringResource(R.string.new_product_package_lang_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A small bitmap for the thumbnail (the viewer decodes the photo itself). */
private fun decodePreview(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 256 && bounds.outHeight / (sample * 2) >= 256) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

/** The dashed outline of the label-photo card: a place waiting for the photo. */
private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewProductView(
    draft: ProductDraft,
    viewModel: NewProductViewModel,
    onSave: (NewProduct, ByteArray?, String) -> Unit,
) {
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val photo by viewModel.photo.collectAsStateWithLifecycle()
    val reading by viewModel.reading.collectAsStateWithLifecycle()
    val aiReady by viewModel.aiReady.collectAsStateWithLifecycle()
    val savedPhoto by viewModel.savedPhoto.collectAsStateWithLifecycle()
    val askPhoto by viewModel.askPhoto.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val pickers = rememberPhotoPickers(
        onCameraFile = { file -> viewModel.addPhoto(context, Uri.fromFile(file), deleteAfter = file) },
        onGallery = { uris -> uris.firstOrNull()?.let { viewModel.addPhoto(context, it) } },
    )
    val product = fields.toProduct(draft.code)
    val correction = draft.correction
    val changes = correction?.let { correctionChanges(it, product) }.orEmpty()
    val baseline = correction?.let { correctionBaseline(it, product) }
    val unchanged = correction != null && changes.isEmpty() && photo == null
    val problems = newProductProblems(product, correction?.per)
    val shown = if (fields.tried) problems else emptyList()
    // Save takes the cursor to the first value missing or wrong (the fields' order).
    val nameFocus = remember { FocusRequester() }
    val servingFocus = remember { FocusRequester() }
    val caloriesFocus = remember { FocusRequester() }
    val proteinFocus = remember { FocusRequester() }
    fun save(withoutPhoto: Boolean) {
        val result = viewModel.submit(draft.code, correction, askForPhoto = draft.initial == null, withoutPhoto = withoutPhoto)
        if (result != null) {
            val (saved, jpeg, lang) = result
            onSave(saved, jpeg, lang)
            return
        }
        val focus = when (problems.firstOrNull()) {
            NewProductProblem.NAME -> nameFocus
            NewProductProblem.SERVING -> servingFocus
            NewProductProblem.CALORIES, NewProductProblem.TOO_HIGH -> caloriesFocus
            NewProductProblem.PROTEIN -> proteinFocus
            null -> null
        }
        runCatching { focus?.requestFocus() }
    }

    @Composable
    fun Error(problem: NewProductProblem) {
        if (problem !in shown) return
        Text(
            stringResource(
                when (problem) {
                    NewProductProblem.NAME -> R.string.new_product_error_name
                    NewProductProblem.CALORIES -> R.string.new_product_error_calories
                    NewProductProblem.PROTEIN -> R.string.new_product_error_protein
                    NewProductProblem.SERVING -> R.string.new_product_error_serving
                    NewProductProblem.TOO_HIGH -> R.string.new_product_error_too_high
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    @Composable
    fun NumberField(
        label: String,
        value: String,
        nutrient: LabelNutrient,
        problem: NewProductProblem?,
        modifier: Modifier,
        focus: FocusRequester? = null,
        onChange: (String) -> Unit,
    ) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FieldLabel(label)
            CompactField(
                value = value,
                onValueChange = { onChange(it.filter { c -> c.isDigit() || c == ',' || c == '.' }) },
                placeholder = "",
                decimal = true,
                isError = problem != null && problem in shown,
                modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
            )
            if (problem != null) Error(problem)
            val before = baseline?.value(nutrient)
            if (before != null && nutrient in changes) {
                Text(
                    stringResource(R.string.new_product_off_value, formatNumber(before, if (nutrient == LabelNutrient.CALORIES) 0 else 1)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (correction != null) {
                Text(
                    if (fields.brand.isBlank()) fields.name else "${fields.name} · ${fields.brand}",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                stringResource(R.string.new_product_barcode, draft.code),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (correction != null) {
                Text(
                    stringResource(R.string.new_product_correct_check),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val current = photo
        val saved = savedPhoto
        val failed = reading as? LabelReadState.Failed
        if (current != null || saved != null) {
            val yours = current != null || saved!!.fromDevice
            val source = when {
                current != null -> stringResource(R.string.new_product_photo_sent_on_save)
                saved!!.fromDevice -> stringResource(R.string.new_product_photo_waiting)
                else -> stringResource(R.string.new_product_photo_on_off)
            }
            var viewing by remember { mutableStateOf(false) }
            var changeMenu by remember { mutableStateOf(false) }
            val viewLabel = stringResource(R.string.new_product_view_photo)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .clickable(onClickLabel = viewLabel) { viewing = true }
                            .semantics { contentDescription = viewLabel },
                    ) {
                        Image(
                            current?.preview ?: saved!!.preview,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                        ) {
                            Icon(Icons.Filled.OpenInFull, contentDescription = null, Modifier.size(14.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(if (yours) R.string.new_product_your_photo else R.string.new_product_off_photo),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (reading == LabelReadState.Reading) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                when {
                                    reading == LabelReadState.Reading -> stringResource(R.string.new_product_reading)
                                    reading == LabelReadState.Done -> stringResource(R.string.new_product_read_done)
                                    failed != null -> stringResource(R.string.new_product_read_failed) +
                                        (failed.failure?.let { " " + stringResource(it.messageRes()) } ?: "")
                                    else -> source
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (failed != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    }
                }
                FlowRow(verticalArrangement = Arrangement.Center, modifier = Modifier.offset(x = (-12).dp)) {
                    if (aiReady && reading == LabelReadState.Reading) {
                        TextButton(onClick = viewModel::cancelReading) {
                            Icon(Icons.Filled.Close, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_cancel))
                        }
                    } else if (aiReady && reading != LabelReadState.Done) {
                        FilledTonalButton(onClick = viewModel::fillWithAi, modifier = Modifier.padding(start = 12.dp, end = 4.dp)) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(if (failed != null) R.string.common_retry else R.string.new_product_fill_with_ai))
                        }
                    }
                    Box {
                        TextButton(onClick = { changeMenu = true }) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.new_product_change_photo))
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = changeMenu, onDismissRequest = { changeMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.photo_camera)) },
                                leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                                onClick = {
                                    changeMenu = false
                                    pickers.openCamera()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.new_product_choose_from_gallery)) },
                                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                                onClick = {
                                    changeMenu = false
                                    pickers.openGallery()
                                },
                            )
                        }
                    }
                    if (current != null) {
                        TextButton(
                            onClick = viewModel::removePhoto,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.new_product_remove_photo))
                        }
                    }
                }
            }
            if (viewing) {
                val jpeg = remember(current, saved) { viewModel.photoJpeg() }
                LabelPhotoViewer(
                    jpeg = jpeg,
                    preview = current?.preview ?: saved!!.preview,
                    source = if (yours) source else stringResource(R.string.new_product_from_off),
                    onDismiss = { viewing = false },
                    onCamera = pickers.openCamera,
                    onGallery = pickers.openGallery,
                    onRemove = if (current != null) viewModel::removePhoto else null,
                )
            }
        } else {
            val primary = MaterialTheme.colorScheme.primary
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(primary.copy(alpha = 0.06f))
                    .dashedBorder(primary.copy(alpha = 0.5f), 12.dp)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(primary.copy(alpha = 0.12f)),
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.new_product_photo_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.new_product_recommended),
                                style = MaterialTheme.typography.labelSmall,
                                color = primary,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(primary.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        Text(
                            stringResource(
                                if (failed != null) R.string.new_product_read_failed
                                else if (aiReady) R.string.new_product_label_photo_hint_ai else R.string.new_product_label_photo_hint,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (failed != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = pickers.openCamera, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.photo_camera), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = pickers.openGallery) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.photo_gallery), maxLines = 1)
                    }
                }
            }
        }

        // A correction keeps Open Food Facts' name and brand (shown above).
        if (correction == null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel(stringResource(R.string.new_product_name))
                CompactField(
                    value = fields.name,
                    onValueChange = { value -> viewModel.edit { it.copy(name = value) } },
                    placeholder = stringResource(R.string.new_product_name_placeholder),
                    capitalize = true,
                    isError = NewProductProblem.NAME in shown,
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                )
                Error(NewProductProblem.NAME)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel("${stringResource(R.string.new_product_brand)} (${stringResource(R.string.new_product_optional)})")
                CompactField(
                    value = fields.brand,
                    onValueChange = { value -> viewModel.edit { it.copy(brand = value) } },
                    placeholder = "",
                    capitalize = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!draft.existing) PackageLangField(fields.lang) { value -> viewModel.edit { it.copy(lang = value) } }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(stringResource(R.string.new_product_basis_label))
            val bases = listOf(
                LabelBasis.PER_100G to R.string.new_product_basis_100g,
                LabelBasis.PER_100ML to R.string.new_product_basis_100ml,
                LabelBasis.SERVING to R.string.new_product_basis_serving,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                bases.forEachIndexed { index, (basis, label) ->
                    SegmentedButton(
                        selected = fields.basis == basis,
                        onClick = { viewModel.edit { it.copy(basis = basis) } },
                        shape = SegmentedButtonDefaults.itemShape(index, bases.size),
                        icon = {},
                    ) {
                        Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FieldLabel(
                stringResource(R.string.new_product_serving_size) +
                    if (fields.basis == LabelBasis.SERVING) "" else " (${stringResource(R.string.new_product_optional)})",
            )
            CompactField(
                value = fields.serving,
                onValueChange = { value -> viewModel.edit { it.copy(serving = value) } },
                placeholder = stringResource(R.string.new_product_serving_placeholder),
                isError = NewProductProblem.SERVING in shown,
                modifier = Modifier.fillMaxWidth().focusRequester(servingFocus),
            )
            Error(NewProductProblem.SERVING)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(stringResource(R.string.new_product_calories), fields.calories, LabelNutrient.CALORIES, NewProductProblem.CALORIES, Modifier.weight(1f), caloriesFocus) { value ->
                viewModel.edit { it.copy(calories = value) }
            }
            NumberField(stringResource(R.string.new_product_protein), fields.protein, LabelNutrient.PROTEIN, NewProductProblem.PROTEIN, Modifier.weight(1f), proteinFocus) { value ->
                viewModel.edit { it.copy(protein = value) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(stringResource(R.string.new_product_carbs), fields.carbs, LabelNutrient.CARBS, null, Modifier.weight(1f)) { value ->
                viewModel.edit { it.copy(carbs = value) }
            }
            NumberField(stringResource(R.string.new_product_fat), fields.fat, LabelNutrient.FAT, null, Modifier.weight(1f)) { value ->
                viewModel.edit { it.copy(fat = value) }
            }
        }
        if (fields.basis == LabelBasis.SERVING && product.calories != null && product.protein != null &&
            NewProductProblem.SERVING !in problems
        ) {
            val per100 = newProductPer100g(product)
            Text(
                stringResource(
                    R.string.new_product_per_100,
                    formatNumber(per100.calories),
                    formatNumber(per100.protein, 1),
                    if (fields.serving.contains("ml", ignoreCase = true)) "ml" else "g",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Error(NewProductProblem.TOO_HIGH)
        if (fields.tried && unchanged && problems.isEmpty()) {
            Text(
                stringResource(R.string.new_product_error_unchanged),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            stringResource(
                when {
                    correction != null -> R.string.new_product_consent_correction
                    draft.existing -> R.string.new_product_consent_existing
                    else -> R.string.new_product_consent
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (askPhoto) {
            val primary = MaterialTheme.colorScheme.primary
            // Taller than the Save button it replaces: bring its buttons into view.
            val reminder = remember { BringIntoViewRequester() }
            LaunchedEffect(Unit) { reminder.bringIntoView() }
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .bringIntoViewRequester(reminder)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(primary.copy(alpha = 0.06f))
                    .border(1.dp, primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Text(
                    stringResource(R.string.new_product_no_photo),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = pickers.openCamera, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.photo_camera), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(onClick = pickers.openGallery, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.photo_gallery), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                TextButton(
                    onClick = { save(withoutPhoto = true) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.new_product_save_without_photo))
                }
            }
        } else {
            Button(
                onClick = { save(withoutPhoto = false) },
                enabled = reading != LabelReadState.Reading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (draft.initial != null) R.string.new_product_save_edit else R.string.new_product_save))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
