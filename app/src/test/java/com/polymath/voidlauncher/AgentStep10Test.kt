package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.NotifIntelligence
import com.polymath.voidlauncher.model.NotificationItem
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * impl-06 step 10: notification depth — score reasons (10.1) + tap-only
 * actions (10.3). Scoring numbers are unchanged from the pre-step-10
 * behaviour; only the reason is newly surfaced.
 *
 * NOT built (product-owner decisions, recorded not taken):
 * - 10.2 per-persona notification rules (scope would be filter only,
 *   never auto-dismiss).
 * - 10.4 quiet hours / focus filtering of the displayed list
 *   (default off, filter only, never dismiss system notifications).
 */
class AgentStep10Test {

    private val engine = NotifIntelligence()

    private fun item(key: String, pkg: String, title: String, text: String, postTime: Long) =
        NotificationItem(key = key, packageName = pkg, appName = pkg, title = title, text = text, postTimeMs = postTime)

    @Test
    fun reasonComposesWhitelistKeywordAndRecency() {
        val now = System.currentTimeMillis()
        val scored = engine.scoreWithReasons(
            items = listOf(item("1", "com.example.agent", "Build failed", "see log", now)),
            whitelist = listOf("com.example.agent"),
            blacklist = emptyList(),
            priorityKeywords = listOf("failed"),
            filterOngoing = false,
            maxCount = 5
        )
        assertEquals(1, scored.size)
        // 1.0 base + 5 whitelist + 3 keyword + 1 recent = 10.0, unchanged.
        assertEquals(10.0f, scored[0].score, 0.001f)
        assertTrue(scored[0].reason.contains("whitelist +5"))
        assertTrue(scored[0].reason.contains("keyword 'failed' +3"))
        assertTrue(scored[0].reason.contains("recent +1"))
    }

    @Test
    fun reasonOmitsAbsentBoostsAndOldItemsLoseRecency() {
        val old = System.currentTimeMillis() - 3_600_000L
        val scored = engine.scoreWithReasons(
            items = listOf(item("1", "com.chat", "Hi", "hello", old)),
            whitelist = emptyList(),
            blacklist = emptyList(),
            priorityKeywords = listOf("failed"),
            filterOngoing = false,
            maxCount = 5
        )
        assertEquals(1.0f, scored[0].score, 0.001f)
        assertEquals("base 1.0", scored[0].reason)
    }

    @Test
    fun scoreAndFilterStillAgreesWithScoredOrder() {
        val now = System.currentTimeMillis()
        val items = listOf(
            item("low", "com.chat", "Hi", "hello", now),
            item("high", "com.example.agent", "Build failed", "x", now)
        )
        val plain = engine.scoreAndFilter(items, listOf("com.example.agent"), emptyList(), listOf("failed"), false, 5)
        val scored = engine.scoreWithReasons(items, listOf("com.example.agent"), emptyList(), listOf("failed"), false, 5)
        assertEquals(scored.map { it.item.key }, plain.map { it.key })
        assertEquals("high", scored[0].item.key)
    }

    @Test
    fun adapterShowsReasonOnLongPressAndMapsActions() {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val adapter = File(root, "app/src/main/java/com/polymath/voidlauncher/ui/NotificationAdapter.kt").readText()
        assertTrue(adapter.contains("tvNotifReason"))
        assertTrue(adapter.contains("setOnLongClickListener"))
        assertTrue(adapter.contains("layoutNotifActions"))
        assertTrue(adapter.contains("action.intent?.send()"))
        // Dismiss stays per-tap; no bulk/auto dismiss added here.
        assertFalse(adapter.contains("dismissAll"))
        val listener = File(root, "app/src/main/java/com/polymath/voidlauncher/core/VoidNotificationListenerService.kt").readText()
        assertTrue(listener.contains("notif.actions"))
        assertTrue(listener.contains("NotifAction"))
        val activity = File(root, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt").readText()
        assertTrue(activity.contains("scoreWithReasons"))
        // 10.2 / 10.4 not built: the flow still uses global config rules.
        assertTrue(activity.contains("config.notifWhitelistPackages"))
    }

    private fun findRepoRoot(): File? {
        var dir: File? = File("").absoluteFile
        var hops = 0
        while (dir != null && hops < 6) {
            if (File(dir, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt").exists()) return dir
            dir = dir.parentFile
            hops++
        }
        return null
    }
}
