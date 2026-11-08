package com.tim.d2a.ui

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.tim.d2a.TextCapture
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import com.tim.d2a.R
import com.tim.d2a.data.AnkiDeck
import com.tim.d2a.data.AnkiDestination
import com.tim.d2a.data.AnkiDroidClient
import com.tim.d2a.data.AnkiDroidOperations
import com.tim.d2a.data.AnkiStudyNoteSpec
import com.tim.d2a.data.StudyContext
import com.tim.d2a.data.CardDraft
import com.tim.d2a.data.DraftStorage
import com.tim.d2a.data.DictionaryEntry
import com.tim.d2a.data.DictionaryImportProgress
import com.tim.d2a.data.DictionaryInfo
import com.tim.d2a.data.LookupDirection
import com.tim.d2a.data.LookupResult
import com.tim.d2a.data.MdxDictionary
import com.tim.d2a.data.OpenRouterClient
import com.tim.d2a.data.OpenRouterModel
import com.tim.d2a.data.OpenRouterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private enum class AppScreen { LOOKUP, CARD_EDITOR, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DictAnkiApp(
    capture: TextCapture? = null,
    reviewRequest: Long = 0,
    onExitCapture: () -> Unit = {},
    openRouterService: OpenRouterService? = null,
    persistSettings: Boolean = true,
    preferencesOverride: SharedPreferences? = null,
    dictionaryOverride: MdxDictionary? = null,
    dictionaryPickerOverride: (((Uri?) -> Unit) -> Unit)? = null,
    ankiOperationsOverride: AnkiDroidOperations? = null,
    imageServiceOverride: com.tim.d2a.data.FlashcardImageService? = null,
) {
    val context = LocalContext.current.applicationContext
    var startup by remember(preferencesOverride, dictionaryOverride, persistSettings) {
        mutableStateOf<AppStartup?>(null)
    }
    var startupError by remember { mutableStateOf<String?>(null) }
    var startupAttempt by remember { mutableStateOf(0) }
    LaunchedEffect(preferencesOverride, dictionaryOverride, persistSettings, startupAttempt) {
        startupError = null
        try {
            startup = withContext(Dispatchers.IO) {
                val prefs = preferencesOverride ?: securePreferences(context)
                val dictionary = dictionaryOverride ?: MdxDictionary(
                    context.contentResolver, File(context.filesDir, "dictionary-index.sqlite"),
                )
                AppStartup(
                    prefs, dictionary, dictionary.info(), dictionary.importedInfos(),
                    if (persistSettings) prefs.getString(KEY_OPENROUTER, "").orEmpty() else "",
                    prefs.getString(KEY_MODEL, "google/gemini-2.5-flash-lite").orEmpty(),
                    prefs.getString(KEY_LANGUAGE, "English").orEmpty(),
                    if (persistSettings) readDraftState(prefs, "draft").firstOrNull() else null,
                    if (persistSettings) readDraftState(prefs, "saved_drafts").toSet() else emptySet(),
                    if (persistSettings) readDraftState(prefs, "draft_archive") else emptyList(),
                    if (persistSettings) prefs.getString(KEY_ANKI_DECK, null) else null,
                    prefs.getString("image_model", "").orEmpty(),
                    prefs.getString("image_sort", "cost").orEmpty(),
                    prefs.getString("auto_generate_images", "true") != "false",
                )
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            startupError = error.message ?: "Could not load the app."
        }
    }
    val loaded = startup
    if (loaded == null) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (startupError == null) {
                    CircularProgressIndicator()
                } else {
                    Text(startupError.orEmpty())
                    TextButton(onClick = { startupAttempt++ }) { Text("Retry") }
                }
            }
        }
        return
    }
    LoadedDictAnkiApp(loaded, openRouterService, persistSettings, dictionaryPickerOverride, ankiOperationsOverride, capture, onExitCapture, imageServiceOverride, reviewRequest)
}

private data class AppStartup(
    val prefs: SharedPreferences,
    val dictionary: MdxDictionary,
    val dictionaryInfo: DictionaryInfo,
    val importedDictionaries: List<DictionaryInfo>,
    val apiKey: String,
    val model: String,
    val targetLanguage: String,
    val draft: CardDraft?,
    val savedDrafts: Set<CardDraft>,
    val archivedDrafts: List<CardDraft>,
    val selectedDeckId: String?,
    val imageModel: String,
    val imageSort: String,
    val autoGenerateImages: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LoadedDictAnkiApp(
    startup: AppStartup,
    openRouterService: OpenRouterService?,
    persistSettings: Boolean,
    dictionaryPickerOverride: (((Uri?) -> Unit) -> Unit)?,
    ankiOperationsOverride: AnkiDroidOperations?,
    capture: TextCapture?,
    onExitCapture: () -> Unit,
    imageServiceOverride: com.tim.d2a.data.FlashcardImageService?,
    reviewRequest: Long,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = startup.prefs
    // Serialize blocking commits so an older snapshot cannot overwrite a newer one.
    val persistenceMutex = remember { Mutex() }
    val dictionary = startup.dictionary
    val anki = ankiOperationsOverride ?: remember { AnkiDroidClient(context) }
    val openRouter = openRouterService ?: remember { OpenRouterClient() }
    val imageService = imageServiceOverride ?: remember { com.tim.d2a.data.FlashcardImageClient() }

    var screen by rememberSaveable {
        mutableStateOf(AppScreen.LOOKUP)
    }
    var lookupDirection by rememberSaveable { mutableStateOf(LookupDirection.SPANISH_TO_ENGLISH) }
    var query by rememberSaveable { mutableStateOf("") }
    var contextText by rememberSaveable { mutableStateOf("") }
    var contextExpanded by rememberSaveable { mutableStateOf(false) }
    var lookup by remember { mutableStateOf(LookupResult("", emptyList(), "", startup.dictionaryInfo.name)) }
    var dictionaryInfo by remember { mutableStateOf(startup.dictionaryInfo) }
    var importedDictionaries by remember { mutableStateOf(startup.importedDictionaries) }
    val importProgressState = remember { MutableStateFlow<DictionaryImportProgress?>(null) }
    val importProgress by importProgressState.collectAsState()
    var draft by rememberSaveable(saver = androidx.compose.runtime.saveable.Saver(
        save = { state: androidx.compose.runtime.MutableState<CardDraft?> -> DraftStorage.encode(listOfNotNull(state.value)) },
        restore = { mutableStateOf(DraftStorage.decode(it).firstOrNull()) },
    )) { mutableStateOf(startup.draft?.takeUnless { it.copy(noteKey = "") in startup.savedDrafts }) }
    val generation by com.tim.d2a.CardGenerationService.state.collectAsState()
    val isGenerating = generation.running
    var handledGeneration by rememberSaveable { mutableStateOf(0L) }
    var isSaving by remember { mutableStateOf(false) }
    var cardStatus by remember { mutableStateOf<String?>(null) }
    var savedDrafts by remember { mutableStateOf(startup.savedDrafts) }
    var archivedDrafts by remember { mutableStateOf(startup.archivedDrafts.filterNot { it.copy(noteKey = "") in startup.savedDrafts }) }
    var showDrafts by remember { mutableStateOf(false) }
    var captureHandled by rememberSaveable { mutableStateOf<String?>(null) }
    var persistenceError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(draft, savedDrafts, archivedDrafts, isGenerating) {
        if (isGenerating || generation.result != null) return@LaunchedEffect
        val currentDraft = listOfNotNull(draft?.takeUnless { it.copy(noteKey = "") in savedDrafts })
        val currentSaved = savedDrafts.toList()
        val currentArchive = archivedDrafts
        if (persistSettings) try {
            persistenceMutex.withLock { withContext(Dispatchers.IO) {
                check(prefs.edit()
                    .putString("draft", DraftStorage.encode(currentDraft))
                    .putString("saved_drafts", DraftStorage.encode(currentSaved))
                    .putString("draft_archive", DraftStorage.encode(currentArchive)).commit()) {
                    "Could not store drafts. Free device storage before closing the app."
                }
            } }
            persistenceError = null
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { persistenceError = error.message ?: "Could not store drafts." }
    }
    fun wasAdded(card: CardDraft) = card.copy(noteKey = "") in savedDrafts
    LaunchedEffect(generation) {
        if (!generation.running && generation.id > handledGeneration) {
            handledGeneration = generation.id
            generation.result?.let { result ->
                result.previousDraft?.let { archivedDrafts = (archivedDrafts + it).distinctBy { card -> card.noteKey } }
                draft = result.draft
                lookup = result.lookup
                cardStatus = result.message
                screen = AppScreen.CARD_EDITOR
                com.tim.d2a.CardGenerationService.state.value = generation.copy(result = null)
            }
        }
    }
    LaunchedEffect(reviewRequest) {
        if (reviewRequest != 0L) {
            val currentGen = com.tim.d2a.CardGenerationService.state.value
            if (currentGen.running) {
                screen = AppScreen.LOOKUP
            } else {
                currentGen.result?.let { result ->
                    handledGeneration = currentGen.id
                    result.previousDraft?.let { archivedDrafts = (archivedDrafts + it).distinctBy { card -> card.noteKey } }
                    draft = result.draft
                    lookup = result.lookup
                    cardStatus = result.message
                    com.tim.d2a.CardGenerationService.state.value = currentGen.copy(result = null)
                }
                if (draft != null) screen = AppScreen.CARD_EDITOR
                else screen = AppScreen.LOOKUP
            }
        }
    }
    var isUpdatingTemplates by remember { mutableStateOf(false) }
    var selectedDeckId by rememberSaveable {
        mutableStateOf(startup.selectedDeckId)
    }
    var decks by remember { mutableStateOf(emptyList<AnkiDeck>()) }
    var loadingDecks by remember { mutableStateOf(true) }
    var deckError by remember { mutableStateOf<String?>(null) }
    var deckRefresh by remember { mutableStateOf(0) }
    var ankiInstalled by remember { mutableStateOf(false) }
    var ankiHasPermission by remember { mutableStateOf(false) }
    val selectedDeck = decks.firstOrNull { it.id.toString() == selectedDeckId }

    LifecycleResumeEffect(Unit) {
        deckRefresh += 1
        onPauseOrDispose { }
    }
    LaunchedEffect(anki, deckRefresh) {
        loadingDecks = true
        deckError = null
        try {
            val availability = withContext(Dispatchers.IO) { anki.isInstalled() to anki.hasPermission() }
            ankiInstalled = availability.first
            ankiHasPermission = availability.second
            decks = when {
                !ankiInstalled -> error("Install AnkiDroid to choose a deck.")
                !ankiHasPermission -> error("Allow AnkiDroid access to choose a deck.")
                else -> anki.listDecks()
            }
            if (decks.isEmpty()) deckError = "Create a deck in AnkiDroid, then refresh."
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            decks = emptyList()
            deckError = error.message ?: "Could not load AnkiDroid decks."
        } finally {
            loadingDecks = false
        }
    }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var apiKey by rememberSaveable { mutableStateOf(startup.apiKey) }
    var model by rememberSaveable { mutableStateOf(startup.model) }
    var imageModel by rememberSaveable { mutableStateOf(startup.imageModel) }
    var imageSort by rememberSaveable { mutableStateOf(startup.imageSort) }
    var autoGenerateImages by rememberSaveable { mutableStateOf(startup.autoGenerateImages) }
    var targetLanguage by rememberSaveable { mutableStateOf(startup.targetLanguage) }
    var validatedApiKey by remember { mutableStateOf<String?>(null) }
    val aiConnected = apiKey.isNotBlank() && validatedApiKey == apiKey && model.isNotBlank()

    LaunchedEffect(openRouter) {
        val savedKey = apiKey
        if (savedKey.isNotBlank()) {
            runCatching { openRouter.validateApiKey(savedKey) }
                .onSuccess { if (apiKey == savedKey) validatedApiKey = savedKey }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; statusMessage = it.message ?: "Could not connect to OpenRouter." }
        }
    }

    fun saveSetting(key: String, value: String) {
        if (persistSettings) scope.launch {
            try {
                persistenceMutex.withLock { withContext(Dispatchers.IO) {
                    check(prefs.edit().putString(key, value).commit()) { "Could not store settings." }
                } }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { persistenceError = error.message ?: "Could not store settings." }
        }
    }

    val selectDeck: (AnkiDeck) -> Unit = { deck ->
        selectedDeckId = deck.id.toString()
        saveSetting(KEY_ANKI_DECK, deck.id.toString())
    }

    fun importDictionary(uri: Uri?, persistPermission: Boolean) {
        if (uri == null || importProgressState.value != null) return
        importProgressState.value = DictionaryImportProgress("Preparing dictionary import…")
        scope.launch {
            try {
                runCatching { withContext(Dispatchers.IO) {
                    if (persistPermission) {
                        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    }
                    val imported = dictionary.import(uri) { progress -> importProgressState.value = progress }
                    val savedName = if (persistSettings) prefs.getString(dictionaryNamePreferenceKey(uri.toString()), null) else null
                    savedName?.takeIf { it.isNotBlank() }?.let { dictionary.rename(uri.toString(), it) } ?: imported
                } }.onSuccess { imported ->
                    dictionaryInfo = withContext(Dispatchers.IO) { dictionary.info() }
                    importedDictionaries = withContext(Dispatchers.IO) { dictionary.importedInfos() }
                    lookup = withContext(Dispatchers.IO) { dictionary.lookup(query, contextText, lookupDirection) }
                    if (persistSettings) withContext(Dispatchers.IO) {
                        val savedUris = prefs.getStringSet(KEY_MDX_URIS, null)?.toMutableSet()
                            ?: prefs.getString(KEY_MDX_URI_LEGACY, null)?.let { mutableSetOf(it) }
                            ?: mutableSetOf()
                        savedUris += uri.toString()
                        prefs.edit().putStringSet(KEY_MDX_URIS, savedUris).remove(KEY_MDX_URI_LEGACY).apply()
                    }
                    statusMessage = "Imported ${imported.name}"
                }.onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    statusMessage = it.message ?: "Could not import this dictionary"
                }
            } finally {
                importProgressState.value = null
            }
        }
    }

    fun renameDictionary(info: DictionaryInfo, name: String) {
        val uri = info.uri ?: return
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { dictionary.rename(uri, name) } }.onSuccess { renamed ->
                if (renamed == null) return@onSuccess
                saveSetting(dictionaryNamePreferenceKey(uri), renamed.name)
                dictionaryInfo = withContext(Dispatchers.IO) { dictionary.info() }
                importedDictionaries = withContext(Dispatchers.IO) { dictionary.importedInfos() }
                lookup = withContext(Dispatchers.IO) { dictionary.lookup(query, contextText, lookupDirection) }
                statusMessage = "Renamed to ${renamed.name}"
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; statusMessage = it.message ?: "Could not rename this dictionary" }
        }
    }

    fun removeDictionary(info: DictionaryInfo) {
        val uri = info.uri ?: return
        scope.launch {
            try {
                if (!withContext(Dispatchers.IO) { dictionary.remove(uri) }) return@launch
                if (persistSettings) withContext(Dispatchers.IO) {
                    val savedUris = prefs.getStringSet(KEY_MDX_URIS, null)?.toMutableSet() ?: mutableSetOf()
                    savedUris.remove(uri)
                    prefs.edit()
                        .putStringSet(KEY_MDX_URIS, savedUris)
                        .remove(KEY_MDX_URI_LEGACY)
                        .remove(dictionaryNamePreferenceKey(uri))
                        .apply()
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            Uri.parse(uri),
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
                dictionaryInfo = withContext(Dispatchers.IO) { dictionary.info() }
                importedDictionaries = withContext(Dispatchers.IO) { dictionary.importedInfos() }
                lookup = withContext(Dispatchers.IO) { dictionary.lookup(query, contextText, lookupDirection) }
                statusMessage = "Removed ${info.name}"
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { statusMessage = error.message ?: "Could not remove this dictionary." }
        }
    }

    LaunchedEffect(Unit) {
        if (persistSettings) {
            importProgressState.value = DictionaryImportProgress("Restoring dictionaries…")
            try {
                val restored = withContext(Dispatchers.IO) {
                    val persistedUris = prefs.getStringSet(KEY_MDX_URIS, null)?.toList()
                        ?: prefs.getString(KEY_MDX_URI_LEGACY, null)?.let(::listOf)
                        ?: emptyList()
                    val indexed = startup.importedDictionaries.associateBy { it.uri }
                    val failed = mutableListOf<String>()
                    var restoreError: String? = null
                    persistedUris.forEach { persistedUri ->
                        try {
                            val existing = indexed[persistedUri]
                            if (existing == null) {
                                importProgressState.value = DictionaryImportProgress("Restoring dictionaries…")
                            }
                            val info = existing ?: dictionary.import(Uri.parse(persistedUri)) {
                                importProgressState.value = it
                            }
                            prefs.getString(dictionaryNamePreferenceKey(persistedUri), null)
                                ?.takeIf { it.isNotBlank() && it != info.name }
                                ?.let { dictionary.rename(persistedUri, it) }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            failed += persistedUri
                            restoreError = "Could not restore dictionary: ${error.message}. You can add it again."
                        }
                    }
                    if (failed.isNotEmpty() || prefs.contains(KEY_MDX_URI_LEGACY)) {
                        prefs.edit().putStringSet(KEY_MDX_URIS, (persistedUris - failed.toSet()).toSet())
                            .remove(KEY_MDX_URI_LEGACY).apply()
                    }
                    Triple(dictionary.info(), dictionary.importedInfos(), restoreError)
                }
                dictionaryInfo = restored.first
                importedDictionaries = restored.second
                restored.third?.let { statusMessage = it }
                // Startup never searches a saved or placeholder query. Lookup is explicitly requested.
                lookup = lookup.copy(dictionaryName = dictionaryInfo.name)
            } finally {
                importProgressState.value = null
            }
        }
    }

    val dictionaryPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        importDictionary(uri, persistPermission = true)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        deckRefresh += 1
        statusMessage = if (granted) "AnkiDroid access granted" else "AnkiDroid access is still needed to add cards"
    }

    var lookupJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun lookupWord() {
        val requestedQuery = query
        val requestedContext = contextText
        val requestedDirection = lookupDirection
        lookupJob?.cancel()
        statusMessage = null
        lookupJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    dictionary.lookup(requestedQuery, requestedContext, requestedDirection)
                }
                if (query == requestedQuery && contextText == requestedContext && lookupDirection == requestedDirection) {
                    lookup = result
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { statusMessage = error.message ?: "Lookup failed. Try again." }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) statusMessage = "Generation continues. Enable notifications in Android Settings for completion alerts."
    }
    LaunchedEffect(generation.error) { generation.error?.let { statusMessage = it } }

    fun generateCard(entry: DictionaryEntry? = null) {
        if (!aiConnected) {
            statusMessage = "Connect AI in Settings to generate a card. Local lookup works offline."
            screen = AppScreen.SETTINGS
        } else if (query.length > 200 || contextText.length > 12_000) {
            statusMessage = "Use a word or phrase up to 200 characters and a surrounding passage up to 12,000 characters."
        } else if (!isGenerating && query.isNotBlank()) {
            val requestedQuery = query
            val requestedContext = contextText
            val requestedDirection = lookupDirection
            val requestedKey = apiKey
            val requestedModel = model
            val requestedImageModel = imageModel
            val requestedAutoImages = autoGenerateImages
            val requestedLanguage = targetLanguage.ifBlank { "English" }
            val appContext = context.applicationContext
            val taskDictionary = dictionary
            val taskRouter = openRouter
            val taskImages = imageService
            val taskPrefs = prefs
            val shouldPersist = persistSettings
            val previousDraft = draft?.takeUnless(::wasAdded)
            val previousArchive = archivedDrafts
            val task: suspend () -> com.tim.d2a.GenerationResult = {
                    val currentLookup = withContext(Dispatchers.IO) {
                        taskDictionary.lookup(requestedQuery, requestedContext, requestedDirection)
                    }
                    val selected = entry?.takeIf { it in currentLookup.entries }
                        ?: currentLookup.entries.firstOrNull { it.headword.equals(currentLookup.query, true) }
                    val references = (if (entry == null) currentLookup.entries else currentLookup.entries.filter { it.source == entry.source }).take(12).map { it.copy(definition = it.definition.take(2000), example = it.example.take(1000)) }
                    val ai = taskRouter.generateCard(
                        apiKey = requestedKey, model = requestedModel,
                        targetLanguage = requestedLanguage, lookupDirection = requestedDirection,
                        query = currentLookup.query, context = requestedContext.trim(),
                        dictionaryEntry = selected?.copy(definition = selected.definition.take(2000), example = selected.example.take(1000)),
                        additionalDictionaryEntries = references.filterNot { it.headword == selected?.headword && it.source == selected?.source },
                    )
                    val generatedDraft = CardDraft(
                        word = ai.spanishWord, context = ai.spanishContext,
                        meaning = ai.meaning, translation = ai.translation,
                        explanation = ai.explanation,
                        definitions = ai.dictionaryNotes,
                        tags = ai.tags.joinToString(", "), productionPrompt = ai.productionPrompt,
                        productionAnswer = ai.productionAnswer, includeProduction = true,
                        productionContext = ai.productionContext,
                        productionTranslation = ai.productionTranslation,
                        recognitionAlternatives = ai.recognitionAlternatives,
                        productionAlternatives = ai.productionAlternatives,
                        imagePrompt = ai.imagePrompt,
                    )
                    var resultDraft = generatedDraft
                    var resultStatus: String? = null
                    suspend fun persistGeneratedDraft() {
                        if (shouldPersist) persistenceMutex.withLock { withContext(Dispatchers.IO) {
                            val archive = (previousArchive + listOfNotNull(previousDraft)).distinctBy { it.noteKey }
                            check(taskPrefs.edit().putString("draft", DraftStorage.encode(listOf(resultDraft)))
                                .putString("draft_archive", DraftStorage.encode(archive)).commit()) { "Could not store generated draft." }
                        } }
                    }
                    persistGeneratedDraft()
                    if (com.tim.d2a.CardGenerationService.state.value.running) {
                        com.tim.d2a.CardGenerationService.state.value = com.tim.d2a.CardGenerationService.state.value.copy(
                            result = com.tim.d2a.GenerationResult(resultDraft, currentLookup, "Text ready; image generation was interrupted.", previousDraft))
                    }
                    if (requestedAutoImages) {
                        if (requestedImageModel.isBlank()) {
                            resultStatus = "Choose an image model under Memory image to enable automatic images for future cards."
                        } else {
                            try {
                                val selectedImageModel = taskImages.models(requestedKey).firstOrNull { it.id == requestedImageModel }
                                    ?: error("Saved image model is unavailable. Choose another under Memory image.")
                                val image = taskImages.generate(appContext, requestedKey, selectedImageModel,
                                    com.tim.d2a.data.FlashcardImagePrompt.build(generatedDraft))
                                resultDraft = generatedDraft.copy(imageFilename = image.filename)
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (failure: Exception) {
                                resultStatus = "Card ready. Image generation failed: ${failure.message ?: "Try again under Memory image."}"
                            }
                        }
                    }

                persistGeneratedDraft()
                com.tim.d2a.GenerationResult(resultDraft, currentLookup, resultStatus, previousDraft)
            }
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                !prefs.getBoolean("notification_permission_requested", false)) {
                prefs.edit().putBoolean("notification_permission_requested", true).apply()
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            try {
                com.tim.d2a.CardGenerationService.start(appContext, task)
            } catch (error: Exception) {
                statusMessage = error.message ?: "Could not start generation. Try again with the app open."
            }
        }
    }

    var captureGenerationPending by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(capture?.id, aiConnected, isGenerating) {
        if (capture != null && capture.id != captureHandled && (apiKey.isBlank() || aiConnected)) {
            val text = capture.text
            if (!capture.shared && text.length <= 100 && text.split(Regex("\\s+")).size <= 8) {
                query = text
                contextText = ""
            } else {
                query = ""
                contextText = text
                contextExpanded = true
            }
            lookup = LookupResult("", emptyList(), "", dictionaryInfo.name)
            screen = AppScreen.LOOKUP
            captureHandled = capture.id
            captureGenerationPending = query.isNotBlank()
            if (captureGenerationPending && !aiConnected) generateCard()
        }
        if (captureGenerationPending && aiConnected && !isGenerating) {
            captureGenerationPending = false
            generateCard()
        }
    }
    fun navigateBack() {
        if (capture != null) onExitCapture() else screen = AppScreen.LOOKUP
    }

    BackHandler(enabled = capture != null || screen != AppScreen.LOOKUP) {
        navigateBack()
    }

    if (showDrafts) AlertDialog(
        onDismissRequest = { showDrafts = false },
        title = { Text("Unfinished drafts") },
        text = { LazyColumn { itemsIndexed(archivedDrafts) { index, card ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val previous = draft
                    archivedDrafts = archivedDrafts.filterIndexed { i, _ -> i != index }
                    if (previous != null && !wasAdded(previous)) archivedDrafts = archivedDrafts + previous
                    draft = card
                    cardStatus = null
                    showDrafts = false
                    screen = AppScreen.CARD_EDITOR
                }, modifier = Modifier.weight(1f)) { Text(card.word) }
                IconButton(onClick = { archivedDrafts = archivedDrafts.filterIndexed { i, _ -> i != index } }) { Icon(Icons.Default.Delete, "Delete draft") }
            }
        } } },
        confirmButton = { TextButton(onClick = { showDrafts = false }) { Text("Close") } },
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        AnimatedContent(
            targetState = screen,
            // Scaffold already reserves safe drawing space, including the IME.
            // Mark it consumed so child imePadding does not reserve it again.
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen",
        ) { target ->
            when (target) {
                AppScreen.LOOKUP -> LookupScreen(
                    lookupDirection = lookupDirection,
                    onDirectionChange = { lookupDirection = it; lookup = lookup.copy(query = "", entries = emptyList()); statusMessage = null },
                    query = query,
                    onQueryChange = { query = it; lookup = lookup.copy(query = "", entries = emptyList()) },
                    contextText = contextText,
                    onContextChange = { contextText = it },
                    contextExpanded = contextExpanded,
                    onContextExpandedChange = { contextExpanded = it },
                    dictionaryInfo = dictionaryInfo,
                    result = lookup,
                    isGenerating = isGenerating,
                    statusMessage = persistenceError ?: statusMessage,
                    onSettings = { screen = AppScreen.SETTINGS },
                    draftWord = draft?.takeUnless(::wasAdded)?.word,
                    draftAdded = draft?.let(::wasAdded) == true,
                    onResumeDraft = { screen = AppScreen.CARD_EDITOR },
                    onLookup = ::lookupWord,
                    aiConnected = aiConnected,
                    onDrafts = { showDrafts = true },
                    draftCount = archivedDrafts.size,
                    onCancelGeneration = { com.tim.d2a.CardGenerationService.cancel(context); statusMessage = "Generation cancelled. Your previous draft is preserved." },
                    onGenerate = { generateCard(it) },
                    autoGenerateImages = autoGenerateImages,
                    onAutoGenerateImages = { autoGenerateImages = it; saveSetting("auto_generate_images", it.toString()) },
                )

                AppScreen.CARD_EDITOR -> draft?.let { current ->
                    CardEditorScreen(
                        draft = current,
                        imageService = imageService,
                        apiKey = apiKey, imageModel = imageModel, imageSort = imageSort,
                        onImageModel = { imageModel = it; saveSetting("image_model", it) },
                        onImageSort = { imageSort = it; saveSetting("image_sort", it) },
                        isSaving = isSaving,
                        alreadyAdded = wasAdded(current),
                        decks = decks,
                        selectedDeck = selectedDeck,
                        missingDeck = selectedDeckId != null && selectedDeck == null,
                        loadingDecks = loadingDecks,
                        deckError = deckError,
                        onSelectDeck = selectDeck,
                        onRefreshDecks = { deckRefresh += 1 },
                        onGrantAnki = { permissionLauncher.launch(ANKI_PERMISSION) },
                        needsAnkiPermission = ankiInstalled && !ankiHasPermission,
                        onAnkiSettings = { screen = AppScreen.SETTINGS },
                        statusMessage = persistenceError ?: cardStatus,
                        onBack = { navigateBack() },
                        onDraftChange = { draft = it.copy(noteKey = java.util.UUID.randomUUID().toString()); cardStatus = null },
                        onSave = {
                            val chosenDeck = selectedDeck
                            if (chosenDeck == null || isSaving || wasAdded(current)) return@CardEditorScreen
                            isSaving = true
                            scope.launch {
                                try {
                                    cardStatus = null
                                    runCatching {
                                        if (!withContext(Dispatchers.IO) { anki.isInstalled() }) error("Install AnkiDroid before adding a card.")
                                        if (!anki.hasPermission()) error("Allow Dict·Anki to access AnkiDroid, then try again.")
                                        val deck = anki.listDecks().firstOrNull { it.id == chosenDeck.id }
                                            ?: error("The selected deck is no longer available. Refresh and choose a deck.")
                                        val modelId = anki.ensureStudyModel()
                                        val destination = AnkiDestination(deck, modelId, AnkiDroidClient.STUDY_MODEL_NAME)
                                        anki.addNote(destination, current)
                                    }.onSuccess {
                                        savedDrafts = savedDrafts + current.copy(noteKey = "")
                                        archivedDrafts = archivedDrafts.filterNot(::wasAdded)
                                        cardStatus = "Added to ${chosenDeck.name}"
                                    }.onFailure {
                                        if (it is kotlinx.coroutines.CancellationException) throw it
                                        cardStatus = it.message ?: "Could not add the note"
                                    }
                                } finally { isSaving = false }
                            }
                        },
                    )
                } ?: LookupScreen(
                    lookupDirection = lookupDirection,
                    onDirectionChange = { lookupDirection = it; lookup = lookup.copy(query = "", entries = emptyList()); statusMessage = null },
                    query = query,
                    onQueryChange = { query = it; lookup = lookup.copy(query = "", entries = emptyList()) },
                    contextText = contextText,
                    onContextChange = { contextText = it },
                    contextExpanded = contextExpanded,
                    onContextExpandedChange = { contextExpanded = it },
                    dictionaryInfo = dictionaryInfo,
                    result = lookup,
                    isGenerating = false,
                    statusMessage = persistenceError ?: statusMessage,
                    onSettings = { screen = AppScreen.SETTINGS },
                    onLookup = ::lookupWord,
                    onGenerate = {},
                )

                AppScreen.SETTINGS -> SettingsScreen(
                    dictionaryInfo = dictionaryInfo,
                    importedDictionaries = importedDictionaries,
                    openRouter = openRouter,
                    apiKey = apiKey,
                    model = model,
                    targetLanguage = targetLanguage,
                    decks = decks,
                    selectedDeck = selectedDeck,
                    missingDeck = selectedDeckId != null && selectedDeck == null,
                    loadingDecks = loadingDecks,
                    deckError = deckError,
                    onSelectDeck = selectDeck,
                    onRefreshDecks = { deckRefresh += 1 },
                    isUpdatingTemplates = isUpdatingTemplates,
                    onUpdateTemplates = {
                        if (!isUpdatingTemplates) {
                            isUpdatingTemplates = true
                            scope.launch {
                                try {
                                    anki.updateStudyTemplates()
                                    statusMessage = "Updated the v2 card layout. Existing note content and review progress are preserved."
                                } catch (error: Exception) {
                                    if (error is kotlinx.coroutines.CancellationException) throw error
                                    statusMessage = error.message ?: "Could not update the card layout."
                                } finally {
                                    isUpdatingTemplates = false
                                }
                            }
                        }
                    },
                    ankiInstalled = ankiInstalled,
                    ankiHasPermission = ankiHasPermission,
                    statusMessage = persistenceError ?: statusMessage,
                    importProgress = importProgress,
                    onBack = { navigateBack() },
                    onApiKeyChange = {
                        validatedApiKey = null
                        screen = AppScreen.SETTINGS
                        apiKey = it
                        saveSetting(KEY_OPENROUTER, it)
                    },
                    onKeyValidated = { key -> if (apiKey == key) validatedApiKey = key },
                    aiConnected = aiConnected,
                    onModelChange = { model = it; saveSetting(KEY_MODEL, it) },
                    onTargetLanguageChange = { targetLanguage = it; saveSetting(KEY_LANGUAGE, it.ifBlank { "English" }) },
                    onImportDictionary = {
                        if (dictionaryPickerOverride != null) {
                            dictionaryPickerOverride { uri -> importDictionary(uri, persistPermission = false) }
                        } else {
                            dictionaryPickerLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                        }
                    },
                    onRenameDictionary = ::renameDictionary,
                    onRemoveDictionary = ::removeDictionary,
                    onDictionaryDirection = { info, direction -> scope.launch {
                        try {
                            withContext(Dispatchers.IO) { dictionary.setDirection(info.uri.orEmpty(), direction) }
                            importedDictionaries = withContext(Dispatchers.IO) { dictionary.importedInfos() }
                            lookup = lookup.copy(query = "", entries = emptyList())
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (error: Exception) { statusMessage = error.message ?: "Could not update dictionary direction." }
                    } },
                    onPreviewDictionary = { info -> withContext(Dispatchers.IO) { dictionary.preview(info.uri.orEmpty()) } },
                    onGrantAnki = { permissionLauncher.launch(ANKI_PERMISSION) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookupScreen(
    lookupDirection: LookupDirection,
    onDirectionChange: (LookupDirection) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    contextText: String,
    onContextChange: (String) -> Unit,
    contextExpanded: Boolean,
    onContextExpandedChange: (Boolean) -> Unit,
    dictionaryInfo: DictionaryInfo,
    result: LookupResult,
    isGenerating: Boolean,
    statusMessage: String?,
    onSettings: () -> Unit,
    onLookup: () -> Unit,
    onGenerate: (DictionaryEntry?) -> Unit,
    draftWord: String? = null,
    draftAdded: Boolean = false,
    onResumeDraft: () -> Unit = {},
    aiConnected: Boolean = true,
    onDrafts: () -> Unit = {},
    draftCount: Int = 0,
    onCancelGeneration: () -> Unit = {},
    autoGenerateImages: Boolean = true,
    onAutoGenerateImages: (Boolean) -> Unit = {},
) {
    val scrollState = rememberScrollState()
    var wordValue by remember { mutableStateOf(TextFieldValue(query)) }
    var contextValue by remember { mutableStateOf(TextFieldValue(contextText)) }
    LaunchedEffect(contextText) {
        if (contextValue.text != contextText) contextValue = TextFieldValue(contextText)
    }
    val wordInteractions = remember { MutableInteractionSource() }
    LaunchedEffect(query) {
        if (wordValue.text != query) wordValue = TextFieldValue(query)
    }
    LaunchedEffect(wordInteractions) {
        wordInteractions.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                wordValue = wordValue.copy(selection = TextRange(0, wordValue.text.length))
            }
        }
    }
    var selectedSource by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(result.query) { selectedSource = null }
    val visibleEntries = result.entries.filter { selectedSource == null || it.source == selectedSource }
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding().testTag("lookup-screen-list"),
        contentPadding = PaddingValues(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            AppTopBar(
                title = "dict·anki",
                subtitle = "quick lookup → lasting memory",
                leading = {
                    Image(
                        painter = painterResource(R.drawable.ic_app_logo),
                        contentDescription = null,
                        modifier = Modifier.padding(start = 16.dp, end = 4.dp).size(44.dp),
                    )
                },
                trailing = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") } },
            )
        }
        if (draftCount > 0) item { TextButton(onClick = onDrafts, modifier = Modifier.padding(horizontal = 20.dp)) { Text("Other drafts · $draftCount") } }
        if (!aiConnected) item { TextButton(onClick = onSettings, modifier = Modifier.padding(horizontal = 20.dp)) { Text("Connect AI to generate cards · lookup works offline") } }
        if (draftWord != null) item {
            OutlinedButton(
                onClick = onResumeDraft,
                enabled = !isGenerating,
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().testTag("resume-draft"),
            ) { Text("${if (draftAdded) "Review saved card" else "Continue draft"}: $draftWord") }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("What did you hear?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(if (query.isBlank() && contextText.isNotBlank()) "Highlight the word or phrase in the context below, then tap Use selected phrase." else "Type a word or phrase. Add the surrounding sentence when the meaning depends on context.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f)),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LookupDirection.entries.forEach { direction ->
                            FilterChip(
                                selected = lookupDirection == direction,
                                onClick = { onDirectionChange(direction) },
                                enabled = !isGenerating,
                                label = { Text(direction.label) },
                                modifier = Modifier.testTag("lookup-direction:${direction.name}"),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = wordValue,
                        onValueChange = { wordValue = it; if (it.text != query) onQueryChange(it.text) },
                        interactionSource = wordInteractions,
                        enabled = !isGenerating,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Word or phrase") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (query.isNotBlank()) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onLookup() }),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onContextExpandedChange(!contextExpanded) }.padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.MoreHoriz, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Add context", fontWeight = FontWeight.SemiBold)
                            Text(if (contextText.isBlank()) "Optional, but helps disambiguate" else "Context included", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ExpandMore, null)
                    }
                    if (contextExpanded) {
                        OutlinedTextField(
                            value = contextValue,
                            onValueChange = {
                                contextValue = it
                                if (it.text != contextText) onContextChange(it.text)
                            },
                            enabled = !isGenerating,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5,
                            label = { Text("Where did you hear it?") },
                            placeholder = { Text("Paste the sentence, subtitle, or a rough memory…") },
                            shape = RoundedCornerShape(14.dp),
                        )
                        val selectedPhrase = contextValue.text.substring(
                            contextValue.selection.min, contextValue.selection.max,
                        ).trim()
                        TextButton(
                            onClick = { onQueryChange(selectedPhrase) },
                            enabled = selectedPhrase.isNotBlank() && !isGenerating,
                            modifier = Modifier.testTag("use-selected-phrase"),
                        ) { Text("Use selected phrase") }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Generate images automatically")
                            Text("Uses your saved image model · additional cost", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = autoGenerateImages, onCheckedChange = onAutoGenerateImages,
                            enabled = !isGenerating, modifier = Modifier.testTag("auto-generate-images"))
                    }
                    Button(
                        onClick = { onGenerate(null) },
                        enabled = query.isNotBlank() && !isGenerating,
                        modifier = Modifier.fillMaxWidth().testTag("generate-card"),
                    ) { Text(if (isGenerating) "Preparing your card…" else "Generate study card") }
                    if (isGenerating) TextButton(onClick = onCancelGeneration) { Text("Cancel generation") }
                    Button(
                        onClick = onLookup,
                        enabled = query.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.Search, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Look up in ${dictionaryInfo.name}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (statusMessage != null) {
            item { StatusBanner(statusMessage) }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text(dictionaryInfo.name, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(if (dictionaryInfo.isDemo) "demo source" else "local MDX", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (result.entries.map { it.source }.distinct().size > 1) item {
            FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selectedSource == null, onClick = { selectedSource = null }, label = { Text("All dictionaries") })
                result.entries.map { it.source }.distinct().forEach { source ->
                    FilterChip(selected = selectedSource == source, onClick = { selectedSource = source }, label = { Text(source) })
                }
            }
        }
        if (result.query.isNotBlank() && result.entries.isNotEmpty() && result.entries.none { it.headword.equals(result.query, true) }) item { Text("Related headwords · no exact match for “${result.query}”", Modifier.padding(horizontal = 20.dp)) }
        if (result.query.isNotBlank()) item {
            ResultHeader(result.query, visibleEntries.size)
        }
        if (result.query.isNotBlank() && result.entries.isEmpty()) item {
            Text("No dictionary match. Try the infinitive or dictionary headword for an inflected word, or generate a card using your context.", Modifier.padding(horizontal = 20.dp))
        }
        itemsIndexed(visibleEntries, key = { index, _ -> index }) { _, entry ->
            DefinitionCard(
                entry = entry,
                isPrimary = entry == result.entries.firstOrNull(),
                onGenerate = { onGenerate(entry) },
                isGenerating = isGenerating,
            )
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp).horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip("MDX", Icons.Default.Book)
                if (contextText.isNotBlank()) AssistChip("Context-aware", Icons.Default.Link)
                AssistChip("Editable card", Icons.Default.ContentCopy)
            }
        }
    }
}

@Composable
private fun CardEditorScreen(
    draft: CardDraft,
    imageService: com.tim.d2a.data.FlashcardImageService?,
    apiKey: String,
    imageModel: String,
    imageSort: String,
    onImageModel: (String) -> Unit,
    onImageSort: (String) -> Unit,
    isSaving: Boolean,
    alreadyAdded: Boolean,
    decks: List<AnkiDeck>,
    selectedDeck: AnkiDeck?,
    missingDeck: Boolean,
    loadingDecks: Boolean,
    deckError: String?,
    onSelectDeck: (AnkiDeck) -> Unit,
    onRefreshDecks: () -> Unit,
    onGrantAnki: () -> Unit,
    needsAnkiPermission: Boolean,
    onAnkiSettings: () -> Unit,
    statusMessage: String?,
    onBack: () -> Unit,
    onDraftChange: (CardDraft) -> Unit,
    onSave: () -> Unit,
) {
    val imageControls = rememberImageControls(draft, apiKey, imageModel, imageSort, !isSaving, onImageModel, onImageSort, onDraftChange, imageService)
    val validationError = when {
        draft.word.isBlank() -> "Enter a Spanish word or phrase."
        draft.meaning.isBlank() -> "Enter the English meaning."
        else -> AnkiStudyNoteSpec.validationError(draft)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalEditorEnabled provides !isSaving) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Review card",
                subtitle = "AI-generated · always editable",
                leading = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 10.dp, tonalElevation = 4.dp) {
                Column(Modifier.imePadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    if (statusMessage != null) StatusBanner(statusMessage)
                    if (alreadyAdded) Text("This card is already saved. Edits create a new note.",
                        style = MaterialTheme.typography.bodySmall)
                    if (isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (validationError != null) {
                        Text(validationError, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("save-validation-error"))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Production card", style = MaterialTheme.typography.labelMedium)
                            Switch(
                                checked = draft.includeProduction,
                                onCheckedChange = { onDraftChange(draft.copy(includeProduction = it)) },
                                enabled = !isSaving,
                                modifier = Modifier.testTag("include-production-switch"),
                            )
                        }
                        Button(
                            onClick = onSave,
                            enabled = !alreadyAdded && !isSaving && !imageControls.isGenerating && !loadingDecks && selectedDeck != null && validationError == null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("add-to-anki"),
                            shape = RoundedCornerShape(15.dp),
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text(when { alreadyAdded -> "Added to AnkiDroid"; isSaving -> "Adding to AnkiDroid…"; else -> "Add to AnkiDroid" }, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.navigationBars,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().testTag("card-editor-screen-list"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                DeckPicker(decks, selectedDeck, missingDeck, loadingDecks, deckError,
                    enabled = !isSaving, onSelectDeck = onSelectDeck, onRefreshDecks = onRefreshDecks)
                if (needsAnkiPermission) TextButton(onClick = onGrantAnki) { Text("Allow AnkiDroid access") }
                if (deckError != null) TextButton(onClick = onAnkiSettings) { Text("AnkiDroid setup") }
            }
            item { CardPreview(draft) }
            item { imageControls.content() }
            item { EditorField("Explanation", draft.explanation, { onDraftChange(draft.copy(explanation = it)) }, minLines = 2) }
            item {
                var showFields by remember { mutableStateOf(false) }
                TextButton(onClick = { showFields = !showFields }, modifier = Modifier.testTag("preview-fields")) {
                    Text(if (showFields) "Hide card fields" else "Preview card fields")
                }
                if (showFields) Column(Modifier.testTag("card-fields-preview"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val savedFields = runCatching { AnkiStudyNoteSpec.buildFields(draft) }.getOrNull()
                    if (savedFields == null) Text(AnkiStudyNoteSpec.validationError(draft).orEmpty())
                    else AnkiStudyNoteSpec.fields.zip(savedFields).forEach { (name, value) -> Text("$name: $value") }
                }
            }
            item {
                SectionTitle("Recognition card", "The Spanish phrase becomes its English meaning when you flip.")
            }
            item {
                EditorField("Spanish word or phrase", draft.word, {
                    onDraftChange(draft.copy(word = it, productionAnswer = it))
                }, testTag = "spanish-word-field")
                Text("Changing the expression also requires updating it in every example below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                EditorField("Meaning", draft.meaning, { onDraftChange(draft.copy(meaning = it)) }, minLines = 2)
            }
            item {
                EditorField("Translation", draft.translation, { onDraftChange(draft.copy(translation = it)) }, placeholder = "Optional full-sentence translation", minLines = 2)
            }
            item {
                EditorField("Spanish recognition sentence · mark the phrase with [[…]]", draft.context,
                    { onDraftChange(draft.copy(context = it)) }, minLines = 2, testTag = "recognition-context-field")
            }
            if (draft.recognitionAlternatives.isNotEmpty()) item {
                ContextAlternativesEditor("Other recognition examples", draft.recognitionAlternatives,
                    { onDraftChange(draft.copy(recognitionAlternatives = it)) })
            }
            item {
                EditorField("Dictionary notes", draft.definitions, { onDraftChange(draft.copy(definitions = it)) }, minLines = 3, testTag = "dictionary-notes-field")
            }
            if (draft.includeProduction) {
                item { EditorField("Spanish production sentence · mark the phrase with [[…]]", draft.productionContext,
                    { onDraftChange(draft.copy(productionContext = it)) }, minLines = 2, testTag = "production-context-field") }
                item { EditorField("Production sentence translation", draft.productionTranslation,
                    { onDraftChange(draft.copy(productionTranslation = it)) }, minLines = 2) }
                if (draft.productionAlternatives.isNotEmpty()) item {
                    ContextAlternativesEditor("Other production examples", draft.productionAlternatives,
                        { onDraftChange(draft.copy(productionAlternatives = it)) })
                }
                item {
                    var expanded by remember { mutableStateOf(true) }
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide English cue" else "Edit English cue") }
                    if (expanded) EditorField("English cue · mark the phrase with [[…]]", draft.productionPrompt,
                        { onDraftChange(draft.copy(productionPrompt = it)) }, minLines = 2)
                }
            }
            item { EditorField("Tags", draft.tags, { onDraftChange(draft.copy(tags = it)) }, placeholder = "language, tv-show, phrase", minLines = 1) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text("Review before sending. Your card is only written to AnkiDroid after you tap the button.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    dictionaryInfo: DictionaryInfo,
    importedDictionaries: List<DictionaryInfo>,
    openRouter: OpenRouterService,
    apiKey: String,
    model: String,
    targetLanguage: String,
    decks: List<AnkiDeck>,
    selectedDeck: AnkiDeck?,
    missingDeck: Boolean,
    loadingDecks: Boolean,
    deckError: String?,
    onSelectDeck: (AnkiDeck) -> Unit,
    onRefreshDecks: () -> Unit,
    isUpdatingTemplates: Boolean,
    onUpdateTemplates: () -> Unit,
    ankiInstalled: Boolean,
    ankiHasPermission: Boolean,
    statusMessage: String?,
    importProgress: DictionaryImportProgress?,
    onBack: () -> Unit,
    onApiKeyChange: (String) -> Unit,
    onKeyValidated: (String) -> Unit,
    aiConnected: Boolean,
    onModelChange: (String) -> Unit,
    onTargetLanguageChange: (String) -> Unit,
    onImportDictionary: () -> Unit,
    onRenameDictionary: (DictionaryInfo, String) -> Unit,
    onRemoveDictionary: (DictionaryInfo) -> Unit,
    onDictionaryDirection: (DictionaryInfo, LookupDirection?) -> Unit,
    onPreviewDictionary: suspend (DictionaryInfo) -> List<DictionaryEntry>,
    onGrantAnki: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var models by remember { mutableStateOf(emptyList<OpenRouterModel>()) }
    var loadingModels by remember { mutableStateOf(true) }
    var modelLoadError by remember { mutableStateOf<String?>(null) }
    var modelListVersion by remember { mutableStateOf(0) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var modelSearch by remember { mutableStateOf("") }
    var validatingKey by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    val latestApiKey by androidx.compose.runtime.rememberUpdatedState(apiKey)
    var keyValidationMessage by remember { mutableStateOf<String?>(null) }
    var keyValidationSucceeded by remember { mutableStateOf(false) }
    var editingDictionary by remember { mutableStateOf<DictionaryInfo?>(null) }
    var dictionaryNameDraft by remember { mutableStateOf("") }
    var removingDictionary by remember { mutableStateOf<DictionaryInfo?>(null) }
    var preview by remember { mutableStateOf<Pair<DictionaryInfo, List<DictionaryEntry>>?>(null) }
    var dictionaryPreviewError by remember { mutableStateOf<String?>(null) }
    val matchingModels = remember(models, modelSearch) {
        models.filter { candidate ->
            candidate.supportsJsonResponseFormat &&
                (modelSearch.isBlank() || candidate.id.contains(modelSearch, ignoreCase = true) || candidate.name.contains(modelSearch, ignoreCase = true))
        }
    }
    val selectedModel = models.firstOrNull { it.id == model }

    LaunchedEffect(openRouter, modelListVersion, apiKey) {
        if (apiKey.isBlank()) {
            models = emptyList()
            loadingModels = false
            modelLoadError = null
            return@LaunchedEffect
        }
        loadingModels = true
        modelLoadError = null
        delay(400)
        try {
            models = openRouter.listJsonCapableModels(apiKey)
            if (models.isNotEmpty() && models.none { it.id == model }) {
                modelLoadError = "Your selected model is unavailable. Choose a replacement and check its price."
                onModelChange("")
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { modelLoadError = error.message ?: "Could not load models from OpenRouter." }
        loadingModels = false
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Settings",
                subtitle = "local sources + your own AI key",
                leading = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().testTag("settings-screen-list"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!aiConnected) item(key = "ai-required") {
                Text("Connect AI to generate cards", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Dict·Anki requires OpenRouter. Enter and validate your API key, then choose a model to create cards. Local dictionary lookup and saved drafts work offline.")
            }
            if (statusMessage != null) item { StatusBanner(statusMessage) }
            item(key = "dictionaries") {
                SettingsSection("Dictionaries", "Choose the headword language for each dictionary. Dictionaries assigned to the current lookup direction are searched on this device.", Icons.AutoMirrored.Filled.MenuBook) {
                    if (importedDictionaries.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(dictionaryInfo.name, fontWeight = FontWeight.Bold)
                                Text("Demo entries · ready to try", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.secondary)
                        }
                    } else {
                        importedDictionaries.forEach { dictionary ->
                            Row(
                                modifier = Modifier.fillMaxWidth().testTag("dictionary-option:${dictionary.name}"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(dictionary.name, fontWeight = FontWeight.Bold)
                                    Text("${dictionary.entryCount} entries · local MDX", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(
                                    onClick = { dictionaryPreviewError = null; scope.launch { runCatching { onPreviewDictionary(dictionary) }.onSuccess { preview = dictionary to it }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; dictionaryPreviewError = it.message ?: "Could not preview dictionary." } } },
                                    modifier = Modifier.size(40.dp),
                                ) { Icon(Icons.Default.Visibility, "Preview ${dictionary.name}", tint = MaterialTheme.colorScheme.primary) }
                                IconButton(
                                    onClick = { editingDictionary = dictionary; dictionaryNameDraft = dictionary.name },
                                    modifier = Modifier.size(40.dp),
                                ) { Icon(Icons.Default.Edit, "Rename ${dictionary.name}") }
                                IconButton(
                                    onClick = { removingDictionary = dictionary },
                                    modifier = Modifier.size(40.dp),
                                ) { Icon(Icons.Default.Delete, "Remove ${dictionary.name}", tint = MaterialTheme.colorScheme.error) }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(selected = dictionary.lookupDirection == null,
                                    onClick = { onDictionaryDirection(dictionary, null) }, label = { Text("Both directions") })
                                LookupDirection.entries.forEach { direction ->
                                    FilterChip(selected = dictionary.lookupDirection == direction,
                                        onClick = { onDictionaryDirection(dictionary, direction) }, label = { Text(direction.label) })
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                        }
                    }
                    dictionaryPreviewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (importProgress != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(importProgress.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (importProgress.fraction == null) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            } else {
                                LinearProgressIndicator(
                                    progress = { importProgress.fraction.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onImportDictionary, enabled = importProgress == null, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Book, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (importProgress == null) "Add an .mdx dictionary" else "Importing dictionary…")
                    }
                    Text("The prototype parses common raw/zlib MDX blocks and reports encrypted or unsupported bundles when they cannot be read.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "openrouter") {
                SettingsSection("OpenRouter · bring your own key", "AI is required to create study cards. The key is stored on this device and sent only to the endpoint below.", Icons.Default.AutoAwesome) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            onApiKeyChange(it)
                            keyValidationMessage = null
                            keyValidationSucceeded = false
                        },
                        modifier = Modifier.fillMaxWidth().testTag("openrouter-api-key"),
                        singleLine = true,
                        label = { Text("OpenRouter API key") },
                        placeholder = { Text("sk-or-v1-…") },
                        leadingIcon = { Icon(Icons.Default.Key, null) },
                        visualTransformation = if (showApiKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { showApiKey = !showApiKey }) { Icon(Icons.Default.Visibility, if (showApiKey) "Hide API key" else "Show API key") } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = {
                                validatingKey = true
                                keyValidationMessage = null
                                keyValidationSucceeded = false
                                val keyToValidate = apiKey
                                scope.launch {
                                    runCatching { openRouter.validateApiKey(keyToValidate) }
                                        .onSuccess {
                                            if (latestApiKey == keyToValidate) {
                                                keyValidationMessage = "OpenRouter API key is valid."
                                                keyValidationSucceeded = true
                                                onKeyValidated(keyToValidate)
                                            }
                                        }
                                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; if (latestApiKey == keyToValidate) keyValidationMessage = it.message ?: "Could not validate this key." }
                                    validatingKey = false
                                }
                            },
                            enabled = apiKey.isNotBlank() && !validatingKey,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            if (validatingKey) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (validatingKey) "Validating…" else "Validate key")
                        }
                    }
                    keyValidationMessage?.let {
                        Text(
                            it,
                            modifier = Modifier.testTag("key-validation-result"),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (keyValidationSucceeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    OutlinedButton(
                        onClick = {
                            modelSearch = ""
                            modelMenuExpanded = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("model-picker-button"),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Cloud, null)
                        Spacer(Modifier.width(8.dp))
                        Text(selectedModel?.name ?: model.ifBlank { "Choose a model" }, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ExpandMore, "Choose model")
                    }
                    when {
                        apiKey.isBlank() -> Text("Enter an OpenRouter key to load models that support structured output.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        loadingModels -> Text("Loading models that support structured output…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        modelLoadError != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(modelLoadError.orEmpty(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { modelListVersion += 1 }) { Text("Retry") }
                        }
                        else -> Text("Only models that support structured JSON output are listed. Speed is ranked by recent OpenRouter throughput.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = targetLanguage,
                        onValueChange = onTargetLanguageChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Full-sentence translation language") },
                        shape = RoundedCornerShape(12.dp),
                    )
                    Text("Card cues remain English and answers Spanish. This setting changes full-sentence translations only. Model prices show input/output cost per million tokens.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                SettingsSection("AnkiDroid", "Dict·Anki creates a custom note type with Recognition and Vocabulary Production templates.", Icons.Default.ContentCopy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (ankiInstalled) "AnkiDroid detected" else "AnkiDroid not detected", fontWeight = FontWeight.Bold)
                            Text(if (ankiHasPermission) "Database access granted" else "Database access required", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (ankiInstalled && !ankiHasPermission) {
                            TextButton(onClick = onGrantAnki) { Text("Allow") }
                        }
                    }
                    if (!ankiInstalled) {
                        val localContext = LocalContext.current
                        TextButton(onClick = {
                            val browser = android.content.Intent(android.content.Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=com.ichi2.anki"))
                            runCatching { localContext.startActivity(browser) }
                        }) { Text("Install AnkiDroid") }
                    }
                    DeckPicker(decks, selectedDeck, missingDeck, loadingDecks, deckError,
                        onSelectDeck = onSelectDeck, onRefreshDecks = onRefreshDecks)
                    Text("Layout updates apply to existing v2 cards without regenerating their content. They replace manual template edits.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onUpdateTemplates,
                        enabled = ankiInstalled && ankiHasPermission && !isUpdatingTemplates,
                        modifier = Modifier.testTag("update-card-layout")) {
                        Text(if (isUpdatingTemplates) "Updating layout…" else "Update card layout")
                    }
                }
            }
        }
    }

    editingDictionary?.let { dictionary ->
        AlertDialog(
            onDismissRequest = { editingDictionary = null },
            title = { Text("Rename dictionary") },
            text = {
                OutlinedTextField(
                    value = dictionaryNameDraft,
                    onValueChange = { dictionaryNameDraft = it },
                    modifier = Modifier.fillMaxWidth().testTag("dictionary-name-field"),
                    singleLine = true,
                    label = { Text("Dictionary name") },
                    shape = RoundedCornerShape(12.dp),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameDictionary(dictionary, dictionaryNameDraft)
                        editingDictionary = null
                    },
                    enabled = dictionaryNameDraft.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingDictionary = null }) { Text("Cancel") } },
        )
    }

    removingDictionary?.let { dictionary ->
        AlertDialog(
            onDismissRequest = { removingDictionary = null },
            title = { Text("Remove dictionary?") },
            text = { Text("${dictionary.name} will be removed from Dict·Anki. The original MDX file stays unchanged.") },
            confirmButton = {
                TextButton(
                    onClick = { onRemoveDictionary(dictionary); removingDictionary = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removingDictionary = null }) { Text("Cancel") } },
        )
    }

    preview?.let { (dictionary, entries) ->
        ModalBottomSheet(onDismissRequest = { preview = null }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(dictionary.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Sample entries · ${dictionary.entryCount} total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (entries.isEmpty()) {
                    Text("No entries are available to preview.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(entries, key = { index, _ -> index }) { _, entry ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.headword, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (entry.partOfSpeech.isNotBlank()) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(entry.partOfSpeech, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Text(entry.definition, style = MaterialTheme.typography.bodyMedium)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                        }
                    }
                }
            }
        }
    }

    if (modelMenuExpanded) {
        ModalBottomSheet(onDismissRequest = { modelMenuExpanded = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Choose a model", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (apiKey.isNotBlank()) {
                    OutlinedTextField(
                        value = modelSearch,
                        onValueChange = { modelSearch = it },
                        modifier = Modifier.fillMaxWidth().testTag("model-search-field"),
                        singleLine = true,
                        label = { Text("Search models") },
                        placeholder = { Text("Name or model ID") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
                when {
                    apiKey.isBlank() -> Text("Enter an API key to load models.", style = MaterialTheme.typography.bodyMedium)
                    loadingModels -> Text("Loading models that support structured output…", style = MaterialTheme.typography.bodyMedium)
                    modelLoadError != null -> TextButton(onClick = { modelListVersion += 1 }) {
                        Text("Could not load models. Tap to retry.")
                    }
                    matchingModels.isEmpty() -> Text("No matching models with structured output.", style = MaterialTheme.typography.bodyMedium)
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(matchingModels, key = { it.id }) { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onModelChange(item.id)
                                        modelSearch = ""
                                        modelMenuExpanded = false
                                        focusManager.clearFocus()
                                    }
                                    .testTag("model-option:${item.id}")
                                    .padding(vertical = 12.dp),
                            ) {
                                Text(item.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${item.speedLabel} · ${modelCost(item)} per 1M tokens",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(item.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DeckPicker(
    decks: List<AnkiDeck>,
    selectedDeck: AnkiDeck?,
    missingDeck: Boolean,
    loadingDecks: Boolean,
    deckError: String?,
    enabled: Boolean = true,
    onSelectDeck: (AnkiDeck) -> Unit,
    onRefreshDecks: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Destination deck", fontWeight = FontWeight.SemiBold)
        OutlinedButton(
            onClick = { search = ""; expanded = true },
            enabled = enabled && !loadingDecks && decks.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag("deck-picker-button"),
        ) {
            Text(selectedDeck?.name ?: "Choose a deck", modifier = Modifier.weight(1f))
            Icon(Icons.Default.ExpandMore, null)
        }
        when {
            loadingDecks -> Text("Loading AnkiDroid decks…")
            deckError != null -> Text(deckError, style = MaterialTheme.typography.bodySmall)
            missingDeck -> Text("Your saved deck is no longer available. Choose a destination.", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onRefreshDecks, enabled = enabled && !loadingDecks,
            modifier = Modifier.testTag("refresh-decks")) { Text("Refresh decks") }
    }
    if (expanded && enabled) ModalBottomSheet(onDismissRequest = { expanded = false }) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Choose a deck", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = search, onValueChange = { search = it }, singleLine = true,
                label = { Text("Search decks") }, modifier = Modifier.fillMaxWidth().testTag("deck-search-field"))
            val matchingDecks = remember(decks, search) { decks.filter { it.name.contains(search, ignoreCase = true) } }
            if (matchingDecks.isEmpty()) Text("No matching decks.")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(matchingDecks, key = { it.id }) { deck ->
                    Row(Modifier.fillMaxWidth().clickable { onSelectDeck(deck); expanded = false }
                        .testTag("deck-option:${deck.id}").padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(deck.name, modifier = Modifier.weight(1f))
                        if (deck.id == selectedDeck?.id) Icon(Icons.Default.Check, "Selected")
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextAlternativesEditor(title: String, contexts: List<StudyContext>, onChange: (List<StudyContext>) -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide $title" else "$title · ${contexts.size}") }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        contexts.forEachIndexed { index, context ->
            EditorField("Example ${index + 2} · mark the phrase with [[…]]", context.sentence, { text ->
                onChange(contexts.mapIndexed { i, item -> if (i == index) item.copy(sentence = text) else item })
            }, minLines = 2)
            EditorField("Example ${index + 2} translation", context.translation, { text ->
                onChange(contexts.mapIndexed { i, item -> if (i == index) item.copy(translation = text) else item })
            })
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CardPreview(draft: CardDraft) {
    var production by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf(false) }
    var draw by remember { mutableStateOf(0) }
    val showProduction = production && draft.includeProduction
    val night = isSystemInDarkTheme()
    val error = AnkiStudyNoteSpec.validationError(draft)
    Card(Modifier.fillMaxWidth().testTag("card-preview"), shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .8f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showProduction, onClick = { production = false; reveal = false },
                    label = { Text("Recognition") }, modifier = Modifier.testTag("preview-recognition"))
                if (draft.includeProduction) FilterChip(selected = showProduction,
                    onClick = { production = true; reveal = false }, label = { Text("Production") },
                    modifier = Modifier.testTag("preview-production"))
            }
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("card-validation-error"))
            else {
                var html by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(draft, showProduction, reveal, night) {
                    html = withContext(Dispatchers.IO) {
                        val source = PreviewImageClient.imageUrl(draft.imageFilename)
                        AnkiStudyNoteSpec.previewHtml(draft, showProduction, reveal, night, source)
                    }
                }
                AndroidView(
                    factory = { context -> WebView(context).apply {
                        webViewClient = PreviewImageClient(context.filesDir)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                    } },
                    modifier = Modifier.fillMaxWidth().height((260 * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)).dp).testTag("card-preview-webview"),
                    update = { web ->
                        val content = html
                        val revision = content to draw
                        if (content != null && web.tag != revision) {
                            web.tag = revision
                            web.loadDataWithBaseURL("https://dict-anki-preview.invalid/", content, "text/html", "UTF-8", null)
                        }
                    },
                    onRelease = { it.destroy() },
                )
                Row {
                    TextButton(onClick = { reveal = !reveal }, modifier = Modifier.testTag("preview-flip")) {
                        Text(if (reveal) "Show front" else "Show answer")
                    }
                    TextButton(onClick = { reveal = false; draw += 1 }, modifier = Modifier.testTag("preview-next-context")) {
                        Text("Next example")
                    }
                }
                Text("${1 + if (showProduction) draft.productionAlternatives.size else draft.recognitionAlternatives.size} saved examples · context varies on each draw",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DefinitionCard(entry: DictionaryEntry, isPrimary: Boolean, onGenerate: () -> Unit, isGenerating: Boolean) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isPrimary) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .46f)),
        border = if (isPrimary) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .8f)) else null,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.headword, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                if (entry.partOfSpeech.isNotBlank()) Text(entry.partOfSpeech, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))

            }
            Text(entry.definition, style = MaterialTheme.typography.bodyLarge, lineHeight = 25.sp)
            if (entry.example.isNotBlank()) {
                Text("“${entry.example}”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .5f))
            Button(
                onClick = onGenerate,
                enabled = !isGenerating,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isGenerating) "Preparing your card…" else "Create study card", fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
            }
        }
    }
}

@Composable
private fun EditorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    minLines: Int = 1,
    testTag: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = LocalEditorEnabled.current,
        modifier = Modifier.fillMaxWidth().then(testTag?.let { Modifier.testTag(it) } ?: Modifier),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        minLines = minLines,
        maxLines = 6,
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsSection(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)) {
                    Icon(icon, null, modifier = Modifier.padding(8.dp).size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AppTopBar(title: String, subtitle: String, leading: @Composable (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    TopAppBar(
        title = {
            Column {
                Text(title, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.3).sp)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        navigationIcon = { leading?.invoke() },
        actions = { trailing?.invoke() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

@Composable
private fun ResultHeader(query: String, count: Int) {
    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text("Meaning", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(query.ifBlank { "Your lookup" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Text("$count ${if (count == 1) "result" else "results"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusBanner(message: String) {
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

private fun modelCost(model: OpenRouterModel): String {
    fun price(amount: Double): String {
        if (amount <= 0.0) return "free"
        val decimals = when {
            amount >= 0.01 -> 2
            amount >= 0.0001 -> 4
            else -> 6
        }
        return "\$${String.format(java.util.Locale.US, "%.$decimals" + "f", amount)}"
    }
    return "Input ${price(model.promptPricePerMillion)} · output ${price(model.completionPricePerMillion)}"
}

@Composable
private fun AssistChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun securePreferences(context: Context): SharedPreferences = runCatching {
    val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    EncryptedSharedPreferences.create(
        context,
        "dict_anki_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}.getOrElse {
    context.getSharedPreferences("dict_anki_settings", Context.MODE_PRIVATE)
}

private const val KEY_ANKI_DECK = "anki_deck_id"
private const val KEY_OPENROUTER = "openrouter_key"
private const val KEY_MODEL = "openrouter_model"
private const val KEY_LANGUAGE = "target_language"
private const val KEY_MDX_URIS = "mdx_uris"
private const val KEY_MDX_URI_LEGACY = "mdx_uri"
private fun dictionaryNamePreferenceKey(uri: String): String = "mdx_name:$uri"
private const val ANKI_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"

private fun readDraftState(prefs: SharedPreferences, key: String): List<CardDraft> = runCatching {
    DraftStorage.decode(prefs.getString(key, "[]").orEmpty())
}.getOrDefault(emptyList())

private val LocalEditorEnabled = androidx.compose.runtime.compositionLocalOf { true }
