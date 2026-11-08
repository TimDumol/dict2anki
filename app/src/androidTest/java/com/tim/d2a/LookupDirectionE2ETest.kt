package com.tim.d2a

import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tim.d2a.data.*
import com.tim.d2a.ui.DictAnkiApp
import com.tim.d2a.ui.theme.DictAnkiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LookupDirectionE2ETest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun lookupStartsWithEmptyInputAndNoResults() {
        showApp(TestOpenRouterService())
        composeRule.onNodeWithText("Word or phrase").assertTextContains("", substring = false)
        composeRule.onNodeWithTag("generate-card").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("serendipity", substring = true).assertDoesNotExist()
    }

    @Test fun tappingWordSelectsAllForReplacement() {
        showApp(TestOpenRouterService())
        val field = composeRule.onNodeWithText("Word or phrase")
        field.performTextReplacement("previous phrase")
        field.performTouchInput { click() }
        composeRule.waitForIdle()
        field.performTextInput("nuevo")
        field.assertTextContains("nuevo", substring = false)
    }

    @Test fun englishLookupCreatesSpanishRecognitionAndEnglishProduction() {
        var requestedDirection: LookupDirection? = null
        showApp(object : OpenRouterService {
            override suspend fun listJsonCapableModels(apiKey: String) = emptyList<OpenRouterModel>()
            override suspend fun validateApiKey(apiKey: String) = Unit
            override suspend fun generateCard(
                apiKey: String, model: String, targetLanguage: String, query: String,
                context: String, dictionaryEntry: DictionaryEntry?,
                additionalDictionaryEntries: List<DictionaryEntry>, lookupDirection: LookupDirection,
            ): AiCardContent {
                requestedDirection = lookupDirection
                assertEquals("serendipity", query)
                assertEquals("An unexpected discovery during a trip.", context)
                return AiCardContent(
                    meaning = "A fortunate chance discovery", translation = "", explanation = "",
                    tags = emptyList(), productionPrompt = "It was pure [[serendipity]].",
                    productionAnswer = "serendipia", spanishWord = "serendipia", spanishContext = "Fue pura [[serendipia]].", productionContext = "Encontré [[serendipia]] en mi viaje.",
                )
            }
        })
        composeRule.onNodeWithText("Word or phrase").performTextReplacement("serendipity")
        composeRule.onNodeWithTag("lookup-direction:ENGLISH_TO_SPANISH").performClick()
        composeRule.onNodeWithText("Look up in Demo dictionary").performClick()
        composeRule.onNodeWithText("Add context").performClick()
        composeRule.onNodeWithText("Where did you hear it?").performTextReplacement("An unexpected discovery during a trip.")
        composeRule.onNodeWithTag("lookup-screen-list").performScrollToNode(hasText("Create study card"))
        composeRule.onNodeWithText("Create study card").performClick()
        composeRule.onNodeWithTag("card-editor-screen-list").performScrollToNode(hasTestTag("spanish-word-field"))
        composeRule.onNodeWithTag("spanish-word-field").assertTextContains("serendipia")
        composeRule.onNodeWithTag("include-production-switch").assertIsOn()
        composeRule.onNodeWithTag("card-editor-screen-list")
            .performScrollToNode(hasTestTag("production-context-field"))
        composeRule.onNodeWithTag("production-context-field").assertTextContains("Encontré [[serendipia]] en mi viaje.")
        assertEquals(LookupDirection.ENGLISH_TO_SPANISH, requestedDirection)
    }

    @Test fun generationFailureKeepsLookupAndDoesNotCreateDraft() {
        showApp(object : OpenRouterService {
            override suspend fun listJsonCapableModels(apiKey: String) = emptyList<OpenRouterModel>()
            override suspend fun validateApiKey(apiKey: String) = Unit
            override suspend fun generateCard(
                apiKey: String, model: String, targetLanguage: String, query: String,
                context: String, dictionaryEntry: DictionaryEntry?,
                additionalDictionaryEntries: List<DictionaryEntry>, lookupDirection: LookupDirection,
            ): AiCardContent = error("Offline")
        })
        composeRule.onNodeWithText("Word or phrase").performTextReplacement("serendipity")
        composeRule.onNodeWithTag("lookup-direction:ENGLISH_TO_SPANISH").performClick()
        composeRule.onNodeWithText("Look up in Demo dictionary").performClick()
        composeRule.onNodeWithTag("lookup-screen-list").performScrollToNode(hasText("Create study card"))
        composeRule.onNodeWithText("Create study card").performClick()
        composeRule.onNodeWithTag("lookup-screen-list").performScrollToNode(hasText("Offline"))
        composeRule.onNodeWithText("Offline").assertExists()
        composeRule.onNodeWithTag("card-editor-screen-list").assertDoesNotExist()
        composeRule.onNodeWithText("Create study card").assertIsEnabled()
    }

    @Test fun unmatchedEnglishPhraseUsesCurrentInputAndPreviewsFields() {
        unmatchedPhrase(LookupDirection.ENGLISH_TO_SPANISH, "I am running late")
    }

    @Test fun unmatchedSpanishPhraseUsesCurrentInputAndPreviewsFields() {
        unmatchedPhrase(LookupDirection.SPANISH_TO_ENGLISH, "voy con retraso")
    }

    private fun unmatchedPhrase(direction: LookupDirection, phrase: String) {
        showApp(object : OpenRouterService {
            override suspend fun listJsonCapableModels(apiKey: String) = emptyList<OpenRouterModel>()
            override suspend fun validateApiKey(apiKey: String) = Unit
            override suspend fun generateCard(
                apiKey: String, model: String, targetLanguage: String, query: String,
                context: String, dictionaryEntry: DictionaryEntry?,
                additionalDictionaryEntries: List<DictionaryEntry>, lookupDirection: LookupDirection,
            ): AiCardContent {
                assertEquals(phrase, query)
                assertEquals(direction, lookupDirection)
                org.junit.Assert.assertNull(dictionaryEntry)
                org.junit.Assert.assertTrue(additionalDictionaryEntries.isEmpty())
                return AiCardContent(
                    meaning = "running late", translation = "I am running late for our meeting.",
                    explanation = "Used when delayed.", tags = emptyList(),
                    productionPrompt = "I am [[running late]] for a meeting.",
                    productionAnswer = "voy con retraso", spanishWord = "voy con retraso",
                    spanishContext = "[[voy con retraso]] para nuestra reunión.",
                    productionContext = "Hoy [[voy con retraso]] al trabajo.",
                )
            }
        })
        composeRule.onNodeWithTag("lookup-direction:${direction.name}").performClick()
        composeRule.onNodeWithText("Word or phrase").performTextReplacement(phrase)
        composeRule.onNodeWithTag("generate-card").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("card-editor-screen-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("card-preview").assertExists()
        composeRule.onNodeWithTag("preview-production").performClick()
        composeRule.onNodeWithTag("preview-flip").performClick()
        composeRule.onNodeWithTag("card-preview-webview").assertExists()
        composeRule.onNodeWithTag("card-editor-screen-list").performScrollToNode(hasTestTag("preview-fields"))
        composeRule.onNodeWithTag("preview-fields").performClick()
        composeRule.onNodeWithText("SelectedText: voy con retraso").assertExists()
        composeRule.onNodeWithText("Explanation: Used when delayed.").assertExists()
    }

    private fun showApp(service: OpenRouterService) {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                DictAnkiTheme { DictAnkiApp(openRouterService = service, persistSettings = false) }
            }
        }
        connectTestAi(composeRule)
    }
}
