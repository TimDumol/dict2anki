package com.tim.d2a

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.tim.d2a.data.*

internal fun connectTestAi(rule: ComposeContentTestRule, returnToLookup: Boolean = true) {
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("lookup-screen-list").fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithContentDescription("Settings").performClick()
    rule.waitUntil(5_000) {
        rule.onAllNodesWithTag("settings-screen-list").fetchSemanticsNodes().isNotEmpty()
    }
    rule.onNodeWithTag("settings-screen-list").performScrollToNode(hasTestTag("openrouter-api-key"))
    rule.onNodeWithTag("lookup-screen-list").assertDoesNotExist()
    rule.onNodeWithTag("openrouter-api-key").performScrollTo().performTextInput("test-key")
    rule.onNodeWithTag("openrouter-api-key").performImeAction()
    rule.onNodeWithText("Validate key").performScrollTo().performClick()
    rule.waitUntil(5_000) {
        rule.onAllNodesWithText("OpenRouter API key is valid.").fetchSemanticsNodes().isNotEmpty()
    }
    if (returnToLookup) rule.onNodeWithContentDescription("Back").performClick()
}

internal class TestOpenRouterService : OpenRouterService {
    override suspend fun validateApiKey(apiKey: String) = Unit
    override suspend fun listJsonCapableModels(apiKey: String) = emptyList<OpenRouterModel>()
    override suspend fun generateCard(
        apiKey: String, model: String, targetLanguage: String, query: String, context: String,
        dictionaryEntry: DictionaryEntry?, additionalDictionaryEntries: List<DictionaryEntry>,
        lookupDirection: LookupDirection,
    ) = AiCardContent(
        spanishWord = "serendipia", spanishContext = "Encontré [[serendipia]] en este viaje.", meaning = dictionaryEntry?.definition ?: "fortunate discovery",
        translation = "", explanation = "", tags = emptyList(),
        productionPrompt = "It was pure [[serendipity]].", productionAnswer = "serendipia", productionContext = "La [[serendipia]] cambió mis planes.",
    )
}
