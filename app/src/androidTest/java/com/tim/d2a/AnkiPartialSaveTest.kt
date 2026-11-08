package com.tim.d2a

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.tim.d2a.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AnkiPartialSaveTest {
    @Test fun retryAfterCardMoveFailureReusesInsertedNoteAcrossClientInstances() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = Uri.parse("content://com.tim.d2a.anki-fixtures")
        context.contentResolver.call(root, "reset", null, android.os.Bundle().apply { putString("fields", AnkiStudyNoteSpec.fields.joinToString(AnkiStudyNoteSpec.FIELD_SEPARATOR)) })
        val noteKey = "partial-save-${System.nanoTime()}"
        val card = CardDraft("tarde", "Llegué [[tarde]].", "late", "", "", "", "", "I arrived [[late]].", "tarde", noteKey = noteKey)
        val destination = AnkiDestination(AnkiDeck(101, "Spanish"), 42, AnkiStudyNoteSpec.MODEL_NAME)
        fun client() = object : AnkiDroidClient(context, "com.tim.d2a.anki-fixtures") {
            override fun isInstalled() = true
            override fun hasPermission() = true
        }
        assertTrue(runCatching { client().addNote(destination, card) }.isFailure)
        assertEquals(9001L, client().addNote(destination, card))
        assertEquals(1, context.contentResolver.call(root, "stats", null, null)!!.getInt("inserted"))
        context.getSharedPreferences("anki_save_receipts", Context.MODE_PRIVATE).edit().remove(noteKey).commit()
        Unit
    }
}
