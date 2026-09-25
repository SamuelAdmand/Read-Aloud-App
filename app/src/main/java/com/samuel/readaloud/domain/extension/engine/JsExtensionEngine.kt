package com.samuel.readaloud.domain.extension.engine

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.samuel.readaloud.domain.extension.InstalledExtension
import com.samuel.readaloud.model.Article
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

/**
 * Expected JSON payload format returned by an extension's index.js script.
 */
data class JsExtractionPayload(
    @SerializedName("title") val title: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("error") val error: String? = null
)

/**
 * High-performance headless JavaScript execution engine powered by Android WebView.
 *
 * Runs the extension's extraction script inside the browser context, granting
 * full DOM access (document.querySelector, navigation, cookie context, etc.)
 * matching the SpotiFLAC extension architecture.
 *
 * Highly optimized for speed: disables images, media, and fonts to ensure instant
 * article DOM parsing without network bottlenecks.
 */
class JsExtensionEngine(private val context: Context) {

    private val gson = Gson()
    private val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun execute(
        extension: InstalledExtension,
        url: String,
        initialHtml: String? = null
    ): Result<Article> = withContext(Dispatchers.Main) {
        val scriptContent = try {
            extension.scriptFile.readText()
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Failed to read extension script: ${e.message}"))
        }

        try {
            withTimeout(8_000L) {
                suspendCancellableCoroutine { continuation ->
                    val webView = WebView(context)
                    val settings = webView.settings
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = userAgent
                    // Performance optimizations: do not load media/images
                    settings.loadsImagesAutomatically = false
                    settings.blockNetworkImage = true

                    var isResumed = false

                    fun finishWith(result: Result<Article>) {
                        if (!isResumed) {
                            isResumed = true
                            try {
                                webView.stopLoading()
                                webView.destroy()
                            } catch (e: Exception) {
                                // Ignore cleanup exceptions
                            }
                            continuation.resume(result)
                        }
                    }

                    fun evaluateScript(view: WebView) {
                        if (isResumed) return

                        val wrappedScript = """
                            (function() {
                                try {
                                    var result = (function() {
                                        $scriptContent
                                    })();
                                    if (typeof result === 'object') {
                                        return JSON.stringify(result);
                                    }
                                    return result;
                                } catch (err) {
                                    return JSON.stringify({ error: err.toString() });
                                }
                            })();
                        """.trimIndent()

                        view.evaluateJavascript(wrappedScript) { evalResultJson ->
                            if (isResumed) return@evaluateJavascript

                            try {
                                if (evalResultJson.isNullOrBlank() || evalResultJson == "null") {
                                    finishWith(Result.failure(Exception("Extension script returned null or empty output")))
                                    return@evaluateJavascript
                                }

                                val rawPayload = try {
                                    gson.fromJson(evalResultJson, String::class.java) ?: evalResultJson
                                } catch (e: Exception) {
                                    evalResultJson
                                }

                                val payload = gson.fromJson(rawPayload, JsExtractionPayload::class.java)

                                if (payload?.error != null) {
                                    finishWith(Result.failure(Exception("Extension error: ${payload.error}")))
                                    return@evaluateJavascript
                                }

                                val title = payload?.title?.trim()?.takeIf { it.isNotEmpty() } ?: "No Title"
                                val text = payload?.text?.trim() ?: ""

                                if (text.isNotEmpty()) {
                                    finishWith(Result.success(Article(title = title, text = text, sourceUrl = url)))
                                } else {
                                    finishWith(Result.failure(Exception("Extension found no readable article body")))
                                }
                            } catch (e: Exception) {
                                finishWith(Result.failure(e))
                            }
                        }
                    }

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            if (isResumed || view == null) return

                            // If HTML is already in memory, evaluate quickly; otherwise allow brief delay
                            val delayMs = if (!initialHtml.isNullOrBlank()) 60L else 350L
                            view.postDelayed({
                                evaluateScript(view)
                            }, delayMs)
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val path = request?.url?.path?.lowercase() ?: ""
                            // Instantly drop media, image, and font requests to accelerate extraction
                            if (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                                path.endsWith(".gif") || path.endsWith(".webp") || path.endsWith(".svg") ||
                                path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") ||
                                path.endsWith(".mp4") || path.endsWith(".mp3")) {
                                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        @Suppress("DEPRECATION")
                        @Deprecated("Deprecated in Java")
                        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                            if (!isResumed && failingUrl == url) {
                                finishWith(Result.failure(Exception("WebView load error: $description")))
                            }
                        }
                    }

                    if (!initialHtml.isNullOrBlank()) {
                        webView.loadDataWithBaseURL(url, initialHtml, "text/html", "UTF-8", null)
                    } else {
                        webView.loadUrl(url)
                    }

                    continuation.invokeOnCancellation {
                        try {
                            webView.stopLoading()
                            webView.destroy()
                        } catch (e: Exception) {
                            // Ignored
                        }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception("Extraction timed out (8s)"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
