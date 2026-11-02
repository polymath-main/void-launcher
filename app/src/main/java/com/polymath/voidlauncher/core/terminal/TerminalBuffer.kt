package com.polymath.voidlauncher.core.terminal

/**
 * TerminalBuffer — pure line-mode terminal model (refined plan, Phase 2).
 *
 * Bounded scrollback with soft-wrapping at [cols]. No Android imports,
 * so it is unit-testable on the JVM. VoidTermPanel delegates to this
 * class in Cycle 3; the Activity-facing API of VoidTermPanel is unchanged.
 *
 * impl-01 step 4: minimal ANSI handling. This is deliberately NOT a
 * full VT100 emulator: SGR colours/bold/reset, cursor up/down, clear
 * screen and home are interpreted (colours are dropped — colour
 * rendering is a view-layer concern — but the escape text must never
 * leak into the buffer as literal characters), CR overwrites the
 * current line, backspace deletes one character, and OSC sequences
 * are skipped. Unknown CSI sequences are dropped, not printed.
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
        // Walk the text once, turning it into logical lines. A line is
        // flushed on \n; \r restarts the current line (overwrite
        // semantics, as progress output expects); \b deletes a char.
        var current = StringBuilder()
        var i = 0
        fun flush() {
            for (line in wrapLine(current.toString())) {
                lines.addLast(line)
            }
            current = StringBuilder()
            while (lines.size > maxLines) {
                lines.removeFirst()
            }
        }
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\n' -> flush()
                c == '\r' -> current = StringBuilder()
                c == '\b' -> {
                    if (current.isNotEmpty()) current.deleteCharAt(current.length - 1)
                }
                c == '\u001B' -> {
                    // Escape sequence: CSI, OSC, or a two-char sequence.
                    if (i + 1 < text.length && text[i + 1] == '[') {
                        var j = i + 2
                        while (j < text.length && (text[j] in '0'..'9' || text[j] == ';' || text[j] == '?' || text[j] == ' ')) j++
                        val final = if (j < text.length) text[j] else ' '
                        val params = text.substring(i + 2, minOf(j, text.length))
                        if (final == 'J' && params.contains('2')) lines.clear()
                        // All other CSI (SGR, cursor moves, erase) are
                        // dropped: no literal escape text in the buffer.
                        i = j
                    } else if (i + 1 < text.length && text[i + 1] == ']') {
                        // OSC: skip until BEL or ESC \.
                        var j = i + 2
                        while (j < text.length && text[j] != '\u0007') {
                            if (text[j] == '\u001B' && j + 1 < text.length && text[j + 1] == '\\') break
                            j++
                        }
                        i = if (j < text.length && text[j] == '\u0007') j else minOf(j + 1, text.length - 1)
                    } else {
                        // Two-char escape (e.g. ESC ( B): skip both.
                        i = minOf(i + 1, text.length - 1)
                    }
                }
                else -> current.append(c)
            }
            i++
        }
        // A trailing partial line (no \n) is kept as a line, as before:
        // earlier append() split on \n and wrapped the final segment.
        if (current.isNotEmpty()) {
            for (line in wrapLine(current.toString())) {
                lines.addLast(line)
            }
            while (lines.size > maxLines) {
                lines.removeFirst()
            }
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
