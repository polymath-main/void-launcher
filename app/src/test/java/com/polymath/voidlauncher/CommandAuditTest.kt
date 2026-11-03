package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.CommandAudit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** impl-04 step 8: redaction, ordering, and bounding of the audit log. */
class CommandAuditTest {

    @Test
    fun redactsSecretsBeforeWriting() {
        val dir = createTempDir("audit")
        val audit = CommandAudit(File(dir, "audit.jsonl"))
        audit.record("SHELL", ":run curl -H 'Authorization: Bearer abc123' https://x", "OK", 0, 0)
        audit.record("OMNI", ":eval token=supersecret123", "OK")
        val raw = File(dir, "audit.jsonl").readText()
        assertFalse(raw.contains("abc123"))
        assertFalse(raw.contains("supersecret123"))
        assertTrue(raw.contains("Bearer ***"))
        assertTrue(raw.contains("token=***"))
    }

    @Test
    fun recentIsNewestFirstAndBounded() {
        val dir = createTempDir("audit2")
        val audit = CommandAudit(File(dir, "audit.jsonl"), maxEntries = 3)
        audit.record("OMNI", "one", "OK")
        audit.record("OMNI", "two", "OK")
        audit.record("OMNI", "three", "OK")
        audit.record("OMNI", "four", "OK")
        val entries = audit.recent(10)
        assertEquals(3, entries.size)
        assertEquals("four", entries[0].input)
        assertEquals("two", entries[2].input)
    }

    @Test
    fun shellOutputIsNeverWritten() {
        val dir = createTempDir("audit3")
        val audit = CommandAudit(File(dir, "audit.jsonl"))
        audit.record("SHELL", ":run echo secret-output-text", "OK", 0, 0)
        // Only the command text is stored; output text is never an
        // audit field, so a distinctive output string cannot appear
        // unless it was also in the command itself.
        val entries = audit.recent()
        assertEquals(1, entries.size)
        assertEquals(":run echo secret-output-text", entries[0].input)
        assertEquals(0, entries[0].exitCode)
    }
}
