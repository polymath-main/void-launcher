package com.polymath.voidlauncher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * UI v2 Cycle 2: COCKPIT home port assertions, as layout-text checks
 * (the same pattern as TermuxRemovalCycle3Test — the JVM harness has
 * no Android layout inflater, so the XML itself is the artefact under
 * test; a real device/CI build is still the visual proof).
 *
 * What is asserted here is what the port changed: borderless intent /
 * notification lines, borderless dock without subtitles, 48dp touch
 * targets, hidden-by-default keypad, and no colour outside colors.xml.
 * Runtime overrides are documented in LOOP_LOG.md, not hidden by this
 * test: config font sizes (hud 11sp, omni 12.5sp, dock 12.5sp defaults)
 * still win over XML text sizes at runtime, and the [GO] button and
 * the single identity badge are deliberately retained (see log).
 */
class UiV2CockpitTest {

    private fun layoutText(): String {
        val root = findRepoRoot()
        assumeTrue("repo sources not found; see LOOP_LOG", root != null)
        val layout = File(root, "app/src/main/res/layout/activity_void_launcher.xml")
        assumeTrue("layout missing", layout.exists())
        return layout.readText()
    }

    private fun colorsText(): String {
        val root = findRepoRoot()
        assumeTrue("repo sources not found; see LOOP_LOG", root != null)
        val colors = File(root, "app/src/main/res/values/colors.xml")
        assumeTrue("colors.xml missing", colors.exists())
        return colors.readText()
    }

    /** Block from the element with [id] to its closing tag at [indent] spaces. */
    private fun block(text: String, id: String, indent: String): String {
        val idIdx = text.indexOf("android:id=\"@+id/$id\"")
        assertTrue("missing id $id", idIdx >= 0)
        val start = text.lastIndexOf("<LinearLayout", idIdx)
        assertTrue("no LinearLayout before $id", start >= 0)
        val close = text.indexOf("\n$indent</LinearLayout>", idIdx)
        assertTrue("no closing tag for $id", close >= 0)
        return text.substring(start, close)
    }

    @Test
    fun intentAndNotificationLinesAreBorderless() {
        val text = layoutText()
        val intent = block(text, "intentCard", "        ")
        assertFalse("intentCard must not use the cockpit card background", intent.contains("bg_cockpit_card"))
        val notif = block(text, "notificationsCard", "        ")
        assertFalse("notificationsCard must not use the cockpit card background", notif.contains("bg_cockpit_card"))
        assertTrue("notification header must be muted", notif.contains("@color/text_muted"))
        assertTrue("notification header must be 12sp", notif.contains("android:textSize=\"12sp\""))
    }

    @Test
    fun dockIsBorderlessWithoutSubtitlesAnd48dp() {
        val text = layoutText()
        for (n in 1..5) {
            val slot = block(text, "btnSlot$n", "            ")
            assertFalse("btnSlot$n must be borderless", slot.contains("bg_bare_slot"))
            assertTrue("btnSlot$n must be 48dp hit area", slot.contains("android:minHeight=\"48dp\""))
            val textViews = Regex("<TextView").findAll(slot).count()
            assertEquals("btnSlot$n must have label only, no subtitle on home", 1, textViews)
        }
    }

    @Test
    fun omniIs48dpAndKeypadHiddenByDefault() {
        val text = layoutText()
        val omni = block(text, "omniBarLayout", "        ")
        assertTrue("omni bar must be 48dp", omni.contains("android:minHeight=\"48dp\""))
        assertTrue("omni input must be 14sp in XML", omni.contains("android:textSize=\"14sp\""))
        assertTrue("[123] toggle must be 48dp", omni.contains("android:minHeight=\"48dp\""))
        assertTrue("[123] toggle must be surface + 1px border", omni.contains("@drawable/bg_digit_btn"))
        val keypadIdx = text.indexOf("android:id=\"@+id/digitKeypadRow\"")
        assertTrue("digitKeypadRow missing", keypadIdx >= 0)
        val keypadHead = text.substring(keypadIdx, minOf(keypadIdx + 400, text.length))
        assertTrue("keypad row must be hidden by default", keypadHead.contains("android:visibility=\"gone\""))
    }

    @Test
    fun hudLineUsesSpecTokens() {
        val text = layoutText()
        assertTrue("battery must be text_secondary", text.contains("android:id=\"@+id/tvBatteryTemp\""))
        val battIdx = text.indexOf("android:id=\"@+id/tvBatteryTemp\"")
        val battHead = text.substring(battIdx, minOf(battIdx + 500, text.length))
        assertTrue("battery must be text_secondary", battHead.contains("@color/text_secondary"))
        val memIdx = text.indexOf("android:id=\"@+id/tvMemory\"")
        val memHead = text.substring(memIdx, minOf(memIdx + 500, text.length))
        assertTrue("memory must be text_secondary 12sp", memHead.contains("@color/text_secondary") && memHead.contains("android:textSize=\"12sp\""))
    }

    @Test
    fun layoutUsesOnlyColorsFromColorsXml() {
        val text = layoutText()
        val colors = colorsText()
        val used = Regex("@color/([A-Za-z0-9_]+)").findAll(text).map { it.groupValues[1] }.toSet()
        assertTrue("layout must reference colours", used.isNotEmpty())
        val missing = used.filter { !colors.contains("<color name=\"$it\"") }
        assertTrue("layout references undefined colours: $missing", missing.isEmpty())
    }

    private fun findRepoRoot(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (dir == null) return null
            if (File(dir, "app/src/main/java/com/polymath/voidlauncher/core/BareIntegerRouter.kt").exists()) {
                return dir
            }
            dir = dir?.parentFile
        }
        return null
    }
}
