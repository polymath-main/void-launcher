package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.terminal.TerminalBuffer
import com.polymath.voidlauncher.core.terminal.TerminalCommandRunner
import com.polymath.voidlauncher.core.terminal.TerminalRunResult
import com.polymath.voidlauncher.core.terminal.TerminalSession
import com.polymath.voidlauncher.core.terminal.TerminalSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalTest {

    @Test
    fun bufferWrapsAtCols() {
        // cols has a minimum of 8, as in the old VoidTermPanel.
        val buf = TerminalBuffer(rows = 8, cols = 8)
        buf.append("abcdefghij")
        assertEquals(listOf("abcdefgh", "ij"), buf.allLines())
    }

    @Test
    fun bufferCapsScrollbackAtRowsTimesFour() {
        val buf = TerminalBuffer(rows = 2, cols = 48)
        for (i in 1..20) buf.append("line $i")
        assertEquals(8, buf.allLines().size)
        assertEquals("line 13", buf.allLines().first())
    }

    @Test
    fun viewportIsLastRows() {
        val buf = TerminalBuffer(rows = 2, cols = 48)
        buf.append("a\nb\nc")
        assertEquals(listOf("b", "c"), buf.viewport())
        assertEquals("b\nc", buf.renderedViewport())
    }

    @Test
    fun sessionAppendsPromptAndRealOutput() {
        val session = TerminalSession(id = "test")
        val runner = TerminalCommandRunner { TerminalRunResult("hello\n", "", 0) }
        val result = session.execute("echo hello", runner)
        assertEquals(0, result.exitCode)
        assertTrue(session.buffer.allLines().contains(">_ echo hello"))
        assertTrue(session.buffer.allLines().contains("hello"))
        assertEquals(TerminalSessionState.Exited(0), session.state)
        assertEquals(listOf("echo hello"), session.history)
    }

    @Test
    fun sessionMarksNonZeroExit() {
        val session = TerminalSession(id = "test")
        val runner = TerminalCommandRunner { TerminalRunResult("", "boom", 3) }
        session.execute("false", runner)
        assertEquals(TerminalSessionState.Exited(3), session.state)
        assertTrue(session.buffer.allLines().contains("stderr: boom"))
        assertTrue(session.buffer.allLines().contains("[exit 3]"))
    }

    @Test
    fun sessionBlankCommandFailsWithoutRunning() {
        var called = false
        val runner = TerminalCommandRunner {
            called = true
            TerminalRunResult("", "", 0)
        }
        val session = TerminalSession(id = "test")
        session.execute("   ", runner)
        assertTrue(!called)
        assertTrue(session.state is TerminalSessionState.Failed)
    }
}
