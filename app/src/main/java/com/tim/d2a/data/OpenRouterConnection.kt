package com.tim.d2a.data

import java.net.HttpURLConnection
import java.net.URL

/** Validate before attaching credentials; redirects must never forward them elsewhere. */
internal fun openRouterConnection(endpoint: String): HttpURLConnection {
    // Construction performs no I/O; the allowlist below runs before opening the connection.
    // foxguard: ignore[kt/no-ssrf]
    val url = URL(endpoint)
    require(url.protocol == "https" && url.host == "openrouter.ai" &&
        (url.port == -1 || url.port == 443) && url.userInfo == null &&
        url.path.startsWith("/api/v1/") && url.ref == null) {
        "OpenRouter requests must use https://openrouter.ai/api/v1/."
    }
    return (url.openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = false
    }
}
