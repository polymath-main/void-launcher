package com.polymath.voidlauncher.core.js

/**
 * QuickJS engine backend (impl-04 step 7a, skeleton + availability gate).
 *
 * HONEST LIMIT, READ BEFORE CLAIMING JS MACROS RUN ON DEVICE:
 * the native library `voidquickjs` (QuickJS 2024-01-13 C sources
 * built via CMake/NDK, with the `Void.*` host API, manifest
 * permission checks in native code, and the interrupt-handler
 * timeout) is NOT in this repo and NOT in the APK yet. Building
 * it is a CI/device step that this JVM-only loop cannot verify:
 * Gradle on this VM is blocked and there is no device here.
 *
 * Until that native module lands, [isAvailable] is false and
 * [run] returns [JsResult.Unavailable] — `:void-js run` reports
 * that, it does not fake execution. The runner, manifest,
 * permission, sandbox, trigger, and command-surface code in this
 * package are real and JVM-tested with a fake engine; they do not
 * depend on this class being available. Do not remove this gate
 * or stub a fake success here.
 *
 * Native contract for the future module (from the prototype
 * `src/void_macro.c`): `nativeRun(source, manifestJson, timeoutMs)`
 * returns a JSON-ish result string the Kotlin side maps to
 * [JsResult]; host calls from JS go back through JNI into the
 * [JsAdapters] passed to [run]. The native side must enforce the
 * same manifest permissions as [PermissionCheckingAdapters] —
 * defence in depth, not instead of it.
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
            val resultJson = nativeRun(source, manifestJson(manifest), manifest.timeoutMs)
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
        // DENIED:<permission> | TIMEOUT. Keep this tiny and explicit;
        // a malformed native result is a failure, never a success.
        return when {
            json.startsWith("OK:") -> JsResult.Success(json.removePrefix("OK:"))
            json.startsWith("DENIED:") -> JsResult.PermissionDenied(json.removePrefix("DENIED:"))
            json.startsWith("TIMEOUT") -> JsResult.Timeout(-1)
            json.startsWith("ERR:") -> JsResult.Failure(json.removePrefix("ERR:"))
            else -> JsResult.Failure("malformed QuickJS native result")
        }
    }

    private external fun nativeRun(source: String, manifestJson: String, timeoutMs: Long): String
}
