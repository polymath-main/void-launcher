package com.polymath.voidlauncher.core

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
    private val termuxIpc: TermuxIpcDispatcher
) {

    companion object {
        private const val DEFAULT_MAX_LINES = 100
    }

    private val lineBuffer = LinkedList<String>()
    var maxLines: Int = DEFAULT_MAX_LINES
    var isVisible: Boolean = false

    fun appendOutput(text: String) {
        val lines = text.split("\n")
        for (line in lines) {
            lineBuffer.add(line)
        }
        while (lineBuffer.size > maxLines) {
            lineBuffer.removeFirst()
        }
    }

    fun execute(command: String, inBackground: Boolean = true) {
        appendOutput(">_ $command")
        termuxIpc.dispatchCommand(command, inBackground = inBackground)
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
