package com.example.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.browser.BrowserUiState
import com.example.browser.BrowserViewModel
import com.example.browser.UrlUtils
import com.example.browser.ViaWebView
import com.example.browser.ViaWebViewCallbacks
import com.example.browser.ViaWebViewController
import com.example.ui.components.QuickSettingsSheet
import com.example.ui.components.TabSwitcherSheet
import com.example.ui.components.ViaStartPage

@Composable
fun BrowserScreen(
    uiState: BrowserUiState,
    viewModel: BrowserViewModel
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val omniboxFocusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    val webViewController = remember { ViaWebViewController() }

    val activeTab = uiState.activeTab

    val webCallbacks = remember(viewModel) {
        object : ViaWebViewCallbacks {
            override fun onAdBlocked() {
                viewModel.onAdRequestBlocked()
            }

            override fun onPageStarted(
                tabId: String,
                url: String,
                canGoBack: Boolean,
                canGoForward: Boolean
            ) {
                viewModel.onPageStarted(tabId, url, canGoBack, canGoForward)
            }

            override fun onPageFinished(
                tabId: String,
                url: String,
                title: String?,
                canGoBack: Boolean,
                canGoForward: Boolean
            ) {
                viewModel.onPageFinished(tabId, url, title, canGoBack, canGoForward)
            }

            override fun onNavigationHistoryUpdated(
                tabId: String,
                url: String?,
                canGoBack: Boolean,
                canGoForward: Boolean
            ) {
                viewModel.onNavigationHistoryUpdated(tabId, url, canGoBack, canGoForward)
            }

            override fun onProgressChanged(tabId: String, progress: Int) {
                viewModel.onProgressChanged(tabId, progress)
            }

            override fun onTitleReceived(tabId: String, title: String?) {
                viewModel.onTitleReceived(tabId, title)
            }
        }
    }

    // Prune closed tab WebViews and destroy all on screen disposal to prevent leaks
    LaunchedEffect(uiState.tabs) {
        webViewController.pruneClosedTabs(uiState.tabs.map { it.id }.toSet())
    }

    DisposableEffect(webViewController) {
        onDispose {
            webViewController.destroyAll()
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearStatusMessage()
    }

    // System Back handling: dismiss sheets/focus first, then WebView back history, else exit
    BackHandler(
        enabled = uiState.showQuickSettings ||
            uiState.showTabSwitcher ||
            uiState.isOmniboxFocused ||
            (!activeTab.isHome && activeTab.canGoBack)
    ) {
        when {
            uiState.showQuickSettings -> viewModel.setShowQuickSettings(false)
            uiState.showTabSwitcher -> viewModel.setShowTabSwitcher(false)
            uiState.isOmniboxFocused -> focusManager.clearFocus()
            !activeTab.isHome && activeTab.canGoBack -> webViewController.goBack(activeTab.id)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SmartOmniboxHeader(
                omniboxText = uiState.omniboxText,
                activeUrl = activeTab.url,
                isHome = activeTab.isHome,
                isFocused = uiState.isOmniboxFocused,
                isAdBlockEnabled = uiState.settings.isAdBlockEnabled,
                focusRequester = omniboxFocusRequester,
                onTextChange = viewModel::onOmniboxTextChange,
                onFocusChange = viewModel::onOmniboxFocusChanged,
                onSubmit = { query ->
                    val target = viewModel.submitOmnibox(query)
                    if (target != null) {
                        focusManager.clearFocus()
                        if (!activeTab.isHome) {
                            webViewController.loadUrl(activeTab.id, target)
                        }
                    }
                },
                onShieldClick = { viewModel.setShowQuickSettings(true) }
            )

            // Linear progress bar right under the omnibox
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !activeTab.isHome && activeTab.isLoading && activeTab.progress in 1..99,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = activeTab.progress / 100f,
                        label = "webview_progress"
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .testTag("page_progress_bar"),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // Main content viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (activeTab.isHome) {
                    ViaStartPage(
                        quickLinks = uiState.quickLinks,
                        isAdBlockEnabled = uiState.settings.isAdBlockEnabled,
                        blockedCount = uiState.blockedCount,
                        onSearchTriggerClick = {
                            omniboxFocusRequester.requestFocus()
                        },
                        onQuickLinkClick = { url ->
                            focusManager.clearFocus()
                            viewModel.navigateActiveTabTo(url)
                        },
                        onShieldClick = { viewModel.setShowQuickSettings(true) }
                    )
                } else {
                    ViaWebView(
                        tab = activeTab,
                        settings = uiState.settings,
                        controller = webViewController,
                        callbacks = webCallbacks
                    )
                }
            }

            // Minimalist bottom navigation bar
            BrowserBottomBar(
                canGoBack = !activeTab.isHome && activeTab.canGoBack,
                canGoForward = !activeTab.isHome && activeTab.canGoForward,
                isHome = activeTab.isHome,
                isLoading = !activeTab.isHome && activeTab.isLoading,
                tabCount = uiState.tabs.size,
                onBackClick = { webViewController.goBack(activeTab.id) },
                onForwardClick = { webViewController.goForward(activeTab.id) },
                onHomeClick = {
                    focusManager.clearFocus()
                    webViewController.destroyTab(activeTab.id)
                    viewModel.goHome()
                },
                onTabsClick = {
                    focusManager.clearFocus()
                    viewModel.setShowTabSwitcher(true)
                },
                onRefreshOrStopClick = {
                    if (activeTab.isLoading) {
                        webViewController.stopLoading(activeTab.id)
                    } else {
                        webViewController.reload(activeTab.id)
                    }
                },
                onMenuClick = {
                    focusManager.clearFocus()
                    viewModel.setShowQuickSettings(true)
                }
            )
        }
    }

    if (uiState.showQuickSettings) {
        val copiedMsg = stringResource(R.string.url_copied_message)
        val clearedMsg = stringResource(R.string.cache_cleared_message)
        QuickSettingsSheet(
            settings = uiState.settings,
            blockedCount = uiState.blockedCount,
            hasActivePage = !activeTab.isHome && activeTab.url.isNotBlank(),
            onDismiss = { viewModel.setShowQuickSettings(false) },
            onToggleAdBlock = viewModel::toggleAdBlock,
            onToggleDisableImages = viewModel::toggleDisableImages,
            onToggleNightMode = viewModel::toggleNightMode,
            onToggleDesktopMode = viewModel::toggleDesktopMode,
            onCopyUrl = {
                clipboardManager.setText(AnnotatedString(activeTab.url))
                viewModel.showStatusMessage(copiedMsg)
            },
            onShareUrl = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, activeTab.title)
                    putExtra(Intent.EXTRA_TEXT, activeTab.url)
                }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_page)))
            },
            onClearCache = {
                webViewController.clearBrowsingData()
                viewModel.showStatusMessage(clearedMsg)
            }
        )
    }

    if (uiState.showTabSwitcher) {
        TabSwitcherSheet(
            tabs = uiState.tabs,
            activeTabId = uiState.activeTabId,
            onDismiss = { viewModel.setShowTabSwitcher(false) },
            onSelectTab = viewModel::selectTab,
            onCloseTab = viewModel::closeTab,
            onNewTab = viewModel::createNewTab
        )
    }
}

@Composable
private fun SmartOmniboxHeader(
    omniboxText: String,
    activeUrl: String,
    isHome: Boolean,
    isFocused: Boolean,
    isAdBlockEnabled: Boolean,
    focusRequester: FocusRequester,
    onTextChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: (String) -> Unit,
    onShieldClick: () -> Unit
) {
    val displayedText = if (isFocused) {
        omniboxText
    } else if (isHome) {
        ""
    } else {
        UrlUtils.displayHost(activeUrl)
    }

    var textFieldValue by remember { mutableStateOf(TextFieldValue(displayedText)) }

    LaunchedEffect(displayedText, isFocused) {
        if (textFieldValue.text != displayedText) {
            textFieldValue = if (isFocused && displayedText.isNotEmpty()) {
                TextFieldValue(
                    text = displayedText,
                    selection = TextRange(0, displayedText.length)
                )
            } else {
                TextFieldValue(
                    text = displayedText,
                    selection = TextRange(displayedText.length)
                )
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = if (isFocused) 1.5.dp else 1.dp,
                        color = if (isFocused) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = RoundedCornerShape(22.dp)
                    )
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val leadingIcon = when {
                    isHome || isFocused -> Icons.Rounded.Search
                    UrlUtils.isSecureUrl(activeUrl) -> Icons.Rounded.Lock
                    else -> Icons.Rounded.LockOpen
                }
                val leadingTint = when {
                    !isHome && !isFocused && UrlUtils.isSecureUrl(activeUrl) ->
                        MaterialTheme.colorScheme.secondary
                    isFocused -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val leadingDescription = when {
                    isHome || isFocused -> null
                    UrlUtils.isSecureUrl(activeUrl) -> stringResource(R.string.cd_secure_connection)
                    else -> stringResource(R.string.cd_insecure_connection)
                }

                Icon(
                    imageVector = leadingIcon,
                    contentDescription = leadingDescription,
                    tint = leadingTint,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (textFieldValue.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.omnibox_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { updated ->
                            textFieldValue = updated
                            onTextChange(updated.text)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = if (!isFocused && !isHome) {
                                MaterialTheme.typography.labelMedium.fontFamily
                            } else {
                                MaterialTheme.typography.bodyMedium.fontFamily
                            },
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { onSubmit(textFieldValue.text) }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                onFocusChange(focusState.isFocused)
                            }
                            .testTag("omnibox_input")
                    )
                }

                if (isFocused && textFieldValue.text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            textFieldValue = TextFieldValue("")
                            onTextChange("")
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("omnibox_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_clear_url),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onShieldClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("omnibox_shield_button")
                    ) {
                        Icon(
                            imageVector = if (isAdBlockEnabled) {
                                Icons.Rounded.Shield
                            } else {
                                Icons.Outlined.Shield
                            },
                            contentDescription = stringResource(
                                if (isAdBlockEnabled) R.string.cd_shield_active else R.string.cd_shield_inactive
                            ),
                            tint = if (isAdBlockEnabled) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserBottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    isHome: Boolean,
    isLoading: Boolean,
    tabCount: Int,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onHomeClick: () -> Unit,
    onTabsClick: () -> Unit,
    onRefreshOrStopClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = 0.8.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    enabled = canGoBack,
                    onClick = onBackClick,
                    testTag = "nav_back"
                )

                BottomNavIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = stringResource(R.string.cd_forward),
                    enabled = canGoForward,
                    onClick = onForwardClick,
                    testTag = "nav_forward"
                )

                BottomNavIconButton(
                    icon = if (isHome) Icons.Rounded.Home else Icons.Outlined.Home,
                    contentDescription = stringResource(R.string.cd_home),
                    enabled = true,
                    tint = if (isHome) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    onClick = onHomeClick,
                    testTag = "nav_home"
                )

                IconButton(
                    onClick = onTabsClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("nav_tabs")
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .border(
                                width = 1.8.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = RoundedCornerShape(6.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabCount.coerceAtMost(99).toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                BottomNavIconButton(
                    icon = if (isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                    contentDescription = stringResource(
                        if (isLoading) R.string.cd_stop else R.string.cd_refresh
                    ),
                    enabled = !isHome,
                    onClick = onRefreshOrStopClick,
                    testTag = "nav_refresh_stop"
                )

                BottomNavIconButton(
                    icon = Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.cd_menu),
                    enabled = true,
                    onClick = onMenuClick,
                    testTag = "nav_menu"
                )
            }
        }
    }
}

@Composable
private fun BottomNavIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
    testTag: String
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else 0.35f)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
