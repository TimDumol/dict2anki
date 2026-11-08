package com.tim.d2a

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tim.d2a.data.AiCardContent
import com.tim.d2a.data.DictionaryEntry
import com.tim.d2a.data.OpenRouterModel
import com.tim.d2a.data.OpenRouterService
import com.tim.d2a.ui.DictAnkiApp
import com.tim.d2a.ui.theme.DictAnkiTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var service: FakeOpenRouterService

    @Test
    fun keyValidationAndSearchableJsonModelSelection() {
        service = FakeOpenRouterService()
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent {
                DictAnkiTheme {
                    DictAnkiApp(openRouterService = service, persistSettings = false)
                }
            }
        }

        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("lookup-screen-list").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("openrouter-api-key").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Connect AI to generate cards").assertExists()
        composeRule.onNodeWithTag("openrouter-api-key").performTextClearance()
        composeRule.onNodeWithTag("openrouter-api-key").performTextInput("test-key")
        composeRule.onNodeWithTag("openrouter-api-key").performImeAction()
        composeRule.waitUntil(6_000) { service.modelListRequests > 0 }

        composeRule.onNodeWithTag("model-picker-button").performClick()
        val modelSearch = composeRule.onNodeWithTag("model-search-field")
        modelSearch.performTextInput("gemini")
        composeRule.onNodeWithTag("model-option:google/gemini-2.5-flash-lite").assertExists()
        composeRule.onNodeWithText("Fast · Input $0.10 · output $0.40 per 1M tokens").assertExists()
        composeRule.onNodeWithTag("model-option:unsupported/freeform-model").assertDoesNotExist()
        composeRule.onNodeWithTag("model-option:google/gemini-2.5-flash-lite").performClick()
        composeRule.onNodeWithText("Google: Gemini 2.5 Flash Lite").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("openrouter-api-key").performTextClearance()
        composeRule.onNodeWithTag("openrouter-api-key").performTextInput("invalid-key")
        composeRule.onNodeWithTag("openrouter-api-key").performImeAction()
        composeRule.onNodeWithText("Validate key").performClick()
        composeRule.waitUntil(5_000) { service.validationRequests == 1 }
        composeRule.onNodeWithText("OpenRouter rejected this API key.").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("openrouter-api-key").performTextClearance()
        composeRule.onNodeWithTag("openrouter-api-key").performTextInput("test-key")
        composeRule.onNodeWithTag("openrouter-api-key").performImeAction()
        composeRule.onNodeWithText("Validate key").performClick()
        composeRule.waitUntil(5_000) { service.validationRequests == 2 }
        composeRule.onNodeWithText("OpenRouter API key is valid.").performScrollTo().assertIsDisplayed()
    }

    @After
    fun returnToLookupScreen() {
        if (::service.isInitialized) {
            runCatching { composeRule.onNodeWithContentDescription("Back").performClick() }
        }
    }

    private class FakeOpenRouterService : OpenRouterService {
        @Volatile
        var modelListRequests = 0

        @Volatile
        var validationRequests = 0

        override suspend fun listJsonCapableModels(apiKey: String): List<OpenRouterModel> {
            modelListRequests += 1
            return listOf(
                OpenRouterModel(
                    id = "google/gemini-2.5-flash-lite",
                    name = "Google: Gemini 2.5 Flash Lite",
                    promptPricePerMillion = 0.10,
                    completionPricePerMillion = 0.40,
                    speedLabel = "Fast",
                ),
                OpenRouterModel(
                    id = "unsupported/freeform-model",
                    name = "Unsupported freeform model",
                    promptPricePerMillion = 0.0,
                    completionPricePerMillion = 0.0,
                    speedLabel = "Fast",
                    supportsJsonResponseFormat = false,
                ),
            )
        }

        override suspend fun validateApiKey(apiKey: String) {
            validationRequests += 1
            if (apiKey != "test-key") throw IllegalStateException("OpenRouter rejected this API key.")
        }

        override suspend fun generateCard(
            apiKey: String,
            model: String,
            targetLanguage: String,
            query: String,
            context: String,
            dictionaryEntry: DictionaryEntry?,
            additionalDictionaryEntries: List<DictionaryEntry>,
            lookupDirection: com.tim.d2a.data.LookupDirection,
        ): AiCardContent = error("Card generation is outside this Settings flow test.")
    }
}
