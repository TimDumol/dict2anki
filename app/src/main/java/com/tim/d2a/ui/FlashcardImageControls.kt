package com.tim.d2a.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tim.d2a.data.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ImageControls(val isGenerating: Boolean, val content: @Composable () -> Unit)

/** Keep request state outside the editor's lazy items, so scrolling cannot cancel generation. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun rememberImageControls(
    draft: CardDraft,
    apiKey: String,
    selectedId: String,
    sort: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onSort: (String) -> Unit,
    onChange: (CardDraft) -> Unit,
    service: FlashcardImageService?,
): ImageControls {
    val context = LocalContext.current
    val client = service ?: remember { FlashcardImageClient() }
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<ImageModel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    var cost by remember { mutableStateOf<Double?>(null) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var customize by rememberSaveable { mutableStateOf(false) }
    var direction by rememberSaveable(draft.word, draft.meaning) { mutableStateOf("") }
    val currentDraft by rememberUpdatedState(draft)
    val change by rememberUpdatedState(onChange)
    val selected = models.firstOrNull { it.id == selectedId }
    LaunchedEffect(client, apiKey, loadAttempt) {
        models = emptyList()
        if (apiKey.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        try { models = client.models(apiKey) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Could not load image models." }
        finally { loading = false }
    }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val targetWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }.coerceAtLeast(1)
    val targetHeight = with(density) { 240.dp.roundToPx() }.coerceAtLeast(1)
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, draft.imageFilename, targetWidth, targetHeight) {
        value = null
        value = if (draft.imageFilename.isBlank()) null else withContext(Dispatchers.IO) {
            val path = File(context.filesDir, "images/${draft.imageFilename}").path
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, bounds)
            // Decode near the fitted display size, including large images from high-resolution models.
            val ratio = maxOf(bounds.outWidth.toFloat() / targetWidth, bounds.outHeight.toFloat() / targetHeight)
            var sample = 1
            while (sample <= Int.MAX_VALUE / 2 && sample * 2 <= ratio) sample *= 2
            android.graphics.BitmapFactory.decodeFile(path,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })?.apply {
                prepareToDraw()
            }?.asImageBitmap()
        }
    }
    if (picker) ModalBottomSheet(onDismissRequest = { picker = false }) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Image model", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(search, { search = it }, label = { Text("Search models") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(sort == "cost", { onSort("cost") }, label = { Text("Lowest cost") })
                FilterChip(sort == "name", { onSort("name") }, label = { Text("Name") })
            }
            Text("USD rates. Sort estimates use 300 input + 1,500 output tokens or one 1 MP image, at the lowest listed tier. Actual usage, size and tier change the charge.", style = MaterialTheme.typography.bodySmall)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error != null) TextButton(onClick = { loadAttempt++ }) { Text("Retry loading models") }
            val filtered = models.filter { it.name.contains(search, true) || it.id.contains(search, true) }
            val sorted = if (sort == "cost") filtered.sortedWith(compareBy<ImageModel> { it.estimatedCost ?: Double.POSITIVE_INFINITY }.thenBy { it.name }) else filtered.sortedBy { it.name }
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                items(sorted, key = { it.id }) { model ->
                    Column(Modifier.fillMaxWidth().clickable {
                        onSelect(model.id)
                        picker = false
                    }.padding(vertical = 12.dp)) {
                        Text((if (selectedId == model.id) "✓ " else "") + model.name, style = MaterialTheme.typography.titleSmall)
                        Text(model.priceLabel, style = MaterialTheme.typography.bodySmall)
                        model.estimatedCost?.let { Text("Comparison estimate: ${usd(it)}", style = MaterialTheme.typography.bodySmall) }
                    }
                    HorizontalDivider()
                }
                if (sorted.isEmpty() && !loading) item { Text("No matching image models.") }
            }
        }
    }
    return ImageControls(generating) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Memory image", style = MaterialTheme.typography.titleMedium)
            Text("Optional illustration · shown with the answer", style = MaterialTheme.typography.bodySmall)
            bitmap?.let { Image(it, "Generated vocabulary illustration", Modifier.fillMaxWidth().heightIn(max = 240.dp)) }
            OutlinedButton(onClick = { picker = true }, enabled = enabled && !generating && apiKey.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("image-model-picker")) {
                Text(selected?.name ?: selectedId.ifBlank { "Choose image model" })
            }
            if (selected != null) Text(selected.priceLabel, style = MaterialTheme.typography.bodySmall)
            if (apiKey.isBlank()) Text("Add your OpenRouter key in Settings to generate images.")
            if (loading) Text("Loading image models and prices…", style = MaterialTheme.typography.bodySmall)
            if (error != null) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (selected == null) TextButton(onClick = { loadAttempt++ }) { Text("Retry") }
            }
            if (!loading && selectedId.isNotBlank() && selected == null && models.isNotEmpty()) Text("Saved model is unavailable. Choose another model.")
            if (customize) OutlinedTextField(direction, { direction = it }, label = { Text("Optional visual direction") }, enabled = enabled && !generating, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val model = selected ?: return@Button
                    val requestedDraft = draft
                    val prompt = FlashcardImagePrompt.build(draft, direction)
                    generating = true
                    error = null
                    cost = null
                    scope.launch {
                        try {
                            val result = client.generate(context, apiKey, model, prompt)
                            // Preserve edits made during the request, but never attach to a different sense.
                            val latest = currentDraft
                            if (latest.word == requestedDraft.word && latest.meaning == requestedDraft.meaning) {
                                change(latest.copy(imageFilename = result.filename))
                                cost = result.cost
                            } else error = "The expression changed. Generate an image for the updated meaning."
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (failure: Exception) { error = failure.message ?: "Image generation failed. Try again." }
                        finally { generating = false }
                    }
                }, enabled = enabled && !generating && selected != null && apiKey.isNotBlank(), modifier = Modifier.testTag("generate-image")) {
                    Text(if (generating) "Generating…" else if (draft.imageFilename.isBlank()) "Generate image" else "Regenerate")
                }
                TextButton(onClick = { customize = !customize }, enabled = enabled && !generating) { Text(if (customize) "Hide options" else "Customize") }
                if (draft.imageFilename.isNotBlank()) TextButton(onClick = {
                    onChange(draft.copy(imageFilename = "")); cost = null
                }, enabled = enabled && !generating) { Text("Remove") }
            }
            if (generating) LinearProgressIndicator(Modifier.fillMaxWidth())
            cost?.let { Text("Generation cost: ${usd(it)}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
