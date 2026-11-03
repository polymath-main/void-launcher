package com.polymath.voidlauncher

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Area 07 step 6 — CI changes (source-text; the CI file itself
 * cannot run on this VM, and no CI run is dispatched by agents).
 */
class ConfigStep6Test {

    private fun read(rel: String): String {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val file = File(root, rel)
        assumeTrue("$rel missing", file.exists())
        return file.readText()
    }

    @Test
    fun ciKeepsLoadBearingFlagAndAddsCheapChecks() {
        val gradle = read("app/build.gradle")
        assertTrue("returnDefaultValues must stay (CI stub Log)", gradle.contains("unitTests.returnDefaultValues = true"))
        val yml = read(".github/workflows/build.yml")
        assertTrue(yml.contains("actions/setup-java@v5"))
        assertTrue(yml.contains("lintDebug"))
        assertTrue(yml.contains("continue-on-error: true"))
        assertTrue(yml.contains("check-version-consistency.sh"))
        assertTrue(yml.contains("sha256sum"))
        assertTrue(yml.contains("void-launcher-v3.0.0-release-debug-signed"))
        assertTrue("old unlabelled release artifact name must be gone", !yml.contains("name: void-launcher-v3.0.0-release\n"))
        assertTrue("emulator milestone must be recorded, not claimed done", yml.contains("OPEN CI MILESTONE"))
        val script = read("scripts/check-version-consistency.sh")
        assertTrue(script.contains("build.gradle"))
        assertTrue(script.contains("ARCHITECTURE.md"))
        assertTrue("version check must not compare against tags", script.contains("NOT compared against git tags"))
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
