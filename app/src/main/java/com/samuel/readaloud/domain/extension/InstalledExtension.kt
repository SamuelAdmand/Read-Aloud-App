package com.samuel.readaloud.domain.extension

import java.io.File
import java.net.URI

/**
 * Represents a locally installed extractor extension.
 */
data class InstalledExtension(
    val manifest: ExtensionManifest,
    val installDir: File,
    val scriptFile: File,
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val disabledSites: Set<String> = emptySet()
) {
    /**
     * Returns the list of supported websites either from explicit manifest.sites
     * or computed from manifest.domains.
     */
    fun getEffectiveSites(): List<SupportedSiteInfo> {
        val declaredSites = manifest.safeSites
        if (declaredSites.isNotEmpty()) {
            return declaredSites
        }
        return manifest.safeDomains.map { domain ->
            val cleanName = if (domain == "*") "Universal (All Websites)" else domain.removePrefix("*.")
            SupportedSiteInfo(
                name = cleanName,
                domain = domain,
                domains = listOf(domain),
                description = "Custom rules for $cleanName"
            )
        }
    }

    /**
     * Checks if this extension matches the given article URL based on domain patterns
     * and ensures the specific site rule is not disabled.
     */
    fun matchesUrl(url: String): Boolean {
        if (!isEnabled) return false
        val cleanUrl = url.lowercase().trim()
        val host = try {
            val uri = URI(if (!cleanUrl.startsWith("http")) "https://$cleanUrl" else cleanUrl)
            uri.host ?: cleanUrl
        } catch (e: Exception) {
            cleanUrl
        }

        // Check if any matching site pattern is explicitly disabled
        val matchingSite = getEffectiveSites().find { site ->
            val siteDomains = site.domains ?: emptyList()
            val allPatterns = (listOf(site.domain) + siteDomains).distinct()
            allPatterns.any { pattern ->
                matchesPattern(host, pattern.lowercase().trim())
            }
        }

        if (matchingSite != null && disabledSites.contains(matchingSite.domain)) {
            return false
        }

        return manifest.safeDomains.any { domainPattern ->
            matchesPattern(host, domainPattern.lowercase().trim())
        }
    }

    private fun matchesPattern(host: String, pattern: String): Boolean {
        return when {
            pattern == "*" -> true
            pattern.startsWith("*.") -> {
                val root = pattern.removePrefix("*.")
                host == root || host.endsWith(".$root")
            }
            else -> host == pattern || host.endsWith(".$pattern")
        }
    }
}
