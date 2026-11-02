package com.polymath.voidlauncher.core

import com.polymath.voidlauncher.core.shell.ShellPort

import android.content.Context
import java.util.LinkedList

/**
 * VoidTerm Embedded Panel (F-14).
 *
 * Coordinates an in-cockpit terminal line buffer, enabling direct
 * command dispatch and live output streaming without switching apps.
 */
class VoidTermPanel(
    private val context: Context,
    private val shellPort: ShellPort
) {

    companion object {
        private const val DEFAULT_MAX_LINES = 100
    }

    private val lineBuffer = LinkedList<String>()
    var maxLines: Int = DEFAULT_MAX_LINES
    var isVisible: Boolean = false

    /** Visible viewport height in rows (config: `voidterm_rows`). */
    var rows: Int = 8
        set(value) {
            field = value.coerceAtLeast(1)
            // Keep a bounded scrollback of a few screens.
            maxLines = field * 4
        }

    /** Terminal column width used for soft-wrapping output (config: `voidterm_cols`). */
    var cols: Int = 48
        set(value) {
            field = value.coerceAtLeast(8)
        }

    fun appendOutput(text: String) {
        for (raw in text.split("\n")) {
            for (line in wrapLine(raw)) {
                lineBuffer.add(line)
            }
        }
        while (lineBuffer.size > maxLines) {
            lineBuffer.removeFirst()
        }
    }

    /** Soft-wraps a single logical line to the configured column width. */
    private fun wrapLine(line: String): List<String> {
        if (cols <= 0 || line.length <= cols) return listOf(line)
        val chunks = mutableListOf<String>()
        var i = 0
        while (i < line.length) {
            val end = (i + cols).coerceAtMost(line.length)
            chunks.add(line.substring(i, end))
            i = end
        }
        return chunks
    }

    fun execute(command: String, inBackground: Boolean = true) {
        appendOutput(">_ $command")
        shellPort.dispatchCommand(command, inBackground = inBackground)
        if (inBackground) {
            appendOutput("[DISPATCHED HEADLESS TO TERMUX]")
        }
    }

    fun getRenderedBuffer(): String {
        return lineBuffer.joinToString("\n")
    }

    fun clear() {
        lineBuffer.clear()
    }
}
