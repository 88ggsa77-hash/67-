package com.example

import com.example.browser.AdBlocker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViaBrowserUnitTest {

    @Test
    fun adBlocker_blocksExactAndSubdomainAdHosts() {
        assertTrue(AdBlocker.isAdHost("doubleclick.net"))
        assertTrue(AdBlocker.isAdHost("stats.g.doubleclick.net"))
        assertTrue(AdBlocker.isAdHost("PAGEAD2.GOOGLESYNDICATION.COM"))
        assertFalse(AdBlocker.isAdHost("en.wikipedia.org"))
        assertFalse(AdBlocker.isAdHost("github.com"))
    }

    @Test
    fun adBlocker_supportsDynamicCustomHosts() {
        val host = "custom-tracker.example.org"
        assertFalse(AdBlocker.isAdHost(host))
        AdBlocker.addCustomHost(host)
        assertTrue(AdBlocker.isAdHost("sub.$host"))
        AdBlocker.removeCustomHost(host)
        assertFalse(AdBlocker.isAdHost("sub.$host"))
    }
}
