package com.samuel.readaloud.repository

import android.content.Context
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
 * Employs a pure native, ultra-fast extraction pipeline:
 * 1. Fast asynchronous HTML retrieval via OkHttp (~200ms).
 * 2. In-memory heuristic DOM parsing via Mozilla Readability4J + HtmlToMarkdownConverter (~20ms).
 * 3. Client-rendered SPA fallback via headless WebView only if initial HTML was blocked or empty.
 */
class UrlRepository(private val context: Context) {

    private val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    private val nativeExtractor = ReadabilityExtractor()

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun extractArticle(url: String): Result<Article> = withContext(Dispatchers.IO) {
        // Strategy 1: Instant pure-native fetch with OkHttp
        val okHttpHtml = fetchWithOkHttp(url)

        if (!okHttpHtml.isNullOrBlank()) {
            val nativeResult = nativeExtractor.extract(okHttpHtml, url)
            if (nativeResult.isSuccess) {
                return@withContext nativeResult
            }
        }

        // Strategy 2: Deep Headless WebView Fallback (only for client-rendered JavaScript SPAs)
        try {
            val webViewHtmlResult = WebViewExtractor(context).getHtml(url)
            if (webViewHtmlResult.isSuccess) {
                val webViewHtml = webViewHtmlResult.getOrNull()
                if (!webViewHtml.isNullOrBlank()) {
                    val fallbackResult = nativeExtractor.extract(webViewHtml, url)
                    if (fallbackResult.isSuccess) {
                        return@withContext fallbackResult
                    }
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
