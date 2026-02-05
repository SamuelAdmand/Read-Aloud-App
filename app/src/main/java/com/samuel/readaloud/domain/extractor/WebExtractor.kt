package com.samuel.readaloud.domain.extractor

import com.samuel.readaloud.model.Article

/**
 * Interface for extracting main content from a web page.
 */
interface WebExtractor {
    suspend fun extract(html: String, url: String): Result<Article>
}
