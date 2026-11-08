package com.tim.d2a.data

/** The selected occurrence is data; templates decide whether to show a phrase, gloss, or gap. */
internal data class StudySentence(val before: String, val target: String, val after: String) {
    val text: String get() = before + target + after

    companion object {
        private val marker = Regex("\\[\\[(.+?)\\]\\]", RegexOption.DOT_MATCHES_ALL)

        fun spanish(sentence: String, phrase: String): StudySentence {
            require(phrase.isNotBlank()) { "Enter a Spanish word or phrase." }
            val text = sentence.ifBlank { phrase }
            if ("[[" in text || "]]" in text) {
                // Explicit markers select the surface form for this example, which may be
                // a conjugation of the lexical phrase. Unmarked legacy sentences still
                // require an unambiguous occurrence of the phrase below.
                return marked(text, "Spanish sentence")
            }
            val occurrences = buildList {
                var from = 0
                while (from <= text.length - phrase.length) {
                    val start = text.indexOf(phrase, from, ignoreCase = true)
                    if (start < 0) break
                    val end = start + phrase.length
                    if ((!isWordCharacter(phrase.codePointAt(0)) || start == 0 ||
                            !isWordCharacter(text.codePointBefore(start))) &&
                        (!isWordCharacter(phrase.codePointBefore(phrase.length)) || end == text.length ||
                            !isWordCharacter(text.codePointAt(end)))) add(start)
                    from = start + 1
                }
            }
            require(occurrences.isNotEmpty()) { "Include the Spanish word or phrase in its sentence." }
            require(occurrences.size == 1) {
                "The phrase occurs more than once. Mark the occurrence to study with [[…]]."
            }
            val start = occurrences.single()
            return StudySentence(text.substring(0, start), text.substring(start, start + phrase.length),
                text.substring(start + phrase.length))
        }

        fun english(prompt: String): StudySentence = marked(prompt, "English cue")

        private fun isWordCharacter(codePoint: Int): Boolean = Character.isLetterOrDigit(codePoint) ||
            Character.getType(codePoint) in listOf(
                Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(),
                Character.ENCLOSING_MARK.toInt(),
            )

        private fun marked(text: String, label: String): StudySentence {
            val matches = marker.findAll(text).toList()
            require(matches.size == 1) { "$label must mark exactly one phrase with [[…]]." }
            val match = matches.single()
            val parts = StudySentence(text.substring(0, match.range.first), match.groupValues[1],
                text.substring(match.range.last + 1))
            require(parts.target.isNotBlank() && listOf(parts.before, parts.target, parts.after)
                .none { "[[" in it || "]]" in it }) {
                "$label must mark exactly one nonempty phrase with [[…]]."
            }
            return parts
        }
    }
}
