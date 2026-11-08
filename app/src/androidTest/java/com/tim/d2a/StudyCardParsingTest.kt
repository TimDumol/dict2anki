package com.tim.d2a

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tim.d2a.data.OpenRouterClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyCardParsingTest {
    @Test fun reportsTruncatedOutputBeforeParsingIncompleteJson() {
        val body = JSONObject().put("choices", JSONArray().put(JSONObject()
            .put("finish_reason", "length")
            .put("message", JSONObject().put("content", "{\"spanish_word\":\"manzana\","))))
        val error = assertThrows(IllegalStateException::class.java) {
            OpenRouterClient().parseResponse(body.toString())
        }
        assertTrue(error.message!!.contains("ran out of output tokens"))
        assertFalse(error.message!!.contains("spanish_word"))
    }

    @Test fun acceptsDistinctMarkedExamplesWithSeparateTranslations() {
        val card = OpenRouterClient().parseResponse(response(content()))
        assertEquals("A late arrival rushing toward a closing door.", card.imagePrompt)
        assertEquals("tarde", card.productionAnswer)
        assertEquals(4, card.recognitionAlternatives.size)
        assertEquals(4, card.productionAlternatives.size)
        assertEquals("Hoy volveré [[tarde]].", card.productionContext)
    }

    @Test fun acceptsDifferentConjugationsOfAnInfinitive() {
        val verb = content().apply {
            put("spanish_word", "padecer"); put("production_answer", "padecer")
            put("meaning", "suffer from")
            put("spanish_context", "Ella [[padece]] asma.")
            put("production_context", "Ellos [[padecen]] asma.")
            put("recognition_alternatives", contexts("Él [[padeció]] asma.", "Yo [[padezco]] asma.",
                "Nosotros [[padecíamos]] asma.", "Tú [[padeces]] asma."))
            put("production_alternatives", contexts("Ana [[padecía]] asma.", "Vosotros [[padecéis]] asma.",
                "Ellas [[padecieron]] asma.", "Nosotros [[padecemos]] asma."))
        }
        val card = OpenRouterClient().parseResponse(response(verb))
        assertEquals("padecer", card.spanishWord)
        assertEquals("Ellos [[padecen]] asma.", card.productionContext)
    }

    @Test fun rejectsRepeatedExamplesDifferentExpressionsAndMissingFields() {
        val duplicates = content().apply { put("production_context", getString("spanish_context")) }
        assertThrows(IllegalArgumentException::class.java) { OpenRouterClient().parseResponse(response(duplicates)) }
        val mismatched = content().apply { put("production_answer", "temprano") }
        assertThrows(IllegalArgumentException::class.java) { OpenRouterClient().parseResponse(response(mismatched)) }
        val omitted = content().apply { remove("production_context") }
        assertThrows(IllegalStateException::class.java) { OpenRouterClient().parseResponse(response(omitted)) }
        val target = content().apply {
            getJSONArray("production_alternatives").getJSONObject(0).put("sentence", "No matching expression.")
        }
        assertThrows(IllegalArgumentException::class.java) { OpenRouterClient().parseResponse(response(target)) }
    }

    private fun content() = JSONObject().apply {
        put("spanish_word", "tarde"); put("spanish_context", "Llegué [[tarde]] a casa.")
        put("meaning", "late"); put("translation", "I got home late."); put("dictionary_notes", ""); put("image_prompt", "A late arrival rushing toward a closing door.")
        put("explanation", "Here tarde means late rather than afternoon.")
        put("tags", JSONArray()); put("production_prompt", "I will return [[late]] today.")
        put("production_answer", "tarde"); put("production_context", "Hoy volveré [[tarde]].")
        put("production_translation", "I will return late today.")
        put("recognition_alternatives", contexts("El tren salió [[tarde]].", "Siempre cenamos [[tarde]].", "Ayer me desperté [[tarde]].", "El partido terminó [[tarde]]."))
        put("production_alternatives", contexts("La tienda cierra [[tarde]].", "Nunca llegues [[tarde]] a clase.", "La reunión empezó [[tarde]].", "Nos acostamos [[tarde]]."))
    }
    private fun contexts(vararg sentences: String) = JSONArray().apply {
        sentences.forEach { put(JSONObject().put("sentence", it).put("translation", "English translation.")) }
    }
    private fun response(content: JSONObject) = JSONObject().put("choices", JSONArray().put(
        JSONObject().put("message", JSONObject().put("content", content.toString())))).toString()
}
