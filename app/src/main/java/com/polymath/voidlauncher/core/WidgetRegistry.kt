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
                        refreshIntervalMs = json.optLong("refresh_ms", 5000L),
                        heightDp = json.optInt("height_dp", 48),
                        actionOnClick = json.optString("action_on_click", ""),
                        isEnabled = json.optBoolean("enabled", true)
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
            "HTTP" -> fetchHttp(descriptor.source, callback)
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
        io.execute {
            val out = when (val res = voidShell.run(command)) {
                is ShellResult.Success -> {
                    val combined = if (res.error.isNotBlank()) "${res.output}\n${res.error}".trim() else res.output.trim()
                    combined.take(MAX_BODY_CHARS).ifBlank { "(no output)" }
                }
                is ShellResult.Failed -> res.reason.take(MAX_BODY_CHARS)
            }
            deliver(callback, out)
        }
    }

    private fun fetchHttp(url: String, callback: (String) -> Unit) {
        if (url.isBlank()) {
            deliver(callback, "(empty url)")
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
