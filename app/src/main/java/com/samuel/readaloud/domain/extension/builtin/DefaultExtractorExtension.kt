package com.samuel.readaloud.domain.extension.builtin

import com.samuel.readaloud.domain.extension.ExtensionManifest
import com.samuel.readaloud.domain.extension.SupportedSiteInfo

/**
 * Built-in universal fallback extractor.
 *
 * Ensures that ReadAloud functions out of the box for any website without
 * requiring the user to install third-party extensions first.
 */
object DefaultExtractorExtension {

    const val ID: String = "builtin.universal.readability"

    val manifest = ExtensionManifest(
        id = ID,
        name = "Built-in Universal Extractor",
        version = "1.0.0",
        author = "ReadAloud Core",
        description = "Universal fallback extractor with DOM cleanup for standard web articles.",
        domains = listOf("*"),
        priority = -100, // Lowest priority so any installed specialized extension overrides it
        main = "index.js",
        sites = listOf(
            SupportedSiteInfo(
                name = "Universal Web Reader",
                domain = "*",
                domains = listOf("*"),
                description = "Standard heuristic extraction for web articles",
                improvements = listOf(
                    "Heuristic article container scoring using Mozilla Readability principles",
                    "Automatic removal of headers, footers, navigation bars, and aside columns",
                    "Filters out cookie consent banners and paywall curtain overlays",
                    "Preserves clean headline hierarchy and paragraph flow"
                ),
                notes = "Core engine: Fallback DOM parser active when specialized rules are not required.",
                lastUpdated = "September 2026"
            )
        )
    )

    val script: String = """
        (function() {
            try {
                // 1. Extract Title
                var titleEl = document.querySelector('h1') ||
                              document.querySelector('meta[property="og:title"]') ||
                              document.querySelector('meta[name="twitter:title"]');
                var title = titleEl ? (titleEl.innerText || titleEl.getAttribute('content') || document.title) : document.title;
                title = (title || "").trim();

                // 2. Select main content container
                var contentContainer = document.querySelector('article') ||
                                       document.querySelector('[role="main"]') ||
                                       document.querySelector('.post-content') ||
                                       document.querySelector('.article-body') ||
                                       document.querySelector('.story-content') ||
                                       document.querySelector('.entry-content') ||
                                       document.querySelector('main') ||
                                       document.body;

                if (!contentContainer) {
                    return JSON.stringify({ title: title, text: "" });
                }

                // 3. Clone node to avoid altering live view
                var clone = contentContainer.cloneNode(true);

                // 4. Remove clutter, navigation, ads, and paywall overlays
                var removeSelectors = [
                    'script', 'style', 'nav', 'header', 'footer', 'aside',
                    'noscript', 'iframe', 'svg', '.advertisement', '.ad',
                    '.social-share', '.comments', '.cookie-banner',
                    '.paywall-overlay', '.subscription-prompt', '#sidebar'
                ];

                removeSelectors.forEach(function(sel) {
                    var items = clone.querySelectorAll(sel);
                    for (var i = 0; i < items.length; i++) {
                        items[i].remove();
                    }
                });

                // 5. Extract clean text
                var extractedText = (clone.innerText || clone.textContent || "").trim();

                return JSON.stringify({
                    title: title,
                    text: extractedText
                });
            } catch (err) {
                return JSON.stringify({
                    title: document.title || "",
                    text: document.body ? (document.body.innerText || "") : ""
                });
            }
        })();
    """.trimIndent()
}
