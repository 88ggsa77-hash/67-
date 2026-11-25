package com.example.browser

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance O(1) host-based Ad & Tracker blocker for Android WebView.
 * Designed to execute safely on WebView's background network threads inside shouldInterceptRequest.
 */
object AdBlocker {

    private val customBlockedHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val defaultAdHosts: HashSet<String> = hashSetOf(
        // Google Ads & Analytics
        "doubleclick.net",
        "adservice.google.com",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com",
        "fundingchoicesmessages.google.com",
        "admob.com",
        // Meta / Facebook Trackers
        "ads.facebook.com",
        "an.facebook.com",
        "pixel.facebook.com",
        "connect.facebook.net",
        // Amazon & Microsoft / Bing
        "amazon-adsystem.com",
        "aax.amazon-adsystem.com",
        "c.amazon-adsystem.com",
        "bat.bing.com",
        "clarity.ms",
        "ads.msn.com",
        // Major Ad Exchanges & Programmatic Networks
        "adnxs.com",
        "ib.adnxs.com",
        "rubiconproject.com",
        "fastlane.rubiconproject.com",
        "pubmatic.com",
        "ads.pubmatic.com",
        "openx.net",
        "criteo.com",
        "criteo.net",
        "bidder.criteo.com",
        "casalemedia.com",
        "js-sec.indexww.com",
        "indexww.com",
        "adsrvr.org",
        "match.adsrvr.org",
        "adform.net",
        "bidswitch.net",
        "smartadserver.com",
        "yieldmo.com",
        "sharethrough.com",
        "triplelift.com",
        "tlx.3lift.com",
        "3lift.com",
        "spotxchange.com",
        "advertising.com",
        "adtechus.com",
        "contextweb.com",
        "lijit.com",
        "sovrn.com",
        "media.net",
        "contextual.media.net",
        "adroll.com",
        "mathtag.com",
        "turn.com",
        "rlcdn.com",
        "tapad.com",
        "teads.tv",
        "a.teads.tv",
        "gumgum.com",
        "kargo.com",
        "sonobi.com",
        "rhythmone.com",
        "undertone.com",
        "zemanta.com",
        // Native & Pop-under Ads
        "taboola.com",
        "cdn.taboola.com",
        "trc.taboola.com",
        "outbrain.com",
        "widgets.outbrain.com",
        "mgid.com",
        "revcontent.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "adcash.com",
        "exoclick.com",
        "juicyads.com",
        "trafficjunky.net",
        "hilltopads.net",
        "clickadu.com",
        // Analytics, Telemetry & Attribution
        "scorecardresearch.com",
        "sb.scorecardresearch.com",
        "quantserve.com",
        "pixel.quantserve.com",
        "moatads.com",
        "z.moatads.com",
        "chartbeat.com",
        "static.chartbeat.com",
        "hotjar.com",
        "script.hotjar.com",
        "static.hotjar.com",
        "mixpanel.com",
        "api.mixpanel.com",
        "cdn.mxpnl.com",
        "amplitude.com",
        "api.amplitude.com",
        "segment.io",
        "segment.com",
        "cdn.segment.com",
        "api.segment.io",
        "appsflyer.com",
        "adjust.com",
        "app.adjust.com",
        "branch.io",
        "api2.branch.io",
        "kochava.com",
        "flurry.com",
        "bluekai.com",
        "krxd.net",
        "demdex.net",
        "omtrdc.net",
        "2o7.net",
        "newrelic.com",
        "bam.nr-data.net",
        "js-agent.newrelic.com",
        "bugsnag.com",
        "sentry.io",
        "heapanalytics.com",
        "fullstory.com",
        "mouseflow.com",
        "crazyegg.com",
        "optimizely.com",
        "kissmetrics.com",
        // Social & Mobile Ad SDKs
        "ads.twitter.com",
        "analytics.twitter.com",
        "static.ads-twitter.com",
        "ads.tiktok.com",
        "analytics.tiktok.com",
        "ads.linkedin.com",
        "px.ads.linkedin.com",
        "snap.licdn.com",
        "ads.pinterest.com",
        "ct.pinterest.com",
        "tr.snapchat.com",
        "sc-static.net",
        "ads.yahoo.com",
        "gemini.yahoo.com",
        "analytics.yahoo.com",
        "unityads.unity3d.com",
        "applovin.com",
        "d.applovin.com",
        "vungle.com",
        "ironsrc.com",
        "inmobi.com",
        "mopub.com",
        "smaato.net",
        "chartboost.com",
        "startappservice.com",
        "adcolony.com",
        "fyber.com",
        "yandexadexchange.net",
        "an.yandex.ru",
        "mc.yandex.ru"
    )

    private val emptyHeaders = mapOf(
        "Access-Control-Allow-Origin" to "*",
        "Cache-Control" to "no-store"
    )

    /**
     * Checks if the given request URI belongs to a known ad or tracker host (or subdomain).
     */
    fun isAdUrl(uri: Uri?): Boolean {
        val host = uri?.host ?: return false
        return isAdHost(host)
    }

    /**
     * Checks whether [host] or any of its parent domains is present in the O(1) HashSet.
     * Example: "pagead2.googlesyndication.com" -> checks "pagead2.googlesyndication.com",
     * then "googlesyndication.com".
     */
    fun isAdHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        var candidate = host.lowercase(Locale.ROOT).trimEnd('.')
        while (candidate.isNotEmpty()) {
            if (defaultAdHosts.contains(candidate) || customBlockedHosts.contains(candidate)) {
                return true
            }
            val dotIndex = candidate.indexOf('.')
            if (dotIndex < 0 || dotIndex >= candidate.length - 1) {
                break
            }
            candidate = candidate.substring(dotIndex + 1)
        }
        return false
    }

    /**
     * Extensibility hook allowing runtime host additions without mutating the core HashSet.
     */
    fun addCustomHost(host: String) {
        val normalized = host.trim().lowercase(Locale.ROOT).trimEnd('.')
        if (normalized.isNotEmpty()) {
            customBlockedHosts.add(normalized)
        }
    }

    fun removeCustomHost(host: String) {
        customBlockedHosts.remove(host.trim().lowercase(Locale.ROOT).trimEnd('.'))
    }

    /**
     * Returns an empty HTTP 200 WebResourceResponse so blocked ad scripts, pixels, and frames
     * resolve silently without triggering browser error banners or breaking page layout.
     */
    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            200,
            "OK",
            emptyHeaders,
            ByteArrayInputStream(ByteArray(0))
        )
    }
}
