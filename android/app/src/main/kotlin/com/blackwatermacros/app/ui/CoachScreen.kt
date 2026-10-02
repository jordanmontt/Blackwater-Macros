package com.blackwatermacros.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackwatermacros.app.R
import com.blackwatermacros.app.core.AiProvider
import com.blackwatermacros.app.core.AiRole
import com.blackwatermacros.app.data.ai.AiEngineChoice
import com.blackwatermacros.app.data.ai.MAX_PHOTOS
import com.blackwatermacros.app.ui.foods.rememberPhotoPickers
import kotlinx.coroutines.launch

/** Short provider name for «Gemini · nube». */
fun AiProvider.shortLabelRes(): Int = when (this) {
    AiProvider.GEMINI -> R.string.ai_short_gemini
    AiProvider.OPENAI -> R.string.ai_short_openai
    AiProvider.ANTHROPIC -> R.string.ai_short_anthropic
    AiProvider.OPENROUTER -> R.string.ai_short_openrouter
    AiProvider.CUSTOM -> R.string.ai_short_custom
}

/**
 * Coach tab (§4.3, web `/coach`): example questions, a streamed chat, «Nueva
 * conversación». The ViewModel belongs to the activity so the conversation
 * survives tab switches; nothing is stored.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachScreen(
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CoachViewModel = viewModel(viewModelStoreOwner = LocalActivity.current as ComponentActivity),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.aiSettings.collectAsStateWithLifecycle()
    val engine by viewModel.engine.collectAsStateWithLifecycle()
    val localModel by viewModel.localModelSpec.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val lastLength = state.messages.lastOrNull()?.text?.length ?: 0

    LaunchedEffect(state.messages.size, lastLength) {
        if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.size - 1, Int.MAX_VALUE / 2)
    }

    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var photoMenu by remember { mutableStateOf(false) }
    val textOnlyMessage = stringResource(R.string.coach_photos_text_only)
    val photoPrompt = stringResource(R.string.coach_photo_prompt)
    val pickers = rememberPhotoPickers(
        onCameraFile = { viewModel.addCameraFile(context, it) },
        onGallery = { viewModel.addPhotos(context, it) },
    )

    fun send(text: String) {
        if ((text.isBlank() && photos.isEmpty()) || state.streaming) return
        draft = ""
        viewModel.send(text, photoPrompt)
    }

    /** Photos need the cloud or a phone model that reads images (Gemma); Qwen reads text only. */
    fun openPhotoMenu() {
        scope.launch {
            if (viewModel.canSendPhotos()) photoMenu = true else snackbarHostState.showSnackbar(textOnlyMessage)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenteredTopAppBar(
                title = stringResource(R.string.tab_coach),
                trailing = {
                    if (state.messages.isNotEmpty()) {
                        IconButton(onClick = viewModel::reset) {
                            Icon(
                                Icons.Filled.AddComment,
                                contentDescription = stringResource(R.string.coach_new_chat),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).imePadding()) {
            if (engine == null) {
                SettingsCard(null) {
                    Text(stringResource(R.string.coach_not_configured), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onOpenAiSettings) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.photo_configure))
                    }
                }
                return@Column
            }

            if (state.messages.isEmpty()) {
                Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.coach_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        stringArrayResource(R.array.coach_examples).forEach { example ->
                            OutlinedButton(onClick = { send(example) }, shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                                Text(example, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    CardDescription(stringResource(R.string.coach_memory_only))
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        val pending = state.streaming && message.id == state.messages.last().id
                        MessageBubble(message, pending)
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
            ) {
                if (photos.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        photos.forEachIndexed { index, photo ->
                            Box {
                                Image(
                                    photo.preview,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                                )
                                IconButton(
                                    onClick = { viewModel.removePhoto(photo.id) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(22.dp)
                                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.coach_photo_remove, index + 1),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        if (engine == AiEngineChoice.DEVICE) {
                            stringResource(R.string.coach_photos_on_device)
                        } else {
                            stringResource(R.string.coach_photos_kept, stringResource(settings.provider.shortLabelRes()))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Box {
                        OutlinedIconButton(
                            onClick = ::openPhotoMenu,
                            enabled = !state.streaming && photos.size < MAX_PHOTOS,
                        ) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = stringResource(R.string.coach_add_photo))
                        }
                        DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.photo_camera)) },
                                leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                                onClick = { photoMenu = false; pickers.openCamera() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.photo_gallery)) },
                                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                                onClick = { photoMenu = false; pickers.openGallery() },
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        CompactTextArea(
                            draft,
                            { draft = it },
                            Modifier.fillMaxWidth(),
                            placeholder = stringResource(R.string.coach_placeholder),
                            minHeight = 44.dp,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (state.streaming) {
                        OutlinedIconButton(onClick = viewModel::stop) {
                            Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.coach_stop))
                        }
                    } else {
                        FilledIconButton(onClick = { send(draft) }, enabled = draft.isNotBlank() || photos.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.coach_send))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                val engineLabel = if (engine == AiEngineChoice.DEVICE) {
                    stringResource(R.string.coach_engine_device, localModel.name)
                } else {
                    stringResource(R.string.coach_engine, stringResource(settings.provider.shortLabelRes()))
                }
                val noData = if (settings.coachSeesData) "" else " · " + stringResource(R.string.coach_no_data)
                Text(
                    "${stringResource(R.string.coach_disclaimer)} $engineLabel$noData",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageBubble(message: ChatMessage, pending: Boolean) {
    val mine = message.role == AiRole.USER
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .then(
                    if (mine) {
                        Modifier.background(MaterialTheme.colorScheme.primary, shape)
                    } else {
                        Modifier
                            .background(MaterialTheme.colorScheme.surface, shape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    },
                )
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            when {
                message.error != null -> Column {
                    // An answer that stopped early keeps what arrived, with the reason under it.
                    if (message.text.isNotBlank()) {
                        Text(simpleMarkdown(message.text), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        stringResource(message.error.messageRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (message.errorDetail.isNotBlank()) {
                        Text(
                            message.errorDetail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                message.text.isEmpty() && pending -> Text(
                    stringResource(R.string.coach_thinking),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                mine -> Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (message.previews.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            message.previews.forEachIndexed { index, preview ->
                                Image(
                                    preview,
                                    contentDescription = stringResource(R.string.coach_photo_sent, index + 1),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(10.dp)),
                                )
                            }
                        }
                    }
                    if (message.text.isNotEmpty()) {
                        Text(message.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                else -> Text(simpleMarkdown(message.text), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Enough Markdown for chat answers (web `SimpleMarkdown`): «- » lists as bullets, **bold**, no # headings. */
internal fun simpleMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.trim().lines().map { line ->
        line.replace(Regex("^#{1,6}\\s+"), "").replace(Regex("^\\s*[-*•]\\s+"), "• ")
    }
    lines.forEachIndexed { index, line ->
        val parts = line.split("**")
        parts.forEachIndexed { i, part ->
            // Odd pieces sit between a pair of ** (an unpaired ** leaves the rest plain).
            if (i % 2 == 1 && i < parts.size - 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
        }
        if (index < lines.size - 1) append('\n')
    }
}
