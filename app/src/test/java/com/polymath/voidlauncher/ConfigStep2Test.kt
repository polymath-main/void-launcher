package com.polymath.voidlauncher

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import com.polymath.voidlauncher.core.ConfigValidator

/**
 * Area 07 step 2 — validation is warning-only, never a load gate.
 * Pure-validator tests run for real in the JVM harness; the
 * ConfigManager wiring (lastValidationWarnings, lastSaveWasFallback,
 * backupCurrentStore) is Android-bound, so it is asserted by
 * source-text here and needs CI/device verification for behaviour.
 */
class ConfigStep2Test {

    @Test
    fun cleanConfigProducesNoWarnings() {
        val warnings = ConfigValidator.validate(JSONObject("""{"theme_id":"void","drawer_width_percent":85,"thermal_limit_celsius":39.0,"colors":{"accent":"#E11D48"}}"""))
        assertTrue("clean config must warn nothing, got $warnings", warnings.isEmpty())
    }

    @Test
    fun unknownKeyIsNamedNotSilent() {
        val warnings = ConfigValidator.validate(JSONObject("""{"theme_idd":"void"}"""))
        assertTrue(warnings.any { it.key == "theme_idd" })
    }

    @Test
    fun badHexAndOutOfRangeAndUnknownEnumAreAllNamed() {
        val warnings = ConfigValidator.validate(JSONObject("""
            {"colors":{"accent":"red"},"drawer_width_percent":250,
             "default_home_mode":"BOGUS","gesture_swipe_up":"FLY",
             "thermal_limit_celsius":99.0,"app_icon_style":"sparkle"}
        """.trimIndent()))
        val keys = warnings.map { it.key }.toSet()
        assertTrue("colors.accent" in keys)
        assertTrue("drawer_width_percent" in keys)
        assertTrue("default_home_mode" in keys)
        assertTrue("gesture_swipe_up" in keys)
        assertTrue("thermal_limit_celsius" in keys)
        assertTrue("app_icon_style" in keys)
    }

    @Test
    fun futureSchemaVersionWarnsButIsNotAGate() {
        val warnings = ConfigValidator.validate(JSONObject("""{"schema_version":99}"""))
        assertTrue(warnings.any { it.key == "schema_version" })
        // Warning-only: the validator has no reject/fail path.
        assertEquals(1, warnings.size)
    }

    @Test
    fun knownKeysDoNotWarn() {
        // Every key the serializer writes must be known to the
        // validator, or valid configs would warn spuriously.
        val json = JSONObject()
        for (key in ConfigValidator.knownTopLevelKeys) json.put(key, 1)
        val warnings = ConfigValidator.validate(json)
        val unknown = warnings.filter { it.message.contains("Unknown key") }
        assertTrue("known keys must not warn as unknown: $unknown", unknown.isEmpty())
    }

    private fun read(rel: String): String {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val file = File(root, rel)
        assumeTrue("$rel missing", file.exists())
        return file.readText()
    }

    @Test
    fun configManagerWiresValidationAndFallbackVisibility() {
        val text = read("app/src/main/java/com/polymath/voidlauncher/core/ConfigManager.kt")
        assertTrue(text.contains("lastValidationWarnings = if (json != null) ConfigValidator.validate(json)"))
        assertTrue(text.contains("lastSaveWasFallback = true"))
        assertTrue(text.contains("fun backupCurrentStore()"))
        assertTrue(text.contains("config.json.bak"))
    }

    private fun findRepoRoot(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (dir == null) return null
            if (File(dir, "app/src/main/java/com/polymath/voidlauncher/core/BareIntegerRouter.kt").exists()) return dir
            dir = dir?.parentFile
        }
        return null
    }
}
