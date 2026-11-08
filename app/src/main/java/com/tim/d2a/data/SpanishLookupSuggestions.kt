package com.tim.d2a.data

/** Candidate headwords, never automatic corrections; only actual dictionary entries are offered. */
internal object SpanishLookupSuggestions {
    private val irregular = mapOf(
        "tuve" to listOf("tener"), "tengo" to listOf("tener"), "tiene" to listOf("tener"),
        "fui" to listOf("ir", "ser"), "fue" to listOf("ir", "ser"), "soy" to listOf("ser"),
        "es" to listOf("ser"), "era" to listOf("ser"), "voy" to listOf("ir"), "va" to listOf("ir"),
        "estoy" to listOf("estar"), "estuve" to listOf("estar"), "hice" to listOf("hacer"),
        "hizo" to listOf("hacer"), "hago" to listOf("hacer"), "dije" to listOf("decir"),
        "dijo" to listOf("decir"), "digo" to listOf("decir"), "puedo" to listOf("poder"),
        "pude" to listOf("poder"), "quiero" to listOf("querer"), "quise" to listOf("querer"),
        "vengo" to listOf("venir"), "vine" to listOf("venir"), "sé" to listOf("saber"),
        "supe" to listOf("saber"), "puse" to listOf("poner"), "pongo" to listOf("poner"),
        "vi" to listOf("ver"), "vio" to listOf("ver"), "veo" to listOf("ver"),
    )
    private val suffixes = listOf("ábamos", "abais", "aban", "ando", "iendo", "ados", "idas", "idos",
        "ado", "ido", "aste", "aron", "iste", "ieron", "amos", "emos", "imos", "aba", "ías",
        "ía", "áis", "éis", "an", "en", "as", "es", "ó", "é", "í", "o", "a", "e")
    fun candidates(word: String): List<String> {
        val clean = word.trim().lowercase(java.util.Locale.ROOT)
        if (' ' in clean) return emptyList()
        irregular[clean]?.let { return it }
        return (suffixes.flatMap { suffix ->
            if (clean.endsWith(suffix) && clean.length - suffix.length >= 2) {
                val stem = clean.dropLast(suffix.length)
                listOf(stem + "ar", stem + "er", stem + "ir")
            } else emptyList()
        }).distinct().filterNot { it == clean }
    }
}
