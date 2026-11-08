package com.tim.d2a.data

import org.junit.Assert.*
import org.junit.Test

class FlashcardImageTest {
    @Test fun comparesTokenAndImagePricesWithoutCallingMissingPricesFree() {
        val token = ImageModel("token", "Token model", null, listOf(
            ImagePrice("input_text", "token", 0.000002), ImagePrice("output_image", "token", 0.00003)))
        assertEquals(0.0456, token.estimatedCost!!, 0.000001)
        assertTrue(token.priceLabel.contains("$2/M tokens"))
        assertTrue(token.priceLabel.contains("$30/M tokens"))
        val fixed = ImageModel("fixed", "Fixed", null, listOf(ImagePrice("output_image", "image", 0.04)))
        assertEquals(0.04, fixed.estimatedCost!!, 0.000001)
        assertEquals(listOf("fixed", "token"), listOf(token, fixed).sortedBy { it.estimatedCost }.map { it.id })
        assertNull(fixed.copy(prices = emptyList()).estimatedCost)
        assertNull(fixed.copy(prices = listOf(ImagePrice("output", "unknown", 1.0))).estimatedCost)
    }

    @Test fun tiersUseLowestPriceRatherThanAddingMutuallyExclusiveResolutions() {
        val model = ImageModel("tiered", "Tiered", null, listOf(
            ImagePrice("output_image", "image", 0.04, "1k"), ImagePrice("output_image", "image", 0.08, "2k")))
        assertEquals(0.04, model.estimatedCost!!, 0.000001)
        assertTrue(model.priceLabel.contains("2k"))
    }

    @Test fun promptUsesContextualSenseAndAvoidsTextOrSentenceSpecificCues() {
        val draft = CardDraft("manzana", "La tienda está en esta [[manzana]].", "city block",
            "The shop is on this block.", "", "", "", "", "")
        val brief = "Jewel-bright buildings encircled by four streets. Again, no fruit."
        val prompt = FlashcardImagePrompt.build(draft.copy(imagePrompt = brief), "Use warm colors")
        assertTrue(prompt.contains(brief))
        assertTrue(FlashcardImagePrompt.build(draft).contains("Design a distinctive scene"))
        assertTrue(prompt.contains("Intended meaning: city block"))
        assertTrue(prompt.contains("La tienda está en esta manzana."))
        assertTrue(prompt.contains("Use warm colors"))
        assertTrue(prompt.contains("No text, letters, labels"))
        assertTrue(prompt.contains("rotates through different examples"))
        assertTrue(prompt.contains("thumbnail size"))
    }
}
