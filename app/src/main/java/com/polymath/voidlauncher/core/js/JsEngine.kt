package com.polymath.voidlauncher.core.js

/**
 * JS engine interface (impl-04 step 7a).
 *
 * One implementation runs a script through QuickJS (see
 * [QuickJsEngine]); tests and the JVM harness use a fake. The
 * interface deliberately says nothing about QuickJS: the runner
 * enforces manifest permissions, sandboxing, and the wall-clock
 * timeout around whatever engine is plugged in, so swapping the
 * engine cannot silently drop a gate.
 *
 * No DOM, no network globals, no remote code loading: an engine
 * must not expose `fetch`, `XMLHttpRequest`, `WebSocket`, or DOM
 * objects. Adding any of those is a separate, explicitly approved
 * security design (plan gate S6), not an engine detail.
 */
interface JsEngine {
    /** Human-readable engine identity for `:void-js` output. */
    val displayName: String

    /** True if this engine can actually execute a script here. */
    val isAvailable: Boolean

    fun run(source: String, manifest: JsMacroManifest, adapters: JsAdapters): JsResult
}

sealed class JsResult {
    data class Success(val output: String) : JsResult()
    data class Failure(val reason: String) : JsResult()
    data class PermissionDenied(val permission: String) : JsResult()
    data class Timeout(val timeoutMs: Long) : JsResult()
    data class Unavailable(val reason: String) : JsResult()
}

/** Telemetry snapshot handed to JS macros (BatteryManager on Android). */
data class JsTelemetry(
    val batteryPct: Int,
    val memFreeMB: Long,
    val memTotalMB: Long,
    val uptimeSec: Long
)

/** One app entry for `Void.apps` (PackageManager on Android). */
data class JsApp(val packageName: String, val label: String)

/**
 * Host adapters a JS macro may call through `Void.*`. Every method
 * here is already permission-gated by [PermissionCheckingAdapters]
 * before it reaches the real implementation: an engine must never
 * call the delegate directly.
 */
interface JsAdapters {
    fun log(message: String)
    fun notify(title: String, body: String)
    fun setMode(name: String)
    fun telemetry(): JsTelemetry
    fun appsList(): List<JsApp>
    fun appsSearch(query: String): List<JsApp>
    fun appsLaunch(packageName: String): Boolean
    fun filesList(path: String): List<String>
    fun filesRead(path: String): String
    fun filesWrite(path: String, content: String)
    fun storageGet(key: String): String?
    fun storageSet(key: String, value: String)
    fun sleep(ms: Long)
}

/** Thrown by the permission wrapper; the runner converts it to [JsResult.PermissionDenied]. */
class JsPermissionDeniedException(val permission: String) :
    RuntimeException("permission denied: $permission")

/**
 * Wraps a delegate adapter set and throws [JsPermissionDeniedException]
 * for any call outside [manifest] permissions. `log` and `sleep` need
 * no permission (prototype behaviour: log is unpermissioned, sleep
 * is capped by the caller, not by a permission).
 */
class PermissionCheckingAdapters(
    private val delegate: JsAdapters,
    private val manifest: JsMacroManifest
) : JsAdapters {

    private fun require(permission: JsMacroManifest.Permission) {
        if (permission !in manifest.permissions) {
            throw JsPermissionDeniedException(permission.name.lowercase())
        }
    }

    override fun log(message: String) = delegate.log(message)
    override fun notify(title: String, body: String) {
        require(JsMacroManifest.Permission.NOTIFY)
        delegate.notify(title, body)
    }

    override fun setMode(name: String) {
        require(JsMacroManifest.Permission.MODE)
        delegate.setMode(name)
    }

    override fun telemetry(): JsTelemetry {
        require(JsMacroManifest.Permission.TELEMETRY)
        return delegate.telemetry()
    }

    override fun appsList(): List<JsApp> {
        require(JsMacroManifest.Permission.APPS)
        return delegate.appsList()
    }

    override fun appsSearch(query: String): List<JsApp> {
        require(JsMacroManifest.Permission.APPS)
        return delegate.appsSearch(query)
    }

    override fun appsLaunch(packageName: String): Boolean {
        require(JsMacroManifest.Permission.APPS)
        return delegate.appsLaunch(packageName)
    }

    override fun filesList(path: String): List<String> {
        require(JsMacroManifest.Permission.FILES)
        return delegate.filesList(path)
    }

    override fun filesRead(path: String): String {
        require(JsMacroManifest.Permission.FILES)
        return delegate.filesRead(path)
    }

    override fun filesWrite(path: String, content: String) {
        require(JsMacroManifest.Permission.FILES)
        delegate.filesWrite(path, content)
    }

    override fun storageGet(key: String): String? {
        require(JsMacroManifest.Permission.STORAGE)
        return delegate.storageGet(key)
    }

    override fun storageSet(key: String, value: String) {
        require(JsMacroManifest.Permission.STORAGE)
        delegate.storageSet(key, value)
    }

    override fun sleep(ms: Long) {
        // Prototype cap: 5,000 ms per call, enforced here so no
        // engine can sleep past it by calling the delegate directly.
        delegate.sleep(ms.coerceIn(0L, 5000L))
    }
}
