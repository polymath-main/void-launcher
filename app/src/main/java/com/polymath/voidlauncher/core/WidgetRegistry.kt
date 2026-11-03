package com.polymath.voidlauncher.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.polymath.voidlauncher.core.shell.ShellResult
import com.polymath.voidlauncher.core.shell.VoidShell
import com.polymath.voidlauncher.model.WidgetDescriptor
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * VoidWidget Registry (F-04).
 *
 * Discovers and executes pluggable card widgets declared as
 * `*.widget.json` descriptors in the configured widgets directory.
 *
 * Supported types:
 *  - `STATIC` — renders `source` verbatim.
 *  - `SCRIPT` — runs `source` through [VoidShell] and renders captured stdout/stderr.
 *  - `HTTP`   — GETs `source` with [HttpURLConnection] (no third-party HTTP dependency).
 */
class WidgetRegistry(
    private val context: Context,
    private val voidShell: VoidShell = VoidShell()
) {

    companion object {
        private const val TAG = "WidgetRegistry"
        private const val HTTP_TIMEOUT_MS = 5_000
        private const val MAX_BODY_CHARS = 1_000
    }

    private val widgets = mutableListOf<WidgetDescriptor>()
    private val io = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    // impl-06 step 4: tap approvals, persisted per (widget id, action
    // hash). Plain app launches never consult this store (see
    // WidgetPolicy.needsApproval); a stored approval is never blanket.
    private val approvalPrefs =
        context.getSharedPreferences("void_widget_approvals", Context.MODE_PRIVATE)

    fun needsActionApproval(descriptor: WidgetDescriptor): Boolean {
        if (!WidgetPolicy.needsApproval(descriptor.actionOnClick)) return false
        val key = WidgetPolicy.approvalKey(descriptor.id, descriptor.actionOnClick)
        return !approvalPrefs.getStringSet("approved", emptySet()).orEmpty().contains(key)
    }

    fun approveAction(descriptor: WidgetDescriptor) {
        val key = WidgetPolicy.approvalKey(descriptor.id, descriptor.actionOnClick)
        val set = approvalPrefs.getStringSet("approved", emptySet()).orEmpty().toMutableSet()
        set.add(key)
        approvalPrefs.edit().putStringSet("approved", set).apply()
    }

    fun loadWidgets(directoryPath: String): List<WidgetDescriptor> {
        widgets.clear()
        try {
            val dir = File(directoryPath)
            if (!dir.exists() || !dir.isDirectory) return emptyList()

            val files = dir.listFiles { f -> f.name.endsWith(".widget.json") } ?: return emptyList()
            for (file in files) {
                try {
                    val json = JSONObject(file.readText())
                    val id = json.optString("id", file.nameWithoutExtension)
                    val descriptor = WidgetDescriptor(
                        id = id,
                        title = json.optString("title", id),
                        type = json.optString("type", "SCRIPT"),
                        source = json.optString("source", ""),
                        // impl-06 step 4: clamp at load, so a widget JSON
                        // cannot ask for a 100 ms refresh or 1000 dp card.
                        refreshIntervalMs = WidgetPolicy.clampRefreshIntervalMs(
                            json.optLong("refresh_ms", 5000L)
                        ),
                        heightDp = WidgetPolicy.clampHeightDp(json.optInt("height_dp", 48)),
                        actionOnClick = json.optString("action_on_click", ""),
                        isEnabled = json.optBoolean("enabled", true),
                        allowInsecureHttp = json.optBoolean("allow_insecure_http", false)
                    )
                    if (descriptor.isEnabled) {
                        widgets.add(descriptor)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing widget file ${file.name}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot load widgets from $directoryPath: ${e.message}")
        }
        return widgets
    }

    fun getActiveWidgets(): List<WidgetDescriptor> = widgets

    /**
     * Executes a widget and delivers its rendered content to [callback] on the main thread.
     */
    fun refreshWidget(descriptor: WidgetDescriptor, callback: (String) -> Unit) {
        when (descriptor.type.uppercase()) {
            "SCRIPT" -> runScript(descriptor.source, callback)
            "HTTP" -> fetchHttpChecked(descriptor.source, descriptor.allowInsecureHttp, callback)
            else -> deliver(callback, descriptor.source)
        }
    }

    /** Synchronous best-effort content, used for the initial paint before [refreshWidget] resolves. */
    fun formatWidgetContent(descriptor: WidgetDescriptor): String {
        return when (descriptor.type.uppercase()) {
            "STATIC" -> descriptor.source
            "SCRIPT", "HTTP" -> "… ${descriptor.title}"
            else -> descriptor.source
        }
    }

    private fun runScript(command: String, callback: (String) -> Unit) {
        if (command.isBlank()) {
            deliver(callback, "(empty script)")
            return
        }
        // impl-06 step 4: 10 s deck-side timeout (VoidShell's own
        // timeout is 30 s — the deck must not wait that long). The
        // timeout unblocks the DECK; it does not kill the worker
        // thread, which finishes when VoidShell's own timeout fires.
        // Rendering "(timed out)" instead of hanging the pool is the
        // contract; a true kill would need a shell-area change.
        val future = io.submit<String> {
            when (val res = voidShell.run(command)) {
                is ShellResult.Success -> {
                    val combined = if (res.error.isNotBlank()) "${res.output}\n${res.error}".trim() else res.output.trim()
                    combined.take(MAX_BODY_CHARS).ifBlank { "(no output)" }
                }
                is ShellResult.Failed -> res.reason.take(MAX_BODY_CHARS)
            }
        }
        io.execute {
            val out = try {
                future.get(WidgetPolicy.SCRIPT_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                "(timed out)"
            }
            deliver(callback, out)
        }
    }

    private fun fetchHttpChecked(url: String, allowInsecureHttp: Boolean, callback: (String) -> Unit) {
        if (url.isBlank()) {
            deliver(callback, "(empty url)")
            return
        }
        if (!WidgetPolicy.httpAllowed(url, allowInsecureHttp)) {
            // impl-06 step 4: blocked URLs are LOGGED, never toasted —
            // a widget deck must not spam toasts for untrusted JSON.
            Log.w(TAG, "Blocked widget HTTP fetch (allowlist): $url")
            deliver(callback, "(blocked URL)")
            return
        }
        io.execute {
            var conn: HttpURLConnection? = null
            val body = try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = HTTP_TIMEOUT_MS
                    readTimeout = HTTP_TIMEOUT_MS
                    setRequestProperty("Accept", "application/json, text/plain, */*")
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                "HTTP $code: ${text.take(MAX_BODY_CHARS)}"
            } catch (e: Exception) {
                "HTTP error: ${e.message}"
            } finally {
                try { conn?.disconnect() } catch (_: Exception) {}
            }
            deliver(callback, body)
        }
    }

    private fun deliver(callback: (String) -> Unit, text: String) {
        main.post { callback(text.ifBlank { "(empty)" }) }
    }

    /** Stops background HTTP/script workers (safe to call from `onDestroy`). */
    fun shutdown() {
        try {
            io.shutdownNow()
        } catch (_: Exception) {
        }
    }
}
