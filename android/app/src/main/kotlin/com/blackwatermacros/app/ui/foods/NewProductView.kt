package com.blackwatermacros.app.ui.foods

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import com.blackwatermacros.app.core.OffKnownProduct
import com.blackwatermacros.app.core.newProductPer100g
import com.blackwatermacros.app.core.newProductProblems
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

/** «Añadir este producto» (from a barcode miss) or «Editar» (from the portion screen). */
data class ProductDraft(
    val code: String,
    val existing: Boolean,
    val known: OffKnownProduct = OffKnownProduct(null, null),
    val initial: NewProduct? = null,
)

class LabelPhoto(val image: AiImage, val preview: ImageBitmap)

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
    /** Save was tapped: show what is missing. */
    val tried: Boolean = false,
) {
    /** What the AI read; a name or brand already typed stays. */
    fun withReading(reading: LabelReading) = copy(
        name = name.ifBlank { reading.name.orEmpty() },
        brand = brand.ifBlank { reading.brand.orEmpty() },
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

        fun of(draft: ProductDraft): NewProductFields {
            val initial = draft.initial ?: return NewProductFields(name = draft.known.name.orEmpty(), brand = draft.known.brand.orEmpty())
            return NewProductFields(
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
 * the AI from a photo when AI is set up for photos. The photo lives in memory
 * until saving, then waits on the phone only until Open Food Facts has it.
 */
class NewProductViewModel(
    private val reader: LabelReader = AppGraph.labelReader,
    settings: AiSettingsStore = AppGraph.aiSettings,
    localModel: StateFlow<LocalModelState> = AppGraph.localModels.state,
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

    private var draftCode: String? = null
    private var job: Job? = null

    /** A new form for [draft] (the same draft again keeps what was typed, e.g. after rotating). */
    fun start(draft: ProductDraft) {
        if (draftCode == draft.code && _fields.value != NewProductFields()) return
        draftCode = draft.code
        job?.cancel()
        _fields.value = NewProductFields.of(draft)
        _photo.value = null
        _reading.value = LabelReadState.Idle
    }

    fun edit(change: (NewProductFields) -> NewProductFields) = _fields.update(change)

    fun addPhoto(context: Context, uri: Uri, deleteAfter: File? = null) {
        job?.cancel()
        job = viewModelScope.launch {
            val scaled = PhotoCodec.read(context, uri, deleteAfter)
            if (scaled == null) {
                _reading.value = LabelReadState.Failed(null)
                return@launch
            }
            _photo.value = LabelPhoto(scaled.image, scaled.preview.asImageBitmap())
            if (!aiReady.value) {
                _reading.value = LabelReadState.Idle
                return@launch
            }
            _reading.value = LabelReadState.Reading
            _reading.value = try {
                val read = reader.read(scaled.image, aiLanguageName(appLocale().language))
                _fields.update { it.withReading(read) }
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

    fun removePhoto() {
        job?.cancel()
        _photo.value = null
        _reading.value = LabelReadState.Idle
    }

    /** The product and the photo (JPEG) to save, or null with the problems shown. */
    fun submit(code: String): Pair<NewProduct, ByteArray?>? {
        val product = _fields.value.toProduct(code)
        if (newProductProblems(product).isNotEmpty() || _reading.value == LabelReadState.Reading) {
            _fields.update { it.copy(tried = true) }
            return null
        }
        val jpeg = _photo.value?.image?.data?.let { Base64.decode(it, Base64.NO_WRAP) }
        draftCode = null
        return product to jpeg
    }
}

@Composable
fun NewProductView(
    draft: ProductDraft,
    viewModel: NewProductViewModel,
    onSave: (NewProduct, ByteArray?) -> Unit,
) {
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val photo by viewModel.photo.collectAsStateWithLifecycle()
    val reading by viewModel.reading.collectAsStateWithLifecycle()
    val aiReady by viewModel.aiReady.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val pickers = rememberPhotoPickers(
        onCameraFile = { file -> viewModel.addPhoto(context, Uri.fromFile(file), deleteAfter = file) },
        onGallery = { uris -> uris.firstOrNull()?.let { viewModel.addPhoto(context, it) } },
    )
    val product = fields.toProduct(draft.code)
    val problems = newProductProblems(product)
    val shown = if (fields.tried) problems else emptyList()

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
    fun NumberField(label: String, value: String, problem: NewProductProblem?, modifier: Modifier, onChange: (String) -> Unit) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FieldLabel(label)
            CompactField(
                value = value,
                onValueChange = { onChange(it.filter { c -> c.isDigit() || c == ',' || c == '.' }) },
                placeholder = "",
                decimal = true,
                isError = problem != null && problem in shown,
                modifier = Modifier.fillMaxWidth(),
            )
            if (problem != null) Error(problem)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.new_product_barcode, draft.code),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val current = photo
        if (current == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = pickers.openCamera, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.new_product_label_photo), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = pickers.openGallery) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = stringResource(R.string.photo_gallery), Modifier.size(18.dp))
                }
            }
            Text(
                stringResource(
                    if (reading is LabelReadState.Failed) R.string.new_product_read_failed
                    else if (aiReady) R.string.new_product_label_photo_hint_ai else R.string.new_product_label_photo_hint,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (reading is LabelReadState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    current.preview,
                    contentDescription = stringResource(R.string.new_product_label_photo_alt),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (reading == LabelReadState.Reading) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        val failed = reading as? LabelReadState.Failed
                        Text(
                            when {
                                reading == LabelReadState.Reading -> stringResource(R.string.new_product_reading)
                                reading == LabelReadState.Done -> stringResource(R.string.new_product_read_done)
                                failed != null -> stringResource(R.string.new_product_read_failed) +
                                    (failed.failure?.let { " " + stringResource(it.messageRes()) } ?: "")
                                else -> stringResource(R.string.new_product_label_photo_hint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (failed != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row {
                        TextButton(onClick = pickers.openCamera) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.new_product_retake))
                        }
                        TextButton(onClick = viewModel::removePhoto) {
                            Icon(Icons.Filled.Close, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.new_product_remove_photo))
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FieldLabel(stringResource(R.string.new_product_name))
            CompactField(
                value = fields.name,
                onValueChange = { value -> viewModel.edit { it.copy(name = value) } },
                placeholder = stringResource(R.string.new_product_name_placeholder),
                capitalize = true,
                isError = NewProductProblem.NAME in shown,
                modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth(),
            )
            Error(NewProductProblem.SERVING)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(stringResource(R.string.new_product_calories), fields.calories, NewProductProblem.CALORIES, Modifier.weight(1f)) { value ->
                viewModel.edit { it.copy(calories = value) }
            }
            NumberField(stringResource(R.string.new_product_protein), fields.protein, NewProductProblem.PROTEIN, Modifier.weight(1f)) { value ->
                viewModel.edit { it.copy(protein = value) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(stringResource(R.string.new_product_carbs), fields.carbs, null, Modifier.weight(1f)) { value ->
                viewModel.edit { it.copy(carbs = value) }
            }
            NumberField(stringResource(R.string.new_product_fat), fields.fat, null, Modifier.weight(1f)) { value ->
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            stringResource(if (draft.existing) R.string.new_product_consent_existing else R.string.new_product_consent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { viewModel.submit(draft.code)?.let { (saved, jpeg) -> onSave(saved, jpeg) } },
            enabled = reading != LabelReadState.Reading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (draft.initial != null) R.string.new_product_save_edit else R.string.new_product_save))
        }
        Spacer(Modifier.height(4.dp))
    }
}
