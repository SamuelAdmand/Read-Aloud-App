package com.samuel.readaloud.repository

import android.content.Context
import com.chaquo.python.Python
import com.samuel.readaloud.model.Article
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
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

    suspend fun extractArticle(url: String): Result<Article> = withContext(Dispatchers.IO) {
        // Strategy 1: Try Fast OkHttp Download
        val okHttpHtml = fetchWithOkHttp(url)

        // Attempt extraction with OkHttp content
        var extractionResult = if (okHttpHtml != null) {
            parseWithPython(okHttpHtml, url)
        } else {
            Result.failure(Exception("OkHttp failed"))
        }

        // Strategy 2: The "Nuclear Option" (WebView Fallback)
        // If OkHttp failed completely, OR if Python couldn't find text (likely JS-rendered content)
        if (extractionResult.isFailure) {
            try {
                // Switch to Main thread for WebView (handled internally by WebViewExtractor), then back here
                val webViewHtmlResult = WebViewExtractor(context).getHtml(url)

                if (webViewHtmlResult.isSuccess) {
                    val webViewHtml = webViewHtmlResult.getOrNull()
                    if (!webViewHtml.isNullOrBlank()) {
                        extractionResult = parseWithPython(webViewHtml, url)
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

    private fun parseWithPython(htmlContent: String, url: String): Result<Article> {
        return try {
            val python = Python.getInstance()
            val module = python.getModule("url_extractor")

            val jsonResult = module.callAttr("extract_from_html", htmlContent, url).toString()
            val jsonObject = JSONObject(jsonResult)

            if (jsonObject.has("error")) {
                Result.failure(Exception(jsonObject.getString("error")))
            } else {
                val title = jsonObject.optString("title", "No Title")
                val text = jsonObject.optString("text", "")

                if (text.isBlank()) {
                    Result.failure(Exception("No text found"))
                } else {
                    Result.success(Article(title = title, text = text, sourceUrl = url))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}