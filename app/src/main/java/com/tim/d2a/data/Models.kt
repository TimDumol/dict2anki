package com.tim.d2a.data

enum class LookupDirection(val label: String) {
    SPANISH_TO_ENGLISH("Spanish → English"),
    ENGLISH_TO_SPANISH("English → Spanish"),
}

data class DictionaryEntry(
    val headword: String,
    val partOfSpeech: String,
    val definition: String,
    val example: String = "",
    val source: String,
)

data class LookupResult(
    val query: String,
    val entries: List<DictionaryEntry>,
    val context: String = "",
    val dictionaryName: String,
)

data class DictionaryInfo(
    val name: String,
    val uri: String? = null,
    val isDemo: Boolean = false,
    val supportsBinaryLookup: Boolean = false,
    val entryCount: Int = 0,
    val lookupDirection: LookupDirection? = null,
)

data class DictionaryImportProgress(
    val message: String,
    val fraction: Float? = null,
)

data class CardDraft(
    val word: String,
    val context: String,
    val meaning: String,
    val translation: String,
    val explanation: String,
    val definitions: String,
    val tags: String,
    val productionPrompt: String,
    val productionAnswer: String,
    val includeProduction: Boolean = false,
    val productionContext: String = "",
    val productionTranslation: String = "",
    val recognitionAlternatives: List<StudyContext> = emptyList(),
    val productionAlternatives: List<StudyContext> = emptyList(),
    val noteKey: String = java.util.UUID.randomUUID().toString(),
    val imageFilename: String = "",
    val imagePrompt: String = "",
)

data class StudyContext(val sentence: String, val translation: String = "")

data class AiCardContent(
    val meaning: String,
    val translation: String,
    val explanation: String,
    val tags: List<String>,
    val productionPrompt: String,
    val productionAnswer: String,
    val spanishWord: String = "",
    val spanishContext: String = "",
    val productionContext: String = "",
    val productionTranslation: String = "",
    val recognitionAlternatives: List<StudyContext> = emptyList(),
    val productionAlternatives: List<StudyContext> = emptyList(),
    val dictionaryNotes: String = "",
    val imagePrompt: String = "",
)

data class OpenRouterModel(
    val id: String,
    val name: String,
    val promptPricePerMillion: Double,
    val completionPricePerMillion: Double,
    val speedLabel: String,
    val supportsJsonResponseFormat: Boolean = true,
)

data class AnkiDeck(val id: Long, val name: String)

data class AnkiDestination(
    val deck: AnkiDeck,
    val modelId: Long,
    val modelName: String,
)

interface AnkiDroidOperations {
    fun isInstalled(): Boolean
    fun hasPermission(): Boolean
    suspend fun listDecks(): List<AnkiDeck>
    suspend fun updateStudyTemplates(): Long = ensureStudyModel()
    suspend fun ensureStudyModel(): Long
    suspend fun addNote(destination: AnkiDestination, draft: CardDraft): Long
}
