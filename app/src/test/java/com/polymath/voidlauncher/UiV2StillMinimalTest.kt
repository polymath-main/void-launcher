package com.polymath.voidlauncher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * UI v2 Cycle 3: STILL + MINIMAL port assertions, as layout/activity
 * source-text checks (same JVM-harness pattern as UiV2CockpitTest —
 * no Android layout inflater here, so the XML/activity source is the
 * artefact under test; a device/CI build is still the visual proof).
 *
 * MINIMAL has no new XML of its own: it is the visibility set
 * (omni + dock only), so it is asserted on the Activity source, not
 * the layout. STILL keeps its own still_* colour tokens (existing
 * STILL system) rather than the preset text tokens — see LOOP_LOG.
 */
class UiV2StillMinimalTest {

    private fun read(rel: String): String {
        val root = findRepoRoot()
        assumeTrue("repo sources not found; see LOOP_LOG", root != null)
        val file = File(root, rel)
        assumeTrue("$rel missing", file.exists())
        return file.readText()
    }

    private fun layoutText() = read("app/src/main/res/layout/activity_void_launcher.xml")
    private fun activityText() = read("app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt")

    /** Function body from "private fun NAME(" to the next top-level private fun. */
    private fun functionBody(text: String, name: String): String {
        val start = text.indexOf("private fun $name(")
        assertTrue("missing function $name", start >= 0)
        val next = text.indexOf("\n    private fun ", start + 10)
        return if (next >= 0) text.substring(start, next) else text.substring(start)
    }

    @Test
    fun stillClockIs20spLetterSpacedWith48dpTopMargin() {
        val text = layoutText()
        val clockIdx = text.indexOf("android:id=\"@+id/tvStillClock\"")
        assertTrue("tvStillClock missing", clockIdx >= 0)
        val head = text.substring(clockIdx, minOf(clockIdx + 600, text.length))
        assertTrue("STILL clock must be the 20sp system ceiling", head.contains("android:textSize=\"20sp\""))
        assertTrue("STILL clock must be letter-spaced", head.contains("android:letterSpacing=\"0.08\""))
        val stillIdx = text.indexOf("android:id=\"@+id/stillHomeLayout\"")
        val padIdx = text.indexOf("android:paddingTop=\"48dp\"", stillIdx)
        assertTrue("STILL container must have 48dp top margin", padIdx >= 0 && padIdx - stillIdx < 1500)
    }

    @Test
    fun stillDateAndStatusUseSpecSizes() {
        val text = layoutText()
        val dateIdx = text.indexOf("android:id=\"@+id/tvStillDate\"")
        assertTrue("tvStillDate missing", dateIdx >= 0)
        assertTrue(
            "STILL date must be 14sp",
            text.substring(dateIdx, minOf(dateIdx + 600, text.length)).contains("android:textSize=\"14sp\"")
        )
        val statusIdx = text.indexOf("android:id=\"@+id/tvStillStatus\"")
        assertTrue("tvStillStatus missing", statusIdx >= 0)
        assertTrue(
            "STILL status must be 12sp",
            text.substring(statusIdx, minOf(statusIdx + 600, text.length)).contains("android:textSize=\"12sp\"")
        )
    }

    @Test
    fun stillFavoritesAreSlots135AsTextList48dp() {
        val activity = activityText()
        val favs = functionBody(activity, "rebuildStillFavorites")
        assertTrue(
            "STILL favorites must be slots 1/3/5 only",
            favs.contains("filter { it.index in setOf(1, 3, 5) }")
        )
        assertTrue("STILL favorite rows must be 48dp", favs.contains("dpToPx(48)"))
        assertTrue(
            "STILL favorite label must carry its dock index",
            favs.contains("text = \"\${slot.index} · \${slot.label}\"")
        )
        assertTrue("STILL favorites must not be cards", !favs.contains("cornerRadius = dpToPxF(config.cockpitCardRadiusDp)"))
    }

    @Test
    fun minimalShowsOmniAndDockOnly() {
        val activity = activityText()
        val mode = functionBody(activity, "applyHomeMode")
        // Dock/toggle follow config in MINIMAL (not forced GONE):
        // applyConfig (1) + MINIMAL branch (1) + gesture path (1) = 3.
        val dockConditional = "binding.bareIntegerDock.visibility = if (config.showDock) View.VISIBLE else View.GONE"
        assertEquals(
            "dock must follow showDock in applyConfig, MINIMAL branch, and gesture path",
            3, activity.split(dockConditional).size - 1
        )
        // Toggle conditional: applyConfig (1) + COCKPIT (1) + STILL (1)
        // + MINIMAL (1) + gesture path (1) = 5.
        val toggleConditional = "binding.btnToggleKeypad.visibility = if (config.showDigitKeypad) View.VISIBLE else View.GONE"
        assertEquals(
            "keypad toggle must follow showDigitKeypad everywhere",
            5, activity.split(toggleConditional).size - 1
        )
        // HUD line hidden in STILL + MINIMAL branches (2), gesture path (1).
        val hudHide = "(binding.tvClock.parent as? View)?.visibility = View.GONE"
        assertEquals("HUD line must be hidden in STILL and MINIMAL branches", 2, mode.split(hudHide).size - 1)
        val gesture = functionBody(activity, "toggleMinimalMode")
        assertTrue("gesture MINIMAL must hide the HUD line too", gesture.contains(hudHide))
        // MINIMAL branch still hides the cards and the STILL layout.
        val minimalIdx = mode.indexOf("\"MINIMAL\"")
        assertTrue("MINIMAL branch missing", minimalIdx >= 0)
        val minimalBranch = mode.substring(minimalIdx, minOf(minimalIdx + 1200, mode.length))
        assertTrue("MINIMAL must hide telemetry card", minimalBranch.contains("binding.telemetryCard.visibility = View.GONE"))
        assertTrue("MINIMAL must hide intent card", minimalBranch.contains("binding.intentCard.visibility = View.GONE"))
        assertTrue("MINIMAL must hide notifications card", minimalBranch.contains("binding.notificationsCard.visibility = View.GONE"))
        assertTrue("MINIMAL must hide STILL layout", minimalBranch.contains("binding.stillHomeLayout.visibility = View.GONE"))
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
