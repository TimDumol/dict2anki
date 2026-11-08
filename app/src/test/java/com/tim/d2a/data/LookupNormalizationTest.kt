package com.tim.d2a.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupNormalizationTest {
    @Test fun accentsCaseAndWhitespaceNormalizeForSuggestions() {
        assertEquals("arbol", foldHeadword(" ÁRBOL "))
        assertEquals("me apetece", foldHeadword(" me   apetece "))
        assertEquals(foldHeadword("café"), foldHeadword("cafe\u0301"))
    }
    @Test fun inflectionsOfferHeadwordCandidatesWithoutReplacingTheQuery() {
        assertEquals(listOf("tener"), SpanishLookupSuggestions.candidates("tuve"))
        assertTrue("hablar" in SpanishLookupSuggestions.candidates("habló"))
        assertTrue("comer" in SpanishLookupSuggestions.candidates("comiendo"))
        assertTrue(SpanishLookupSuggestions.candidates("me apetece").isEmpty())
    }
}
