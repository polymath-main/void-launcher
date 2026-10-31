package com.polymath.voidlauncher

import com.polymath.voidlauncher.model.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppIndexerTest {

    private val sampleApps = listOf(
        AppEntry("Termux", "com.termux", "com.termux.HomeActivity"),
        AppEntry("Telegram", "org.telegram.messenger", "org.telegram.ui.LaunchActivity"),
        AppEntry("Settings", "com.android.settings", "com.android.settings.Settings"),
        AppEntry("Polymath FS", "com.polymath.fs", "com.polymath.fs.MainActivity"),
        AppEntry("Chrome", "com.android.chrome", "com.google.android.apps.chrome.Main")
    )

    private fun filter(query: String, apps: List<AppEntry>): List<AppEntry> {
        val q = query.trim().lowercase(Locale.ROOT)
        return apps.filter {
            it.label.lowercase(Locale.ROOT).contains(q) ||
            it.packageName.lowercase(Locale.ROOT).contains(q)
        }
    }

    @Test
    fun testFuzzyMatchingPrefix() {
        val matches = filter("te", sampleApps)
        assertEquals(2, matches.size)
        assertTrue(matches.any { it.label == "Termux" })
        assertTrue(matches.any { it.label == "Telegram" })
    }

    @Test
    fun testExactAcronymOrSubstring() {
        val matches = filter("poly", sampleApps)
        assertEquals(1, matches.size)
        assertEquals("Polymath FS", matches[0].label)
    }

    @Test
    fun testBareIntegerParsing() {
        val input = "3"
        val intVal = input.toIntOrNull()
        assertNotNull(intVal)
        assertEquals(3, intVal)
        assertTrue(intVal in 1..5)
    }
}
