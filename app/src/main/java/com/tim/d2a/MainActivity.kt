package com.tim.d2a

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.tim.d2a.ui.DictAnkiApp
import com.tim.d2a.ui.theme.DictAnkiTheme

data class TextCapture(val text: String, val id: String = java.util.UUID.randomUUID().toString(), val shared: Boolean = false)

class MainActivity : ComponentActivity() {
    private val reviewRequest = mutableStateOf(0L)
    private val capture = mutableStateOf<TextCapture?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            acceptIntent(intent)
        } else {
            savedInstanceState.getString("capture_text")?.let { text ->
                capture.value = TextCapture(text, savedInstanceState.getString("capture_id")!!, savedInstanceState.getBoolean("capture_shared"))
            }
        }
        setContent { DictAnkiTheme { DictAnkiApp(capture = capture.value, reviewRequest = reviewRequest.value, onExitCapture = { finish() }) } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        capture.value?.let {
            outState.putString("capture_text", it.text)
            outState.putString("capture_id", it.id)
            outState.putBoolean("capture_shared", it.shared)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        acceptIntent(intent)
    }
    private fun acceptIntent(intent: Intent?) {
        if (intent?.action == CardGenerationService.REVIEW) {
            reviewRequest.value = System.nanoTime()
            capture.value = null
        }
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            else -> null
        }?.toString()?.trim().orEmpty()
        if (text.isNotBlank()) capture.value = TextCapture(text, shared = intent?.action == Intent.ACTION_SEND)
    }
}
