package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.CommandGuide
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ui-dispatch-fix-1: real assertions on the CommandGuide table
 * (pure Kotlin, actually executed) — not source-text assertions.
 * The Activity dispatch itself is Android-bound and cannot run in
 * this JVM harness; its proof is the CI build, see UI_AUDIT.md.
 */
class DispatchFixTest {

    private fun entry(name: String) =
        CommandGuide.ENTRIES.firstOrNull { it.names.contains(name) }

    @Test
    fun helpDocumentsLegacyImportAndVerify() {
        val import = entry("legacy import")
        val verify = entry("legacy verify")
        assertTrue("legacy import row missing", import != null)
        assertTrue("legacy verify row missing", verify != null)
        assertTrue(import!!.usage.contains("legacy import"))
        assertTrue(verify!!.usage.contains("legacy verify"))
    }

    @Test
    fun helpDocumentsVoidJsTriggerForms() {
        val js = entry("void-js")
        assertTrue("void-js row missing", js != null)
        assertTrue("triggers form missing from :help", js!!.usage.contains("triggers <name>"))
        assertTrue("fire form missing from :help", js.usage.contains("fire <name> <trigger>"))
        assertTrue("trigger attach/detach missing from :help", js.usage.contains("trigger attach|detach"))
    }

    @Test
    fun helpDocumentsMacrosListAndEveryBareUsageCommandHasAUsage() {
        // :macros already documented [list|delete]; the fix makes
        // the list form actually dispatch, and every bare-arg
        // command in the dispatch fix must have a usage string to
        // print instead of UNKNOWN.
        val macros = entry("macros")
        assertTrue("macros row missing", macros != null)
        assertTrue(macros!!.usage.contains("list"))
        for (name in listOf("eval", "task", "kill", "record", "play", "run", "sh", "mode", "id", "work", "snippet")) {
            val e = entry(name)
            assertTrue("no :help usage for bare :$name", e != null && e.usage.startsWith(":$name"))
        }
    }

    @Test
    fun helpGuideMentionsNewRows() {
        val guide = CommandGuide.formatQuickGuide()
        assertTrue(guide.contains(":legacy import"))
        assertTrue(guide.contains(":legacy verify"))
        assertTrue(guide.contains("trigger attach|detach"))
    }
}
