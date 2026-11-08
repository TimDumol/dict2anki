package com.tim.d2a.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.Locale

/** Persistent, indexed copy of imported dictionaries for fast startup and lookup. */
internal class DictionaryIndex(file: File) {
    private val database: SQLiteDatabase

    init {
        file.parentFile?.mkdirs()
        database = SQLiteDatabase.openOrCreateDatabase(file, null)
        database.setForeignKeyConstraintsEnabled(true)
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS dictionaries (
                uri TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                entry_count INTEGER NOT NULL,
                supports_binary_lookup INTEGER NOT NULL,
                sort_order INTEGER NOT NULL
            )""".trimIndent(),
        )
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS dictionary_entries (
                uri TEXT NOT NULL REFERENCES dictionaries(uri) ON DELETE CASCADE,
                ordinal INTEGER NOT NULL,
                headword TEXT NOT NULL,
                normalized_headword TEXT NOT NULL,
                part_of_speech TEXT NOT NULL,
                definition TEXT NOT NULL,
                example TEXT NOT NULL,
                PRIMARY KEY(uri, ordinal)
            )""".trimIndent(),
        )
        val columns = database.rawQuery("PRAGMA table_info(dictionaries)", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
        }
        if ("lookup_direction" !in columns) database.execSQL("ALTER TABLE dictionaries ADD COLUMN lookup_direction TEXT")
        val entryColumns = database.rawQuery("PRAGMA table_info(dictionary_entries)", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
        }
        if ("folded_headword" !in entryColumns) {
            database.beginTransaction()
            try {
                database.execSQL("ALTER TABLE dictionary_entries ADD COLUMN folded_headword TEXT NOT NULL DEFAULT ''")
                database.rawQuery("SELECT rowid, headword FROM dictionary_entries", null).use { cursor ->
                    val update = database.compileStatement("UPDATE dictionary_entries SET folded_headword = ? WHERE rowid = ?")
                    update.use { while (cursor.moveToNext()) {
                        it.bindString(1, foldHeadword(cursor.getString(1)))
                        it.bindLong(2, cursor.getLong(0))
                        it.executeUpdateDelete()
                    } }
                }
                database.setTransactionSuccessful()
            } finally { database.endTransaction() }
        }
        database.execSQL("CREATE INDEX IF NOT EXISTS dictionary_entries_folded ON dictionary_entries(folded_headword, uri)")
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS dictionary_entries_lookup ON dictionary_entries(normalized_headword, uri)",
        )
    }

    @Synchronized
    fun infos(): List<DictionaryInfo> = database.rawQuery(
        "SELECT uri, name, entry_count, supports_binary_lookup, lookup_direction FROM dictionaries ORDER BY sort_order",
        null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(
                DictionaryInfo(
                    name = cursor.getString(1),
                    uri = cursor.getString(0),
                    isDemo = false,
                    supportsBinaryLookup = cursor.getInt(3) != 0,
                    lookupDirection = cursor.getString(4)?.let { LookupDirection.valueOf(it) },
                    entryCount = cursor.getInt(2),
                ),
            )
        }
    }

    @Synchronized
    fun info(uri: String): DictionaryInfo? = database.rawQuery(
        "SELECT uri, name, entry_count, supports_binary_lookup, lookup_direction FROM dictionaries WHERE uri = ?",
        arrayOf(uri),
    ).use { cursor ->
        if (!cursor.moveToFirst()) null else DictionaryInfo(
            name = cursor.getString(1),
            uri = cursor.getString(0),
            isDemo = false,
            supportsBinaryLookup = cursor.getInt(3) != 0,
                    lookupDirection = cursor.getString(4)?.let { LookupDirection.valueOf(it) },
            entryCount = cursor.getInt(2),
        )
    }

    @Synchronized
    fun save(info: DictionaryInfo, entries: Sequence<DictionaryEntry>): DictionaryInfo {
        val uri = requireNotNull(info.uri)
        database.beginTransaction()
        try {
            val position = database.rawQuery("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM dictionaries", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            val values = ContentValues().apply {
                put("uri", uri)
                put("name", info.name)
                put("entry_count", 0)
                put("supports_binary_lookup", if (info.supportsBinaryLookup) 1 else 0)
                put("sort_order", position)
            }
            database.insertOrThrow("dictionaries", null, values)
            val statement = database.compileStatement(
                "INSERT INTO dictionary_entries(uri, ordinal, headword, normalized_headword, part_of_speech, definition, example, folded_headword) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            )
            var entryCount = 0
            try {
                entries.forEachIndexed { ordinal, entry ->
                    statement.clearBindings()
                    statement.bindString(1, uri)
                    statement.bindLong(2, ordinal.toLong())
                    statement.bindString(3, entry.headword)
                    statement.bindString(4, entry.headword.lowercase(Locale.ROOT))
                    statement.bindString(5, entry.partOfSpeech)
                    statement.bindString(6, entry.definition)
                    statement.bindString(7, entry.example)
                    statement.bindString(8, foldHeadword(entry.headword))
                    statement.executeInsert()
                    entryCount++
                }
            } finally {
                statement.close()
            }
            require(entryCount > 0) { "No readable entries were found." }
            database.execSQL("UPDATE dictionaries SET entry_count = ? WHERE uri = ?", arrayOf(entryCount, uri))
            database.setTransactionSuccessful()
            return info.copy(entryCount = entryCount)
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun rename(uri: String, name: String): DictionaryInfo? {
        database.execSQL("UPDATE dictionaries SET name = ? WHERE uri = ?", arrayOf(name, uri))
        return info(uri)
    }

    @Synchronized
    fun setDirection(uri: String, direction: LookupDirection?): DictionaryInfo? {
        val values = ContentValues().apply { put("lookup_direction", direction?.name) }
        database.update("dictionaries", values, "uri = ?", arrayOf(uri))
        return info(uri)
    }

    @Synchronized
    fun remove(uri: String): Boolean {
        database.beginTransaction()
        return try {
            val changed = database.delete("dictionaries", "uri = ?", arrayOf(uri)) > 0
            if (changed) database.delete("dictionary_entries", "uri = ?", arrayOf(uri))
            database.setTransactionSuccessful()
            changed
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun preview(uri: String, limit: Int): List<DictionaryEntry> = queryEntries(
        "e.uri = ?",
        arrayOf(uri),
        "e.ordinal",
        limit.coerceAtLeast(0),
    )

    @Synchronized
    fun search(query: String, direction: LookupDirection? = null): List<DictionaryEntry> {
        val directionCondition = if (direction == null) "" else " AND (d.lookup_direction IS NULL OR d.lookup_direction = '${direction.name}')"
        val normalized = query.lowercase(Locale.ROOT)
        val exact = queryEntries("e.normalized_headword = ?" + directionCondition, arrayOf(normalized), "d.sort_order, e.ordinal")
        val prefix = queryEntries(
            "e.normalized_headword > ? AND e.normalized_headword < ? AND e.normalized_headword <> ?" + directionCondition,
            arrayOf(normalized, normalized + "\uDBFF\uDFFF", normalized),
            "d.sort_order, e.ordinal",
            limit = 4,
        )
        if (exact.isNotEmpty()) return exact + prefix

        val folded = queryEntries("e.folded_headword = ?" + directionCondition,
            arrayOf(foldHeadword(query)), "d.sort_order, e.ordinal", limit = 8)
        if (folded.isNotEmpty() || prefix.isNotEmpty()) return (folded + prefix).distinct()

        // Keep the existing infix-search behavior as a fallback for words with no exact or prefix hit.
        val escaped = normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return queryEntries(
            "e.normalized_headword LIKE ? ESCAPE '\\'" + directionCondition,
            arrayOf("%$escaped%"),
            "d.sort_order, e.ordinal",
            limit = 4,
        )
    }

    private fun queryEntries(
        where: String,
        args: Array<String>,
        orderBy: String,
        limit: Int? = null,
    ): List<DictionaryEntry> {
        val limitSql = limit?.let { " LIMIT $it" }.orEmpty()
        val sql = """SELECT e.headword, e.part_of_speech, e.definition, e.example, d.name
            FROM dictionary_entries e JOIN dictionaries d ON d.uri = e.uri
            WHERE $where ORDER BY $orderBy$limitSql""".trimIndent()
        return database.rawQuery(sql, args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(
                    DictionaryEntry(
                        headword = cursor.getString(0),
                        partOfSpeech = cursor.getString(1),
                        definition = cursor.getString(2),
                        example = cursor.getString(3),
                        source = cursor.getString(4),
                    ),
                )
            }
        }
    }
}

internal fun foldHeadword(text: String): String = java.text.Normalizer.normalize(
    text.trim().lowercase(Locale.ROOT).replace(headwordWhitespace, " "), java.text.Normalizer.Form.NFD,
).replace(headwordMarks, "")

private val headwordWhitespace = Regex("\\s+")
private val headwordMarks = Regex("\\p{M}+")
