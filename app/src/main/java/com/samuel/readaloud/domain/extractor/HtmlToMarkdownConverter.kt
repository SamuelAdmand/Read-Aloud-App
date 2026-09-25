package com.samuel.readaloud.domain.extractor

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Converts structured HTML (e.g. from Readability4J or WebViews) into clean, standard Markdown.
 * Preserves hierarchical headings, paragraphs, lists, quotes, inline formatting, and links,
 * while stripping citation references, infoboxes, and edit buttons.
 */
object HtmlToMarkdownConverter {

    fun convert(html: String): String {
        if (html.isBlank()) return ""

        val doc = Jsoup.parse(html)
        // Clean noisy tags and all citation/reference elements, datelines, and promo overlays
        doc.select(
            "script, style, nav, footer, header, noscript, svg, button, form, " +
            "sup.reference, .reference, .mw-ref, .mw-editsection, .reflist, " +
            "ol.references, .citation, .footnotes, .footnote, " +
            "a[href*='cite_note'], a[href*='cite_ref'], a[href*='#ref'], " +
            "a[href*='citenote'], a[href*='citeref'], a[href*='#cite'], " +
            "table.infobox, .infobox, .navbox, .noprint, .portal, .thumbcaption, " +
            ".hatnote, .ambox, .sidebar, " +
            "time, .date, .publish-date, .published-date, .story-date, .article-date, " +
            ".read-time, .reading-time, .time-to-read, " +
            "[itemprop='datePublished'], [itemprop='dateModified'], " +
            ".story-details, .article-info, .publish-info, .dateline, .byline-date, " +
            "figcaption, .caption, .image-caption, .photo-caption, .img-caption, " +
            ".photo-credit, .image-credit, .credit, .media-caption, " +
            ".also-read, .read-more, .read-also, .related-articles, .related-news, " +
            ".related-stories, .see-also, .article-tags, .tags, .tags-list, " +
            ".newsletter, .subscription, .ad, .advertisement, .social-share, " +
            ".share-bar, .author-bio, .comments"
        ).remove()

        val body = doc.body() ?: return ""
        val builder = StringBuilder()
        traverseNode(body, builder)

        return cleanMarkdown(builder.toString())
    }

    private fun traverseNode(node: Node, builder: StringBuilder) {
        when (node) {
            is TextNode -> {
                val text = node.text()
                builder.append(text)
            }
            is Element -> {
                when (node.tagName().lowercase()) {
                    "h1" -> {
                        builder.append("\n\n# ")
                        appendChildren(node, builder)
                        builder.append("\n\n")
                    }
                    "h2" -> {
                        builder.append("\n\n## ")
                        appendChildren(node, builder)
                        builder.append("\n\n")
                    }
                    "h3" -> {
                        builder.append("\n\n### ")
                        appendChildren(node, builder)
                        builder.append("\n\n")
                    }
                    "h4" -> {
                        builder.append("\n\n#### ")
                        appendChildren(node, builder)
                        builder.append("\n\n")
                    }
                    "h5" -> {
                        builder.append("\n\n##### ")
                        appendChildren(node, builder)
                        builder.append("\n\n")
                    }
                    "h6" -> {
                        builder.append("\n\n###### ")
                        appendChildren(node, builder)
                        builder.append("\n\n")
                    }
                    "p" -> {
                        builder.append("\n\n")
                        appendChildren(node, builder)
                        builder.append("\n\n")
                    }
                    "blockquote" -> {
                        builder.append("\n\n> ")
                        val inner = StringBuilder()
                        appendChildren(node, inner)
                        val quoteLines = inner.toString().trim().lines()
                        builder.append(quoteLines.joinToString("\n> "))
                        builder.append("\n\n")
                    }
                    "ul" -> {
                        builder.append("\n\n")
                        for (child in node.children()) {
                            if (child.tagName().lowercase() == "li") {
                                builder.append("- ")
                                appendChildren(child, builder)
                                builder.append("\n")
                            } else {
                                traverseNode(child, builder)
                            }
                        }
                        builder.append("\n")
                    }
                    "ol" -> {
                        builder.append("\n\n")
                        var index = 1
                        for (child in node.children()) {
                            if (child.tagName().lowercase() == "li") {
                                builder.append("$index. ")
                                appendChildren(child, builder)
                                builder.append("\n")
                                index++
                            } else {
                                traverseNode(child, builder)
                            }
                        }
                        builder.append("\n")
                    }
                    "li" -> {
                        appendChildren(node, builder)
                    }
                    "pre" -> {
                        builder.append("\n\n```\n")
                        val code = node.text()
                        builder.append(code)
                        builder.append("\n```\n\n")
                    }
                    "code" -> {
                        if (node.parent()?.tagName()?.lowercase() != "pre") {
                            builder.append("`")
                            appendChildren(node, builder)
                            builder.append("`")
                        } else {
                            appendChildren(node, builder)
                        }
                    }
                    "b", "strong" -> {
                        val inner = StringBuilder()
                        appendChildren(node, inner)
                        val text = inner.toString()
                        if (text.isNotBlank()) {
                            builder.append("**").append(text.trim()).append("** ")
                        }
                    }
                    "i", "em" -> {
                        val inner = StringBuilder()
                        appendChildren(node, inner)
                        val text = inner.toString()
                        if (text.isNotBlank()) {
                            builder.append("*").append(text.trim()).append("* ")
                        }
                    }
                    "a" -> {
                        val href = node.attr("href").trim()
                        val linkText = StringBuilder()
                        appendChildren(node, linkText)
                        var title = linkText.toString().trim()

                        // Strip surrounding citation brackets if title has them (e.g. "[3]")
                        if (title.startsWith("[") && title.endsWith("]") && title.length > 2) {
                            title = title.substring(1, title.length - 1).trim()
                        }

                        // Ignore citation markers like [1], [2], [edit]
                        if (title.matches(Regex("^(?:\\[?\\d+\\]?|edit|citation needed|note\\s*\\d+)$", RegexOption.IGNORE_CASE))) {
                            return
                        }

                        // Ignore internal reference anchor links or javascript voids
                        if (href.startsWith("#cite") || href.startsWith("#ref") || href.startsWith("#note") || href.startsWith("javascript:")) {
                            if (title.isNotEmpty() && !title.matches(Regex("^\\d+$"))) {
                                builder.append(title).append(" ")
                            }
                            return
                        }

                        if (title.isNotEmpty()) {
                            if (href.isNotEmpty()) {
                                builder.append("[$title]($href) ")
                            } else {
                                builder.append(title).append(" ")
                            }
                        }
                    }
                    "hr" -> {
                        builder.append("\n\n---\n\n")
                    }
                    "br" -> {
                        builder.append("\n")
                    }
                    "div", "section", "article" -> {
                        appendChildren(node, builder)
                    }
                    else -> {
                        appendChildren(node, builder)
                    }
                }
            }
        }
    }

    private fun appendChildren(element: Element, builder: StringBuilder) {
        for (child in element.childNodes()) {
            traverseNode(child, builder)
        }
    }

    private fun cleanMarkdown(text: String): String {
        val lines = text.lines()
        val cleanedLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                cleanedLines.add("")
                continue
            }

            // 1. Filter out read-time and dateline/timestamp preamble lines
            // e.g.: "3 min read Hyderabad , New Delhi , Patna Sep 25, 2026 07:42 PM IST First published on: Sep 25, 2026 at 04:54 PM IST"
            if (isMetadataOrDatelineLine(trimmed)) {
                continue
            }

            // 2. Filter out promo lines (e.g. "Also read: ...", "Read more: ...")
            if (isPromoLine(trimmed)) {
                continue
            }

            // 3. Filter out photo credit lines (e.g. "(Photo: PTI)", "Image credit: Reuters")
            if (isPhotoCreditLine(trimmed)) {
                continue
            }

            // 4. Clean inline citation artifacts while preserving real markdown links
            val cleanedLine = line
                .replace(Regex("\\[\\[?\\s*\\d+\\s*\\]?\\]\\(#[^)]*\\)"), "") // [[3]](#citenote...)
                .replace(Regex("\\[\\s*\\d+\\s*\\]"), "")                     // [1]
                .replace(Regex("\\[\\s*edit\\s*\\]", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\[\\s*citation needed\\s*\\]", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\[\\s*note\\s*\\d+\\s*\\]", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\[\\s*\\]\\([^)]*\\)"), "")                 // Empty links [](url)

            if (cleanedLine.isNotBlank()) {
                cleanedLines.add(cleanedLine.trimEnd())
            }
        }

        return cleanedLines.joinToString("\n")
            .replace(Regex("[ \\t]+"), " ")            // Collapse multiple horizontal spaces
            .replace(Regex("\\n{3,}"), "\n\n")         // Collapse 3+ newlines to double newlines
            .trim()
    }

    private fun isMetadataOrDatelineLine(line: String): Boolean {
        // e.g. "3 min read Hyderabad, New Delhi, Patna Sep 25, 2026 07:42 PM IST..."
        if (line.matches(Regex("^(?:\\d+\\s*min(?:ute)?s?\\s*read\\b|read\\s*time\\b).*", RegexOption.IGNORE_CASE))) {
            return true
        }
        // e.g. "First published on: Sep 25, 2026 at 04:54 PM IST" or "Updated on: ..."
        if (line.matches(Regex("^(?:First\\s+)?(?:published|updated|last\\s+modified)(?:\\s*on)?\\s*:.*", RegexOption.IGNORE_CASE))) {
            return true
        }
        // Standalone date with timestamp line (e.g. "Sep 25, 2026 07:42 PM IST")
        if (line.matches(Regex("^(?:[A-Z][a-z]{2,8}\\s+\\d{1,2},\\s+\\d{4}\\s+\\d{1,2}:\\d{2}\\s*(?:AM|PM)?(?:\\s*[A-Z]{2,4})?).*"))) {
            return true
        }
        return false
    }

    private fun isPromoLine(line: String): Boolean {
        return line.matches(
            Regex(
                "^(?:also\\s+read|read\\s+also|read\\s+more|must\\s+read|related\\s+story|related\\s+stories|related\\s+articles?|click\\s+here|follow\\s+us|subscribe\\s+to)\\s*:.*",
                RegexOption.IGNORE_CASE
            )
        )
    }

    private fun isPhotoCreditLine(line: String): Boolean {
        return line.matches(
            Regex(
                "^\\(?(?:photo|image|credit|file\\s+photo|representative\\s+image)\\s*:.*\\)?$",
                RegexOption.IGNORE_CASE
            )
        ) || line.matches(
            Regex(
                "^\\(?(?:PTI|Reuters|AP|AFP|ANI)\\s+Photo\\)?$",
                RegexOption.IGNORE_CASE
            )
        )
    }
}
