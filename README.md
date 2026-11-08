# Dict·Anki

Native Android prototype for the “I just heard this” workflow:

1. Choose Spanish → English or English → Spanish lookup and enter a word or phrase.
2. Optionally paste the surrounding sentence to disambiguate it.
3. Optionally look it up in a local MDX dictionary.
4. Generate an editable study-card draft, generated through OpenRouter using your own API key.
5. Review the card and add a custom Recognition + Vocabulary Production note type to AnkiDroid.

The interaction is intentionally manual rather than reader-driven. It borrows Readest’s useful study-card shape: dictionary-backed content, an AI-assisted single-pass draft, explicit card choices, and a review/edit step before the note is written.

## Run

Open the repository in Android Studio and run the `app` configuration on an Android 8+ device or emulator. The checked-in workspace does not include a Gradle wrapper, so Android Studio’s bundled Gradle tooling is the expected runner for this prototype.

For a command-line build and install over ADB, run:

```sh
./scripts/build-install.sh
```

The script uses `~/Android/Sdk` by default, or `ANDROID_SDK_ROOT` if set. If Gradle is not already available, it downloads Gradle 8.9 into the ignored `.gradle/gradle-dist` directory. With more than one ADB device connected, set `ANDROID_SERIAL` to the target device serial.

Run the Compose end-to-end dictionary import and Settings tests on an emulator with:

```sh
./scripts/e2e-emulator.sh
```

The runner uses the first connected emulator, or starts the first available AVD. If no AVD exists, it creates `dict2anki-e2e` from the Android 35 Google APIs x86_64 system image. Set `ANDROID_AVD_NAME` to choose a profile or `ANDROID_SERIAL` to select an already-running emulator.

Instrumentation tests use a separate `e2e` build (`com.tim.d2a.e2e`, labeled **Dict·Anki Tests**). Test installation, data resets, and cleanup leave the regular `com.tim.d2a` app and its saved data alone. To run these tests on a connected phone, use `ANDROID_SERIAL=<phone-serial> .gradle/gradle-dist/gradle-8.9/bin/gradle :app:connectedE2eAndroidTest`, or select the `e2e` build variant in Android Studio. The former `connectedDebugAndroidTest` task is replaced by `connectedE2eAndroidTest`; `build-install.sh` still installs the regular debug app.

Lookup uses a small demo dictionary until you import your own. Connect AI in Settings when you want to generate cards. Settings lets you add multiple `.mdx` files through the Storage Access Framework. Imported dictionaries are searched together and their definitions are included as context when drafting a card. The current MDX boundary parses common MDX 1.2/2.0 raw and zlib key/record blocks and honors the encoding declared in each file. Import copies the source and decoded records to temporary files, bounds individual decompression blocks, and streams entries into SQLite to avoid holding the whole dictionary in memory. Source files up to 512 MB are accepted. Parsed entries are stored in a local SQLite index, so later launches use the searchable copy instead of reopening and decoding the original dictionary. Encrypted or LZO-compressed bundles report an import error until an additional native codec phase supports them.

Both lookup directions generate Spanish → English recognition and English-cued Spanish production cards. English lookups use headwords from your imported dictionaries (import an English–Spanish MDX for bilingual dictionary results). Generation also works without a dictionary match. AI creates five short Spanish contexts for recognition and five different contexts for production, using the same lexical expression and sense throughout. Verb examples may use different conjugations; each marked occurrence supplies that example’s Spanish answer. Supplied context is preserved as the first recognition example. Each example has its own translation. Review uses the actual Anki HTML/CSS with direction selection, a flip control, and a next-example control. All generated examples can be edited; the saved Anki fields can also be inspected. AI generation chooses the Spanish equivalent and translates any English context into Spanish for the recognition front. AI must be connected for card generation; local lookup and draft review work offline. Production is enabled by default and can be turned off.

Backing out of Review preserves an unfinished draft. Use **Continue draft** on Lookup to reopen it, and return to AnkiDroid to manage completed cards. The production switch and Add button stay together below the scrolling form; save results and validation errors remain visible there. Successful saves disable Add for unchanged content and remove the completed card from draft recovery. Editing a saved card creates a new note, with a separate context-selection key. Failed saves can be retried. Draft recovery and successful-save tracking persist across app restarts.

## OpenRouter

Add an OpenRouter key in Settings and validate it with OpenRouter before use. The searchable model picker lists models that support structured output and shows input/output cost per million tokens plus a relative speed tier based on OpenRouter’s recent throughput ordering. Card generation requests a strict JSON Schema response with required fields and no extra properties, including four alternative sentence/translation pairs for each direction. The key is stored with AndroidX Security’s encrypted preferences when available, and the app calls the OpenAI-compatible `/chat/completions` endpoint directly from the device. The app requires a validated key and a selected model before generation is available. Saved keys are validated again on launch. If generation fails, the app shows an error and lets you retry; it never creates a dictionary-only draft.

## Flashcard images

Lookup → **Generate images automatically** is on by default and remembers your choice. After you choose an image model in Review → **Memory image**, new cards generate an illustration using that model and the same OpenRouter key (a separate paid request). If no model is selected or image generation fails, the text draft remains available. Review → **Memory image** also lets you add an illustration manually. Choose an image model once, then tap **Generate image** on subsequent drafts. The app remembers the image model and sort order across launches. **Customize** accepts optional visual direction; the initial card-generation call also designs a concrete visual scene for the chosen sense, with one distinctive memory hook. Image generation combines this brief with the current Spanish expression, meaning, and context to request a striking illustration without text that works across the rotating examples. Older drafts use the same visual guidance without a saved scene brief. Regenerate keeps the previous image until a replacement succeeds; Remove excludes it from the note.

The searchable picker discovers models through OpenRouter’s `/images/models` API and loads provider pricing. It displays per-image, per-megapixel, and token-based rates in USD. **Lowest cost** compares one image at 1 MP or 300 input plus 1,500 output tokens, using the lowest listed tier; this is a comparison estimate, not a final quote. Unknown prices sort last. The chosen provider is pinned when OpenRouter supplies a provider tag. Generation uses `/images`; reported usage cost is shown after success when available. Every Generate or Regenerate is a separate paid request.

Images persist with drafts as local PNG files. Adding a note imports the image into AnkiDroid media and includes it in the existing Explanation field, keeping the v2 note schema compatible. The image appears on the answer side in both directions, including the app preview. Anki sync can then transfer the media along with the note. Custom templates must render Explanation to show the image. SVG-only outputs are unsupported; choose a raster image model.

## AnkiDroid

Install AnkiDroid and grant the database permission from Settings. The app uses the documented `content://com.ichi2.anki.flashcards` provider directly, creates or reuses the regular note type `Dict·Anki Language v2`, inserts the note, and moves every generated card into your selected deck. Choose a destination in Settings or on the review screen; the app remembers the deck across launches. Refresh the deck list after making changes in AnkiDroid. If the selected deck disappears, choose another before adding cards.

A follow-up can add model version migration, duplicate checks, and richer MDX block decompression without changing the UI flow.

Recognition shows a highlighted Spanish expression within a Spanish sentence; flipping replaces that expression with its English gloss in the same position. Production uses a different Spanish sentence, replacing the expression with a brief bracketed English gloss on the front and restoring the Spanish answer on the back. The inline slot reserves the space needed for both faces to avoid moving the surrounding words. A small EN/ES cue identifies the requested answer language. Translation, explanation, dictionary notes, and source stay under one ＋ control on the back.

Each direction draws from its own five saved contexts. Template JavaScript chooses a context on the question, avoids immediately repeating the previous context, and stores that selection under a unique note key and direction so the answer uses the same example and translation. Reviews are offline: no AI request is made when drawing a card. The first example remains usable without JavaScript or browser storage. Randomization is a presentation feature; each direction still has one Anki card and one scheduling history for all its examples.

The v2 schema stores content separately from presentation: full sentences, selected occurrence segments, expression, gloss, translations, explanation, dictionary notes, English production cue, and context collections. Editors use `[[double brackets]]` to identify one occurrence; saved sentences contain no highlighting markup. This makes later layouts possible without regenerating the data. Disabled production uses an empty `EnableProduction` field so Anki skips its card.

New note types receive the app templates once. Adding another note preserves manual changes to template names, HTML, and CSS. Settings → AnkiDroid → **Update card layout** explicitly applies the current app templates to all v2 notes, replacing manual template edits while retaining their content and review progress. Layout changes keep the same schema and note type. Schema changes require a migration: AnkiDroid’s provider cannot change field names on an existing note type. Existing v1 notes retain their fields, templates, and scheduling; this release starts new notes in v2 rather than rewriting them.

## Capture and recovery

Android **Share → Dict·Anki** puts shared text in context, including short sentences without final punctuation. Highlight the target expression in the context field and tap **Use selected phrase**, then generate the card. Selected text **Process Text → Dict·Anki** generates immediately for short selections; longer text opens as context for phrase selection. Repeated shares update the open activity.

Local lookup and draft review work without an AI connection. Drafts, archived drafts, and successful-save tracking survive rotation and app restarts. Generating another card archives the previous unfinished draft; **Other drafts** reopens it. Completed cards are removed from draft recovery and the archive, including older saved cards on startup. Editing the API key preserves drafts. Generation runs in a user-started `dataSync` foreground service independently of the screen, so switching apps, locking the device, and activity recreation do not cancel it. A quiet ongoing notification offers Cancel; a separate completion notification opens Review. Android 13+ asks for notification permission on the first generation; denying it still permits generation, but hides notification drawer alerts. Text drafts are committed before optional image generation and the final draft is committed before the completion alert. Requests time out after ten minutes and are never automatically replayed after process death or force-stop, avoiding duplicate paid API calls. Reopen the app and check drafts before retrying an interrupted request.

This follows Android’s [foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) and [notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission) guidance.

Set each imported dictionary to Spanish → English, English → Spanish, or both in Settings. Exact lookup preserves accents; accent-folded matches are offered as related headwords when exact and prefix results are absent. Lookup also normalizes whitespace and surrounding sentence punctuation. For Spanish inflections without a direct match, lookup offers dictionary entries for common irregular forms and plausible regular infinitives as related headwords. These are suggestions, not automatic corrections. Inflected words can also be generated directly from their context.

Anki saves first write a pending receipt, then retain the provider's note ID before moving or validating cards. An interrupted insertion with an unknown result asks you to check AnkiDroid before creating another note. Retrying a partial save resumes that note rather than inserting another. Editing a draft starts a new save identity. Model removal requires choosing a replacement explicitly; the app does not silently select another paid model.

## Responsiveness

Startup reads encrypted settings and recovers draft history on the I/O dispatcher. Dictionary imports, permissions, indexing, lookups, and Anki provider operations run in the background; preview HTML is assembled on the Default dispatcher. Settings and draft commits are serialized off the main thread. Lookup requests cancel previous requests and discard results if their inputs have changed. Anki availability is refreshed on resume or explicit refresh rather than during recomposition.

`ResponsivenessTest` checks that startup recovery and settings reads/writes stay off the main thread. Run it with `:app:connectedE2eAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tim.d2a.ResponsivenessTest` on a connected device.
