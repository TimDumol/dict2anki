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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeckSelectionE2ETest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private val anki = FakeAnki()

    @Test fun selectedDeckIsUsedAndRestoredAfterAppRemount() {
        val prefs = composeRule.activity.getSharedPreferences("deck-selection-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val mount = mutableStateOf(0)
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                key(mount.value) {
                    DictAnkiTheme {
                        DictAnkiApp(openRouterService = TestOpenRouterService(),
                            preferencesOverride = prefs, ankiOperationsOverride = anki)
                    }
                }
            }
        }
        connectTestAi(composeRule, returnToLookup = false)
        composeRule.onNodeWithTag("settings-screen-list").performScrollToNode(hasTestTag("deck-picker-button"))
        selectSpanishDeck()
        assertEquals("202", prefs.getString("anki_deck_id", null))
        composeRule.onNodeWithContentDescription("Back").performClick()
        generateDraft()
        composeRule.onNodeWithTag("deck-picker-button").assertTextContains("Languages::Spanish")
        composeRule.onNodeWithTag("add-to-anki").performClick()
        composeRule.waitUntil(5_000) { anki.destination != null }
        assertEquals(202L, anki.destination?.deck?.id)

        composeRule.runOnIdle { mount.value += 1 }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("lookup-screen-list").fetchSemanticsNodes().isNotEmpty()
        }
        generateDraft()
        composeRule.onNodeWithTag("deck-picker-button").assertTextContains("Languages::Spanish")
        prefs.edit().clear().commit()
    }

    @Test fun deletedDeckRequiresAnotherSelectionAndNeverFallsBackToFirstDeck() {
        showApp()
        generateDraft()
        composeRule.onNodeWithTag("add-to-anki").assertIsNotEnabled()
        selectSpanishDeck()
        composeRule.onNodeWithTag("add-to-anki").assertIsEnabled()
        anki.availableDecks = listOf(AnkiDeck(101, "Default"))
        // Deck deletion after review must be caught before creating a note or model.
        composeRule.onNodeWithTag("add-to-anki").performClick()
        composeRule.onNodeWithText("The selected deck is no longer available. Refresh and choose a deck.").assertIsDisplayed()
        assertNull(anki.destination)
        assertEquals(0, anki.modelCalls)
        composeRule.onNodeWithTag("refresh-decks").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Your saved deck is no longer available. Choose a destination.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("add-to-anki").assertIsNotEnabled()
        composeRule.onNodeWithTag("deck-picker-button").performClick()
        composeRule.onNodeWithTag("deck-option:101").performClick()
        composeRule.onNodeWithTag("add-to-anki").performClick()
        composeRule.waitUntil(5_000) { anki.destination != null }
        assertEquals(101L, anki.destination?.deck?.id)
    }

    @Test fun deckLoadFailureCanBeRetried() {
        anki.loadError = "Anki collection is unavailable."
        showApp()
        generateDraft()
        composeRule.onNodeWithText("Anki collection is unavailable.").assertExists()
        composeRule.onNodeWithTag("deck-picker-button").assertIsNotEnabled()
        composeRule.onNodeWithTag("add-to-anki").assertIsNotEnabled()
        anki.loadError = null
        composeRule.onNodeWithTag("refresh-decks").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Loading AnkiDroid decks…").fetchSemanticsNodes().isEmpty()
        }
        selectSpanishDeck()
        composeRule.onNodeWithTag("add-to-anki").assertIsEnabled()
    }

    @Test fun productionToggleStaysVisibleAndSystemBackReturnsToLookup() {
        showApp()
        generateDraft()
        composeRule.onNodeWithTag("include-production-switch").assertIsDisplayed().assertIsOn()
        composeRule.onNodeWithTag("card-editor-screen-list")
            .performScrollToNode(hasTestTag("dictionary-notes-field"))
        composeRule.onNodeWithTag("include-production-switch").assertIsDisplayed().performClick().assertIsOff()
        composeRule.onNodeWithTag("add-to-anki").assertIsDisplayed()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("lookup-screen-list").assertIsDisplayed()
        composeRule.onNodeWithText("Word or phrase").assertTextContains("serendipity")
        composeRule.onNodeWithTag("resume-draft").performClick()
        composeRule.onNodeWithTag("include-production-switch").assertIsOff()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithTag("settings-screen-list").assertIsDisplayed()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("lookup-screen-list").assertIsDisplayed()
    }

    @Test fun saveFeedbackStaysVisibleAndUnchangedCardsCannotBeAddedTwice() {
        showApp()
        generateDraft()
        selectSpanishDeck()
        composeRule.onNodeWithTag("card-editor-screen-list")
            .performScrollToNode(hasTestTag("dictionary-notes-field"))
        composeRule.onNodeWithTag("add-to-anki").performClick()
        composeRule.waitUntil(5_000) { anki.noteCalls == 1 }
        composeRule.onNodeWithText("Added to Languages::Spanish").assertIsDisplayed()
        composeRule.onNodeWithTag("add-to-anki").assertIsNotEnabled()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag("resume-draft").performClick()
        composeRule.onNodeWithTag("add-to-anki").assertIsNotEnabled()
        composeRule.onNodeWithTag("include-production-switch").performClick()
        composeRule.onNodeWithTag("add-to-anki").assertIsEnabled().performClick()
        composeRule.waitUntil(5_000) { anki.noteCalls == 2 }
        composeRule.onNodeWithTag("include-production-switch").performClick()
        composeRule.onNodeWithTag("add-to-anki").assertIsNotEnabled()
        assertEquals(2, anki.noteCalls)
        assertEquals(2, anki.noteKeys.distinct().size)
    }

    @Test fun failedSaveShowsFeedbackAndAllowsRetry() {
        showApp()
        generateDraft()
        selectSpanishDeck()
        anki.writeError = "Could not write to AnkiDroid."
        composeRule.onNodeWithTag("card-editor-screen-list")
            .performScrollToNode(hasTestTag("dictionary-notes-field"))
        composeRule.onNodeWithTag("add-to-anki").performClick()
        composeRule.onNodeWithText("Could not write to AnkiDroid.").assertIsDisplayed()
        composeRule.onNodeWithTag("add-to-anki").assertIsEnabled()
        anki.writeError = null
        composeRule.onNodeWithTag("add-to-anki").performClick()
        composeRule.waitUntil(5_000) { anki.noteCalls == 1 }
        composeRule.onNodeWithText("Added to Languages::Spanish").assertIsDisplayed()
        composeRule.onNodeWithTag("add-to-anki").assertIsNotEnabled()
    }

    private fun showApp() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                DictAnkiTheme {
                    DictAnkiApp(openRouterService = TestOpenRouterService(), persistSettings = false,
                        ankiOperationsOverride = anki)
                }
            }
        }
        connectTestAi(composeRule)
    }

    private fun generateDraft() {
        composeRule.onNodeWithText("Word or phrase").performTextReplacement("serendipity")
        composeRule.onNodeWithTag("generate-card").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("card-editor-screen-list").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Loading AnkiDroid decks…").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun selectSpanishDeck() {
        composeRule.onNodeWithTag("deck-picker-button").performClick()
        composeRule.onNodeWithTag("deck-search-field").performTextInput("spanish")
        composeRule.onNodeWithTag("deck-option:101").assertDoesNotExist()
        composeRule.onNodeWithTag("deck-option:202").performClick()
    }

    private class FakeAnki : AnkiDroidOperations {
        @Volatile var availableDecks = listOf(AnkiDeck(101, "Default"), AnkiDeck(202, "Languages::Spanish"))
        @Volatile var loadError: String? = null
        @Volatile var destination: AnkiDestination? = null
        @Volatile var modelCalls = 0
        @Volatile var noteCalls = 0
        @Volatile var writeError: String? = null
        val noteKeys = mutableListOf<String>()
        override fun isInstalled() = true
        override fun hasPermission() = true
        override suspend fun listDecks(): List<AnkiDeck> {
            loadError?.let { error(it) }
            return availableDecks
        }
        override suspend fun ensureStudyModel(): Long { modelCalls += 1; return 42 }
        override suspend fun addNote(destination: AnkiDestination, draft: CardDraft): Long {
            writeError?.let { error(it) }
            noteKeys += draft.noteKey
            noteCalls += 1
            this.destination = destination
            return 9001
        }
    }
}
