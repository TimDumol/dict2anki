package com.tim.d2a

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tim.d2a.data.AnkiStudyNoteSpec
import com.tim.d2a.data.CardDraft
import com.tim.d2a.data.StudyContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class CardLayoutE2ETest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private lateinit var web: WebView
    private val loaded = AtomicBoolean(false)
    private val draft = CardDraft(word = "tarde", context = "Llegué [[tarde]] a casa.", meaning = "late",
        translation = "I got home late.", explanation = "Supporting explanation.", definitions = "Dictionary details.",
        tags = "Spanish", productionPrompt = "I will return [[late]] today.", productionAnswer = "tarde",
        includeProduction = true, productionContext = "Hoy volveré [[tarde]].",
        productionTranslation = "I will return late today.",
        recognitionAlternatives = listOf(StudyContext("El tren salió [[tarde]].", "The train left late."),
            StudyContext("Siempre cenamos [[tarde]].", "We always eat dinner late.")),
        productionAlternatives = listOf(StudyContext("La tienda cierra [[tarde]].", "The shop closes late."),
            StudyContext("Nunca llegues [[tarde]] a clase.", "Never arrive late to class.")))

    @Before fun setUpWebView() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                AndroidView(factory = { context -> WebView(context).also {
                    web = it
                    it.settings.javaScriptEnabled = true
                    it.settings.domStorageEnabled = true
                    it.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) { loaded.set(true) }
                    }
                } }, modifier = Modifier.fillMaxSize())
            }
        }
        composeRule.waitUntil(5_000) { ::web.isInitialized }
    }

    @Test fun productionGlossIsInlineAndFlipKeepsContextAndGeometry() {
        load(production = true, reveal = false)
        val front = probe()
        assertEquals("[late]", front.getString("visible"))
        assertEquals("tarde", front.getString("measure"))
        assertEquals("hidden", front.getString("measureVisibility"))
        assertEquals(0, front.getInt("notes"))
        load(production = true, reveal = true)
        val back = probe()
        assertEquals("tarde", back.getString("visible"))
        assertEquals(front.getString("before"), back.getString("before"))
        assertEquals(front.getString("after"), back.getString("after"))
        for (dimension in listOf("slotX", "slotY", "slotWidth", "slotHeight", "afterX", "afterY")) {
            assertEquals(dimension, front.getDouble(dimension), back.getDouble(dimension), 0.5)
        }
        assertEquals(1, back.getInt("notes"))
        assertTrue(back.getString("translation").isNotEmpty())
    }

    @Test fun recognitionUsesAnIndependentSentenceAndContextsVaryOnEachDraw() {
        load(production = false, reveal = false)
        val front = probe()
        assertEquals("tarde", front.getString("visible"))
        load(production = false, reveal = true)
        val back = probe()
        assertEquals("late", back.getString("visible"))
        assertEquals(front.getString("before"), back.getString("before"))
        assertEquals(front.getString("after"), back.getString("after"))
        load(production = false, reveal = false)
        val next = probe()
        assertNotEquals(front.getString("before"), next.getString("before"))
        load(production = true, reveal = false)
        val production = probe()
        assertNotEquals(next.getString("before"), production.getString("before"))
    }

    @Test fun storedHtmlCannotExecuteAndDarkModeHasReadableColors() {
        val hostile = draft.copy(word = "<img>", context = "Texto [[<img>]].", includeProduction = false,
            recognitionAlternatives = listOf(StudyContext("Otro [[<img>]].")),
            meaning = "<script>window.injected=true</script>")
        load(false, false, hostile, night = true)
        val result = evaluate("JSON.stringify({images:document.querySelectorAll('img').length,injected:!!window.injected,bg:getComputedStyle(document.body).backgroundColor})")
        assertEquals(0, result.getInt("images"))
        assertFalse(result.getBoolean("injected"))
        assertEquals("rgb(23, 28, 38)", result.getString("bg"))
    }

    @Test fun primaryLineBreaksSurviveContextSelection() {
        load(false, false, draft.copy(context = "Ayer\nllegué [[tarde]].", recognitionAlternatives = emptyList()))
        assertEquals("Ayer\nllegué ", probe().getString("before"))
    }

    @Test fun disabledJavaScriptKeepsAUsableFirstExampleOnBothFaces() {
        composeRule.runOnIdle { web.settings.javaScriptEnabled = false }
        load(true, false)
        composeRule.runOnIdle { web.settings.javaScriptEnabled = true }
        val front = probe()
        assertEquals("Hoy volveré ", front.getString("before"))
        composeRule.runOnIdle { web.settings.javaScriptEnabled = false }
        load(true, true)
        composeRule.runOnIdle { web.settings.javaScriptEnabled = true }
        val back = probe()
        assertEquals(front.getString("before"), back.getString("before"))
        assertEquals("tarde", back.getString("visible"))
    }

    private fun load(production: Boolean, reveal: Boolean, card: CardDraft = draft, night: Boolean = false) {
        loaded.set(false)
        val html = AnkiStudyNoteSpec.previewHtml(card, production, reveal, night)
        composeRule.runOnIdle { web.loadDataWithBaseURL("https://card-test.invalid/", html, "text/html", "UTF-8", null) }
        composeRule.waitUntil(10_000) { loaded.get() }
    }

    private fun probe() = evaluate("""
        (function(){
          var slot=document.querySelector('.slot'), r=slot.getBoundingClientRect(), after=document.querySelector('.after'), a=after.getBoundingClientRect();
          return JSON.stringify({visible:document.querySelector('.cue,.solution').textContent,
            before:document.querySelector('.before').textContent,after:after.textContent,
            measure:document.querySelector('.measure').textContent,measureVisibility:getComputedStyle(document.querySelector('.measure')).visibility,
            slotX:r.x,slotY:r.y,slotWidth:r.width,slotHeight:r.height,afterX:a.x,afterY:a.y,
            notes:document.querySelectorAll('.reference').length,notesOpen:!!document.querySelector('details[open]'),
            translation:document.querySelector('.translation')?document.querySelector('.translation').textContent:''});
        })()
    """.trimIndent())

    private fun evaluate(script: String): JSONObject {
        val value = AtomicReference<String?>(null)
        composeRule.runOnIdle { web.evaluateJavascript(script) { value.set(it) } }
        composeRule.waitUntil(5_000) { value.get() != null }
        val decoded = org.json.JSONTokener(value.get()).nextValue() as String
        return JSONObject(decoded)
    }
}
