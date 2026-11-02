package com.blackwatermacros.app.ui.foods

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.blackwatermacros.app.R
import com.blackwatermacros.app.core.BarcodeMiss
import com.blackwatermacros.app.core.mathRound
import com.blackwatermacros.app.core.scalePer100g
import com.blackwatermacros.app.data.foods.FoodChoice
import com.blackwatermacros.app.data.foods.OwnProduct
import com.blackwatermacros.app.data.foods.OwnProductState
import com.blackwatermacros.app.ui.CompactField
import com.blackwatermacros.app.ui.FieldLabel
import com.blackwatermacros.app.ui.formatNumber
import com.blackwatermacros.app.ui.toDecimalInput
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch
import zxingcpp.BarcodeReader

/** Search box, recents, generic foods and Open Food Facts products (web `FoodSearch`). */
@Composable
fun FoodSearchView(
    state: FoodSearchState,
    onQuery: (String) -> Unit,
    /** «Estimar “…” con IA»: the query as a text estimate (e.g. «3 plátanos»). */
    onEstimateQuery: ((String) -> Unit)? = null,
    onPick: (FoodChoice) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CompactField(
            value = state.query,
            onValueChange = onQuery,
            placeholder = stringResource(R.string.food_search_placeholder),
            enabled = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        val query = state.query.trim()
        if (query.isEmpty()) {
            if (state.recents.isNotEmpty()) ResultGroup(stringResource(R.string.food_recent), state.recents, onPick)
        } else {
            if (state.own.isNotEmpty()) ResultGroup(stringResource(R.string.food_your_products), state.own, onPick)
            if (state.generic.isNotEmpty()) ResultGroup(stringResource(R.string.food_generic), state.generic, onPick)
            when (val online = state.online) {
                OnlineResults.Loading -> Hint(stringResource(R.string.food_searching))
                OnlineResults.Failed -> Hint(stringResource(R.string.food_online_error))
                is OnlineResults.Done -> {
                    if (online.choices.isNotEmpty()) {
                        ResultGroup(stringResource(R.string.food_products), online.choices, onPick)
                    } else if (state.generic.isEmpty() && state.own.isEmpty()) {
                        Hint(stringResource(R.string.food_no_results, query))
                    }
                }
                OnlineResults.Idle -> Unit
            }
            if (onEstimateQuery != null && query.length >= 2) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .clickable { onEstimateQuery(query) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.photo_estimate_query, query), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text(
            stringResource(R.string.food_credits),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ResultGroup(title: String, choices: List<FoodChoice>, onPick: (FoodChoice) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        val shape = RoundedCornerShape(12.dp)
        Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
            choices.forEachIndexed { index, choice ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(choice) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        if (choice.brand != null) "${choice.name} · ${choice.brand}" else choice.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        per100Text(choice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun per100Text(choice: FoodChoice) =
    // Same rounding as the portion totals (JS Math.round), so 100 g shows the same kcal.
    stringResource(R.string.food_per_100, formatNumber(mathRound(choice.calories)), formatNumber(choice.protein, 1))

/**
 * How much of the chosen food (web `PortionPicker`). A product the user added
 * ([own]) says so, can be edited ([onEdit]) and shows whether Open Food Facts
 * has it yet.
 */
@Composable
fun PortionView(
    choice: FoodChoice,
    own: OwnProduct? = null,
    onEdit: ((OwnProduct) -> Unit)? = null,
    onCorrect: (suspend () -> Boolean)? = null,
    onAdd: (grams: Double) -> Unit,
) {
    var text by rememberSaveable(choice.key) { mutableStateOf(toDecimalInput(choice.servingGrams ?: 100.0)) }
    val grams = text.replace(",", ".").toDoubleOrNull()
    val valid = grams != null && grams > 0 && grams <= 5000
    val totals = scalePer100g(choice.per100g, if (valid) grams!! else 0.0)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(
                if (choice.brand != null) "${choice.name} · ${choice.brand}" else choice.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(per100Text(choice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (choice.incomplete) Hint(stringResource(R.string.food_incomplete))
            if (own != null) {
                OwnProductLine(own, onEdit)
            } else if (onCorrect != null && choice.source == "off" && choice.code != null) {
                CorrectLink(onCorrect)
            }
        }
        FieldLabel(stringResource(R.string.food_grams))
        CompactField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
            placeholder = "100",
            enabled = true,
            decimal = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { text = "100" }, shape = RoundedCornerShape(50)) { Text("100 g") }
            choice.servingGrams?.let { serving ->
                OutlinedButton(onClick = { text = toDecimalInput(serving) }, shape = RoundedCornerShape(50)) {
                    Text(stringResource(R.string.food_serving, formatNumber(serving, 1)))
                }
            }
        }
        val shape = RoundedCornerShape(12.dp)
        Row(
            Modifier.fillMaxWidth().clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TotalCell(stringResource(R.string.total_calories), "${formatNumber(totals.calories)} kcal")
            TotalCell(stringResource(R.string.macro_protein), "${formatNumber(totals.protein, 1)} g")
            TotalCell(stringResource(R.string.macro_carbs), "${formatNumber(totals.carbs, 1)} g")
            TotalCell(stringResource(R.string.macro_fat), "${formatNumber(totals.fat, 1)} g")
        }
        Button(onClick = { onAdd(grams!!) }, enabled = valid, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.food_add))
        }
    }
}

/** «¿Los valores no coinciden con la tabla? Corrígelos» (false: Open Food Facts couldn't be reached). */
@Composable
private fun CorrectLink(onCorrect: suspend () -> Boolean) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    TextButton(
        onClick = {
            busy = true
            failed = false
            scope.launch {
                failed = !onCorrect()
                busy = false
            }
        },
        enabled = !busy,
        contentPadding = PaddingValues(0.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(stringResource(R.string.new_product_correct_link), style = MaterialTheme.typography.bodySmall)
    }
    if (failed) {
        Text(
            stringResource(R.string.new_product_correct_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** «Tu producto · Editar» (or «Tu corrección») and whether Open Food Facts has it yet. */
@Composable
private fun OwnProductLine(own: OwnProduct, onEdit: ((OwnProduct) -> Unit)?) {
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(if (own.correction != null) R.string.new_product_your_correction else R.string.new_product_yours),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Spacer(Modifier.weight(1f))
        if (onEdit != null) {
            TextButton(onClick = { onEdit(own) }) {
                Icon(Icons.Filled.Edit, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.new_product_edit))
            }
        }
    }
    val sent = own.state == OwnProductState.SENT
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (sent) {
            Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            when (own.state) {
                OwnProductState.SENT -> stringResource(
                    if (own.correction != null) R.string.new_product_status_sent_correction else R.string.new_product_status_sent,
                )
                // WorkManager sends it as soon as there is a connection.
                OwnProductState.PENDING -> stringResource(R.string.new_product_status_pending)
                OwnProductState.ACCOUNT -> stringResource(R.string.new_product_status_account)
                OwnProductState.REJECTED -> stringResource(R.string.new_product_status_rejected) +
                    own.detail.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            },
            style = MaterialTheme.typography.bodySmall,
            color = when (own.state) {
                OwnProductState.SENT -> MaterialTheme.colorScheme.primary
                OwnProductState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.error
            },
        )
    }
}

@Composable
private fun TotalCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Barcode: camera preview with zxing-cpp (asks for the camera the first
 * time), or the code typed by hand ([code], kept by the sheet so it knows there
 * is something to lose). Frames are analysed in memory and dropped: nothing is saved.
 * The camera only runs while waiting for a code: once a code gave nothing usable
 * it stays off (it would read the same label again and again), says why, and
 * offers to add the product from its label ([onAddProduct]; not for a shop
 * label, whose code changes with every package), the other ways in
 * ([onSearchByName], [onPhoto]) or another scan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BarcodeView(
    lookup: BarcodeLookup,
    code: String,
    onCodeChange: (String) -> Unit,
    onCode: (String) -> Unit,
    onScanAgain: () -> Unit,
    onAddProduct: (BarcodeLookup.NotFound) -> Unit,
    onSearchByName: () -> Unit,
    onPhoto: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) {
        if (!granted && !asked) launcher.launch(Manifest.permission.CAMERA)
    }
    val missed = lookup is BarcodeLookup.NotFound || lookup is BarcodeLookup.Offline
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (granted && lookup is BarcodeLookup.Idle) {
            CameraScanner(onCode = {
                onCodeChange(it)
                onCode(it)
            })
        }
        if (missed) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(code, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        when ((lookup as? BarcodeLookup.NotFound)?.reason) {
                            BarcodeMiss.STORE_LABEL -> R.string.food_not_found_store
                            BarcodeMiss.NO_NUTRITION -> R.string.food_not_found_no_nutrition
                            BarcodeMiss.UNKNOWN -> R.string.food_not_found
                            null -> R.string.food_needs_internet
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (lookup is BarcodeLookup.NotFound) {
                    val canAdd = lookup.reason != BarcodeMiss.STORE_LABEL
                    Text(
                        stringResource(if (canAdd) R.string.food_not_found_add else R.string.food_not_found_next),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (canAdd) {
                        Button(onClick = { onAddProduct(lookup) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.food_add_product))
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSearchByName) {
                        Icon(Icons.Filled.Search, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.food_search_by_name))
                    }
                    OutlinedButton(onClick = onPhoto) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.food_take_photo))
                    }
                    if (granted) {
                        TextButton(onClick = onScanAgain) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.food_scan_again))
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (lookup is BarcodeLookup.Loading) {
                    CircularProgressIndicator(Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(
                        when {
                            lookup is BarcodeLookup.Loading -> R.string.food_looking_up
                            !granted -> R.string.food_camera_error
                            else -> R.string.food_scan_hint
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompactField(
                value = code,
                onValueChange = { onCodeChange(it.filter(Char::isDigit)) },
                placeholder = stringResource(R.string.food_manual_code),
                enabled = lookup !is BarcodeLookup.Loading,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { onCode(code) }, enabled = code.length >= 6 && lookup !is BarcodeLookup.Loading) {
                Text(stringResource(R.string.food_look_up))
            }
        }
    }
}

@Composable
private fun CameraScanner(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val done = remember { AtomicBoolean(false) }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
            factory = { ctx ->
                val view = PreviewView(ctx)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                    val reader = BarcodeReader(
                        BarcodeReader.Options(
                            formats = setOf(
                                BarcodeReader.Format.EAN_13,
                                BarcodeReader.Format.EAN_8,
                                BarcodeReader.Format.UPC_A,
                                BarcodeReader.Format.UPC_E,
                                BarcodeReader.Format.CODE_128,
                            ),
                        ),
                    )
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { image ->
                        image.use {
                            val text = runCatching { reader.read(it).firstOrNull()?.text }.getOrNull()
                            if (!text.isNullOrBlank() && done.compareAndSet(false, true)) {
                                mainExecutor.execute { onCode(text) }
                            }
                        }
                    }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }
                }, mainExecutor)
                view
            },
        )
        // Aiming line across the middle of the preview.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.8f)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)),
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            executor.shutdown()
        }
    }
}
