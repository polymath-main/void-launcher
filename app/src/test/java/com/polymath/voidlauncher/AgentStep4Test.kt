package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.WidgetPolicy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** impl-06 step 4: widget safety policy (pure) + wiring assertions. */
class AgentStep4Test {

    @Test
    fun clampsRefreshAndHeight() {
        assertEquals(5_000L, WidgetPolicy.clampRefreshIntervalMs(100L))
        assertEquals(600_000L, WidgetPolicy.clampRefreshIntervalMs(10_000_000L))
        assertEquals(30_000L, WidgetPolicy.clampRefreshIntervalMs(30_000L))
        assertEquals(48, WidgetPolicy.clampHeightDp(10))
        assertEquals(240, WidgetPolicy.clampHeightDp(1000))
        assertEquals(120, WidgetPolicy.clampHeightDp(120))
    }

    @Test
    fun colonAndDockActionsNeedApprovalPlainLaunchDoesNot() {
        assertTrue(WidgetPolicy.needsApproval(":task ls"))
        assertTrue(WidgetPolicy.needsApproval(":kill abc12345"))
        assertTrue(WidgetPolicy.needsApproval("3"))
        assertFalse(WidgetPolicy.needsApproval("Chrome"))
        assertFalse(WidgetPolicy.needsApproval(""))
    }

    @Test
    fun approvalKeyIsPerWidgetAndPerAction() {
        val a = WidgetPolicy.approvalKey("w1", ":task ls")
        assertEquals(a, WidgetPolicy.approvalKey("w1", ":task ls"))
        assertNotEquals(a, WidgetPolicy.approvalKey("w2", ":task ls"))
        assertNotEquals(a, WidgetPolicy.approvalKey("w1", ":task pwd"))
    }

    @Test
    fun httpAllowlist() {
        assertTrue(WidgetPolicy.httpAllowed("https://example.com/x", false))
        assertFalse(WidgetPolicy.httpAllowed("http://example.com/x", false))
        assertFalse(WidgetPolicy.httpAllowed("http://example.com/x", true))
        assertTrue(WidgetPolicy.httpAllowed("http://192.168.1.5/status", true))
        assertTrue(WidgetPolicy.httpAllowed("http://10.0.0.2/status", true))
        assertTrue(WidgetPolicy.httpAllowed("http://172.16.0.9/status", true))
        assertFalse(WidgetPolicy.httpAllowed("http://172.32.0.9/status", true))
        assertTrue(WidgetPolicy.httpAllowed("http://localhost:8080/status", true))
        assertTrue(WidgetPolicy.httpAllowed("http://pi.local/status", true))
        assertFalse(WidgetPolicy.httpAllowed("ftp://example.com/x", true))
        assertFalse(WidgetPolicy.httpAllowed("not a url", true))
    }

    @Test
    fun errorContentDetection() {
        assertTrue(WidgetPolicy.isErrorContent("HTTP error: timeout", "HTTP"))
        assertTrue(WidgetPolicy.isErrorContent("HTTP 404: nope", "HTTP"))
        assertFalse(WidgetPolicy.isErrorContent("HTTP 200: ok", "HTTP"))
        assertTrue(WidgetPolicy.isErrorContent("exit 3: boom", "SCRIPT"))
        assertFalse(WidgetPolicy.isErrorContent("hello", "SCRIPT"))
        assertFalse(WidgetPolicy.isErrorContent("hello", "STATIC"))
    }

    @Test
    fun registryAndDeckWirePolicy() {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val registry = File(root, "app/src/main/java/com/polymath/voidlauncher/core/WidgetRegistry.kt").readText()
        assertTrue(registry.contains("WidgetPolicy.clampRefreshIntervalMs"))
        assertTrue(registry.contains("allow_insecure_http"))
        assertTrue(registry.contains("(timed out)"))
        assertTrue(registry.contains("fun approveAction"))
        assertTrue(registry.contains("Blocked widget HTTP fetch"))
        val activity = File(root, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt").readText()
        assertTrue(activity.contains("deckRefreshRunnable"))
        assertTrue(activity.contains("minimumHeight"))
        assertTrue(activity.contains("needsActionApproval"))
        assertTrue(activity.contains("Run widget action?"))
        assertTrue(activity.contains("removeCallbacks(deckRefreshRunnable)"))
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
