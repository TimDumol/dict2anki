package com.tim.d2a

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tim.d2a.data.*
import com.tim.d2a.ui.DictAnkiApp
import com.tim.d2a.ui.theme.DictAnkiTheme
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlashcardImageE2ETest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun draft() = CardDraft("manzana", "Estamos en esta [[manzana]].", "city block", "We are on this block.", "", "", "", "", "")

    @Test fun apiDiscoversTokenPricesAndSavesDecodedImageWithUsageCost() = runBlocking {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val client = FlashcardImageClient(requestOverride = { path, key, body ->
            assertEquals("fixture-key", key)
            when (path) {
                "/images/models" -> JSONObject("""{"data":[{"id":"fixture/image","name":"Fixture"}]}""")
                "/images/models/fixture/image/endpoints" -> JSONObject("""{"endpoints":[{"provider_tag":"fixture","pricing":[{"billable":"output_image","unit":"token","cost_usd":0.00003}]}]}""")
                "/images" -> {
                    assertEquals("fixture/image", body!!.getString("model"))
                    assertEquals("fixture", body.getJSONObject("provider").getJSONArray("only").getString(0))
                    assertFalse(body.getJSONObject("provider").getBoolean("allow_fallbacks"))
                    assertTrue(body.getString("prompt").contains("city block"))
                    JSONObject().put("data", org.json.JSONArray().put(JSONObject().put("b64_json", encoded)))
                        .put("usage", JSONObject().put("cost", 0.025))
                }
                else -> error("Unexpected endpoint $path")
            }
        })
        val models = client.models("fixture-key")
        assertEquals(0.045, models.single().estimatedCost!!, 0.000001)
        val image = client.generate(rule.activity, "fixture-key", models.single(), FlashcardImagePrompt.build(draft()))
        // test filename is a generated UUID or a fixed fixture value.
        // foxguard: ignore[kt/no-path-traversal]
        val file = File(rule.activity.filesDir, "images/${image.filename}")
        try {
            assertTrue(file.isFile)
            assertEquals(0.025, image.cost!!, 0.000001)
            val saved = draft().copy(imageFilename = image.filename, imagePrompt = "Bright buildings enclosed by four streets.")
            assertEquals(saved, DraftStorage.decode(DraftStorage.encode(listOf(saved))).single())
            val uri = androidx.core.content.FileProvider.getUriForFile(rule.activity, "${rule.activity.packageName}.images", file)
            rule.activity.contentResolver.openInputStream(uri)!!.use { assertArrayEquals(bytes, it.readBytes()) }
        } finally { file.delete() }
    }

    @Test fun ankiImportsMediaAndUsesReturnedNameWithoutDuplicatingNotesOnRetry() = runBlocking {
        val context = rule.activity
        val root = android.net.Uri.parse("content://com.tim.d2a.anki-fixtures")
        context.contentResolver.call(root, "reset", null, android.os.Bundle().apply {
            putString("fields", AnkiStudyNoteSpec.fields.joinToString(AnkiStudyNoteSpec.FIELD_SEPARATOR))
        })
        val card = draft().copy(imageFilename = "dictanki_abcd.png", noteKey = "image-save-${System.nanoTime()}")
        // test filename is a generated UUID or a fixed fixture value.
        // foxguard: ignore[kt/no-path-traversal]
        val file = File(context.filesDir, "images/${card.imageFilename}")
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        val client = object : AnkiDroidClient(context, "com.tim.d2a.anki-fixtures") {
            override fun isInstalled() = true
            override fun hasPermission() = true
        }
        val destination = AnkiDestination(AnkiDeck(101, "Spanish"), 42, AnkiStudyNoteSpec.MODEL_NAME)
        try {
            assertTrue(runCatching { client.addNote(destination, card) }.isFailure)
            assertEquals(9001L, client.addNote(destination, card))
            val stats = context.contentResolver.call(root, "stats", null, null)!!
            assertEquals(1, stats.getInt("inserted"))
            assertTrue(stats.getInt("mediaImports") > 0)
            val explanation = stats.getString("fields")!!.split(AnkiStudyNoteSpec.FIELD_SEPARATOR)[5]
            assertTrue(explanation.contains("src='renamed-image.png'"))
            assertFalse(explanation.contains("&lt;img"))
        } finally {
            file.delete()
            context.getSharedPreferences("anki_save_receipts", Context.MODE_PRIVATE).edit().remove(card.noteKey).commit()
        }
    }

    @Test fun automaticImagesDefaultOnAndOptOutSurvivesRemount() {
        val prefs = rule.activity.getSharedPreferences("auto-image-flow-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val mount = mutableStateOf(0)
        rule.activity.runOnUiThread {
            rule.activity.setContent { key(mount.value) {
                DictAnkiTheme { DictAnkiApp(preferencesOverride = prefs, openRouterService = TestOpenRouterService()) }
            } }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("auto-generate-images").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("auto-generate-images").assertIsOn().performClick()
        rule.waitUntil(5_000) { prefs.getString("auto_generate_images", "") == "false" }
        rule.runOnIdle { mount.value++ }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("auto-generate-images").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("auto-generate-images").assertIsOff()
    }

    @Test fun modelAndSortPersistAndGenerationNeedsOnlyOneTapAfterRemount() {
        val prefs = rule.activity.getSharedPreferences("image-flow-test", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("openrouter_key", "fixture-key")
            .putString("draft", DraftStorage.encode(listOf(draft()))).commit()
        val mount = mutableStateOf(0)
        val models = listOf(ImageModel("expensive", "A costly model", null, listOf(ImagePrice("output_image", "image", 0.1))),
            ImageModel("cheap", "Z affordable model", null, listOf(ImagePrice("output_image", "image", 0.01))))
        var calls = 0
        val service = object : FlashcardImageService {
            override suspend fun models(key: String) = models
            override suspend fun generate(context: Context, key: String, model: ImageModel, prompt: String): GeneratedImage {
                calls++
                if (calls > 1) error("Fixture generation failed")
                assertEquals("cheap", model.id)
                assertTrue(prompt.contains("city block"))
                return GeneratedImage("dictanki_abcd.png", 0.01)
            }
        }
        rule.activity.runOnUiThread {
            rule.activity.setContent { key(mount.value) {
                DictAnkiTheme { DictAnkiApp(preferencesOverride = prefs, openRouterService = TestOpenRouterService(), imageServiceOverride = service) }
            } }
        }
        fun openDraft() {
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("resume-draft").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("resume-draft").performClick()
            rule.onNodeWithTag("card-editor-screen-list").performScrollToNode(hasTestTag("image-model-picker"))
            rule.onNodeWithTag("image-model-picker").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("Z affordable model").fetchSemanticsNodes().isNotEmpty() }
        }
        openDraft()
        rule.onNodeWithText("Name", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Z affordable model").performClick()
        rule.waitUntil(5_000) { prefs.getString("image_model", "") == "cheap" && prefs.getString("image_sort", "") == "name" }
        rule.runOnIdle { mount.value++ }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("resume-draft").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("resume-draft").performClick()
        rule.onNodeWithTag("card-editor-screen-list").performScrollToNode(hasTestTag("generate-image"))
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Z affordable model").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("generate-image").performClick()
        rule.waitUntil(5_000) { calls == 1 && DraftStorage.decode(prefs.getString("draft", "[]")!!).firstOrNull()?.imageFilename == "dictanki_abcd.png" }
        rule.onNodeWithText("Generation cost: $0.01").assertExists()
        rule.onNodeWithTag("generate-image").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Fixture generation failed").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("dictanki_abcd.png", DraftStorage.decode(prefs.getString("draft", "[]")!!).single().imageFilename)
    }
}
