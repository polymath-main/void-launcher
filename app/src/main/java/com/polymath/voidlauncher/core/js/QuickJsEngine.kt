package com.polymath.voidlauncher.core.js

/**
 * QuickJS engine backend (impl-04 step 7a; native module landed
 * on feature/quickjs-engine-real).
 *
 * The native library `voidquickjs` lives in app/src/main/cpp
 * (voidquickjs.c + vendored QuickJS 2024-01-13, built via
 * CMake/NDK) and implements the `Void.*` host API with manifest
 * permission checks in native code and the interrupt-handler
 * timeout. Host calls from JS go back through JNI into the
 * [JsAdapters] passed to [run] — which is why [nativeRun] takes
 * the adapters as an argument; the earlier skeleton omitted it,
 * which made the engine uncallable, and that omission is what
 * this module fixes.
 *
 * [isAvailable] is false when the APK was built without the
 * native library (e.g. the JVM unit-test harness, which has no
 * .so). [run] then returns [JsResult.Unavailable] — `:void-js
 * run` reports that, it does not fake execution. Do not remove
 * this gate or stub a fake success here.
 *
 * Native contract: `nativeRun(source, manifestJson, timeoutMs,
 * adapters)` returns a result string the Kotlin side maps to
 * [JsResult]: OK:<output> | ERR:<reason> | DENIED:<permission> |
 * TIMEOUT:<ms>. The native side enforces the same manifest
 * permissions as [PermissionCheckingAdapters] — defence in
 * depth, not instead of it.
 */
class QuickJsEngine : JsEngine {

    override val displayName: String = "QuickJS 2024-01-13 (JNI)"

    private val nativeLoaded: Boolean = try {
        System.loadLibrary("voidquickjs")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    } catch (_: SecurityException) {
        false
    }

    override val isAvailable: Boolean = nativeLoaded

    override fun run(source: String, manifest: JsMacroManifest, adapters: JsAdapters): JsResult {
        if (!nativeLoaded) {
            return JsResult.Unavailable(
                "QuickJS native library 'voidquickjs' is not built into this APK yet " +
                    "(see QuickJsEngine docs: the native module is a separate CI/device step)"
            )
        }
        // Reached only when the native library actually loaded.
        return try {
            val resultJson = nativeRun(source, manifestJson(manifest), manifest.timeoutMs, adapters)
            parseNativeResult(resultJson)
        } catch (e: Exception) {
            JsResult.Failure("QuickJS native run failed: ${e.message}")
        }
    }

    private fun manifestJson(manifest: JsMacroManifest): String {
        val perms = manifest.permissions.joinToString(",") { "\"${it.name.lowercase()}\"" }
        return "{\"name\":\"${manifest.name}\",\"permissions\":[$perms],\"timeout_ms\":${manifest.timeoutMs}}"
    }

    private fun parseNativeResult(json: String): JsResult {
        // The native side returns one of: OK:<output> | ERR:<reason> |
        // DENIED:<permission> | TIMEOUT:<ms>. Keep this tiny and explicit;
        // a malformed native result is a failure, never a success.
        return when {
            json.startsWith("OK:") -> JsResult.Success(json.removePrefix("OK:"))
            json.startsWith("DENIED:") -> JsResult.PermissionDenied(json.removePrefix("DENIED:"))
            json.startsWith("TIMEOUT") -> JsResult.Timeout(
                json.removePrefix("TIMEOUT").removePrefix(":").toLongOrNull() ?: -1
            )
            json.startsWith("ERR:") -> JsResult.Failure(json.removePrefix("ERR:"))
            else -> JsResult.Failure("malformed QuickJS native result")
        }
    }

    private external fun nativeRun(source: String, manifestJson: String, timeoutMs: Long, adapters: JsAdapters): String
}
