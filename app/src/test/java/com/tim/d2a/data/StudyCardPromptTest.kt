package com.tim.d2a.data

import org.junit.Assert.assertTrue
import org.junit.Test

class StudyCardPromptTest {
    @Test fun keepsManzanaCityBlockSenseAcrossEveryExample() {
        val prompt = StudyCardPrompt.build("manzana", "city block", "English", null,
            emptyList(), LookupDirection.SPANISH_TO_ENGLISH)
        assertTrue(prompt.contains("ALL ten examples must describe city blocks"))
        assertTrue(prompt.contains("check EACH of the ten sentences and translations"))
    }

    @Test fun generatesSampleContextsWithoutDictionaryForBothDirections() {
        LookupDirection.entries.forEach { direction ->
            val query = if (direction == LookupDirection.ENGLISH_TO_SPANISH) "running late" else "voy con retraso"
            val prompt = StudyCardPrompt.build(query, "", "English", null, emptyList(), direction)
            assertTrue(prompt.contains(query))
            assertTrue(prompt.contains(direction.label))
            assertTrue(prompt.contains("invent a natural sample sentence"))
            assertTrue(prompt.contains("a DIFFERENT short Spanish sentence"))
            assertTrue(prompt.contains("If absent, generate using your language knowledge"))
            assertTrue(prompt.contains("Always create Spanish → English recognition and English-cued Spanish production"))
        }
    }

    @Test fun requestsOneMarkedCueWithoutInstructionalBoilerplate() {
        val prompt = StudyCardPrompt.build("tarde", "Llegué tarde.", "English", null, emptyList(), LookupDirection.SPANISH_TO_ENGLISH)
        assertTrue(prompt.contains("[[English phrase]] marking the cue corresponding"))
        assertTrue(prompt.contains("I arrived [[late]] for the meeting."))
        assertTrue(prompt.contains("Never include instructions, questions, labels, HTML, or Anki cloze syntax"))
        assertTrue(prompt.contains("ideally 1–5 words"))
    }

    @Test fun requiresDistinctContextsWithOneSenseAndAllowsConjugations() {
        val prompt = StudyCardPrompt.build("tarde", "", "English", null, emptyList(), LookupDirection.SPANISH_TO_ENGLISH)
        assertTrue(prompt.contains("All ten Spanish sentences must differ meaningfully"))
        assertTrue(prompt.contains("Verb conjugations may differ"))
        assertTrue(prompt.contains("[[padece]], [[padecen]], or [[padeció]]"))
        assertTrue(prompt.contains("same short English meaning"))
        assertTrue(prompt.contains("meaning` is one brief English gloss, ideally 1–5 words"))
        assertTrue(prompt.contains("replaces the bracketed gloss"))
    }

    @Test fun prioritizesContextAndRequiresUsefulReferenceMaterial() {
        val prompt = StudyCardPrompt.build("banco", "A seat in the park, not a financial institution.",
            "English", null, emptyList(), LookupDirection.SPANISH_TO_ENGLISH)
        assertTrue(prompt.contains("A seat in the park, not a financial institution."))
        assertTrue(prompt.contains("Supplied context is the primary evidence"))
        assertTrue(prompt.contains("Always provide an explanation"))
        assertTrue(prompt.contains("Omit unrelated senses and entries"))
        assertTrue(prompt.contains("Never invent dictionary excerpts"))
    }

    @Test fun includesSuppliedContextAndOptionalDictionaryReferences() {
        val entry = DictionaryEntry("tarde", "", "late", source = "Spanish")
        val prompt = StudyCardPrompt.build("tarde", "Llegué tarde.", "English", entry, listOf(entry), LookupDirection.SPANISH_TO_ENGLISH)
        assertTrue(prompt.contains("Llegué tarde."))
        assertTrue(prompt.contains("Dictionary definition: late"))
        assertTrue(prompt.contains("Spanish: tarde — late"))
        assertTrue(prompt.contains("as the lexical expression in spanish_word"))
    }
}
