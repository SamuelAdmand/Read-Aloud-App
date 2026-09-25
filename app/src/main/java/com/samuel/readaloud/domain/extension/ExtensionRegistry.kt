package com.samuel.readaloud.domain.extension

import com.google.gson.annotations.SerializedName

/**
 * Representation of an extension entry in a remote repository (registry.json).
 */
data class RegistryExtensionItem(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("version") val version: String,
    @SerializedName("author") val author: String = "Community",
    @SerializedName("description") val description: String = "",
    @SerializedName("domains") val domains: List<String> = emptyList(),
    @SerializedName("priority") val priority: Int = 0,
    @SerializedName("downloadUrl") val downloadUrl: String,
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("sites") val sites: List<SupportedSiteInfo>? = emptyList()
) {
    val safeDomains: List<String>
        get() = domains ?: emptyList()

    val safeSites: List<SupportedSiteInfo>
        get() = sites ?: emptyList()

    fun getEffectiveSites(): List<SupportedSiteInfo> {
        if (safeSites.isNotEmpty()) return safeSites
        return safeDomains.map { domain ->
            val cleanName = if (domain == "*") "Universal (All Websites)" else domain.removePrefix("*.")
            SupportedSiteInfo(
                name = cleanName,
                domain = domain,
                domains = listOf(domain),
                description = "Custom rules for $cleanName"
            )
        }
    }
}

/**
 * Registry repository containing available extensions.
 */
data class ExtensionRegistry(
    @SerializedName("name") val name: String = "Community Extension Repository",
    @SerializedName("repositoryUrl") val repositoryUrl: String = "",
    @SerializedName("extensions") val extensions: List<RegistryExtensionItem> = emptyList()
)
