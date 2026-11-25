package com.example.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

private const val NIGHT_MODE_CSS =
    "html { filter: invert(90%) hue-rotate(180deg) !important; background-color: #111318 !important; } " +
        "img, video, picture, canvas, svg, [style*='background-image'] { filter: invert(100%) hue-rotate(180deg) !important; }"

private const val INJECT_NIGHT_MODE_JS =
    "(function(){" +
        "var id='via-night-mode-style';" +
        "if(!document.getElementById(id)){" +
        "var s=document.createElement('style');" +
        "s.id=id;" +
        "s.textContent=\"$NIGHT_MODE_CSS\";" +
        "(document.head||document.documentElement).appendChild(s);" +
        "}" +
        "})();"

private const val REMOVE_NIGHT_MODE_JS =
    "(function(){" +
        "var el=document.getElementById('via-night-mode-style');" +
        "if(el){el.remove();}" +
        "})();"

interface ViaWebViewCallbacks {
    fun onAdBlocked()
    fun onPageStarted(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean)
    fun onPageFinished(tabId: String, url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean)
    fun onNavigationHistoryUpdated(tabId: String, url: String?, canGoBack: Boolean, canGoForward: Boolean)
    fun onProgressChanged(tabId: String, progress: Int)
    fun onTitleReceived(tabId: String, title: String?)
}

private class TabWebHolder(
    val webView: WebView,
    val defaultUserAgent: String,
    @Volatile var isAdBlockEnabled: Boolean,
    var isImagesDisabled: Boolean,
    var isNightModeEnabled: Boolean,
    var isDesktopModeEnabled: Boolean,
    var lastRequestedUrl: String? = null
)

/**
 * Manages live WebView instances per tab outside ViewModel to avoid Activity Context leaks,
 * while ensuring clean WebView disposal when tabs close or the host activity is destroyed.
 */
@Stable
class ViaWebViewController {

    private val holders = LinkedHashMap<String, TabWebHolder>()
    private var activeTabId: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun obtainWebView(
        context: Context,
        tabId: String,
        targetUrl: String,
        settings: BrowserSettings,
        callbacks: ViaWebViewCallbacks
    ): WebView {
        activeTabId = tabId
        val existing = holders[tabId]
        if (existing != null) {
            syncTabSettings(tabId, settings, isWebPageActive = true)
            if (targetUrl.isNotBlank() && existing.lastRequestedUrl != targetUrl) {
                existing.lastRequestedUrl = targetUrl
                existing.webView.loadUrl(targetUrl)
            }
            return existing.webView
        }

        val webView = WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val defaultUa = webView.settings.userAgentString ?: ""
        val holder = TabWebHolder(
            webView = webView,
            defaultUserAgent = defaultUa,
            isAdBlockEnabled = settings.isAdBlockEnabled,
            isImagesDisabled = settings.isImagesDisabled,
            isNightModeEnabled = settings.isNightModeEnabled,
            isDesktopModeEnabled = settings.isDesktopModeEnabled,
            lastRequestedUrl = targetUrl.takeIf { it.isNotBlank() }
        )

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            loadsImagesAutomatically = !settings.isImagesDisabled
            blockNetworkImage = settings.isImagesDisabled
            userAgentString = if (settings.isDesktopModeEnabled) DESKTOP_USER_AGENT else defaultUa
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        applyNightMode(webView, settings.isNightModeEnabled)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if (holder.isAdBlockEnabled && AdBlocker.isAdUrl(request?.url)) {
                    callbacks.onAdBlocked()
                    return AdBlocker.createEmptyResponse()
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: return false
                if (scheme == "http" || scheme == "https" || scheme == "about") {
                    return false
                }
                // Handle external app schemes (mailto:, tel:, intent:) safely
                return try {
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                } catch (_: Exception) {
                    true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val currentUrl = url ?: return
                if (currentUrl == "about:blank") return
                holder.lastRequestedUrl = currentUrl
                if (view != null) {
                    applyNightMode(view, holder.isNightModeEnabled)
                    callbacks.onPageStarted(
                        tabId = tabId,
                        url = currentUrl,
                        canGoBack = view.canGoBack(),
                        canGoForward = view.canGoForward()
                    )
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val currentUrl = url ?: return
                if (currentUrl == "about:blank") return
                if (view != null) {
                    applyNightMode(view, holder.isNightModeEnabled)
                    callbacks.onPageFinished(
                        tabId = tabId,
                        url = currentUrl,
                        title = view.title,
                        canGoBack = view.canGoBack(),
                        canGoForward = view.canGoForward()
                    )
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (url == "about:blank") return
                if (view != null) {
                    if (!url.isNullOrBlank()) {
                        holder.lastRequestedUrl = url
                    }
                    callbacks.onNavigationHistoryUpdated(
                        tabId = tabId,
                        url = url,
                        canGoBack = view.canGoBack(),
                        canGoForward = view.canGoForward()
                    )
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                callbacks.onProgressChanged(tabId, newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                callbacks.onTitleReceived(tabId, title)
            }
        }

        holders[tabId] = holder
        if (targetUrl.isNotBlank()) {
            webView.loadUrl(targetUrl)
        }
        return webView
    }

    fun syncTabSettings(tabId: String, settings: BrowserSettings, isWebPageActive: Boolean) {
        val holder = holders[tabId] ?: return
        holder.isAdBlockEnabled = settings.isAdBlockEnabled

        var needsReload = false

        if (holder.isImagesDisabled != settings.isImagesDisabled) {
            holder.isImagesDisabled = settings.isImagesDisabled
            holder.webView.settings.loadsImagesAutomatically = !settings.isImagesDisabled
            holder.webView.settings.blockNetworkImage = settings.isImagesDisabled
            needsReload = true
        }

        if (holder.isDesktopModeEnabled != settings.isDesktopModeEnabled) {
            holder.isDesktopModeEnabled = settings.isDesktopModeEnabled
            holder.webView.settings.userAgentString = if (settings.isDesktopModeEnabled) {
                DESKTOP_USER_AGENT
            } else {
                holder.defaultUserAgent
            }
            needsReload = true
        }

        if (holder.isNightModeEnabled != settings.isNightModeEnabled) {
            holder.isNightModeEnabled = settings.isNightModeEnabled
            applyNightMode(holder.webView, settings.isNightModeEnabled)
        }

        if (needsReload && isWebPageActive) {
            holder.webView.reload()
        }
    }

    fun syncAllHolders(settings: BrowserSettings, activeTabId: String, isActiveTabWeb: Boolean) {
        holders.keys.toList().forEach { id ->
            syncTabSettings(
                tabId = id,
                settings = settings,
                isWebPageActive = (id == activeTabId && isActiveTabWeb)
            )
        }
    }

    private fun applyNightMode(webView: WebView, enabled: Boolean) {
        val webSettings = webView.settings
        val isSystemNightMode = (webView.context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        if (!enabled) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, false)
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(webSettings, WebSettingsCompat.FORCE_DARK_OFF)
            }
            webView.evaluateJavascript(REMOVE_NIGHT_MODE_JS, null)
            return
        }

        // Prefer native androidx.webkit darkening when supported and active; fall back to CSS script
        // on older WebView APIs or when system UI mode is Light so forced Night Mode always works.
        when {
            WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) && isSystemNightMode -> {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, true)
                webView.evaluateJavascript(REMOVE_NIGHT_MODE_JS, null)
            }

            WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) -> {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, true)
                }
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(webSettings, WebSettingsCompat.FORCE_DARK_ON)
                webView.evaluateJavascript(REMOVE_NIGHT_MODE_JS, null)
            }

            else -> {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(webSettings, true)
                }
                webView.evaluateJavascript(INJECT_NIGHT_MODE_JS, null)
            }
        }
    }

    fun loadUrl(tabId: String, url: String) {
        val holder = holders[tabId] ?: return
        holder.lastRequestedUrl = url
        holder.webView.loadUrl(url)
    }

    fun goBack(tabId: String) {
        val webView = holders[tabId]?.webView ?: return
        if (webView.canGoBack()) {
            webView.goBack()
        }
    }

    fun goForward(tabId: String) {
        val webView = holders[tabId]?.webView ?: return
        if (webView.canGoForward()) {
            webView.goForward()
        }
    }

    fun reload(tabId: String) {
        holders[tabId]?.webView?.reload()
    }

    fun stopLoading(tabId: String) {
        holders[tabId]?.webView?.stopLoading()
    }

    fun clearBrowsingData() {
        holders.values.forEach { it.webView.clearCache(true) }
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    fun pruneClosedTabs(openTabIds: Set<String>) {
        val iterator = holders.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in openTabIds) {
                destroyWebView(entry.value.webView)
                iterator.remove()
            }
        }
    }

    fun destroyTab(tabId: String) {
        holders.remove(tabId)?.let { destroyWebView(it.webView) }
    }

    fun onPause() {
        activeTabId?.let { holders[it]?.webView?.onPause() }
    }

    fun onResume() {
        activeTabId?.let { holders[it]?.webView?.onResume() }
    }

    fun destroyAll() {
        holders.values.forEach { destroyWebView(it.webView) }
        holders.clear()
        activeTabId = null
    }

    private fun destroyWebView(webView: WebView) {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.removeAllViews()
        webView.destroy()
    }
}

@Composable
fun ViaWebView(
    tab: BrowserTab,
    settings: BrowserSettings,
    controller: ViaWebViewController,
    callbacks: ViaWebViewCallbacks,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentCallbacks by rememberUpdatedState(callbacks)

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> controller.onPause()
                Lifecycle.Event.ON_RESUME -> controller.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(tab.id, settings, tab.isHome) {
        controller.syncAllHolders(
            settings = settings,
            activeTabId = tab.id,
            isActiveTabWeb = !tab.isHome
        )
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            FrameLayout(context).apply {
                val webView = controller.obtainWebView(
                    context = context,
                    tabId = tab.id,
                    targetUrl = tab.url,
                    settings = settings,
                    callbacks = currentCallbacks
                )
                (webView.parent as? ViewGroup)?.removeView(webView)
                addView(webView)
            }
        },
        update = { container ->
            val webView = controller.obtainWebView(
                context = container.context,
                tabId = tab.id,
                targetUrl = tab.url,
                settings = settings,
                callbacks = currentCallbacks
            )
            if (webView.parent !== container) {
                (webView.parent as? ViewGroup)?.removeView(webView)
                container.removeAllViews()
                container.addView(webView)
            }
        }
    )
}
