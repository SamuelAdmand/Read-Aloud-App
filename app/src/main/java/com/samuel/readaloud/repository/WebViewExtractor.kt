package com.samuel.readaloud.repository

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class WebViewExtractor(private val context: Context) {

    /**
     * Loads the URL in a headless WebView, waits for the page to render,
     * and returns the fully rendered HTML (document.documentElement.outerHTML).
     * Times out after 20 seconds.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun getHtml(url: String): Result<String> = withContext(Dispatchers.Main) {
        try {
            // Enforce a 20-second timeout for the entire operation
            kotlinx.coroutines.withTimeout(20_000L) {
                suspendCancellableCoroutine { continuation ->
                    val webView = WebView(context)

                    val settings = webView.settings
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

                    var isResumed = false

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (isResumed) return

                            // Inject a delay to allow Single Page Applications (React/Vue) to render content
                            view?.postDelayed({
                                if (isResumed) return@postDelayed

                                // Extract the full HTML content
                                view.evaluateJavascript("(function() { return document.documentElement.outerHTML; })();") { htmlJson ->
                                    if (isResumed) return@evaluateJavascript
                                    isResumed = true

                                    try {
                                        val rawHtml = if (htmlJson != null && htmlJson != "null") {
                                            Gson().fromJson(htmlJson, String::class.java)
                                        } else {
                                            ""
                                        }
                                        continuation.resume(Result.success(rawHtml))
                                    } catch (e: Exception) {
                                        continuation.resume(Result.failure(e))
                                    } finally {
                                        webView.destroy()
                                    }
                                }
                            }, 2000)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            // If we hit a hard error, don't wait forever.
                            if (!isResumed) {
                                isResumed = true
                                continuation.resume(Result.failure(Exception("WebView Error: $description")))
                                webView.destroy()
                            }
                        }
                    }

                    webView.loadUrl(url)

                    continuation.invokeOnCancellation {
                        webView.destroy()
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception("Extraction timed out (20s)."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}