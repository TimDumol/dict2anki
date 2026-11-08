package com.tim.d2a.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.Adler32
import java.util.zip.InflaterInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local MDX lookup boundary.
 *
 * The reader handles the common MDX 1.2 and 2.0 layouts, including raw and
 * zlib-compressed key/record blocks. Encrypted and LZO-compressed bundles are
 * rejected with a visible import error until the native reader phase adds
 * those codecs.
 */
class MdxDictionary(
    private val resolver: ContentResolver,
    databaseFile: java.io.File? = null,
) {
    private val index = databaseFile?.let(::DictionaryIndex)
    private val demoEntries = listOf(
        DictionaryEntry("tarde", "adverb", "Late; after the expected time.", "Llegué tarde a la reunión.", "Pocket Spanish"),
        DictionaryEntry("me apetece", "phrase", "I feel like; I would like to.", "Hoy me apetece tomar un café.", "Pocket Spanish"),
        DictionaryEntry("serendipity", "noun", "The occurrence of events by chance in a happy or beneficial way.", "A fortunate stroke of serendipity brought the two old friends together.", "Pocket English"),
        DictionaryEntry("get around", "phrasal verb", "To find a way of avoiding a problem, rule, or restriction.", "We need to get around the traffic before the show starts.", "Pocket English"),
        DictionaryEntry("uncanny", "adjective", "Strange or mysterious in a way that is difficult to explain.", "She had an uncanny ability to know when something was wrong.", "Pocket English"),
        DictionaryEntry("head over heels", "idiom", "Completely in love, or falling in love very quickly.", "He fell head over heels for the city after one weekend.", "Pocket English"),
    )

    private data class ImportedDictionary(
        val info: DictionaryInfo,
        val entries: List<DictionaryEntry>,
    )

    @Volatile
    private var importedDictionaries: List<ImportedDictionary> = emptyList()

    fun info(): DictionaryInfo = info(importedInfos())

    private fun info(imported: List<DictionaryInfo>): DictionaryInfo = when (imported.size) {
        0 -> DictionaryInfo(name = "Demo dictionary", isDemo = true, entryCount = demoEntries.size)
        1 -> imported.single()
        else -> DictionaryInfo(
            name = "${imported.size} local dictionaries",
            entryCount = imported.sumOf { it.entryCount },
        )
    }

    fun importedInfos(): List<DictionaryInfo> = index?.infos() ?: importedDictionaries.map { it.info }

    fun preview(uri: String, limit: Int = 12): List<DictionaryEntry> =
        index?.preview(uri, limit) ?: importedDictionaries.firstOrNull { it.info.uri == uri }?.entries?.take(limit).orEmpty()

    fun rename(uri: String, name: String): DictionaryInfo? {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Enter a dictionary name." }
        if (index != null) return index.rename(uri, cleanName)
        var renamed: DictionaryInfo? = null
        importedDictionaries = importedDictionaries.map { imported ->
            if (imported.info.uri != uri) return@map imported
            val info = imported.info.copy(name = cleanName)
            renamed = info
            ImportedDictionary(info, imported.entries.map { it.copy(source = cleanName) })
        }
        return renamed
    }

    fun setDirection(uri: String, direction: LookupDirection?): DictionaryInfo? {
        if (index != null) return index.setDirection(uri, direction)
        importedDictionaries = importedDictionaries.map {
            if (it.info.uri == uri) it.copy(info = it.info.copy(lookupDirection = direction)) else it
        }
        return importedDictionaries.firstOrNull { it.info.uri == uri }?.info
    }

    fun remove(uri: String): Boolean {
        if (index != null) return index.remove(uri)
        val next = importedDictionaries.filterNot { it.info.uri == uri }
        val changed = next.size != importedDictionaries.size
        importedDictionaries = next
        return changed
    }

    suspend fun import(
        uri: Uri,
        onProgress: (DictionaryImportProgress) -> Unit = {},
    ): DictionaryInfo = withContext(Dispatchers.IO) {
        index?.info(uri.toString())?.let {
            onProgress(DictionaryImportProgress("Already added", 1f))
            return@withContext it
        }
        importedDictionaries.firstOrNull { it.info.uri == uri.toString() }?.let {
            onProgress(DictionaryImportProgress("Already added", 1f))
            return@withContext it.info
        }
        onProgress(DictionaryImportProgress("Opening dictionary…", 0.01f))
        val displayName = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")
        val expectedLength = runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }.getOrDefault(-1L)
        val inputFile = File.createTempFile("dictionary-import-", ".mdx")
        val recordFile = File.createTempFile("dictionary-records-", ".tmp")
        val keyFile = File.createTempFile("dictionary-keys-", ".tmp")
        try {
            resolver.openInputStream(uri)?.use { input ->
                inputFile.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    var lastReported = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_IMPORT_BYTES) { "Dictionary files must be smaller than 512 MB." }
                        output.write(buffer, 0, read)
                        if (total - lastReported >= PROGRESS_CHUNK_BYTES) {
                            onProgress(DictionaryImportProgress("Copying dictionary file…",
                                if (expectedLength > 0) 0.02f + 0.72f * (total.toFloat() / expectedLength).coerceIn(0f, 1f) else null))
                            lastReported = total
                        }
                    }
                }
            } ?: error("Could not read the selected dictionary file")
            val metadata = RandomAccessFile(inputFile, "r").use { file ->
                if (file.length() < 8) HeaderMetadata("", "UTF-8", 0.0, 0, false) else {
                    val prefix = ByteArray(4).also(file::readFully)
                    val standard = ByteBuffer.wrap(prefix).order(ByteOrder.BIG_ENDIAN).int
                    val legacy = ByteBuffer.wrap(prefix).order(ByteOrder.LITTLE_ENDIAN).int
                    val size = listOf(standard, legacy).firstOrNull { it > 0 && it.toLong() <= file.length() - 8 }
                    if (size == null) HeaderMetadata("", "UTF-8", 0.0, 0, false) else {
                        require(size <= MAX_HEADER_BYTES) { "MDX header is too large." }
                        file.seek(0)
                        parseHeader(ByteArray(size + 8).also(file::readFully))
                    }
                }
            }
            val fileName = displayName.substringAfterLast('/').replace(Regex("(?i)\\.mdx$"), "").trim()
            val dictionaryInfo = DictionaryInfo(
                name = fileName.ifBlank { metadata.title.ifBlank { "Imported MDX" } },
                uri = uri.toString(), supportsBinaryLookup = metadata.isRecognized && metadata.encryption == 0,
            )
            val entries = if (metadata.isRecognized) {
                parseMdxEntries(inputFile, recordFile, keyFile, metadata, dictionaryInfo.name) { fraction ->
                    onProgress(DictionaryImportProgress("Decoding dictionary entries…", 0.78f + 0.17f * fraction))
                }
            } else {
                require(inputFile.length() <= MAX_TEXT_BYTES) { "Not a supported MDX file. Select an unencrypted raw/zlib MDX dictionary." }
                parsePlainTextEntries(inputFile.readBytes(), metadata.encoding, dictionaryInfo.name).asSequence()
            }
            onProgress(DictionaryImportProgress("Saving searchable dictionary…", 0.98f))
            val completedInfo = if (index != null) index.save(dictionaryInfo, entries) else {
                val collected = entries.toList()
                require(collected.isNotEmpty()) { "No readable entries were found. This file may use unsupported MDX encryption or compression." }
                dictionaryInfo.copy(entryCount = collected.size).also {
                    importedDictionaries = importedDictionaries + ImportedDictionary(it, collected)
                }
            }
            onProgress(DictionaryImportProgress("Imported ${completedInfo.entryCount} entries", 1f))
            completedInfo
        } finally {
            inputFile.delete()
            recordFile.delete()
            keyFile.delete()
        }
    }

    fun lookup(rawQuery: String, context: String = "", direction: LookupDirection? = null): LookupResult {
        val query = rawQuery.trim().replace(Regex("\\s+"), " ").trim('“', '”', '"', '.', ',', '!', '?', '¿', '¡')
        val imported = importedInfos()
        val dictionaryInfo = info(imported)
        if (query.isBlank()) return LookupResult(query, emptyList(), context, dictionaryInfo.name)
        val normalized = query.lowercase(Locale.ROOT)
        val matches = if (index != null) {
            if (imported.isNotEmpty()) index.search(normalized, direction) else demoEntries.filter { direction == null || (it.source == "Pocket Spanish") == (direction == LookupDirection.SPANISH_TO_ENGLISH) }
                .filter { it.headword.lowercase(Locale.ROOT) == normalized || it.headword.lowercase(Locale.ROOT).contains(normalized) }
        } else {
            importedDictionaries.filter { direction == null || it.info.lookupDirection == null || it.info.lookupDirection == direction }.flatMap { it.entries }.ifEmpty { if (importedDictionaries.isEmpty()) demoEntries.filter { direction == null || (it.source == "Pocket Spanish") == (direction == LookupDirection.SPANISH_TO_ENGLISH) } else emptyList() }
                .filter { it.headword.lowercase(Locale.ROOT) == normalized || it.headword.lowercase(Locale.ROOT).contains(normalized) }
        }.distinct().ifEmpty {
            val available = if (importedDictionaries.isEmpty()) demoEntries.filter {
                direction == null || (it.source == "Pocket Spanish") == (direction == LookupDirection.SPANISH_TO_ENGLISH)
            } else importedDictionaries.filter {
                direction == null || it.info.lookupDirection == null || it.info.lookupDirection == direction
            }.flatMap { it.entries }
            available.filter { foldHeadword(it.headword) == foldHeadword(query) }
        }
        val suggested = if (matches.isEmpty() && direction == LookupDirection.SPANISH_TO_ENGLISH) {
            SpanishLookupSuggestions.candidates(query).flatMap { candidate ->
                if (index != null) index.search(candidate, direction).filter { it.headword.equals(candidate, true) }
                else importedDictionaries.filter { it.info.lookupDirection == null || it.info.lookupDirection == direction }
                    .flatMap { it.entries }.filter { it.headword.equals(candidate, true) }
            }.distinct().take(4)
        } else emptyList()
        val allMatches = matches.ifEmpty { suggested }
        val entries = allMatches.filter { it.headword.equals(query, ignoreCase = true) } +
            allMatches.filterNot { it.headword.equals(query, ignoreCase = true) }.take(4)
        if (entries.isNotEmpty()) return LookupResult(query, entries, context, dictionaryInfo.name)

        return LookupResult(query, emptyList(), context, dictionaryInfo.name)
    }

    private data class HeaderMetadata(
        val title: String,
        val encoding: String,
        val version: Double,
        val encryption: Int,
        val isRecognized: Boolean,
    )

    private fun parseHeader(bytes: ByteArray): HeaderMetadata {
        if (bytes.size < 8) return HeaderMetadata("", "UTF-8", 0.0, 0, false)
        return run {
            val headerSize = headerLength(bytes)
            if (headerSize <= 0 || headerSize > bytes.size - 8) return@run HeaderMetadata("", "UTF-8", 0.0, 0, false)
            val headerBytes = bytes.copyOfRange(4, 4 + headerSize)
            val header = String(headerBytes, Charsets.UTF_16LE)
            fun attribute(name: String): String = Regex("$name=\\\"(.*?)\\\"", RegexOption.IGNORE_CASE)
                .find(header)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val title = attribute("Title").ifBlank { attribute("Description") }
            val encoding = attribute("Encoding").ifBlank { "UTF-8" }
            val version = attribute("GeneratedByEngineVersion").toDoubleOrNull() ?: 1.2
            val encryption = when (val flag = attribute("Encrypted")) {
                "", "No", "no" -> 0
                "Yes", "yes" -> 1
                else -> flag.toIntOrNull() ?: error("Invalid MDX encryption flag")
            }
            val expectedChecksum = ByteBuffer.wrap(bytes, 4 + headerSize, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val actualChecksum = Adler32().apply { update(headerBytes) }.value.toInt()
            require(expectedChecksum == actualChecksum) { "MDX header checksum mismatch. The file may be damaged." }
            HeaderMetadata(title, encoding, version, encryption, true)
        }
    }

    private data class KeyBlockInfo(val entries: Int, val compressedSize: Int, val decompressedSize: Int)

    private fun parseMdxEntries(
        inputFile: File,
        recordFile: File,
        keyFile: File,
        metadata: HeaderMetadata,
        source: String,
        onProgress: (Float) -> Unit,
    ): Sequence<DictionaryEntry> {
        require(metadata.isRecognized && metadata.encryption == 0) { "Encrypted MDX is not supported" }
        val numberWidth = if (metadata.version >= 2.0) 8 else 4
        val input = RandomAccessFile(inputFile, "r")
        try {
            val headerPrefix = ByteArray(4).also(input::readFully)
            val standard = ByteBuffer.wrap(headerPrefix).order(ByteOrder.BIG_ENDIAN).int
            val headerSize = if (standard > 0 && standard.toLong() <= input.length() - 8) standard
                else ByteBuffer.wrap(headerPrefix).order(ByteOrder.LITTLE_ENDIAN).int
            val cursor = ByteCursor(input, 4 + headerSize + 4)
            val keyBlockCount = cursor.readUnsigned(numberWidth).toInt()
            val entryCount = cursor.readUnsigned(numberWidth)
            require(keyBlockCount in 1..100_000 && entryCount in 1..20_000_000) { "Invalid MDX key block preamble" }
            val keyInfoDecompressedSize = if (metadata.version >= 2.0) cursor.readUnsigned(8) else 0L
            val keyInfoCompressedSize = cursor.readUnsigned(numberWidth).toInt()
            val keyBlockSize = if (metadata.version >= 2.0) cursor.readUnsigned(8).toInt() else cursor.readUnsigned(4).toInt()
            if (metadata.version >= 2.0) cursor.skip(4)
            require(keyInfoCompressedSize in 0..MAX_BLOCK_BYTES) { "MDX key metadata is too large." }
            val keyInfoBytes = cursor.readBytes(keyInfoCompressedSize)
            val keyInfo = if (metadata.version >= 2.0) inflate(keyInfoBytes.copyOfRange(8, keyInfoBytes.size)) else keyInfoBytes
            if (metadata.version >= 2.0) require(keyInfoDecompressedSize == 0L || keyInfo.size == keyInfoDecompressedSize.toInt())
            val blockInfo = parseKeyBlockInfo(keyInfo, keyBlockCount, numberWidth, metadata)
            require(blockInfo.sumOf { it.compressedSize.toLong() } == keyBlockSize.toLong()) { "MDX key block size mismatch" }
            val keyCount = parseKeys(cursor, keyFile, blockInfo, numberWidth, metadata)
            require(keyCount == entryCount) { "MDX key count mismatch" }

            val recordBlockCount = cursor.readUnsigned(numberWidth).toInt()
            val recordEntryCount = cursor.readUnsigned(numberWidth)
            require(recordBlockCount in 1..100_000 && recordEntryCount == entryCount) { "Invalid MDX record block preamble" }
            val recordInfoSize = cursor.readUnsigned(numberWidth).toInt()
            val recordBlockSize = cursor.readUnsigned(numberWidth).toInt()
            require(recordInfoSize == recordBlockCount * numberWidth * 2) { "Invalid MDX record info" }
            val recordInfo = List(recordBlockCount) {
                cursor.readUnsigned(numberWidth).toInt() to cursor.readUnsigned(numberWidth).toInt()
            }
            require(recordInfo.sumOf { it.first.toLong() } == recordBlockSize.toLong()) { "MDX record block size mismatch" }
            recordFile.outputStream().use { output ->
                recordInfo.forEachIndexed { index, (compressedSize, decompressedSize) ->
                    require(compressedSize in 8..MAX_BLOCK_BYTES) { "MDX record block is too large." }
                    val decoded = decodeBlock(cursor.readBytes(compressedSize), decompressedSize)
                    output.write(decoded)
                    require(recordFile.length() <= MAX_RECORD_BYTES) { "Decoded dictionary exceeds 2 GB." }
                    onProgress((index + 1f) / recordInfo.size)
                }
            }
            return sequence {
                RandomAccessFile(recordFile, "r").use { records ->
                    DataInputStream(keyFile.inputStream().buffered()).use { keys ->
                        fun readKey(): Pair<Long, String> {
                            val offset = keys.readLong()
                            val size = keys.readInt()
                            require(size in 0..MAX_ENTRY_BYTES) { "MDX headword is too large." }
                            return offset to String(ByteArray(size).also(keys::readFully), Charsets.UTF_8)
                        }
                        var current = readKey()
                        repeat(keyCount.toInt()) { index ->
                            val (start, key) = current
                            val next = if (index + 1 < keyCount) readKey() else null
                            val end = next?.first ?: records.length()
                            require(start >= 0 && end >= start && end <= records.length()) { "Invalid MDX record offset" }
                            require(end - start <= MAX_ENTRY_BYTES) { "A dictionary entry exceeds 2 MB." }
                            records.seek(start)
                            val bytes = ByteArray((end - start).toInt()).also(records::readFully)
                            val definition = decodeText(bytes, metadata.encoding)
                                .replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
                            if (key.isNotBlank() && definition.isNotBlank()) yield(DictionaryEntry(key, "", definition, source = source))
                            if (next != null) current = next
                        }
                    }
                }
            }
        } finally {
            input.close()
        }
    }

    private fun parseKeyBlockInfo(bytes: ByteArray, blockCount: Int, numberWidth: Int, metadata: HeaderMetadata): List<KeyBlockInfo> {
        val cursor = ByteCursor(bytes)
        val lengthWidth = if (metadata.version >= 2.0) 2 else 1
        val unitWidth = if (dictionaryCharset(metadata.encoding) in listOf(Charsets.UTF_16LE, Charsets.UTF_16BE)) 2 else 1
        return List(blockCount) {
            val entries = cursor.readUnsigned(numberWidth).toInt()
            val headSize = cursor.readUnsigned(lengthWidth).toInt()
            cursor.skip(unitWidth * (headSize + if (metadata.version >= 2.0) 1 else 0))
            val tailSize = cursor.readUnsigned(lengthWidth).toInt()
            cursor.skip(unitWidth * (tailSize + if (metadata.version >= 2.0) 1 else 0))
            KeyBlockInfo(entries, cursor.readUnsigned(numberWidth).toInt(), cursor.readUnsigned(numberWidth).toInt())
        }
    }

    private fun parseKeys(input: ByteCursor, keyFile: File, blocks: List<KeyBlockInfo>, numberWidth: Int, metadata: HeaderMetadata): Long {
        var count = 0L
        DataOutputStream(keyFile.outputStream().buffered()).use { output ->
            blocks.forEach { blockInfo ->
                require(blockInfo.compressedSize in 8..MAX_BLOCK_BYTES) { "MDX key block is too large." }
                val block = input.readBytes(blockInfo.compressedSize)
                val cursor = ByteCursor(decodeBlock(block, blockInfo.decompressedSize))
                require(blockInfo.entries in 0..20_000_000) { "Invalid MDX key count" }
                repeat(blockInfo.entries) {
                    val offset = cursor.readUnsigned(numberWidth)
                    val key = cursor.readNullTerminated(metadata.encoding).toByteArray(Charsets.UTF_8)
                    output.writeLong(offset)
                    output.writeInt(key.size)
                    output.write(key)
                    count++
                }
            }
        }
        return count
    }

    private fun decodeBlock(block: ByteArray, expectedSize: Int): ByteArray {
        require(block.size >= 8) { "Short MDX block" }
        require(expectedSize in 0..MAX_BLOCK_BYTES) { "Decoded MDX block is too large." }
        return when (block[0].toInt() and 0xff) {
            0 -> block.copyOfRange(8, block.size).also { require(it.size == expectedSize) { "MDX block size mismatch" } }
            2 -> inflate(block.copyOfRange(8, block.size)).also { require(it.size == expectedSize) }
            else -> error("LZO or unknown MDX compression")
        }
    }

    private fun inflate(bytes: ByteArray): ByteArray = InflaterInputStream(ByteArrayInputStream(bytes)).use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size().toLong() + count <= MAX_BLOCK_BYTES) { "Decoded MDX block is too large." }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }

    private fun decodeText(bytes: ByteArray, encoding: String): String = runCatching {
        String(bytes, dictionaryCharset(encoding)).trim('\u0000')
    }.getOrDefault("")

    private class ByteCursor(private val bytes: ByteArray, private var offset: Int = 0, private val file: RandomAccessFile? = null) {
        constructor(file: RandomAccessFile, offset: Int) : this(ByteArray(0), offset, file)
        private val length: Long get() = file?.length() ?: bytes.size.toLong()
        fun readUnsigned(width: Int): Long {
            require(width == 1 || width == 2 || width == 4 || width == 8)
            val data = readBytes(width)
            var value = 0L
            repeat(width) { value = (value shl 8) or (data[it].toLong() and 0xff) }
            return value
        }

        fun readBytes(length: Int): ByteArray {
            require(length >= 0 && offset.toLong() + length <= this.length) { "Unexpected end of MDX" }
            return if (file != null) {
                file.seek(offset.toLong())
                ByteArray(length).also { file.readFully(it); offset += length }
            } else bytes.copyOfRange(offset, offset + length).also { offset += length }
        }

        fun skip(length: Int) {
            require(length >= 0 && offset.toLong() + length <= this.length) { "Unexpected end of MDX" }
            offset += length
        }

        fun readNullTerminated(encoding: String): String {
            val utf16 = encoding.equals("UTF-16", true) || encoding.equals("UTF-16LE", true) || encoding.equals("UTF-16BE", true)
            val start = offset
            if (utf16) {
                while (offset + 1 < bytes.size && (bytes[offset].toInt() != 0 || bytes[offset + 1].toInt() != 0)) offset += 2
                val charset = dictionaryCharset(encoding)
                return String(bytes.copyOfRange(start, offset), charset).also { offset = (offset + 2).coerceAtMost(bytes.size) }
            }
            while (offset < bytes.size && bytes[offset].toInt() != 0) offset++
            return String(bytes.copyOfRange(start, offset), dictionaryCharset(encoding)).also { if (offset < bytes.size) offset++ }
        }
    }

    private fun parsePlainTextEntries(bytes: ByteArray, encoding: String, source: String): List<DictionaryEntry> {
        val candidates = linkedSetOf<String>()
        val charset = dictionaryCharset(encoding)
        candidates += runCatching { String(bytes, charset) }.getOrDefault("")
        if (charset != Charsets.UTF_8) candidates += runCatching { String(bytes, Charsets.UTF_8) }.getOrDefault("")
        return candidates.flatMap { text ->
            text.split('\n', '\r', '\u0000').mapNotNull { line ->
                val clean = line.replace(Regex("[\\u0001-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "").trim()
                val split = clean.split('\t', limit = 2)
                if (split.size != 2 || split[0].length !in 1..80 || split[1].length !in 3..900) return@mapNotNull null
                DictionaryEntry(split[0].replace(Regex("<[^>]+>"), "").trim(), "", split[1].trim(), source = source)
            }
        }.filter { it.headword.isNotBlank() }.distinct().take(500)
    }

    private fun headerLength(bytes: ByteArray): Int {
        val standard = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.BIG_ENDIAN).int
        if (standard in 1..(bytes.size - 8)) return standard
        return ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
    }

    companion object {
        private const val MAX_IMPORT_BYTES = 512L * 1024 * 1024
        private const val MAX_HEADER_BYTES = 1024 * 1024
        private const val MAX_BLOCK_BYTES = 16 * 1024 * 1024
        private const val MAX_ENTRY_BYTES = 2 * 1024 * 1024
        private const val MAX_TEXT_BYTES = 4 * 1024 * 1024
        private const val MAX_RECORD_BYTES = 2L * 1024 * 1024 * 1024
        private const val PROGRESS_CHUNK_BYTES = 512 * 1024
    }
}

private fun dictionaryCharset(encoding: String): Charset = when (encoding.trim().uppercase(Locale.ROOT)) {
    "UTF-16", "UTF-16LE" -> Charsets.UTF_16LE
    "UTF-16BE" -> Charsets.UTF_16BE
    else -> runCatching { Charset.forName(encoding.trim()) }.getOrDefault(Charsets.UTF_8)
}
