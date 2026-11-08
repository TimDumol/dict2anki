package com.tim.d2a

import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tim.d2a.data.CardDraft
import com.tim.d2a.data.LookupResult
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardGenerationServiceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun awaitCondition(predicate: () -> Boolean) {
        rule.waitUntil(timeoutMillis = 15_000, condition = predicate)
    }

    @After fun cleanup() {
        instrumentation.runOnMainSync {
            context.stopService(android.content.Intent(context, CardGenerationService::class.java))
        }
        awaitCondition { !CardGenerationService.state.value.running }
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }

    @Test fun generationSurvivesBackgroundAndActivityRecreationAndNotifies() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val automation = instrumentation.uiAutomation
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
            rule.runOnUiThread {
                rule.activity.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 991)
            }
            awaitCondition {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) true
                else {
                    val root = instrumentation.uiAutomation.rootInActiveWindow
                    listOf("com.android.permissioncontroller", "com.google.android.permissioncontroller").forEach { pkg ->
                        root?.findAccessibilityNodeInfosByViewId("$pkg:id/permission_allow_button")?.firstOrNull()
                            ?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    false
                }
            }
        }
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val draft = CardDraft("prueba", "Una prueba", "test", "A test", "", "", "", "test", "prueba")
        rule.runOnUiThread {
            CardGenerationService.start(context) {
                entered.complete(Unit)
                release.await()
                GenerationResult(draft, LookupResult("prueba", emptyList(), dictionaryName = "Test"), null, null)
            }
        }
        awaitCondition { entered.isCompleted }
        awaitCondition { manager.activeNotifications.any { it.id == 1001 } }
        rule.activityRule.scenario.recreate()
        rule.runOnUiThread { rule.activity.moveTaskToBack(true) }
        assertTrue(CardGenerationService.state.value.running)
        release.complete(Unit)
        awaitCondition { !CardGenerationService.state.value.running && manager.activeNotifications.any { it.id == 1002 } }
        assertNotNull(manager.activeNotifications.first { it.id == 1002 }.notification.contentIntent)
        awaitCondition { manager.activeNotifications.none { it.id == 1001 } }
        manager.cancel(1002)
    }

    @Test fun cancelStopsWorkWithoutCompletionNotification() {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        rule.runOnUiThread {
            CardGenerationService.start(context) {
                entered.complete(Unit)
                try { CompletableDeferred<Unit>().await() }
                finally { cancelled.complete(Unit) }
                error("Cancelled task should never complete")
            }
        }
        awaitCondition { entered.isCompleted }
        rule.runOnUiThread { CardGenerationService.cancel(context) }
        awaitCondition { cancelled.isCompleted }
        awaitCondition { !CardGenerationService.state.value.running && manager.activeNotifications.none { it.id == 1001 } }
        assertTrue(manager.activeNotifications.none { it.id == 1002 })
    }

    @Test fun sequentialGenerationsReplaceDraftOnReviewIntent() {
        val draftA = CardDraft("primero", "El [[primero]]", "first", "The first", "", "", "", "[[first]]", "primero")
        val draftB = CardDraft("segundo", "El [[segundo]]", "second", "The second", "", "", "", "[[second]]", "segundo")

        awaitCondition { rule.onAllNodesWithTag("lookup-screen-list").fetchSemanticsNodes().isNotEmpty() }
        rule.runOnUiThread {
            CardGenerationService.start(context) {
                GenerationResult(draftA, LookupResult("primero", emptyList(), dictionaryName = "Test"), null, null)
            }
        }
        awaitCondition {
            !CardGenerationService.state.value.running &&
                CardGenerationService.state.value.result == null &&
                rule.onAllNodesWithTag("card-editor-screen-list").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("card-editor-screen-list").performScrollToNode(hasTestTag("spanish-word-field"))
        rule.onNodeWithTag("spanish-word-field").assertTextContains("primero")

        // Simulate generation B completing while the UI has not yet processed generation state before REVIEW intent arrives.
        rule.runOnUiThread {
            CardGenerationService.start(context) {
                GenerationResult(draftB, LookupResult("segundo", emptyList(), dictionaryName = "Test"), null, draftA)
            }
            instrumentation.callActivityOnNewIntent(
                rule.activity,
                android.content.Intent(context, MainActivity::class.java).setAction(CardGenerationService.REVIEW),
            )
        }
        awaitCondition { !CardGenerationService.state.value.running && CardGenerationService.state.value.result == null }
        rule.runOnUiThread {
            instrumentation.callActivityOnNewIntent(
                rule.activity,
                android.content.Intent(context, MainActivity::class.java).setAction(CardGenerationService.REVIEW),
            )
        }
        awaitCondition { rule.onAllNodesWithTag("card-editor-screen-list").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("card-editor-screen-list").performScrollToNode(hasTestTag("spanish-word-field"))
        rule.onNodeWithTag("spanish-word-field").assertTextContains("segundo")
    }
}
