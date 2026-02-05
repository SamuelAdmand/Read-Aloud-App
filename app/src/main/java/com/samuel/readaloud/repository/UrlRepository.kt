package com.samuel.readaloud.repository

import android.content.Context
import com.samuel.readaloud.domain.extractor.ReadabilityExtractor
import com.samuel.readaloud.model.Article
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class UrlRepository(private val context: Context) {

    // Mimic a real Chrome browser on Android
    private val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    // Extractor
    private val nativeExtractor = ReadabilityExtractor()

    suspend fun extractArticle(url: String): Result<Article> = withContext(Dispatchers.IO) {
        // Strategy 1: Try Fast OkHttp Download
        val okHttpHtml = fetchWithOkHttp(url)

        // Attempt extraction with OkHttp content
        var extractionResult = if (okHttpHtml != null) {
            nativeExtractor.extract(okHttpHtml, url)
        } else {
            Result.failure(Exception("OkHttp failed"))
        }

        // Strategy 2: The "Nuclear Option" (WebView Fallback)
        if (extractionResult.isFailure) {
            try {
                val webViewHtmlResult = WebViewExtractor(context).getHtml(url)
                if (webViewHtmlResult.isSuccess) {
                    val webViewHtml = webViewHtmlResult.getOrNull()
                    if (!webViewHtml.isNullOrBlank()) {
                        extractionResult = nativeExtractor.extract(webViewHtml, url)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        extractionResult
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
