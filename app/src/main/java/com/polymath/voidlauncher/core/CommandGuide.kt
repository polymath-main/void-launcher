package com.polymath.voidlauncher.core

/**
 * Table-driven `:help` reference (impl-04 step 0).
 *
 * The old `formatQuickGuide()` was a hard-coded string that rotted:
 * it said dock 1–5 (dock is 1–7) and omitted commands. Every dispatch
 * command the Activity handles must have one row here; adding a
 * command without a row is a review failure, and `:help` is generated
 * from this table so it cannot drift again. Pure Kotlin, no Android
 * imports, so the JVM harness can assert help completeness.
 *
 * Rows are grouped for display; [names] holds every alias that
 * reaches the same behaviour (e.g. `wallpaper` and `wp`).
 */
object CommandGuide {

    data class Entry(val names: List<String>, val usage: String, val description: String)

    val ENTRIES: List<Entry> = listOf(
        Entry(listOf("help", "?"), ":help", "This guide"),
        Entry(listOf("wallpaper", "wp"), ":wallpaper [studio|on|off|pick|set <path>|status|clear]", "Wallpaper studio and modes"),
        Entry(listOf("themes", "theme"), ":theme list|apply <id>|save <name>|export|import <uri|path>", "Theme library"),
        Entry(listOf("eval"), ":eval <math>", "Arithmetic, hex, binary, time"),
        Entry(listOf("task"), ":task <name> <cmd>", "Dispatch an agent task (agent gate applies)"),
        Entry(listOf("tasks"), ":tasks", "List agent tasks (agent gate applies)"),
        Entry(listOf("kill"), ":kill <id>", "Cancel an agent task (agent gate applies, impl-04)"),
        Entry(listOf("graph"), ":graph", "NodeOS AST graph (ast gate applies)"),
        Entry(listOf("persona", "personas"), ":persona", "List personas and the active one"),
        Entry(listOf("persona edit"), ":persona edit", "Manage personas without editing JSON"),
        Entry(listOf("id"), ":id <name>", "Switch persona"),
        Entry(listOf("record"), ":record <name>", "Record a string macro (macros gate applies)"),
        Entry(listOf("stop"), ":stop", "Stop recording and save the macro"),
        Entry(listOf("play"), ":play <name>", "Play a string macro (macros gate applies)"),
        Entry(listOf("macros"), ":macros [list|delete <name>]", "List or delete string macros (impl-04)"),
        Entry(listOf("history", "hist"), ":history", "Recent command history (history gate applies)"),
        Entry(listOf("history delete"), ":history delete <id>", "Delete one history entry by id (impl-04)"),
        Entry(listOf("snippet"), ":snippet <alias> <cmd>", "Save a snippet alias"),
        Entry(listOf("snippet delete"), ":snippet delete <alias>", "Delete a snippet alias (impl-04)"),
        Entry(listOf("snippets"), ":snippets", "List snippet aliases"),
        Entry(listOf("term"), ":term <cmd>", "Run in the VoidTerm panel (voidterm gate applies)"),
        Entry(listOf("power"), ":power", "Telemetry snapshot and power profile"),
        Entry(listOf("focus"), ":focus", "Apply the active focus schedule"),
        Entry(listOf("focus edit"), ":focus edit", "Edit the focus schedule without editing JSON"),
        Entry(listOf("mode"), ":mode <COCKPIT|STILL|MINIMAL|FILES>", "Switch home mode"),
        Entry(listOf("still", "cockpit", "minimal"), ":still | :cockpit | :minimal", "Direct mode switches"),
        Entry(listOf("run"), ":run <cmd>", "Run a shell command via VoidShell"),
        Entry(listOf("sh"), ":sh <cmd>", "Alias for :run"),
        Entry(listOf("!"), ":! <cmd>", "Explicit shell escape hatch (impl-04; a typo'd :command no longer runs shell)"),
        Entry(listOf("work"), ":work <project>", "Open a Void workspace project in Files"),
        Entry(listOf("default", "home"), ":default", "Request default-launcher role"),
        Entry(listOf("notif", "notifications", "alerts"), ":notif", "Open notification-access settings"),
        Entry(listOf("files", "ls"), ":files", "Toggle the Files browser"),
        Entry(listOf("apps", "drawer"), ":apps", "Toggle the app drawer"),
        Entry(listOf("settings", "config", "cfg"), ":settings", "Open Settings"),
        Entry(listOf("clip"), ":clip", "Show clipboard text (display only, never executed)"),
        Entry(listOf("nodeos", "ping"), ":nodeos", "Void daemon task status (NodeOS legacy import is opt-in only)"),
        Entry(listOf("hud", "status"), ":hud", "Void status"),
        Entry(listOf("deck", "widgets"), ":deck", "Open the widget deck"),
        Entry(listOf("cmd", "cc", "search"), ":cmd", "Open the command center"),
        Entry(listOf("reload"), ":reload", "Reload config from disk"),
        Entry(listOf("void-js"), ":void-js list|run <name>|stop <name>", "QuickJS macros (impl-04 step 7; engine availability is reported, not faked)"),
        Entry(listOf("audit"), ":audit", "Command audit log (impl-04 step 8)"),
        Entry(listOf("<alias>"), ":<alias>", "Run a saved snippet alias"),
        Entry(listOf("##"), "<cmd> ## <alias>", "Save an inline snippet alias"),
        Entry(listOf("1..7"), "<digit>", "Bare integer routes to dock slot 1..7"),
    )

    /** All dispatch names, for unknown-command suggestions. */
    val ALL_NAMES: List<String> = ENTRIES.flatMap { it.names }

    fun formatQuickGuide(): String {
        val sb = StringBuilder("=== VOID LAUNCHER QUICK GUIDE ===\n\n")
        sb.appendLine("• APPS       Type app name to search, or tap the right edge for App Drawer")
        sb.appendLine("• DOCK       Tap [1]..[7] to open slot; LONG-PRESS any slot to customize")
        sb.appendLine("• PERSONA    Tap [VOID] badge to cycle; LONG-PRESS to pick mode/persona")
        sb.appendLine()
        for (e in ENTRIES) {
            sb.appendLine("• ${e.usage.padEnd(52)} ${e.description}")
        }
        sb.appendLine()
        sb.appendLine("Shell: :run / :sh / :! run VoidShell. Any other unknown :command")
        sb.appendLine("does NOT run shell — it prints an unknown-command message with")
        sb.appendLine("suggestions. Use :! explicitly when you mean shell.")
        return sb.toString().trimEnd()
    }

    /** Simple suggestions for an unknown colon command: prefix matches, then edit distance <= 2. */
    fun suggest(unknown: String, limit: Int = 3): List<String> {
        val u = unknown.lowercase()
        if (u.isEmpty()) return emptyList()
        val prefix = ALL_NAMES.filter { it.startsWith(u) && it != u }
        if (prefix.isNotEmpty()) return prefix.distinct().take(limit)
        return ALL_NAMES.distinct()
            .map { it to levenshtein(u, it.lowercase()) }
            .filter { it.second in 1..2 }
            .sortedBy { it.second }
            .map { it.first }
            .take(limit)
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            )
        }
        return dp[a.length][b.length]
    }
}
