package com.samuel.readaloud.repository

import com.chaquo.python.Python
import com.samuel.readaloud.model.Article
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class UrlRepository {

    suspend fun extractArticle(url: String): Result<Article> = withContext(Dispatchers.IO) {
        try {
            val python = Python.getInstance()
            val module = python.getModule("url_extractor")

            // Call the Python function
            val jsonResult = module.callAttr("extract_from_url", url).toString()

            // Parse the JSON result
            val jsonObject = JSONObject(jsonResult)

            if (jsonObject.has("error")) {
                val errorMsg = jsonObject.getString("error")
                Result.failure(Exception(errorMsg))
            } else {
                // trafilatura returns 'title' and 'text'
                val title = jsonObject.optString("title", "No Title")
                val text = jsonObject.optString("text", "")

                if (text.isBlank()) {
                    Result.failure(Exception("No text found on this page."))
                } else {
                    Result.success(Article(title = title, text = text, sourceUrl = url))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}