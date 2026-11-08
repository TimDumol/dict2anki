package com.tim.d2a

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tim.d2a.data.*
import com.tim.d2a.ui.DictAnkiApp
import com.tim.d2a.ui.theme.DictAnkiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DraftRecoveryE2ETest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun card() = CardDraft(
        word = "tarde", context = "Llegué [[tarde]] a casa.", meaning = "late",
        translation = "I arrived home late.", explanation = "", definitions = "", tags = "spanish",
        productionPrompt = "I arrived [[late]].", productionAnswer = "tarde", noteKey = "recover-note",
        recognitionAlternatives = listOf(StudyContext("Siempre llego [[tarde]].", "I always arrive late.")),
    )

    @Test fun storageRoundTripPreservesIdentityAndAlternativeExamples() {
        assertEquals(listOf(card()), DraftStorage.decode(DraftStorage.encode(listOf(card()))))
    }

    @Test fun completedDraftIsRemovedFromRecoveryAndArchiveAfterRemount() {
        val prefs = rule.activity.getSharedPreferences("recovery-test", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putString("draft", DraftStorage.encode(listOf(card())))
            .putString("saved_drafts", DraftStorage.encode(listOf(card().copy(noteKey = ""))))
            .putString("draft_archive", DraftStorage.encode(listOf(card())))
            .putString("anki_deck_id", "101").commit()
        val mount = mutableStateOf(0)
        val dictionary = MdxDictionary(rule.activity.contentResolver)
        val anki = object : AnkiDroidOperations {
            override fun isInstalled() = true
            override fun hasPermission() = true
            override suspend fun listDecks() = listOf(AnkiDeck(101, "Spanish"))
            override suspend fun ensureStudyModel() = 1L
            override suspend fun addNote(destination: AnkiDestination, draft: CardDraft): Long = error("Must not add saved content again")
        }
        rule.activity.runOnUiThread {
            rule.activity.setContent { key(mount.value) {
                DictAnkiTheme { DictAnkiApp(preferencesOverride = prefs, dictionaryOverride = dictionary,
                    openRouterService = TestOpenRouterService(), ankiOperationsOverride = anki) }
            } }
        }
        rule.waitUntil(5_000) { prefs.getString("draft", null) == "[]" && prefs.getString("draft_archive", null) == "[]" }
        rule.onNodeWithTag("resume-draft").assertDoesNotExist()
        rule.onNodeWithText("Other drafts", substring = true).assertDoesNotExist()
        rule.runOnIdle { mount.value++ }
        rule.waitForIdle()
        rule.onNodeWithTag("resume-draft").assertDoesNotExist()
        rule.onNodeWithText("Other drafts", substring = true).assertDoesNotExist()
    }
}
