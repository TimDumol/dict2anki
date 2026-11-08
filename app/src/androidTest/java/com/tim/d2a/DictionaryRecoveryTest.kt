package com.tim.d2a

import android.content.Context
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.tim.d2a.data.MdxDictionary
import com.tim.d2a.ui.DictAnkiApp
import com.tim.d2a.ui.theme.DictAnkiTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class DictionaryRecoveryTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Before fun keepScreenOnDuringLongImports() {
        rule.activity.runOnUiThread {
            rule.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun uri(name: String) = Uri.parse("content://com.tim.d2a.dictionary-fixtures/$name.mdx")

    @Test fun failedImportThenRemoveAndReimportPersistsCleanly() = runBlocking {
        val file = File(context.cacheDir, "recovery-${System.nanoTime()}.sqlite")
        val dictionary = MdxDictionary(context.contentResolver, file)
        assertTrue(runCatching { dictionary.import(uri("bad")) }.isFailure)
        assertTrue(dictionary.importedInfos().isEmpty())
        dictionary.import(uri("first"))
        dictionary.import(uri("second"))
        assertEquals(2, dictionary.lookup("serendipity").entries.size)
        assertTrue(dictionary.remove(uri("first").toString()))
        assertTrue(dictionary.remove(uri("second").toString()))
        assertFalse(dictionary.remove(uri("second").toString()))
        dictionary.import(uri("first"))
        val restored = MdxDictionary(context.contentResolver, file)
        assertEquals(1, restored.importedInfos().size)
        assertEquals("Definition from the first dictionary.", restored.lookup("serendipity").entries.single().definition)
        assertTrue(restored.lookup("a phrase absent from all dictionaries").entries.isEmpty())
    }

    @Test fun corruptAndEncryptedFilesFailWithoutPollutingIndex() = runBlocking {
        val dictionary = MdxDictionary(context.contentResolver)
        for (name in listOf("corrupt", "encrypted")) {
            val failure = runCatching { dictionary.import(uri(name)) }.exceptionOrNull()
            assertNotNull(failure)
            assertTrue(failure!!.message.orEmpty().contains(if (name == "corrupt") "checksum" else "Encrypted"))
            assertTrue(dictionary.importedInfos().isEmpty())
        }
        dictionary.import(uri("first"))
        assertEquals(1, dictionary.importedInfos().size)
    }

    @Test fun failedPickerImportCanImmediatelyRetry() {
        val dictionary = MdxDictionary(context.contentResolver)
        var attempts = 0
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                DictAnkiTheme {
                    DictAnkiApp(openRouterService = TestOpenRouterService(), persistSettings = false,
                        dictionaryOverride = dictionary,
                        dictionaryPickerOverride = { it(uri(if (attempts++ == 0) "bad" else "first")) })
                }
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("lookup-screen-list").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Add an .mdx dictionary").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Add an .mdx dictionary").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("No readable entries were found. This file may use unsupported MDX encryption or compression.").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Add an .mdx dictionary").performScrollTo().assertIsEnabled().performClick()
        rule.waitUntil(5_000) { dictionary.importedInfos().size == 1 }
    }

    @Test fun cachedStartupReleasesAddButton() {
        val file = File(context.cacheDir, "startup-${System.nanoTime()}.sqlite")
        val dictionary = MdxDictionary(context.contentResolver, file)
        runBlocking { dictionary.import(uri("first")) }
        val prefs = context.getSharedPreferences("cached-startup-test", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("mdx_uris", setOf(uri("first").toString())).commit()
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                DictAnkiTheme {
                    DictAnkiApp(openRouterService = TestOpenRouterService(), preferencesOverride = prefs,
                        dictionaryOverride = dictionary, dictionaryPickerOverride = { it(uri("second")) })
                }
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("lookup-screen-list").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Add an .mdx dictionary").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Add an .mdx dictionary").performScrollTo().assertIsEnabled().performClick()
        rule.waitUntil(5_000) { dictionary.importedInfos().size == 2 }
        prefs.edit().clear().commit()
    }

    @Test fun streamedIndexFailureRollsBackAndAllowsRetry() {
        val file = File(context.cacheDir, "rollback-${System.nanoTime()}.sqlite")
        val index = com.tim.d2a.data.DictionaryIndex(file)
        val info = com.tim.d2a.data.DictionaryInfo("Streamed", "test:stream", supportsBinaryLookup = true)
        val entry = com.tim.d2a.data.DictionaryEntry("word", "", "Definition", source = "Streamed")
        assertTrue(runCatching {
            index.save(info, sequence { yield(entry); error("Invalid next record") })
        }.isFailure)
        assertTrue(index.infos().isEmpty())
        assertTrue(index.search("word").isEmpty())
        assertEquals(1, index.save(info, sequenceOf(entry)).entryCount)
    }

    @Test fun imports160MegabytesOfDecodedRecordsWithoutWholeDictionaryAllocation() = runBlocking {
        val file = File(context.cacheDir, "large-${System.nanoTime()}.sqlite")
        val dictionary = MdxDictionary(context.contentResolver, file)
        val info = dictionary.import(uri("large"))
        assertEquals(160, info.entryCount)
        assertEquals("A large dictionary definition.", dictionary.lookup("word159").entries.first().definition)
        assertEquals(160, MdxDictionary(context.contentResolver, file).importedInfos().single().entryCount)
    }

    @Test fun importsRequestedPhoneDictionary() = runBlocking {
        val requestedUri = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("dictionaryUri")
        org.junit.Assume.assumeTrue(requestedUri != null)
        val dictionary = MdxDictionary(context.contentResolver, File(context.cacheDir, "phone-import-${System.nanoTime()}.sqlite"))
        val info = dictionary.import(Uri.parse(requestedUri))
        assertTrue(info.entryCount > 0)
        println("Imported phone dictionary: ${info.name}, ${info.entryCount} entries")
    }

    @Test fun startupFailureReleasesAddButtonAndAllowsRetry() {
        val prefs = context.getSharedPreferences("recovery-test", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("mdx_uris", setOf(uri("bad").toString())).commit()
        val dictionary = MdxDictionary(context.contentResolver)
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                DictAnkiTheme {
                    DictAnkiApp(openRouterService = TestOpenRouterService(), preferencesOverride = prefs,
                        dictionaryOverride = dictionary, dictionaryPickerOverride = { it(uri("first")) })
                }
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("lookup-screen-list").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Add an .mdx dictionary").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Add an .mdx dictionary").performScrollTo().assertIsEnabled().performClick()
        rule.waitUntil(5_000) { dictionary.importedInfos().size == 1 }
        rule.onNodeWithText("Add an .mdx dictionary").assertIsEnabled()
        prefs.edit().clear().commit()
    }
}
