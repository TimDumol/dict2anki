package com.tim.d2a.data

/** JSON source data, escaped as HTML only at the Anki field boundary. No template markup is stored. */
internal object StudyContextCodec {
    fun encode(contexts: List<StudyContext>, phrase: String): String = contexts.joinToString(prefix = "[", postfix = "]") { context ->
        val parts = StudySentence.spanish(context.sentence, phrase)
        mapOf("before" to parts.before, "target" to parts.target, "after" to parts.after,
            "translation" to context.translation).entries.joinToString(prefix = "{", postfix = "}") {
            jsonString(it.key) + ":" + jsonString(it.value)
        }
    }

    private fun jsonString(text: String): String = buildString {
        append('"')
        text.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 32) append("\\u" + char.code.toString(16).padStart(4, '0')) else append(char)
            }
        }
        append('"')
    }
}
