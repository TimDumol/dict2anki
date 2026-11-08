package com.tim.d2a.data

import org.junit.Assert.*
import org.junit.Test

class StudySentenceTest {
    @Test fun selectsTheExplicitOccurrenceInRepeatedText() {
        val parts = StudySentence.spanish("Tarde o temprano, llegué [[tarde]].", "tarde")
        assertEquals("Tarde o temprano, llegué ", parts.before)
        assertEquals("tarde", parts.target)
        assertEquals(".", parts.after)
        assertEquals("Tarde o temprano, llegué tarde.", parts.text)
    }

    @Test fun acceptsAnUnambiguousUnmarkedSentenceAndPreservesCase() {
        val parts = StudySentence.spanish("Me apetece tomar café.", "me apetece")
        assertEquals("Me apetece", parts.target)
        assertEquals(" tomar café.", parts.after)
    }

    @Test fun rejectsMissingAmbiguousEmptyTargets() {
        listOf("sin objetivo", "tarde y tarde", "[[]] tarde", "[[tarde]] [[tarde]]", "[[[[tarde]]")
            .forEach { text -> assertThrows(IllegalArgumentException::class.java) { StudySentence.spanish(text, "tarde") } }
    }

    @Test fun explicitMarkersSelectConjugatedAnswersForAnInfinitive() {
        assertEquals("padece", StudySentence.spanish("Ella [[padece]] asma.", "padecer").target)
        assertEquals("padecieron", StudySentence.spanish("Ellos [[padecieron]] mucho.", "padecer").target)
        assertThrows(IllegalArgumentException::class.java) {
            StudySentence.spanish("Ella padece asma.", "padecer")
        }
    }

    @Test fun englishCueHasExactlyOneMarkedPhrase() {
        assertEquals("late", StudySentence.english("I was [[late]] again.").target)
        assertThrows(IllegalArgumentException::class.java) { StudySentence.english("Unmarked cue") }
    }

    @Test fun matchesWholeWordsRatherThanFragments() {
        val parts = StudySentence.spanish("Ella canta en casa.", "en")
        assertEquals("Ella canta ", parts.before)
        assertEquals(" casa.", parts.after)
        assertThrows(IllegalArgumentException::class.java) { StudySentence.spanish("Ella canta.", "la") }
        assertEquals("pan", StudySentence.spanish("Compré empanadas y pan.", "pan").target)
    }

    @Test fun detectsOverlappingOccurrences() {
        assertThrows(IllegalArgumentException::class.java) {
            StudySentence.spanish("va va va", "va va")
        }
    }
}
