package com.tim.d2a.data

import org.junit.Assert.*
import org.junit.Test

class AnkiStudyNoteSpecTest {
    @Test fun savesContentAndSelectedOccurrencesWithoutPresentationMarkup() {
        val fields = values(draft())
        assertEquals("Llegué tarde a casa.", fields["Sentence"])
        assertEquals("Llegué ", fields["SentenceBefore"])
        assertEquals("tarde", fields["SentenceTarget"])
        assertEquals(" a casa.", fields["SentenceAfter"])
        assertEquals("Hoy volveré tarde.", fields["ProductionSentence"])
        assertEquals("Hoy volveré ", fields["ProductionSentenceBefore"])
        assertEquals("tarde", fields["ProductionSentenceTarget"])
        assertEquals(".", fields["ProductionSentenceAfter"])
        assertEquals("I will return late today.", fields["ProductionPrompt"])
        assertEquals("late", fields["ProductionCue"])
        assertEquals("A time expression.", fields["Explanation"])
        assertEquals("Dictionary definition.", fields["Definitions"])
        assertFalse(fields.values.any { it.contains("<mark>") })
        assertEquals("note-1", fields["NoteKey"])
    }

    @Test fun escapesEveryContentFieldAndKeepsJsonAsInertData() {
        val fields = values(draft().copy(word = "<phrase>", context = "Literal [[<phrase>]] & don't execute.",
            meaning = "<img onerror='bad'>", includeProduction = false,
            recognitionAlternatives = listOf(StudyContext("Again [[<phrase>]] & \"quoted\".", "<script>"))))
        assertEquals("&lt;phrase&gt;", fields["SentenceTarget"])
        assertEquals("Literal &lt;phrase&gt; &amp; don&#39;t execute.", fields["Sentence"])
        assertFalse(fields.getValue("RecognitionContexts").contains("<script>"))
        assertTrue(fields.getValue("RecognitionContexts").contains("&lt;script&gt;"))
        assertTrue(fields.getValue("Meaning").contains("&lt;img"))
    }

    @Test fun productionDisabledUsesAnEmptyFieldToSuppressCardGeneration() {
        val draft = draft().copy(includeProduction = false, productionPrompt = "unfinished", productionAnswer = "")
        assertEquals("", values(draft)["EnableProduction"])
        assertEquals("1", values(draft())["EnableProduction"])
        val html = AnkiStudyNoteSpec.previewHtml(draft, production = true, reveal = false)
        assertFalse(html.contains("<main"))
    }

    @Test fun validatesTargetsAndAllAlternativeExamplesBeforeSaving() {
        assertNull(AnkiStudyNoteSpec.validationError(draft()))
        assertNotNull(AnkiStudyNoteSpec.validationError(draft().copy(context = "No target here.")))
        assertNotNull(AnkiStudyNoteSpec.validationError(draft().copy(productionPrompt = "No marked cue.")))
        assertNotNull(AnkiStudyNoteSpec.validationError(draft().copy(productionAnswer = "temprano")))
        assertNotNull(AnkiStudyNoteSpec.validationError(draft().copy(
            productionAlternatives = listOf(StudyContext("This has no Spanish phrase.")))))
    }

    @Test fun savesEachConjugatedAnswerAndUsesItInProductionPreview() {
        val verb = draft().copy(word = "padecer", productionAnswer = "padecer", meaning = "suffer from",
            context = "Ella [[padece]] asma.", productionContext = "Ellos [[padecen]] asma.",
            recognitionAlternatives = listOf(StudyContext("Él [[padeció]] asma.")),
            productionAlternatives = listOf(StudyContext("Nosotros [[padecemos]] asma.")))
        assertNull(AnkiStudyNoteSpec.validationError(verb))
        val fields = values(verb)
        assertEquals("padece", fields["SentenceTarget"])
        assertEquals("padecen", fields["ProductionSentenceTarget"])
        assertTrue(fields.getValue("RecognitionContexts").contains("padeció"))
        assertTrue(fields.getValue("ProductionContexts").contains("padecemos"))
        assertTrue(AnkiStudyNoteSpec.previewHtml(verb, true, true)
            .contains("<span class='solution' id='answer'>padecen</span>"))
    }

    @Test fun separatorsCannotChangeFieldCountAndNoteIdentitySurvivesEdits() {
        val edited = draft().copy(explanation = "one${AnkiStudyNoteSpec.FIELD_SEPARATOR}two")
        val encoded = AnkiStudyNoteSpec.encodeFields(AnkiStudyNoteSpec.buildFields(edited))
        assertEquals(AnkiStudyNoteSpec.fields.size, encoded.split(AnkiStudyNoteSpec.FIELD_SEPARATOR).size)
        assertFalse(encoded.contains("one${AnkiStudyNoteSpec.FIELD_SEPARATOR}two"))
        assertEquals("note-1", values(edited)["NoteKey"])
    }

    @Test fun requiresV2FieldsButAllowsManualTemplateNamesAndLayouts() {
        assertTrue(AnkiStudyNoteSpec.hasExpectedSchema(AnkiStudyNoteSpec.fields, 0))
        assertFalse(AnkiStudyNoteSpec.hasExpectedSchema(AnkiStudyNoteSpec.fields, 1))
        assertFalse(AnkiStudyNoteSpec.hasExpectedSchema(AnkiStudyNoteSpec.fields.dropLast(1), 0))
        assertTrue(AnkiStudyNoteSpec.hasExpectedTemplates(listOf(
            AnkiCardTemplate("My recognition", "{{SelectedText}}", "{{Meaning}}"),
            AnkiCardTemplate("My production", "{{ProductionPrompt}}", "{{ProductionAnswer}}"))))
        assertFalse(AnkiStudyNoteSpec.hasExpectedTemplates(AnkiStudyNoteSpec.templates.take(1)))
    }

    @Test fun bothFacesUseTheSameInlineSlotWithNoRepeatedQuestionOrVisibleNotesOnFront() {
        for (production in listOf(false, true)) {
            val front = AnkiStudyNoteSpec.previewHtml(draft(), production, false)
            val back = AnkiStudyNoteSpec.previewHtml(draft(), production, true)
            assertFalse(front.contains("<section"))
            assertTrue(back.contains("<section"))
            assertTrue(back.indexOf("class='reference'") > back.indexOf("id='answer'"))
            assertTrue(back.contains("A time expression."))
            assertTrue(back.contains("Dictionary definition."))
            assertFalse(front.contains("{{"))
            assertFalse(back.contains("{{FrontSide}}"))
            assertTrue(back.contains("id='answer'"))
            assertTrue(front.contains("class='measure' aria-hidden='true'"))
            assertTrue(back.contains("class='measure' aria-hidden='true'"))
        }
        assertTrue(AnkiStudyNoteSpec.previewHtml(draft(), true, false).contains("<span class='cue'>[late]</span>"))
        assertTrue(AnkiStudyNoteSpec.previewHtml(draft(), true, true).contains("<span class='solution' id='answer'>tarde</span>"))
        assertTrue(AnkiStudyNoteSpec.previewHtml(draft(), false, true).contains("<span class='solution' id='answer'>late</span>"))
    }

    @Test fun storesAllContextsAndTheirTranslationsIndependentlyOfTemplates() {
        val fields = values(draft().copy(
            recognitionAlternatives = listOf(StudyContext("Otra vez [[tarde]].", "Late again.")),
            productionAlternatives = listOf(StudyContext("Nunca llego [[tarde]].", "I never arrive late."))))
        assertTrue(fields.getValue("RecognitionContexts").contains("Late again."))
        assertTrue(fields.getValue("ProductionContexts").contains("I never arrive late."))
        assertFalse(fields.getValue("RecognitionContexts").contains("Nunca llego"))
    }

    @Test fun imageAppearsOnlyOnAnswersAndKeepsExistingSchema() {
        val image = draft().copy(imageFilename = "dictanki_1234-abcd.png", explanation = "<script>unsafe</script>")
        val fields = values(image)
        assertEquals(25, fields.size)
        assertTrue(fields.getValue("Explanation").contains("&lt;script&gt;"))
        assertTrue(fields.getValue("Explanation").contains("src='dictanki_1234-abcd.png'"))
        for (production in listOf(false, true)) {
            assertFalse(AnkiStudyNoteSpec.previewHtml(image, production, false).contains("<img"))
            assertTrue(AnkiStudyNoteSpec.previewHtml(image, production, true).contains("<img"))
        }
        assertTrue(AnkiStudyNoteSpec.previewHtml(image, false, true, imageSource = "data:image/png;base64,AA==")
            .contains("src='data:image/png;base64,AA=='"))
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsUnsafeImageFilenames() {
        AnkiStudyNoteSpec.buildFields(draft().copy(imageFilename = "../private.png"))
    }

    private fun values(draft: CardDraft) = AnkiStudyNoteSpec.fields.zip(AnkiStudyNoteSpec.buildFields(draft)).toMap()
    private fun draft() = CardDraft(word = "tarde", context = "Llegué [[tarde]] a casa.", meaning = "late",
        translation = "I got home late.", explanation = "A time expression.", definitions = "Dictionary definition.",
        tags = "Spanish", productionPrompt = "I will return [[late]] today.", productionAnswer = "tarde",
        includeProduction = true, productionContext = "Hoy volveré [[tarde]].",
        productionTranslation = "I will return late today.", noteKey = "note-1")
}
