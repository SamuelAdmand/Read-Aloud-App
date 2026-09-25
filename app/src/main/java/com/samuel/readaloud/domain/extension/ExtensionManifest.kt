package com.samuel.readaloud.domain.extension

import com.google.gson.annotations.SerializedName

/**
 * Details of an individual website rule or paywall bypass supported by an extension.
 */
data class SupportedSiteInfo(
    @SerializedName("name") val name: String,
    @SerializedName("domain") val domain: String,
    @SerializedName("domains") val domains: List<String>? = emptyList(),
    @SerializedName("description") val description: String? = "",
    @SerializedName("improvements") val improvements: List<String>? = emptyList(),
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("lastUpdated") val lastUpdated: String? = ""
) {
    val safeDomains: List<String>
        get() = domains ?: emptyList()

    val safeImprovements: List<String>
        get() = improvements ?: emptyList()

    val safeDescription: String
        get() = description ?: ""

    val safeNotes: String
        get() = notes ?: ""
}

/**
 * Manifest metadata for a ReadAloud dynamic extractor extension.
 *
 * Modeled after the SpotiFLAC extension manifest specification.
 */
data class ExtensionManifest(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("version") val version: String,
    @SerializedName("author") val author: String? = "Community",
    @SerializedName("description") val description: String? = "",
    @SerializedName("domains") val domains: List<String>? = emptyList(),
    @SerializedName("priority") val priority: Int = 0,
    @SerializedName("main") val main: String? = "index.js",
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("sites") val sites: List<SupportedSiteInfo>? = emptyList()
) {
    val safeDomains: List<String>
        get() = domains ?: emptyList()

    val safeSites: List<SupportedSiteInfo>
        get() = sites ?: emptyList()

    val safeMain: String
        get() = main?.ifEmpty { "index.js" } ?: "index.js"
}
