package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.VoidTermPanel
import com.polymath.voidlauncher.core.shell.ShellPort
import com.polymath.voidlauncher.core.terminal.TerminalBuffer
import com.polymath.voidlauncher.core.terminal.TerminalCommandRunner
import com.polymath.voidlauncher.core.terminal.TerminalRunResult
import com.polymath.voidlauncher.core.terminal.TerminalSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalAnsiSessionsTest {

    @Test
    fun sgrSequencesDoNotLeakAsLiteralText() {
        val buf = TerminalBuffer(rows = 8, cols = 48)
        buf.append("\u001B[31mred\u001B[0m\n")
        assertEquals(listOf("red"), buf.allLines())
    }

    @Test
    fun boldAndResetAndCursorSequencesAreDropped() {
        val buf = TerminalBuffer(rows = 8, cols = 48)
        buf.append("\u001B[1mbold\u001B[0m \u001B[2Kplain\n")
        assertEquals(listOf("bold plain"), buf.allLines())
    }

    @Test
    fun carriageReturnOverwritesCurrentLine() {
        val buf = TerminalBuffer(rows = 8, cols = 48)
        buf.append("old\rnew\n")
        assertEquals(listOf("new"), buf.allLines())
    }

    @Test
    fun backspaceDeletesOneChar() {
        val buf = TerminalBuffer(rows = 8, cols = 48)
        buf.append("ab\bc\n")
        assertEquals(listOf("ac"), buf.allLines())
    }

    @Test
    fun clearScreenEmptiesBuffer() {
        val buf = TerminalBuffer(rows = 8, cols = 48)
        buf.append("before\n")
        buf.append("\u001B[2Jafter\n")
        assertEquals(listOf("after"), buf.allLines())
    }

    @Test
    fun sessionsKeepSeparateBuffersAndHistory() {
        val first = TerminalSession(id = "one")
        val second = TerminalSession(id = "two")
        val runner = TerminalCommandRunner { TerminalRunResult("out\n", "", 0) }
        first.execute("echo a", runner)
        second.execute("echo b", runner)
        assertTrue(first.buffer.allLines().contains("out"))
        assertTrue(second.buffer.allLines().contains("out"))
        assertEquals(listOf("echo a"), first.history)
        assertEquals(listOf("echo b"), second.history)
        // Same output text in both, but prompt lines differ per session.
        assertTrue(first.buffer.allLines().contains(">_ echo a"))
        assertFalse(first.buffer.allLines().contains(">_ echo b"))
    }

    @Test
    fun panelSessionsSwitchAndClose() {
        val port = object : ShellPort {
            override fun dispatchCommand(command: String, workDir: String, inBackground: Boolean) = true
            override fun launchInteractive(workDir: String) {}
            override fun openProject(projectName: String) {}
        }
        val panel = VoidTermPanel(port)
        panel.terminalRunner = TerminalCommandRunner { TerminalRunResult("hi\n", "", 0) }
        panel.execute("echo main")
        panel.createSession("second")
        assertTrue(panel.switchSession("second"))
        panel.execute("echo second")
        assertTrue(panel.getRenderedBuffer().contains(">_ echo second"))
        assertFalse(panel.getRenderedBuffer().contains(">_ echo main"))
        assertTrue(panel.switchSession(VoidTermPanel.DEFAULT_SESSION_ID))
        assertTrue(panel.getRenderedBuffer().contains(">_ echo main"))
        assertEquals(listOf(VoidTermPanel.DEFAULT_SESSION_ID, "second"), panel.listSessions())
        assertTrue(panel.closeSession("second"))
        assertFalse(panel.switchSession("second"))
        // Last session cannot be closed.
        assertFalse(panel.closeSession(VoidTermPanel.DEFAULT_SESSION_ID))
        assertFalse(panel.switchSession("nope"))
    }

    @Test
    fun ansiOutputThroughSessionDoesNotLeakEscapes() {
        val session = TerminalSession(id = "test")
        val runner = TerminalCommandRunner { TerminalRunResult("\u001B[32mgreen\u001B[0m\n", "", 0) }
        session.execute("printf coloured", runner)
        assertFalse(session.buffer.rendered().contains("\u001B"))
        assertTrue(session.buffer.allLines().contains("green"))
    }
}
