package com.samuel.readaloud.ui.extension.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samuel.readaloud.domain.extension.ExtensionManager
import com.samuel.readaloud.domain.extension.InstalledExtension
import com.samuel.readaloud.domain.extension.RegistryExtensionItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel managing the state of installed extensions, store catalog, and repository sources.
 */
class ExtensionsViewModel(application: Application) : AndroidViewModel(application) {

    private val extensionManager = ExtensionManager.getInstance(application)

    val installedExtensions: StateFlow<List<InstalledExtension>> = extensionManager.installedExtensions

    private val _storeExtensions = MutableStateFlow<List<RegistryExtensionItem>>(emptyList())
    val storeExtensions: StateFlow<List<RegistryExtensionItem>> = _storeExtensions.asStateFlow()

    private val _repositoryUrls = MutableStateFlow<List<String>>(emptyList())
    val repositoryUrls: StateFlow<List<String>> = _repositoryUrls.asStateFlow()

    private val _isLoadingStore = MutableStateFlow(false)
    val isLoadingStore: StateFlow<Boolean> = _isLoadingStore.asStateFlow()

    private val _installingIds = MutableStateFlow<Set<String>>(emptySet())
    val installingIds: StateFlow<Set<String>> = _installingIds.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    init {
        loadRepositories()
        refreshCatalog()
    }

    private fun loadRepositories() {
        _repositoryUrls.value = extensionManager.getRepositoryUrls()
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            _isLoadingStore.value = true
            val catalog = mutableListOf<RegistryExtensionItem>()
            val repos = extensionManager.getRepositoryUrls()

            for (repoUrl in repos) {
                val res = extensionManager.fetchRegistry(repoUrl)
                if (res.isSuccess) {
                    val reg = res.getOrNull()
                    if (reg != null) {
                        catalog.addAll(reg.extensions)
                    }
                }
            }

            _storeExtensions.value = catalog.distinctBy { it.id }
            _isLoadingStore.value = false
        }
    }

    fun toggleExtension(id: String, enabled: Boolean) {
        viewModelScope.launch {
            extensionManager.setExtensionEnabled(id, enabled)
        }
    }

    fun toggleSite(extensionId: String, siteDomain: String, enabled: Boolean) {
        viewModelScope.launch {
            extensionManager.setSiteEnabled(extensionId, siteDomain, enabled)
        }
    }

    fun uninstallExtension(id: String) {
        viewModelScope.launch {
            val success = extensionManager.uninstallExtension(id)
            if (success) {
                _userMessage.emit("Extension uninstalled")
            } else {
                _userMessage.emit("Failed to uninstall extension")
            }
        }
    }

    fun installFromStore(item: RegistryExtensionItem) {
        viewModelScope.launch {
            _installingIds.value = _installingIds.value + item.id
            val result = extensionManager.installFromUrl(item.downloadUrl)
            _installingIds.value = _installingIds.value - item.id

            result.fold(
                onSuccess = {
                    _userMessage.emit("Installed '${item.name}' successfully!")
                },
                onFailure = { error ->
                    _userMessage.emit("Install failed: ${error.message}")
                }
            )
        }
    }

    fun installFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val stream = context.contentResolver.openInputStream(uri)
                if (stream == null) {
                    _userMessage.emit("Could not open selected file")
                    return@launch
                }

                val result = stream.use { extensionManager.installFromZip(it) }
                result.fold(
                    onSuccess = { installed ->
                        _userMessage.emit("Successfully imported '${installed.manifest.name}'!")
                    },
                    onFailure = { error ->
                        _userMessage.emit("Import failed: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                _userMessage.emit("Error importing file: ${e.message}")
            }
        }
    }

    fun addRepository(url: String) {
        extensionManager.addRepositoryUrl(url)
        loadRepositories()
        refreshCatalog()
        viewModelScope.launch {
            _userMessage.emit("Repository added!")
        }
    }

    fun removeRepository(url: String) {
        extensionManager.removeRepositoryUrl(url)
        loadRepositories()
        refreshCatalog()
        viewModelScope.launch {
            _userMessage.emit("Repository removed")
        }
    }
}
