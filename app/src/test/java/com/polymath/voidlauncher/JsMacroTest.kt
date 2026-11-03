package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.js.FileJsStorage
import com.polymath.voidlauncher.core.js.JsAdapters
import com.polymath.voidlauncher.core.js.JsApp
import com.polymath.voidlauncher.core.js.JsEngine
import com.polymath.voidlauncher.core.js.JsMacroManifest
import com.polymath.voidlauncher.core.js.JsMacroRunner
import com.polymath.voidlauncher.core.js.JsResult
import com.polymath.voidlauncher.core.js.JsSandbox
import com.polymath.voidlauncher.core.js.JsTelemetry
import com.polymath.voidlauncher.core.js.QuickJsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * impl-04 step 7a tests. These test the runner, manifest, permission
 * wrapper, and sandbox — NOT JS execution itself. The fake engine
 * below is a test double that calls adapters when the source
 * contains a marker (e.g. `CALL:files.read`); it is not a JS
 * interpreter and must never be mistaken for one. Real JS
 * execution needs the QuickJS native library, which is absent
 * here (QuickJsEngine reports unavailable — asserted below).
 */
class JsMacroTest {

    /** Test double: interprets markers in the source as adapter calls. */
    private class FakeEngine(
        private val calls: MutableList<String> = mutableListOf()
    ) : JsEngine {
        override val displayName: String = "Fake (test double)"
        override val isAvailable: Boolean = true

        override fun run(
            source: String,
            manifest: JsMacroManifest,
            adapters: JsAdapters
        ): JsResult {
            return try {
                if ("CALL:files.read" in source) adapters.filesRead("note.txt")
                if ("CALL:files.write" in source) adapters.filesWrite("out.txt", "hi")
                if ("CALL:shell" in source) adapters.log("shell-marker")
                if ("CALL:notify" in source) adapters.notify("t", "b")
                if ("CALL:apps" in source) adapters.appsList()
                if ("CALL:storage" in source) adapters.storageSet("k", "v")
                if ("CALL:slow" in source) Thread.sleep(5000)
                JsResult.Success("fake-ok")
            } catch (e: Exception) {
                throw e
            }
        }
    }

    private class RecordingAdapters : JsAdapters {
        val calls = mutableListOf<String>()
        val storage = mutableMapOf<String, String>()
        override fun log(message: String) { calls.add("log") }
        override fun notify(title: String, body: String) { calls.add("notify") }
        override fun setMode(name: String) { calls.add("mode") }
        override fun telemetry(): JsTelemetry = JsTelemetry(50, 100, 200, 10)
        override fun appsList(): List<JsApp> = listOf(JsApp("com.example", "Example"))
        override fun appsSearch(query: String): List<JsApp> = appsList()
        override fun appsLaunch(packageName: String): Boolean = true
        override fun filesList(path: String): List<String> = emptyList()
        override fun filesRead(path: String): String { calls.add("files.read"); return "data" }
        override fun filesWrite(path: String, content: String) { calls.add("files.write") }
        override fun shellRun(command: String): String { calls.add("shell"); return "shell-ok" }
        override fun storageGet(key: String): String? = storage[key]
        override fun storageSet(key: String, value: String) { storage[key] = value; calls.add("storage") }
        override fun sleep(ms: Long) { calls.add("sleep") }
    }

    private fun writeJsMacro(dir: File, name: String, manifestLine: String?, body: String): File {
        dir.mkdirs()
        val file = File(dir, "$name.js")
        file.writeText(((manifestLine?.let { "$it\n" }) ?: "") + body)
        return file
    }

    @Test
    fun manifestDefaultsDenyFilesShellApps() {
        val m = JsMacroManifest.parse("Void.log(\"hi\");", "plain")
        assertTrue(JsMacroManifest.Permission.NOTIFY in m.permissions)
        assertFalse(JsMacroManifest.Permission.FILES in m.permissions)
        assertFalse(JsMacroManifest.Permission.SHELL in m.permissions)
        assertFalse(JsMacroManifest.Permission.APPS in m.permissions)
        assertEquals(5000L, m.timeoutMs)
    }

    @Test
    fun manifestParsesPermissionsAndClampsTimeout() {
        val m = JsMacroManifest.parse(
            "/* void-macro {\"name\":\"x\",\"permissions\":[\"files\",\"shell\"],\"timeout_ms\":999999} */\nVoid.log(1);",
            "x"
        )
        assertTrue(JsMacroManifest.Permission.FILES in m.permissions)
        assertTrue(JsMacroManifest.Permission.SHELL in m.permissions)
        assertEquals(60000L, m.timeoutMs)
    }

    @Test
    fun permissionWrapperDeniesFilesWithoutPermission() {
        val dir = createTempDir("jsperm")
        val adapters = RecordingAdapters()
        val runner = JsMacroRunner(FakeEngine(), { _, _ -> adapters })
        writeJsMacro(dir, "reader", null, "CALL:files.read")
        val outcome = runner.run("reader", dir.absolutePath)
        assertTrue(outcome.result is JsResult.PermissionDenied)
        assertFalse("files.read" in adapters.calls)
    }

    @Test
    fun permissionWrapperAllowsGrantedPermission() {
        val dir = createTempDir("jsallow")
        val adapters = RecordingAdapters()
        val runner = JsMacroRunner(FakeEngine(), { _, _ -> adapters })
        writeJsMacro(
            dir, "writer",
            "/* void-macro {\"name\":\"writer\",\"permissions\":[\"files\",\"notify\"],\"timeout_ms\":5000} */",
            "CALL:files.write"
        )
        val outcome = runner.run("writer", dir.absolutePath)
        assertTrue(outcome.result is JsResult.Success)
        assertTrue("files.write" in adapters.calls)
    }

    @Test
    fun sandboxDeniesEscapesAndShellMetachars() {
        val root = createTempDir("jswork")
        assertEquals(null, JsSandbox.resolveFile(root.absolutePath, "../../etc/passwd"))
        assertEquals(null, JsSandbox.resolveFile(root.absolutePath, "/etc/passwd"))
        assertTrue(JsSandbox.resolveFile(root.absolutePath, "note.txt") != null)
        assertTrue(JsSandbox.checkShell("echo hi") == null)
        assertTrue(JsSandbox.checkShell("rm -rf /") != null)
        assertTrue(JsSandbox.checkShell("echo hi; cat /etc/passwd") != null)
        assertTrue(JsSandbox.checkShell("ls ..") != null)
    }

    @Test
    fun runnerTimeoutIsEnforced() {
        val dir = createTempDir("jstimeout")
        val runner = JsMacroRunner(FakeEngine(), { _, _ -> RecordingAdapters() })
        writeJsMacro(
            dir, "slow",
            "/* void-macro {\"name\":\"slow\",\"permissions\":[],\"timeout_ms\":1000} */",
            "CALL:slow"
        )
        val outcome = runner.run("slow", dir.absolutePath)
        assertTrue(outcome.result is JsResult.Timeout)
    }

    @Test
    fun quickJsEngineReportsUnavailableWithoutNativeLib() {
        val engine = QuickJsEngine()
        assertFalse(engine.isAvailable)
        val result = engine.run("Void.log(1);", JsMacroManifest.defaults("x"), RecordingAdapters())
        assertTrue(result is JsResult.Unavailable)
    }

    @Test
    fun fileStorageRoundTrips() {
        val dir = createTempDir("jsstorage")
        val storage = FileJsStorage(dir.absolutePath, "m")
        storage.set("count", "3")
        assertEquals("3", storage.get("count"))
        assertEquals(null, storage.get("missing"))
    }

    @Test
    fun triggerAttachmentsAreExplicitOnly() {
        val dir = createTempDir("jstrig")
        assertFalse(com.polymath.voidlauncher.core.js.JsTriggerStore.isAttached(dir.absolutePath, "morning", "scheduled"))
        assertTrue(com.polymath.voidlauncher.core.js.JsTriggerStore.attach(dir.absolutePath, "morning", "scheduled"))
        assertTrue(com.polymath.voidlauncher.core.js.JsTriggerStore.isAttached(dir.absolutePath, "morning", "scheduled"))
        assertFalse(com.polymath.voidlauncher.core.js.JsTriggerStore.isAttached(dir.absolutePath, "morning", "notification:x"))
        assertTrue(com.polymath.voidlauncher.core.js.JsTriggerStore.detach(dir.absolutePath, "morning", "scheduled"))
        assertFalse(com.polymath.voidlauncher.core.js.JsTriggerStore.attach(dir.absolutePath, "../../x", "scheduled"))
    }

    @Test
    fun jsMacrosLiveOutsideStringMacroDir() {
        val dir = createTempDir("jsdir")
        val runner = JsMacroRunner(FakeEngine(), { _, _ -> RecordingAdapters() })
        writeJsMacro(dir, "morning", null, "Void.log(1);")
        assertEquals(listOf("morning"), runner.listMacros(dir.absolutePath))
        assertTrue(runner.deleteMacro("morning", dir.absolutePath))
        assertFalse(runner.deleteMacro("../../x", dir.absolutePath))
    }
}
