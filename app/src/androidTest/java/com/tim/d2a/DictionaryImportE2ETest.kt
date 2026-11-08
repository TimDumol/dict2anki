package com.tim.d2a

import android.content.Context
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tim.d2a.data.AnkiDeck
import com.tim.d2a.data.AnkiDestination
import com.tim.d2a.data.AnkiDroidOperations
import com.tim.d2a.data.CardDraft
import com.tim.d2a.data.MdxDictionary
import com.tim.d2a.ui.DictAnkiApp
import com.tim.d2a.ui.theme.DictAnkiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DictionaryImportE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun importsMultipleDictionariesAndAddsRecognitionAndProductionCards() {
        val anki = FakeAnkiDroidOperations()
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val dictionary = MdxDictionary(appContext.contentResolver)
        val fixtureUris = listOf(
            Uri.parse("content://$FIXTURE_AUTHORITY/first.mdx"),
            Uri.parse("content://$FIXTURE_AUTHORITY/second.mdx"),
        )
        var nextFixture = 0

        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                DictAnkiTheme {
                    DictAnkiApp(
                        persistSettings = false,
                        openRouterService = TestOpenRouterService(),
                        dictionaryOverride = dictionary,
                        dictionaryPickerOverride = { onResult -> onResult(fixtureUris.getOrNull(nextFixture++)) },
                        ankiOperationsOverride = anki,
                    )
                }
            }
        }

        connectTestAi(composeRule, returnToLookup = false)
        composeRule.onNodeWithTag("settings-screen-list").performScrollToNode(hasText("Add an .mdx dictionary"))
        composeRule.onNodeWithText("Add an .mdx dictionary").performClick()
        composeRule.waitUntil(5_000) { dictionary.importedInfos().size == 1 }
        composeRule.onNodeWithTag("dictionary-option:First dictionary").assertExists()

        composeRule.onNodeWithTag("settings-screen-list").performScrollToNode(hasText("Add an .mdx dictionary"))
        composeRule.onNodeWithText("Add an .mdx dictionary").performClick()
        composeRule.waitUntil(5_000) { dictionary.importedInfos().size == 2 }
        composeRule.onNodeWithTag("dictionary-option:First dictionary").assertExists()
        composeRule.onNodeWithTag("dictionary-option:Second dictionary").assertExists()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Word or phrase").performTextReplacement("serendipity")
        composeRule.onNodeWithText("Look up in 2 local dictionaries").performClick()
        composeRule.onNodeWithTag("lookup-screen-list").performScrollToNode(hasText("Definition from the first dictionary."))
        composeRule.onNodeWithText("Definition from the first dictionary.").assertIsDisplayed()
        composeRule.onNodeWithTag("lookup-screen-list")
            .performScrollToNode(hasText("Definition from the second dictionary."))
        composeRule.onNodeWithText("Definition from the second dictionary.").assertIsDisplayed()

        composeRule.onNodeWithTag("lookup-screen-list").performScrollToNode(hasTestTag("generate-card"))
        composeRule.onNodeWithTag("generate-card").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("card-editor-screen-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("card-editor-screen-list")
            .performScrollToNode(hasTestTag("dictionary-notes-field"))
        composeRule.onNodeWithTag("dictionary-notes-field")
            .assertTextContains("First dictionary: Definition from the first dictionary.", substring = true)
            .assertTextContains("Second dictionary: Definition from the second dictionary.", substring = true)

        composeRule.onNodeWithTag("card-editor-screen-list")
            .performScrollToNode(hasText("Recognition card"))
        composeRule.onNodeWithText("Recognition card").assertIsDisplayed()
        composeRule.onNodeWithTag("include-production-switch").assertIsOn()
        composeRule.onNodeWithTag("card-editor-screen-list")
            .performScrollToNode(hasTestTag("production-context-field"))
        composeRule.onNodeWithTag("production-context-field").assertTextContains("La [[serendipia]] cambió mis planes.")

        composeRule.onNodeWithTag("card-editor-screen-list")
            .performScrollToNode(hasTestTag("deck-picker-button"))
        composeRule.onNodeWithTag("deck-picker-button").performClick()
        composeRule.onNodeWithTag("deck-option:101").performClick()
        composeRule.onNodeWithText("Add to AnkiDroid").performClick()
        composeRule.waitUntil(5_000) { anki.addedNote != null }
        composeRule.onNodeWithText("Added to Language").assertIsDisplayed()
        assertEquals(1, anki.ensureModelCalls)
        val addedNote = requireNotNull(anki.addedNote)
        assertEquals("Dict·Anki Language v2", addedNote.first.modelName)
        assertEquals(42L, addedNote.first.modelId)
        assertEquals(101L, addedNote.first.deck.id)
        assertTrue(addedNote.second.includeProduction)
        assertEquals("serendipia", addedNote.second.word)
        assertEquals("It was pure [[serendipity]].", addedNote.second.productionPrompt)
        assertEquals("serendipia", addedNote.second.productionAnswer)
    }

    private class FakeAnkiDroidOperations : AnkiDroidOperations {
        @Volatile
        var ensureModelCalls = 0

        @Volatile
        var addedNote: Pair<AnkiDestination, CardDraft>? = null

        override fun isInstalled() = true

        override fun hasPermission() = true

        override suspend fun listDecks() = listOf(AnkiDeck(id = 101L, name = "Language"))

        override suspend fun ensureStudyModel(): Long {
            ensureModelCalls += 1
            return 42L
        }

        override suspend fun addNote(destination: AnkiDestination, draft: CardDraft): Long {
            addedNote = destination to draft
            return 9001L
        }
    }

    private companion object {
        const val FIXTURE_AUTHORITY = "com.tim.d2a.dictionary-fixtures"
    }
}
