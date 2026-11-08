package com.tim.d2a.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyCardResponseSchemaTest {
    @Test
    fun requestsStrictJsonSchemaOutput() {
        val responseFormat = StudyCardResponseSchema.responseFormat
        val jsonSchema = responseFormat["json_schema"] as Map<*, *>
        val schema = jsonSchema["schema"] as Map<*, *>

        assertEquals("json_schema", responseFormat["type"])
        assertEquals("dict_anki_card", jsonSchema["name"])
        assertEquals(true, jsonSchema["strict"])
        assertEquals("object", schema["type"])
        assertEquals(false, schema["additionalProperties"])
        assertEquals(StudyCardResponseSchema.keys, schema["required"])
    }

    @Test
    fun requiresTypedFieldsAndLimitsTagsToThree() {
        val schema = (StudyCardResponseSchema.responseFormat["json_schema"] as Map<*, *>)["schema"] as Map<*, *>
        val properties = schema["properties"] as Map<*, *>
        val expectedStrings = setOf(
            "spanish_word",
            "spanish_context",
            "meaning",
            "translation",
            "explanation",
            "dictionary_notes",
            "image_prompt",
            "production_prompt",
            "production_answer",
            "production_context",
            "production_translation",
        )

        expectedStrings.forEach { key ->
            assertEquals("string", (properties[key] as Map<*, *>)["type"])
        }
        for (key in listOf("recognition_alternatives", "production_alternatives")) {
            val alternatives = properties[key] as Map<*, *>
            assertEquals("array", alternatives["type"])
            assertEquals(4, alternatives["minItems"])
            assertEquals(4, alternatives["maxItems"])
            val item = alternatives["items"] as Map<*, *>
            assertEquals(false, item["additionalProperties"])
            assertEquals(listOf("sentence", "translation"), item["required"])
        }
        val tags = properties["tags"] as Map<*, *>
        assertEquals("array", tags["type"])
        assertEquals("string", (tags["items"] as Map<*, *>)["type"])
        assertEquals(3, tags["maxItems"])
        assertTrue(StudyCardResponseSchema.keys.containsAll(expectedStrings))
        assertFalse(StudyCardResponseSchema.keys.contains("unused_field"))
    }
}
