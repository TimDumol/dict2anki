package com.tim.d2a.data

import org.json.JSONArray
import org.json.JSONObject

/** Version-independent content storage for drafts and successful save history. */
internal object DraftStorage {
    fun encode(cards: List<CardDraft>): String = JSONArray().apply {
        cards.forEach { card -> put(JSONObject().apply {
            put("imageFilename", card.imageFilename)
            put("imagePrompt", card.imagePrompt)
            put("word", card.word); put("context", card.context); put("meaning", card.meaning)
            put("translation", card.translation); put("explanation", card.explanation)
            put("definitions", card.definitions); put("tags", card.tags)
            put("productionPrompt", card.productionPrompt); put("productionAnswer", card.productionAnswer)
            put("includeProduction", card.includeProduction); put("productionContext", card.productionContext)
            put("productionTranslation", card.productionTranslation); put("noteKey", card.noteKey)
            fun contexts(items: List<StudyContext>) = JSONArray().apply {
                items.forEach { put(JSONObject().put("sentence", it.sentence).put("translation", it.translation)) }
            }
            put("recognitionAlternatives", contexts(card.recognitionAlternatives))
            put("productionAlternatives", contexts(card.productionAlternatives))
        }) }
    }.toString()

    fun decode(json: String): List<CardDraft> {
        val array = JSONArray(json)
        return (0 until array.length()).map { index ->
            val card = array.getJSONObject(index)
            fun contexts(key: String): List<StudyContext> {
                val items = card.optJSONArray(key) ?: return emptyList()
                return (0 until items.length()).map {
                    val item = items.getJSONObject(it)
                    StudyContext(item.getString("sentence"), item.optString("translation"))
                }
            }
            CardDraft(
                imageFilename = card.optString("imageFilename"),
                imagePrompt = card.optString("imagePrompt"),
                word = card.getString("word"), context = card.optString("context"),
                meaning = card.optString("meaning"), translation = card.optString("translation"),
                explanation = card.optString("explanation"), definitions = card.optString("definitions"),
                tags = card.optString("tags"), productionPrompt = card.optString("productionPrompt"),
                productionAnswer = card.optString("productionAnswer"),
                includeProduction = card.optBoolean("includeProduction"),
                productionContext = card.optString("productionContext"),
                productionTranslation = card.optString("productionTranslation"),
                recognitionAlternatives = contexts("recognitionAlternatives"),
                productionAlternatives = contexts("productionAlternatives"), noteKey = card.getString("noteKey"),
            )
        }
    }
}
