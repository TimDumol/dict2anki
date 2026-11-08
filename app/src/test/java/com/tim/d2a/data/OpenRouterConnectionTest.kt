package com.tim.d2a.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class OpenRouterConnectionTest {
    @Test fun credentialsCannotFollowRedirects() {
        val connection = openRouterConnection("https://openrouter.ai/api/v1/chat/completions")
        try { assertFalse(connection.instanceFollowRedirects) }
        finally { connection.disconnect() }
    }

    @Test fun rejectsUntrustedCredentialDestinationsBeforeConnecting() {
        listOf(
            "http://openrouter.ai/api/v1/models",
            "https://evil.example/api/v1/models",
            "https://openrouter.ai.evil.example/api/v1/models",
            "https://openrouter.ai@evil.example/api/v1/models",
            "https://user@openrouter.ai/api/v1/models",
            "https://openrouter.ai:8443/api/v1/models",
            "https://127.0.0.1/api/v1/models",
            "https://openrouter.ai/other/models",
            "https://openrouter.ai/api/v1/models#fragment",
        ).forEach { endpoint ->
            assertThrows(endpoint, IllegalArgumentException::class.java) { openRouterConnection(endpoint) }
        }
    }
}
