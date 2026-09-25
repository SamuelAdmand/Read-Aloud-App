package com.samuel.readaloud.repository

import android.content.Context
import com.samuel.readaloud.domain.extension.ExtensionManager
import com.samuel.readaloud.domain.extractor.ReadabilityExtractor
import com.samuel.readaloud.model.Article
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Repository responsible for fetching and extracting web articles.
 *
 * Prioritizes dynamically loaded website extensions (SpotiFLAC style),
 * falling back to headless WebView rendering and native Readability4J heuristics.
 */
class UrlRepository(private val context: Context) {

    private val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    private val extensionManager = ExtensionManager.getInstance(context)
    private val nativeExtractor = ReadabilityExtractor()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun extractArticle(url: String): Result<Article> = withContext(Dispatchers.IO) {
        // Strategy 1: Attempt fast HTML fetch with OkHttp
        val okHttpHtml = fetchWithOkHttp(url)

        // Strategy 2: If HTML is available, run matched dynamic extension or fast native Readability
        if (!okHttpHtml.isNullOrBlank()) {
            val extensionResult = extensionManager.extractArticle(url, htmlFallback = okHttpHtml)
            if (extensionResult.isSuccess) {
                return@withContext extensionResult
            }

            // Strategy 3: Fast native Readability heuristic
            val nativeResult = nativeExtractor.extract(okHttpHtml, url)
            if (nativeResult.isSuccess) {
                return@withContext nativeResult
            }
        }

        // Strategy 4: Direct headless extension run (if OkHttp was blocked or site requires full browser cookies)
        val directExtResult = extensionManager.extractArticle(url, htmlFallback = null)
        if (directExtResult.isSuccess) {
            return@withContext directExtResult
        }

        // Strategy 5: Deep Headless WebView Fallback (for complex client-rendered SPAs)
        try {
            val webViewHtmlResult = WebViewExtractor(context).getHtml(url)
            if (webViewHtmlResult.isSuccess) {
                val webViewHtml = webViewHtmlResult.getOrNull()
                if (!webViewHtml.isNullOrBlank()) {
                    val fallbackExt = extensionManager.extractArticle(url, htmlFallback = webViewHtml)
                    if (fallbackExt.isSuccess) {
                        return@withContext fallbackExt
                    }
                    return@withContext nativeExtractor.extract(webViewHtml, url)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Result.failure(Exception("Could not extract readable article text from this URL"))
    }

    private fun fetchWithOkHttp(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .header("Connection", "keep-alive")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
