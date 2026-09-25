package com.samuel.readaloud.domain.extractor

import com.samuel.readaloud.model.Article
import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup

/**
 * Native implementation of content extraction using Readability4J and Jsoup.
 */
class ReadabilityExtractor : WebExtractor {

    override suspend fun extract(html: String, url: String): Result<Article> {
        return try {
            val readability4J = Readability4J(url, html)
            val article = readability4J.parse()

            val title = article.title ?: "No Title"
            val htmlContent = article.content ?: ""
            val markdownText = if (htmlContent.isNotBlank()) {
                HtmlToMarkdownConverter.convert(htmlContent)
            } else {
                article.textContent ?: ""
            }

            if (markdownText.isNotBlank()) {
                Result.success(Article(title = title, text = markdownText, sourceUrl = url))
            } else {
                // Fallback to Jsoup if Readability fails to find content
                val doc = Jsoup.parse(html, url)
                val bodyMarkdown = HtmlToMarkdownConverter.convert(doc.body().html())
                val bodyText = if (bodyMarkdown.isNotBlank()) bodyMarkdown else doc.body().text()
                if (bodyText.isNotBlank()) {
                    Result.success(Article(title = title, text = bodyText, sourceUrl = url))
                } else {
                    Result.failure(Exception("Native extraction failed to find content"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
