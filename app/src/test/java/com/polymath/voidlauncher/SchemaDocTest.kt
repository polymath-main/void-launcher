package com.polymath.voidlauncher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Area 07 step 8 — schema doc must not rot like ARCHITECTURE.md
 * did. Every LauncherConfig field must have a row in docs/SCHEMA.md,
 * and every row must name a real field (both directions, so a
 * stale row fails too). Regenerate with
 * scripts/generate-schema-doc.py after changing a field.
 */
class SchemaDocTest {

    @Test
    fun schemaDocMatchesLauncherConfigFieldForField() {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val src = File(root, "app/src/main/java/com/polymath/voidlauncher/model/LauncherConfig.kt")
        val doc = File(root, "docs/SCHEMA.md")
        assumeTrue("LauncherConfig.kt missing", src.exists())
        assumeTrue("docs/SCHEMA.md missing — regenerate it", doc.exists())
        val srcText = src.readText()
        val configBlock = srcText.substringAfter("data class LauncherConfig(")
            .substringBefore("\n) {")
        val fields = Regex("""^\s*val\s+(\w+)\s*:""", RegexOption.MULTILINE)
            .findAll(configBlock).map { it.groupValues[1] }.toSet()
        assertTrue("no fields parsed", fields.isNotEmpty())
        val docText = doc.readText()
        val rows = Regex("""^\| `(\w+)` \|""", RegexOption.MULTILINE)
            .findAll(docText).map { it.groupValues[1] }.toSet()
        val missing = fields - rows
        val stale = rows - fields - setOf(
            // ColorPalette rows live in the colors.* table, not the
            // LauncherConfig block; they are checked by name below.
            "background", "surface", "surfacePressed", "border", "borderActive",
            "textPrimary", "textSecondary", "textMuted", "accent", "accentSurface",
            "secondary", "secondarySurface", "safe", "warning", "critical")
        assertEquals("fields without a SCHEMA.md row: $missing", emptySet<String>(), missing)
        assertEquals("SCHEMA.md rows naming no field: $stale", emptySet<String>(), stale)
        assertTrue("deprecated thermalKillCommands must be flagged",
            docText.contains("`thermalKillCommands`") && docText.contains("DEPRECATED"))
    }

    private fun findRepoRoot(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (dir == null) return null
            if (File(dir, "app/src/main/java/com/polymath/voidlauncher/core/BareIntegerRouter.kt").exists()) return dir
            dir = dir?.parentFile
        }
        return null
    }
}
