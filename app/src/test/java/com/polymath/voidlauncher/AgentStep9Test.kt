package com.polymath.voidlauncher

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** impl-06 step 9: SAF legacy import (copy-only) + blueprint fixes. */
class AgentStep9Test {

    @Test
    fun safImportIsCopyOnlyAndVerifiable() {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val svc = File(root, "app/src/main/java/com/polymath/voidlauncher/core/services/LegacyImportService.kt").readText()
        assertTrue(svc.contains("OpenDocumentTree") || svc.contains("DocumentsContract"))
        assertTrue(svc.contains("copyDocument"))
        assertTrue(svc.contains("MAX_FILE_BYTES"))
        assertTrue(svc.contains("renameTo"))
        assertFalse("sources are never deleted", svc.contains("source.delete"))
        assertFalse("no move semantics", svc.contains("Files.move"))
        val activity = File(root, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt").readText()
        assertTrue(activity.contains("legacyImportLauncher"))
        assertTrue(activity.contains("command == \"legacy import\""))
        assertTrue(activity.contains("command == \"legacy verify\""))
        assertTrue(activity.contains("fun verifyLegacyDb"))
        assertTrue(activity.contains("queryTopology(config.nodeosDbPath)"))
        // With legacy import off, bridge paths are blanked (step 1 + step 6 gate intact).
        assertTrue(activity.contains("nodeOsBridge.workflowPath = \"\""))
        assertTrue(activity.contains("nodeOsBridge.nodeosDbPath = \"\""))
    }

    @Test
    fun blueprint06DriftIsFixed() {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val doc = File(root, "docs/blueprints/06_AGENT_WIDGETS_PERSONA.md").readText()
        // No Termux LINKS remain (the doc names com.termux once, in the
        // sentence describing the removal itself — that is not a link).
        assertFalse("no Termux private-path links", doc.contains("](file:///data/data/com.termux"))
        assertFalse("no VoidShim-as-handler claim", doc.contains("VoidShim handles") || doc.contains("nodeos -c ping` path"))
        assertTrue(doc.contains("VoidShim does not exist"))
        assertTrue(doc.contains("does not install"))
        assertTrue(doc.contains("SCHEDULED while the deck is open") || doc.contains("deck-open-only"))
        assertTrue(doc.contains("[AGENT]"))
        assertTrue(doc.contains("UNDECIDED"))
        assertTrue(doc.contains("copy-only") || doc.contains("COPIED"))
        // I1: with legacy import off, no display path reads the legacy paths.
        // (Covered by the blanking assertion above; the doc states it too.)
        assertTrue(doc.contains("with it off no code path reads those paths"))
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
