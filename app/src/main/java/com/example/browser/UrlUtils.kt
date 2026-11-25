package com.example.browser

import android.net.Uri
import android.util.Patterns
import java.util.Locale

object UrlUtils {

    private const val GOOGLE_SEARCH_BASE = "https://www.google.com/search?q="

    private val localHostPattern = Regex(
        pattern = "^(localhost|\\d{1,3}(\\.\\d{1,3}){3})(:\\d+)?(/.*)?$",
        option = RegexOption.IGNORE_CASE
    )

    private val domainPattern = Regex(
        pattern = "^([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(:\\d+)?(/.*)?$"
    )

    /**
     * Resolves user input from the Smart Omnibox into either a navigable URL
     * or a Google Search URL.
     */
    fun resolveUrlOrSearch(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return ""

        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("about:") ||
            lower.startsWith("file://")
        ) {
            return trimmed
        }

        if (trimmed.contains(' ')) {
            return "$GOOGLE_SEARCH_BASE${Uri.encode(trimmed)}"
        }

        if (localHostPattern.matches(trimmed)) {
            return "http://$trimmed"
        }

        if (domainPattern.matches(trimmed) || Patterns.WEB_URL.matcher(trimmed).matches()) {
            return "https://$trimmed"
        }

        return "$GOOGLE_SEARCH_BASE${Uri.encode(trimmed)}"
    }

    /**
     * Extracts a clean host string for display in tab cards and unfocused omnibox states.
     */
    fun displayHost(url: String): String {
        if (url.isBlank()) return ""
        val host = Uri.parse(url).host
        return if (!host.isNullOrBlank()) {
            host.removePrefix("www.")
        } else {
            url
        }
    }

    fun isSecureUrl(url: String): Boolean {
        return url.startsWith("https://", ignoreCase = true)
    }
}
