package com.tim.d2a

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.tim.d2a.ui.DictAnkiApp
import com.tim.d2a.ui.theme.DictAnkiTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureE2ETest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun send(action: String, extra: String, text: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            type = "text/plain"
            putExtra(extra, text)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        rule.runOnUiThread { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(rule.activity, intent) }
    }

    @Test fun capturedWordGeneratesImmediatelyAndBackExits() {
        val capture = mutableStateOf<TextCapture?>(null)
        val service = TestOpenRouterService()
        rule.runOnUiThread {
            rule.activity.setContent {
                DictAnkiTheme {
                    DictAnkiApp(
                        capture = capture.value,
                        openRouterService = service,
                        persistSettings = false,
                        onExitCapture = { rule.activity.finish() },
                    )
                }
            }
        }
        connectTestAi(rule)
        rule.runOnIdle { capture.value = TextCapture("tarde") }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("AI-generated · always editable").fetchSemanticsNodes().isNotEmpty()
        }
        val activity = rule.activity
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(5_000) { activity.isFinishing || activity.isDestroyed }
    }

    @Test fun processTextBackFinishesActivity() {
        val paragraph = "A long paragraph from a book gives useful context for the expression that I would like to learn today."
        send(Intent.ACTION_PROCESS_TEXT, Intent.EXTRA_PROCESS_TEXT, paragraph)
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(paragraph).fetchSemanticsNodes().isNotEmpty()
        }
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(paragraph).fetchSemanticsNodes().isNotEmpty()
        }
        val activity = rule.activity
        rule.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(5_000) { activity.isFinishing || activity.isDestroyed }
    }

    @Test fun sharedParagraphBecomesContextWithoutGenerating() {
        val paragraph = "A long paragraph from a book gives useful context for the expression that I would like to learn today."
        send(Intent.ACTION_SEND, Intent.EXTRA_TEXT, paragraph)
        rule.waitUntil(10_000) { rule.onAllNodesWithText(paragraph).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Where did you hear it?").assertTextContains(paragraph)
        rule.onNodeWithTag("generate-card").assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun sharedShortSentenceWithoutPeriodAllowsPhraseSelection() {
        val sentence = "Hoy me apetece salir contigo"
        send(Intent.ACTION_SEND, Intent.EXTRA_TEXT, sentence)
        rule.waitUntil(10_000) { rule.onAllNodesWithText(sentence).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("generate-card").assertIsNotEnabled()
        rule.onNodeWithTag("use-selected-phrase").assertIsNotEnabled()
        rule.onNodeWithText("Where did you hear it?").performTextInputSelection(TextRange(4, 14))
        rule.onNodeWithTag("use-selected-phrase").performScrollTo().performClick()
        rule.onNodeWithText("Word or phrase").assertTextContains("me apetece")
        rule.onNodeWithText("Where did you hear it?").assertTextContains(sentence)
        rule.onNodeWithTag("generate-card").assertIsEnabled()
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(sentence).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Where did you hear it?").assertTextContains(sentence)
        rule.onNodeWithText("Word or phrase").assertTextContains("me apetece")
    }
}
