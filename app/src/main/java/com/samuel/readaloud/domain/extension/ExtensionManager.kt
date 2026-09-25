package com.samuel.readaloud.domain.extension

import android.content.Context
import com.google.gson.Gson
import com.samuel.readaloud.domain.extension.builtin.DefaultExtractorExtension
import com.samuel.readaloud.domain.extension.engine.JsExtensionEngine
import com.samuel.readaloud.model.Article
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Core manager for ReadAloud dynamic website extraction extensions.
 *
 * Implements full lifecycle management:
 * - Domain routing and extraction execution.
 * - Dynamic installation from local ZIP / .readaloud-ext files.
 * - Remote installation and catalog fetching from GitHub registry.json repositories.
 * - Built-in fallback extractor initialization.
 */
class ExtensionManager private constructor(private val context: Context) {

    private val gson = Gson()
    private val jsEngine = JsExtensionEngine(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs = context.getSharedPreferences("readaloud_extensions_prefs", Context.MODE_PRIVATE)

    private val extensionsDir = File(context.filesDir, "extensions").apply {
        if (!exists()) mkdirs()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val _installedExtensions = MutableStateFlow<List<InstalledExtension>>(emptyList())
    val installedExtensions: StateFlow<List<InstalledExtension>> = _installedExtensions.asStateFlow()

    companion object {
        const val DEFAULT_REPO_URL = "https://raw.githubusercontent.com/SamuelAdmand/ReadAloud-Extensions/main/registry.json"

        @Volatile
        private var instance: ExtensionManager? = null

        fun getInstance(context: Context): ExtensionManager {
            return instance ?: synchronized(this) {
                instance ?: ExtensionManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    init {
        scope.launch {
            ensureBuiltInExtension()
            loadInstalledExtensions()
        }
    }

    /**
     * Initializes the built-in fallback universal extractor in storage if absent.
     */
    private fun ensureBuiltInExtension() {
        val builtInDir = File(extensionsDir, DefaultExtractorExtension.ID)
        if (!builtInDir.exists()) {
            builtInDir.mkdirs()
        }

        val manifestFile = File(builtInDir, "manifest.json")
        if (!manifestFile.exists()) {
            manifestFile.writeText(gson.toJson(DefaultExtractorExtension.manifest))
        }

        val scriptFile = File(builtInDir, "index.js")
        if (!scriptFile.exists()) {
            scriptFile.writeText(DefaultExtractorExtension.script)
        }
    }

    /**
     * Scans the extensions directory and updates the reactive StateFlow.
     */
    suspend fun loadInstalledExtensions() = withContext(Dispatchers.IO) {
        val list = mutableListOf<InstalledExtension>()
        val folders = extensionsDir.listFiles { f -> f.isDirectory } ?: emptyArray()

        for (folder in folders) {
            val manifestFile = File(folder, "manifest.json")
            if (!manifestFile.exists()) continue

            try {
                val manifest = gson.fromJson(manifestFile.readText(), ExtensionManifest::class.java)
                val scriptFile = File(folder, manifest.safeMain)
                if (scriptFile.exists()) {
                    val isBuiltIn = manifest.id == DefaultExtractorExtension.ID
                    val isEnabled = prefs.getBoolean("ext_enabled_${manifest.id}", true)
                    val disabledSites = prefs.getStringSet("ext_disabled_sites_${manifest.id}", emptySet()) ?: emptySet()

                    list.add(
                        InstalledExtension(
                            manifest = manifest,
                            installDir = folder,
                            scriptFile = scriptFile,
                            isEnabled = isEnabled,
                            isBuiltIn = isBuiltIn,
                            disabledSites = disabledSites
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Sort by priority descending
        list.sortByDescending { it.manifest.priority }
        _installedExtensions.value = list
    }

    /**
     * Finds the best active extension matching the given URL.
     */
    fun findMatchingExtension(url: String): InstalledExtension? {
        val active = _installedExtensions.value.filter { it.matchesUrl(url) }
        return active.maxByOrNull { it.manifest.priority }
    }

    /**
     * Executes extraction using the best matched extension with fallback.
     */
    suspend fun extractArticle(url: String, htmlFallback: String? = null): Result<Article> = withContext(Dispatchers.IO) {
        val candidate = findMatchingExtension(url)
        val builtIn = _installedExtensions.value.find { it.isBuiltIn }

        if (candidate != null && !candidate.isBuiltIn) {
            val customResult = jsEngine.execute(candidate, url, htmlFallback)
            if (customResult.isSuccess) {
                return@withContext customResult
            }
        }

        // Fallback to built-in universal extractor
        if (builtIn != null && builtIn.isEnabled) {
            return@withContext jsEngine.execute(builtIn, url, htmlFallback)
        }

        Result.failure(Exception("No active extractor could parse this URL"))
    }

    /**
     * Installs or updates an extension from a ZIP InputStream (.readaloud-ext or .zip).
     */
    suspend fun installFromZip(inputStream: InputStream): Result<InstalledExtension> = withContext(Dispatchers.IO) {
        val tempExtractDir = File(context.cacheDir, "ext_install_temp_${System.currentTimeMillis()}")
        tempExtractDir.mkdirs()

        try {
            val zis = ZipInputStream(inputStream)
            var entry: ZipEntry? = zis.nextEntry
            val buffer = ByteArray(4096)

            while (entry != null) {
                val newFile = File(tempExtractDir, entry.name)
                // Prevent Zip Slip vulnerability
                if (!newFile.canonicalPath.startsWith(tempExtractDir.canonicalPath)) {
                    throw SecurityException("Invalid ZIP path: ${entry.name}")
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }

            // Find manifest.json (could be at root or inside a single top-level folder)
            val manifestFile = if (File(tempExtractDir, "manifest.json").exists()) {
                File(tempExtractDir, "manifest.json")
            } else {
                tempExtractDir.walkTopDown().firstOrNull { it.name == "manifest.json" }
                    ?: return@withContext Result.failure(Exception("No manifest.json found in extension package"))
            }

            val sourceFolder = manifestFile.parentFile ?: tempExtractDir
            val manifest = gson.fromJson(manifestFile.readText(), ExtensionManifest::class.java)

            if (manifest.id.isBlank()) {
                return@withContext Result.failure(Exception("Invalid manifest: missing extension 'id'"))
            }

            val scriptFile = File(sourceFolder, manifest.safeMain)
            if (!scriptFile.exists()) {
                return@withContext Result.failure(Exception("Entry script '${manifest.safeMain}' not found in package"))
            }

            // Target destination
            val destDir = File(extensionsDir, manifest.id)
            if (destDir.exists()) {
                destDir.deleteRecursively()
            }
            destDir.mkdirs()

            // Copy all files to target
            sourceFolder.copyRecursively(destDir, overwrite = true)

            // Reload extensions
            loadInstalledExtensions()

            val installed = _installedExtensions.value.find { it.manifest.id == manifest.id }
                ?: return@withContext Result.failure(Exception("Installation verification failed"))

            Result.success(installed)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempExtractDir.deleteRecursively()
        }
    }

    /**
     * Downloads and installs an extension package from a remote URL.
     */
    suspend fun installFromUrl(downloadUrl: String): Result<InstalledExtension> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(downloadUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: Failed to download extension"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))
                installFromZip(body.byteStream())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uninstalls a user-installed extension.
     */
    suspend fun uninstallExtension(id: String): Boolean = withContext(Dispatchers.IO) {
        val ext = _installedExtensions.value.find { it.manifest.id == id } ?: return@withContext false
        if (ext.isBuiltIn) return@withContext false // Prevent deleting built-in fallback

        val success = ext.installDir.deleteRecursively()
        prefs.edit().remove("ext_enabled_$id").apply()
        loadInstalledExtensions()
        success
    }

    /**
     * Toggles an extension on or off.
     */
    suspend fun setExtensionEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean("ext_enabled_$id", enabled).apply()
        loadInstalledExtensions()
    }

    /**
     * Toggles an individual website rule on or off within an extension.
     */
    suspend fun setSiteEnabled(extensionId: String, siteDomain: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val key = "ext_disabled_sites_$extensionId"
        val current = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (enabled) {
            current.remove(siteDomain)
        } else {
            current.add(siteDomain)
        }
        prefs.edit().putStringSet(key, current).apply()
        loadInstalledExtensions()
    }

    /**
     * Fetches the catalog of available extensions from a remote registry.json URL.
     */
    suspend fun fetchRegistry(repoUrl: String): Result<ExtensionRegistry> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(repoUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: Failed to fetch registry"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty registry response"))
                val registry = gson.fromJson(body, ExtensionRegistry::class.java)
                Result.success(registry.copy(repositoryUrl = repoUrl))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Retrieves stored repository URLs.
     */
    fun getRepositoryUrls(): List<String> {
        val custom = prefs.getStringSet("custom_repo_urls", emptySet()) ?: emptySet()
        return (listOf(DEFAULT_REPO_URL) + custom).distinct()
    }

    /**
     * Adds a new custom repository URL.
     */
    fun addRepositoryUrl(url: String) {
        val current = prefs.getStringSet("custom_repo_urls", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(url.trim())
        prefs.edit().putStringSet("custom_repo_urls", current).apply()
    }

    /**
     * Removes a custom repository URL.
     */
    fun removeRepositoryUrl(url: String) {
        val current = prefs.getStringSet("custom_repo_urls", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.remove(url.trim())
        prefs.edit().putStringSet("custom_repo_urls", current).apply()
    }
}
