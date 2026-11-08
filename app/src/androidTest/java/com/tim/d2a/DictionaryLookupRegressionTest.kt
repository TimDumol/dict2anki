package com.tim.d2a

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.tim.d2a.data.MdxDictionary
import com.tim.d2a.data.LookupDirection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class DictionaryLookupRegressionTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun uri(name: String) = Uri.parse("content://com.tim.d2a.dictionary-fixtures/$name.mdx")

    @Test fun retainsDifferentSensesOfTheSameHeadword() = runBlocking {
        for (persistent in listOf(false, true)) {
            val file = if (persistent) File(context.cacheDir, "senses-${System.nanoTime()}.sqlite") else null
            val dictionary = MdxDictionary(context.contentResolver, file)
            dictionary.import(uri("senses"))
            assertEquals(listOf("A financial institution.", "A bench for sitting."),
                dictionary.lookup("banco").entries.map { it.definition })
            assertEquals(2, dictionary.lookup("ban").entries.size)
        }
    }

    @Test fun importsExplicitUtf16ByteOrders() = runBlocking {
        for (name in listOf("utf16le", "utf16be")) {
            val dictionary = MdxDictionary(context.contentResolver)
            assertEquals(1, dictionary.import(uri(name)).entryCount)
            assertEquals("A fortunate discovery.", dictionary.lookup("serendipity").entries.single().definition)
        }
    }
    @Test fun foldedSuggestionsAndDictionaryDirectionPersist() = runBlocking {
        val file = File(context.cacheDir, "folded-${System.nanoTime()}.sqlite")
        val dictionary = MdxDictionary(context.contentResolver, file)
        val source = uri("accented")
        dictionary.import(source)
        assertEquals("árbol", dictionary.lookup("arbol").entries.single().headword)
        assertEquals("café", dictionary.lookup(" ¿café? ").entries.single().headword)
        dictionary.setDirection(source.toString(), LookupDirection.SPANISH_TO_ENGLISH)
        assertEquals(0, dictionary.lookup("café", direction = LookupDirection.ENGLISH_TO_SPANISH).entries.size)
        val reopened = MdxDictionary(context.contentResolver, file)
        assertEquals(LookupDirection.SPANISH_TO_ENGLISH, reopened.importedInfos().single().lookupDirection)
        assertEquals("café", reopened.lookup("cafe", direction = LookupDirection.SPANISH_TO_ENGLISH).entries.single().headword)
        val inflected = reopened.lookup("habló", direction = LookupDirection.SPANISH_TO_ENGLISH)
        assertEquals("habló", inflected.query)
        assertEquals("hablar", inflected.entries.single().headword)
        assertEquals("tener", reopened.lookup("tuve", direction = LookupDirection.SPANISH_TO_ENGLISH).entries.single().headword)
    }

}
