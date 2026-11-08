package com.tim.d2a

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tim.d2a.ui.PreviewImageClient
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewImageClientTest {
    @Test fun streamsOnlyValidatedLocalImagesAndHandlesMissingFiles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = File(instrumentation.targetContext.cacheDir, "preview-resource-test")
        val image = File(root, "images/dictanki_abcd.png")
        image.parentFile!!.mkdirs()
        val bytes = byteArrayOf(1, 2, 3, 4)
        image.writeBytes(bytes)
        try {
            instrumentation.runOnMainSync {
                val web = WebView(instrumentation.targetContext)
                try {
                    val client = PreviewImageClient(root)
                    val url = PreviewImageClient.imageUrl(image.name)
                    assertNull(client.shouldInterceptRequest(web, request("data:text/html;charset=utf-8;base64,", mainFrame = true)))
                    assertNull(client.shouldInterceptRequest(web, request("https://dict-anki-preview.invalid/", mainFrame = true)))
                    val response = client.shouldInterceptRequest(web, request(url))!!
                    assertEquals("image/png", response.mimeType)
                    response.data.use { assertArrayEquals(bytes, it.readBytes()) }
                    for (invalid in listOf(
                        PreviewImageClient.imageUrl("dictanki_dead.png"),
                        "https://other.invalid/images/${image.name}",
                        "https://dict-anki-preview.invalid/images/../${image.name}",
                        "$url?unexpected=true",
                    )) {
                        val missing = client.shouldInterceptRequest(web, request(invalid))!!
                        assertEquals(404, missing.statusCode)
                        missing.data.close()
                    }
                    assertEquals("", PreviewImageClient.imageUrl("../secret.png"))
                } finally { web.destroy() }
            }
        } finally { root.deleteRecursively() }
    }

    private fun request(url: String, mainFrame: Boolean = false) = object : WebResourceRequest {
        override fun getUrl() = Uri.parse(url)
        override fun isForMainFrame() = mainFrame
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders() = emptyMap<String, String>()
    }
}
