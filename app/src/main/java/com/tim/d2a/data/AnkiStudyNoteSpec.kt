package com.tim.d2a.data

/** Stable content fields, independent of the app's current card layout. */
internal object AnkiStudyNoteSpec {
    const val MODEL_NAME = "Dict·Anki Language v2"
    const val FIELD_SEPARATOR = "\u001f"

    val fields = listOf(
        "LookupId", "Sentence", "SelectedText", "Meaning", "Translation", "Explanation",
        "Definitions", "Source", "EnableProduction", "ProductionPrompt", "ProductionAnswer",
        "SentenceBefore", "SentenceTarget", "SentenceAfter", "ProductionBefore", "ProductionCue", "ProductionAfter",
        "ProductionSentence", "ProductionSentenceBefore", "ProductionSentenceTarget", "ProductionSentenceAfter",
        "ProductionTranslation", "RecognitionContexts", "ProductionContexts", "NoteKey",
    )

    private const val DETAILS = "<section class='reference' aria-label='Supporting notes'>" +
        "<div class='translation'>{{Translation}}</div>" +
        "{{#Explanation}}<div class='notes explanation'>{{Explanation}}</div>{{/Explanation}}" +
        "{{#Definitions}}<div class='notes definitions'>{{Definitions}}</div>{{/Definitions}}" +
        "{{#Source}}<div class='source'>{{Source}}</div>{{/Source}}</section>"

    // Both faces reserve the same inline space. A flip replaces the cue without reflowing the sentence.
    private fun sentence(production: Boolean, reveal: Boolean): String {
        val prefix = if (production) "ProductionSentence" else "Sentence"
        val question = if (production) "[{{Meaning}}]" else "{{SentenceTarget}}"
        val answer = if (production) "{{ProductionSentenceTarget}}" else "{{Meaning}}"
        val visible = if (reveal) "<span class='solution' id='answer'>$answer</span>" else "<span class='cue'>$question</span>"
        val measure = if (reveal) question else answer
        return "<div class='sentence'><span class='before'>{{" + prefix + "Before}}</span><span class='slot'>$visible" +
            "<span class='measure' aria-hidden='true'>$measure</span></span><span class='after'>{{" + prefix + "After}}</span></div>"
    }

    private fun face(production: Boolean, reveal: Boolean): String {
        val direction = if (production) "production" else "recognition"
        val variants = if (production) "ProductionContexts" else "RecognitionContexts"
        val details = if (!reveal) "" else if (production) DETAILS.replace("{{Translation}}", "{{ProductionTranslation}}") else DETAILS
        return "<main class='study $direction' data-direction='$direction' data-reveal='$reveal'>" +
            "<div class='direction'>" + (if (production) "ES" else "EN") + "</div>" +
            sentence(production, reveal) + details +
            "<div class='variant-data' hidden>{{$variants}}</div><div class='note-key' hidden>{{NoteKey}}</div>" +
            "<div class='gloss-data' hidden>{{Meaning}}</div>" +
            "<div class='primary-target' hidden>{{" + (if (production) "ProductionSentenceTarget" else "SentenceTarget") + "}}</div>" +
            "<div class='primary-translation' hidden>{{" + (if (production) "ProductionTranslation" else "Translation") + "}}</div>" +
            contextScript + "</main>"
    }

    /** Select once per question, reuse on answer. Text enters the DOM through textContent only. */
    private val contextScript = """
        <script>(function () {
          var root = document.currentScript.parentElement;
          try {
            var primary = {before: root.querySelector('.before').textContent,
              target: root.querySelector('.primary-target').textContent, after: root.querySelector('.after').textContent,
              translation: root.querySelector('.primary-translation').textContent};
            var contexts = [primary].concat(JSON.parse(root.querySelector('.variant-data').textContent));
            var key = 'dict-anki-v2:' + root.querySelector('.note-key').textContent + ':' + root.dataset.direction;
            var signature = JSON.stringify(contexts), saved = null, storage = null;
            // Persist across front/back page loads; session storage is a fallback when local storage is unavailable.
            var candidates = [function () { return window.localStorage; }, function () { return window.sessionStorage; }];
            for (var i = 0; i < candidates.length; i++) {
              try {
                var candidate = candidates[i](), probe = key + ':probe';
                candidate.setItem(probe, '1'); candidate.removeItem(probe);
                storage = candidate; break;
              } catch (ignored) {}
            }
            if (storage) {
              try { saved = JSON.parse(storage.getItem(key)); } catch (ignored) {}
            }
            var previous = saved && saved.signature === signature && saved.index >= 0 && saved.index < contexts.length ? saved.index : null;
            var index = 0;
            if (root.dataset.reveal === 'true') {
              index = previous === null ? 0 : previous;
            } else if (storage) {
              index = previous === null ? Math.floor(Math.random() * contexts.length) :
                (previous + 1 + Math.floor(Math.random() * Math.max(1, contexts.length - 1))) % contexts.length;
              // If saving fails, keep the deterministic first example on both faces.
              try { storage.setItem(key, JSON.stringify({signature: signature, index: index})); }
              catch (ignored) { index = 0; }
            }
            var context = contexts[index], gloss = root.querySelector('.gloss-data').textContent;
            var production = root.dataset.direction === 'production', reveal = root.dataset.reveal === 'true';
            var question = production ? '[' + gloss + ']' : context.target;
            var answer = production ? context.target : gloss;
            root.querySelector('.before').textContent = context.before;
            root.querySelector('.after').textContent = context.after;
            root.querySelector(reveal ? '.solution' : '.cue').textContent = reveal ? answer : question;
            root.querySelector('.measure').textContent = reveal ? question : answer;
            var translation = root.querySelector('.translation');
            if (translation) { translation.textContent = context.translation; translation.hidden = !context.translation; }
          } catch (ignored) { /* Stored fields remain a usable first example without JavaScript/storage. */ }
        })();</script>
    """.trimIndent()

    val templates = listOf(
        AnkiCardTemplate("Recognition", face(false, false), face(false, true)),
        AnkiCardTemplate("Vocabulary production", "{{#EnableProduction}}" + face(true, false) + "{{/EnableProduction}}", face(true, true)),
    )

    val css = """
        .card { background:#fbfcff; color:#172033; font-family:Arial,sans-serif; font-size:24px; line-height:1.65; margin:0; padding:24px 20px; text-align:left; }
        .study { max-width:640px; margin:0 auto; --accent:#2855c5; --tint:#e7eeff; }
        .production { --accent:#14734b; --tint:#e2f3ea; }
        .direction { color:#68748a; font-size:11px; line-height:1.4; font-weight:700; letter-spacing:1px; margin-bottom:12px; }
        .sentence { white-space:pre-wrap; }
        .slot { display:inline-grid; vertical-align:baseline; max-width:100%; padding:0 3px; border-radius:3px; background:var(--tint); color:var(--accent); font-weight:600; border-bottom:2px solid var(--accent); }
        .slot > span { grid-area:1 / 1; overflow-wrap:anywhere; }
        .measure { visibility:hidden; pointer-events:none; user-select:none; }
        .reference { border-top:1px solid #68748a33; padding-top:12px; margin-top:32px; font-size:.7em; line-height:1.5; color:#68748a; }
        .translation, .notes { white-space:pre-wrap; margin-top:12px; }
        .source { font-size:.85em; margin-top:16px; }
        .nightMode.card, .night_mode.card { background:#171c26; color:#e4e9f2; }
        .nightMode .study, .night_mode .study { --accent:#9ab7ff; --tint:#263655; }
        .nightMode .production, .night_mode .production { --accent:#85d8ad; --tint:#203e32; }
        .nightMode .direction, .nightMode .reference, .night_mode .direction, .night_mode .reference { color:#aab5c8; }
        @media (max-width:400px) { .card { padding:18px 14px; font-size:22px; } }
    """.trimIndent()

    fun validationError(draft: CardDraft): String? = try {
        try { StudySentence.spanish(draft.context, draft.word) }
        catch (error: IllegalArgumentException) { throw IllegalArgumentException("Recognition example 1: ${error.message}") }
        require(draft.meaning.isNotBlank()) { "Add a short English meaning." }
        if (draft.includeProduction) {
            try { StudySentence.english(draft.productionPrompt) }
            catch (error: IllegalArgumentException) { throw IllegalArgumentException("English cue: ${error.message}") }
            require(draft.productionAnswer.equals(draft.word, ignoreCase = true)) {
                "Production must use the same Spanish expression as recognition."
            }
            require(draft.productionContext.isNotBlank()) { "Add a Spanish production sentence." }
            try { StudySentence.spanish(draft.productionContext, draft.productionAnswer) }
            catch (error: IllegalArgumentException) { throw IllegalArgumentException("Production example 1: ${error.message}") }
            draft.productionAlternatives.forEachIndexed { index, context ->
                try { StudySentence.spanish(context.sentence, draft.productionAnswer) }
                catch (error: IllegalArgumentException) { throw IllegalArgumentException("Production example ${index + 2}: ${error.message}") }
            }
        }
        draft.recognitionAlternatives.forEachIndexed { index, context ->
            try { StudySentence.spanish(context.sentence, draft.word) }
            catch (error: IllegalArgumentException) { throw IllegalArgumentException("Recognition example ${index + 2}: ${error.message}") }
        }
        null
    } catch (error: IllegalArgumentException) {
        error.message
    }

    fun buildFields(draft: CardDraft, imageSource: String = draft.imageFilename): List<String> {
        validationError(draft)?.let { error(it) }
        val spanish = StudySentence.spanish(draft.context, draft.word)
        val english = runCatching { StudySentence.english(draft.productionPrompt) }.getOrNull()
        val production = if (draft.includeProduction) StudySentence.spanish(draft.productionContext, draft.productionAnswer)
            else runCatching { StudySentence.spanish(draft.productionContext, draft.productionAnswer) }.getOrNull()
        val recognitionContexts = draft.recognitionAlternatives
        val productionContexts = if (production == null) emptyList() else
            draft.productionAlternatives.filter { runCatching { StudySentence.spanish(it.sentence, draft.productionAnswer) }.isSuccess }
        return listOf(
            draft.word, spanish.text, draft.word, draft.meaning, draft.translation, draft.explanation,
            draft.definitions, "Dict·Anki · ${draft.tags}", if (draft.includeProduction) "1" else "",
            english?.text ?: draft.productionPrompt, draft.productionAnswer,
            spanish.before, spanish.target, spanish.after,
            english?.before.orEmpty(), english?.target.orEmpty(), english?.after.orEmpty(),
            production?.text.orEmpty(), production?.before.orEmpty(), production?.target.orEmpty(), production?.after.orEmpty(),
            draft.productionTranslation, StudyContextCodec.encode(recognitionContexts, draft.word),
            StudyContextCodec.encode(productionContexts, draft.productionAnswer), draft.noteKey,
        ).map { it.escapeHtml() }.toMutableList().apply {
            if (draft.imageFilename.isNotBlank()) {
                require(Regex("dictanki_[a-f0-9-]+\\.png").matches(draft.imageFilename)) { "Invalid flashcard image filename." }
                this[5] += "<div><img alt='Vocabulary illustration' style='max-width:100%;max-height:320px' src='${imageSource.escapeHtml()}'></div>"
            }
        }
    }

    fun previewHtml(draft: CardDraft, production: Boolean, reveal: Boolean, night: Boolean = false, imageSource: String = draft.imageFilename): String {
        val values = fields.zip(buildFields(draft, imageSource)).toMap()
        val template = templates[if (production) 1 else 0].let { if (reveal) it.answer else it.question }
        val conditions = Regex("\\{\\{#(\\w+)\\}\\}([\\s\\S]*?)\\{\\{/\\1\\}\\}")
        val conditional = conditions.replace(template) { if (values[it.groupValues[1]].isNullOrEmpty()) "" else it.groupValues[2] }
        val html = Regex("\\{\\{(\\w+)\\}\\}").replace(conditional) { values[it.groupValues[1]].orEmpty() }
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'><style>$css</style></head>" +
            "<body class='card" + (if (night) " nightMode" else "") + "'>$html</body></html>"
    }

    fun encodeFields(fields: List<String>): String =
        fields.joinToString(FIELD_SEPARATOR) { it.replace(FIELD_SEPARATOR, " ") }

    fun hasExpectedSchema(fieldNames: List<String>, type: Int): Boolean = type == 0 && fieldNames == fields

    // Field compatibility and card order matter; users may freely change template names and markup.
    fun hasExpectedTemplates(templates: List<AnkiCardTemplate>): Boolean = templates.size == 2

    private fun String.escapeHtml(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")
            .replace("\r\n", "\n").replace('\r', '\n')
}

internal data class AnkiCardTemplate(val name: String, val question: String, val answer: String)
