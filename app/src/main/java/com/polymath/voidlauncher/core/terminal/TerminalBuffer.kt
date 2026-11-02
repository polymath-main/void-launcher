package com.polymath.voidlauncher.core.terminal

/**
 * TerminalBuffer — pure line-mode terminal model (refined plan, Phase 2).
 *
 * Bounded scrollback with soft-wrapping at [cols]. No Android imports,
 * so it is unit-testable on the JVM. VoidTermPanel delegates to this
 * class in Cycle 3; the Activity-facing API of VoidTermPanel is unchanged.
 */
class TerminalBuffer(
    rows: Int = 8,
    cols: Int = 48
) {
    var rows: Int = rows.coerceAtLeast(1)
        set(value) {
            field = value.coerceAtLeast(1)
            // Keep a bounded scrollback of a few screens, as before.
            maxLines = field * 4
        }

    var cols: Int = cols.coerceAtLeast(8)
        set(value) {
            field = value.coerceAtLeast(8)
        }

    var maxLines: Int = rows.coerceAtLeast(1) * 4
        private set

    private val lines = ArrayDeque<String>()

    fun append(text: String) {
        for (raw in text.split("\n")) {
            for (line in wrapLine(raw)) {
                lines.addLast(line)
            }
        }
        while (lines.size > maxLines) {
            lines.removeFirst()
        }
    }

    fun clear() {
        lines.clear()
    }

    fun allLines(): List<String> = lines.toList()

    /** Last [rows] lines — what a line-mode terminal view shows. */
    fun viewport(): List<String> = lines.takeLast(rows)

    fun rendered(): String = lines.joinToString("\n")

    fun renderedViewport(): String = viewport().joinToString("\n")

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
}
