package com.tim.d2a.ui

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

/** Stream private images on WebView's resource thread instead of copying base64 into HTML. */
internal class PreviewImageClient(private val filesDir: File) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        // loadDataWithBaseURL may also intercept the generated main document.
        if (request.isForMainFrame) return null
        val uri = request.url
        val filename = uri.lastPathSegment.orEmpty()
        val stream = if (request.method == "GET" && uri.toString() == imageUrl(filename) && filename.matches(imageName)) {
            // filename matches the strict imageName allowlist above.
            // foxguard: ignore[kt/no-path-traversal]
            try { File(filesDir, "images/$filename").inputStream() }
            catch (_: IOException) { null }
        } else null
        // Never fall through to the network for a missing or unrecognized local resource.
        return if (stream != null) WebResourceResponse("image/png", null, stream)
        else WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
    }

    companion object {
        private val imageName = Regex("dictanki_[a-f0-9-]+\\.png")
        fun imageUrl(filename: String): String =
            if (filename.matches(imageName)) "https://dict-anki-preview.invalid/images/$filename" else ""
    }
}
