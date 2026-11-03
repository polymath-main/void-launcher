package com.polymath.voidlauncher.core

import java.net.URL

/**
 * impl-06 step 4 — pure widget-safety policy (JVM-testable, no Android).
 *
 * - Refresh interval is clamped to 5 s – 10 min; height to 48 – 240 dp.
 *   A widget JSON asking for 100 ms refresh or a 1000 dp card gets the
 *   clamp, never the literal value.
 * - Tap approval: a widget action needs a first-time confirmation unless
 *   it is a plain app launch (app fuzzy-search text). Colon commands
 *   (:task, :sh, :kill, …) and bare-integer dock-slot actions are NOT
 *   plain launches — a dock slot can itself be a run-command slot —
 *   so they need approval. Approval is per (widget id, action hash),
 *   persisted by WidgetRegistry, never a blanket "approve all widgets".
 * - HTTP allowlist: HTTPS always allowed. Plain HTTP only when the
 *   widget opts in (allow_insecure_http) AND the host is a LAN host
 *   (localhost, *.local, or a private IPv4 range) — an untrusted widget
 *   JSON fetching an internal URL is an exfiltration-shaped risk even
 *   though the response is only displayed. Anything else is blocked
 *   (logged, not toasted, by the caller).
 */
object WidgetPolicy {

    const val MIN_REFRESH_MS = 5_000L
    const val MAX_REFRESH_MS = 600_000L
    const val MIN_HEIGHT_DP = 48
    const val MAX_HEIGHT_DP = 240
    const val SCRIPT_TIMEOUT_MS = 10_000L

    fun clampRefreshIntervalMs(ms: Long): Long = ms.coerceIn(MIN_REFRESH_MS, MAX_REFRESH_MS)

    fun clampHeightDp(dp: Int): Int = dp.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)

    /** True when tapping this action must confirm first. */
    fun needsApproval(action: String): Boolean {
        val trimmed = action.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith(":")) return true
        if (trimmed.toIntOrNull() != null) return true
        return false
    }

    fun approvalKey(widgetId: String, action: String): String =
        "$widgetId:${action.hashCode()}"

    fun isLanHost(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || h.endsWith(".local")) return true
        val parts = h.split(".")
        if (parts.size != 4) return false
        val nums = parts.map { it.toIntOrNull() ?: return false }
        return when {
            nums[0] == 10 -> true
            nums[0] == 172 && nums[1] in 16..31 -> true
            nums[0] == 192 && nums[1] == 168 -> true
            else -> false
        }
    }

    fun httpAllowed(url: String, allowInsecureHttp: Boolean): Boolean {
        return try {
            val parsed = URL(url)
            when (parsed.protocol.lowercase()) {
                "https" -> true
                "http" -> allowInsecureHttp && isLanHost(parsed.host)
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Best-effort error detection for deck status text (not a security check). */
    fun isErrorContent(text: String, type: String): Boolean {
        return when {
            text.startsWith("HTTP error:") -> true
            text.startsWith("HTTP ") && !text.startsWith("HTTP 2") -> true
            type.uppercase() == "SCRIPT" &&
                (text.startsWith("exit ") || text.startsWith("VoidShell:")) -> true
            else -> false
        }
    }
}
