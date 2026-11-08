package com.tim.d2a.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Direct, dependency-free implementation of the documented AnkiDroid
 * ContentProvider API. It mirrors the small boundary used by Readest: create
 * or reuse a compatible note type, add the note, then move its cards to the
 * selected deck.
 */
open class AnkiDroidClient(private val context: Context, providerAuthority: String = "com.ichi2.anki.flashcards") : AnkiDroidOperations {
    private val resolver: ContentResolver = context.contentResolver
    private val authority = providerAuthority
    private val root = Uri.parse("content://$authority")
    private val models = Uri.withAppendedPath(root, "models")
    private val notes = Uri.withAppendedPath(root, "notes")
    private val decks = Uri.withAppendedPath(root, "decks")
    private val permission = "com.ichi2.anki.permission.READ_WRITE_DATABASE"

    override fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo("com.ichi2.anki", PackageManager.GET_ACTIVITIES)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override fun hasPermission(): Boolean = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    override suspend fun listDecks(): List<AnkiDeck> = withContext(Dispatchers.IO) {
        if (!isInstalled() || !hasPermission()) return@withContext emptyList()
        queryRows(decks).mapNotNull { row ->
            val id = row.long("deck_id") ?: return@mapNotNull null
            val name = row.string("deck_name") ?: return@mapNotNull null
            if (id <= 0L) null else AnkiDeck(id, name)
        }.sortedBy { it.name }
    }

    override suspend fun ensureStudyModel(): Long = withContext(Dispatchers.IO) {
        check(isInstalled()) { "Install AnkiDroid to create cards." }
        check(hasPermission()) { "Allow Dict·Anki to access AnkiDroid." }
        val namedModels = queryRows(models).filter { it.string("name") == STUDY_MODEL_NAME }
        if (namedModels.isNotEmpty()) {
            val existing = namedModels.singleOrNull()
                ?: error("AnkiDroid has multiple note types named $STUDY_MODEL_NAME. Rename the extra note type and try again.")
            val modelId = existing.long("_id")
                ?: error("AnkiDroid returned an invalid $STUDY_MODEL_NAME note type")
            validateStudyModel(modelId)
            return@withContext modelId
        }

        val values = ContentValues().apply {
            put("name", STUDY_MODEL_NAME)
            put("field_names", STUDY_NOTE_FIELDS.joinToString(AnkiStudyNoteSpec.FIELD_SEPARATOR))
            put("num_cards", 2)
            put("sort_field_index", 0)
            put("type", 0)
            put("css", AnkiStudyNoteSpec.css)
        }
        val modelUri = resolver.insert(models, values)
            ?: error("AnkiDroid did not create the Dict·Anki note type")
        val modelId = modelUri.lastPathSegment?.toLongOrNull()
            ?: error("AnkiDroid returned an invalid note type id")
        updateStudyLayout(modelUri)
        validateStudyModel(modelId)
        modelId
    }

    override suspend fun updateStudyTemplates(): Long = withContext(Dispatchers.IO) {
        val modelId = ensureStudyModel()
        updateStudyLayout(Uri.withAppendedPath(models, modelId.toString()))
        modelId
    }

    private fun updateStudyLayout(modelUri: Uri) {
        check(resolver.update(modelUri, ContentValues().apply {
            put("css", AnkiStudyNoteSpec.css)
        }, null, null) > 0) { "AnkiDroid did not save the study card styling" }
        val templateRoot = Uri.withAppendedPath(modelUri, "templates")
        AnkiStudyNoteSpec.templates.forEachIndexed { index, template ->
            val templateUri = Uri.withAppendedPath(templateRoot, index.toString())
            val templateValues = ContentValues().apply {
                put("card_template_name", template.name)
                put("question_format", template.question)
                put("answer_format", template.answer)
            }
            check(resolver.update(templateUri, templateValues, null, null) > 0) {
                "AnkiDroid did not save the ${template.name} card template"
            }
        }
    }

    override suspend fun addNote(destination: AnkiDestination, draft: CardDraft): Long = withContext(Dispatchers.IO) {
        check(isInstalled()) { "AnkiDroid is not installed." }
        check(hasPermission()) { "Allow Dict·Anki to access AnkiDroid." }
        check(destination.modelName == STUDY_MODEL_NAME) { "Choose the Dict·Anki study note type." }
        validateStudyModel(destination.modelId)
        check(queryRows(decks).any { it.long("deck_id") == destination.deck.id }) {
            "The selected AnkiDroid deck is no longer available."
        }
        AnkiStudyNoteSpec.validationError(draft)?.let { error(it) }
        var mediaName = draft.imageFilename
        if (mediaName.isNotBlank()) {
            check(Regex("dictanki_[a-f0-9-]+\\.png").matches(mediaName)) { "Invalid image filename." }
            val image = java.io.File(context.filesDir, "images/$mediaName")
            check(image.isFile) { "The flashcard image is missing. Remove it or generate another image." }
            val source = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.images", image)
            context.grantUriPermission("com.ichi2.anki", source, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                val imported = resolver.insert(Uri.withAppendedPath(root, "media"), ContentValues().apply {
                    put("file_uri", source.toString())
                    put("preferred_name", mediaName)
                }) ?: error("AnkiDroid could not import the flashcard image.")
                mediaName = imported.lastPathSegment ?: error("AnkiDroid returned an invalid media name.")
            } finally { context.revokeUriPermission(source, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        val fields = AnkiStudyNoteSpec.buildFields(draft, mediaName)
        val values = ContentValues().apply {
            put("mid", destination.modelId)
            put("flds", AnkiStudyNoteSpec.encodeFields(fields))
            put("tags", draft.tags.split(',', ' ', '\n').filter { it.isNotBlank() }.joinToString(" "))
        }
        // Persist the provider receipt before any follow-up operation. Retrying resumes this note.
        val receipts = context.getSharedPreferences("anki_save_receipts", Context.MODE_PRIVATE)
        val receiptKey = draft.noteKey
        val receipt = receipts.getString(receiptKey, null)
        check(receipt != "pending") {
            "A previous save may have reached AnkiDroid but its result was interrupted. Check AnkiDroid before editing this draft to create a new note."
        }
        val existingUri = receipt?.let(Uri::parse)
        val noteUri = if (existingUri != null) {
            check(queryRows(existingUri).isNotEmpty()) {
                "The previously added note was deleted in AnkiDroid. Edit this draft to create a new note."
            }
            existingUri
        } else {
            check(receipts.edit().putString(receiptKey, "pending").commit()) {
                "Could not store a save receipt. Free device storage and try again. No note was added."
            }
            val inserted = resolver.insert(notes, values)
            if (inserted == null) {
                receipts.edit().remove(receiptKey).commit()
                error("AnkiDroid rejected the new note")
            }
            check(receipts.edit().putString(receiptKey, inserted.toString()).commit()) {
                "The note was added, but its save receipt could not be stored. Check AnkiDroid before retrying."
            }
            inserted
        }
        val cardsUri = Uri.withAppendedPath(noteUri, "cards")
        val cardOrdinals = resolver.query(cardsUri, null, null, null, null)?.use { cursor ->
            val ordColumn = cursor.getColumnIndex("ord")
            check(ordColumn >= 0) { "AnkiDroid returned an unexpected card schema" }
            val ordinals = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                val ord = cursor.getString(ordColumn)
                val cardUri = Uri.withAppendedPath(cardsUri, ord)
                check(resolver.update(
                    cardUri,
                    ContentValues().apply { put("deck_id", destination.deck.id) },
                    null,
                    null,
                ) > 0) { "AnkiDroid added the note but could not move every card to the selected deck." }
                ordinals += ord
            }
            ordinals
        } ?: error("AnkiDroid added the note but did not return its generated cards.")
        check("0" in cardOrdinals && (draft.includeProduction == ("1" in cardOrdinals))) {
            "AnkiDroid added the note but did not generate the expected Recognition and Vocabulary Production cards."
        }
        noteUri.lastPathSegment?.toLongOrNull() ?: error("AnkiDroid returned an invalid note id")
    }

    private fun validateStudyModel(modelId: Long) {
        val modelUri = Uri.withAppendedPath(models, modelId.toString())
        val model = queryRows(modelUri).singleOrNull()
            ?: error("The Dict·Anki note type is no longer available in AnkiDroid.")
        val compatible = model.string("name") == STUDY_MODEL_NAME &&
            AnkiStudyNoteSpec.hasExpectedSchema(
                model.string("field_names")?.split(AnkiStudyNoteSpec.FIELD_SEPARATOR).orEmpty(),
                model.int("type") ?: -1,
            )
        check(compatible) {
            "The existing $STUDY_MODEL_NAME note type has incompatible fields. Rename it in AnkiDroid before adding cards."
        }

        val templateUri = Uri.withAppendedPath(modelUri, "templates")
        val templates = queryRows(templateUri).mapNotNull { row ->
            val order = row.int("ord") ?: return@mapNotNull null
            order to AnkiCardTemplate(
                name = row.string("card_template_name").orEmpty(),
                question = row.string("question_format").orEmpty(),
                answer = row.string("answer_format").orEmpty(),
            )
        }.sortedBy { it.first }
        check(templates.map { it.first } == listOf(0, 1)) {
            "The $STUDY_MODEL_NAME note type must have recognition and production card slots."
        }
        check(AnkiStudyNoteSpec.hasExpectedTemplates(templates.map { it.second })) {
            "The existing $STUDY_MODEL_NAME note type must have two card templates. Rename it in AnkiDroid before adding cards."
        }
    }

    private fun queryRows(uri: Uri): List<Row> {
        val result = mutableListOf<Row>()
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) result += Row(cursor)
        }
        return result
    }

    private class Row(cursor: android.database.Cursor) {
        private val values = cursor.columnNames.associateWith { column ->
            val index = cursor.getColumnIndex(column)
            if (index < 0 || cursor.isNull(index)) return@associateWith null
            when (cursor.getType(index)) {
                android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                android.database.Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
                else -> null
            }
        }

        fun string(column: String): String? = values[column] as? String
        fun long(column: String): Long? = (values[column] as? Number)?.toLong()
        fun int(column: String): Int? = (values[column] as? Number)?.toInt()
    }

    companion object {
        const val STUDY_MODEL_NAME = AnkiStudyNoteSpec.MODEL_NAME
        val STUDY_NOTE_FIELDS = AnkiStudyNoteSpec.fields
    }
}
