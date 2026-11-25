package com.example.browser

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Via Start",
    val url: String = "",
    val isHome: Boolean = true,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)

data class QuickLink(
    val label: String,
    val url: String,
    val monogram: String,
    val accentColor: Color
)

data class BrowserUiState(
    val settings: BrowserSettings = BrowserSettings(),
    val tabs: List<BrowserTab> = listOf(BrowserTab()),
    val activeTabId: String = tabs.first().id,
    val omniboxText: String = "",
    val isOmniboxFocused: Boolean = false,
    val blockedCount: Int = 0,
    val showQuickSettings: Boolean = false,
    val showTabSwitcher: Boolean = false,
    val statusMessage: String? = null
) {
    val activeTab: BrowserTab
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()

    val quickLinks: List<QuickLink>
        get() = defaultQuickLinks
}

private val defaultQuickLinks = listOf(
    QuickLink("Google", "https://www.google.com", "G", Color(0xFF2563EB)),
    QuickLink("Wikipedia", "https://en.m.wikipedia.org", "W", Color(0xFF334155)),
    QuickLink("GitHub", "https://github.com", "GH", Color(0xFF0F172A)),
    QuickLink("Hacker News", "https://news.ycombinator.com", "Y", Color(0xFFEA580C)),
    QuickLink("MDN Docs", "https://developer.mozilla.org", "M", Color(0xFF0284C7)),
    QuickLink("DuckDuckGo", "https://duckduckgo.com", "D", Color(0xFFDC2626))
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = BrowserPreferences(application)

    private val initialTab = BrowserTab()
    private val _uiState = MutableStateFlow(
        BrowserUiState(
            tabs = listOf(initialTab),
            activeTabId = initialTab.id
        )
    )
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.settingsFlow.collect { savedSettings ->
                _uiState.update { state ->
                    state.copy(settings = savedSettings)
                }
            }
        }
    }

    fun onOmniboxTextChange(newText: String) {
        _uiState.update { it.copy(omniboxText = newText) }
    }

    fun onOmniboxFocusChanged(focused: Boolean) {
        _uiState.update { state ->
            val updatedText = if (focused) {
                if (state.activeTab.isHome) "" else state.activeTab.url
            } else {
                if (state.activeTab.isHome) "" else state.activeTab.url
            }
            state.copy(
                isOmniboxFocused = focused,
                omniboxText = updatedText
            )
        }
    }

    fun submitOmnibox(rawInput: String = _uiState.value.omniboxText): String? {
        val resolvedUrl = UrlUtils.resolveUrlOrSearch(rawInput)
        if (resolvedUrl.isBlank()) return null
        navigateActiveTabTo(resolvedUrl)
        return resolvedUrl
    }

    fun navigateActiveTabTo(url: String) {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == state.activeTabId) {
                    tab.copy(
                        url = url,
                        title = UrlUtils.displayHost(url).ifBlank { url },
                        isHome = false,
                        isLoading = true,
                        progress = 10
                    )
                } else {
                    tab
                }
            }
            state.copy(
                tabs = updatedTabs,
                omniboxText = url,
                isOmniboxFocused = false
            )
        }
    }

    fun goHome() {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == state.activeTabId) {
                    tab.copy(
                        title = "Via Start",
                        url = "",
                        isHome = true,
                        isLoading = false,
                        progress = 0,
                        canGoBack = false,
                        canGoForward = false
                    )
                } else {
                    tab
                }
            }
            state.copy(
                tabs = updatedTabs,
                omniboxText = "",
                isOmniboxFocused = false
            )
        }
    }

    fun createNewTab() {
        val newTab = BrowserTab()
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = newTab.id,
                omniboxText = "",
                isOmniboxFocused = false,
                showTabSwitcher = false
            )
        }
    }

    fun selectTab(tabId: String) {
        _uiState.update { state ->
            val target = state.tabs.firstOrNull { it.id == tabId } ?: return@update state
            state.copy(
                activeTabId = target.id,
                omniboxText = if (target.isHome) "" else target.url,
                isOmniboxFocused = false,
                showTabSwitcher = false
            )
        }
    }

    fun closeTab(tabId: String) {
        _uiState.update { state ->
            val remaining = state.tabs.filterNot { it.id == tabId }
            if (remaining.isEmpty()) {
                val freshTab = BrowserTab()
                state.copy(
                    tabs = listOf(freshTab),
                    activeTabId = freshTab.id,
                    omniboxText = "",
                    isOmniboxFocused = false,
                    showTabSwitcher = false
                )
            } else {
                val nextActive = if (state.activeTabId == tabId) {
                    remaining.last()
                } else {
                    remaining.firstOrNull { it.id == state.activeTabId } ?: remaining.last()
                }
                state.copy(
                    tabs = remaining,
                    activeTabId = nextActive.id,
                    omniboxText = if (nextActive.isHome) "" else nextActive.url,
                    isOmniboxFocused = false
                )
            }
        }
    }

    fun setShowQuickSettings(show: Boolean) {
        _uiState.update {
            it.copy(
                showQuickSettings = show,
                showTabSwitcher = if (show) false else it.showTabSwitcher
            )
        }
    }

    fun setShowTabSwitcher(show: Boolean) {
        _uiState.update {
            it.copy(
                showTabSwitcher = show,
                showQuickSettings = if (show) false else it.showQuickSettings
            )
        }
    }

    fun toggleAdBlock(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAdBlockEnabled(enabled)
        }
    }

    fun toggleDisableImages(disabled: Boolean) {
        viewModelScope.launch {
            preferences.setImagesDisabled(disabled)
        }
    }

    fun toggleNightMode(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setNightModeEnabled(enabled)
        }
    }

    fun toggleDesktopMode(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setDesktopModeEnabled(enabled)
        }
    }

    /**
     * Thread-safe increment called from WebView's background shouldInterceptRequest thread.
     */
    fun onAdRequestBlocked() {
        _uiState.update { state ->
            state.copy(blockedCount = state.blockedCount + 1)
        }
    }

    fun onPageStarted(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean) {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        url = url,
                        isHome = false,
                        isLoading = true,
                        progress = 15,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward
                    )
                } else {
                    tab
                }
            }
            val updatedOmnibox = if (state.activeTabId == tabId && !state.isOmniboxFocused) {
                url
            } else {
                state.omniboxText
            }
            state.copy(tabs = updatedTabs, omniboxText = updatedOmnibox)
        }
    }

    fun onPageFinished(
        tabId: String,
        url: String,
        title: String?,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        url = url,
                        title = title?.takeIf { it.isNotBlank() }
                            ?: UrlUtils.displayHost(url).ifBlank { url },
                        isHome = false,
                        isLoading = false,
                        progress = 100,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward
                    )
                } else {
                    tab
                }
            }
            val updatedOmnibox = if (state.activeTabId == tabId && !state.isOmniboxFocused) {
                url
            } else {
                state.omniboxText
            }
            state.copy(tabs = updatedTabs, omniboxText = updatedOmnibox)
        }
    }

    fun onNavigationHistoryUpdated(tabId: String, url: String?, canGoBack: Boolean, canGoForward: Boolean) {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        url = url?.takeIf { it.isNotBlank() && it != "about:blank" } ?: tab.url,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward
                    )
                } else {
                    tab
                }
            }
            val active = updatedTabs.firstOrNull { it.id == state.activeTabId }
            val updatedOmnibox = if (state.activeTabId == tabId && !state.isOmniboxFocused && active != null && !active.isHome) {
                active.url
            } else {
                state.omniboxText
            }
            state.copy(tabs = updatedTabs, omniboxText = updatedOmnibox)
        }
    }

    fun onProgressChanged(tabId: String, newProgress: Int) {
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        progress = newProgress,
                        isLoading = newProgress in 1..99
                    )
                } else {
                    tab
                }
            }
            state.copy(tabs = updatedTabs)
        }
    }

    fun onTitleReceived(tabId: String, title: String?) {
        if (title.isNullOrBlank()) return
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == tabId && !tab.isHome) {
                    tab.copy(title = title)
                } else {
                    tab
                }
            }
            state.copy(tabs = updatedTabs)
        }
    }

    fun showStatusMessage(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
