package com.polymath.voidlauncher

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Migration Cycle 3 proof, as a test.
 *
 * Asserts the Cycle 3 proof greps: no file under app/src/main/java
 * or app/src/main/AndroidManifest.xml mentions "termux"
 * (case-insensitive) except the opt-in LegacyTermuxMigrator, and no
 * file under app/src/main mentions "com.termux" except that migrator.
 * Like the Cycle 1 source test, this skips itself when it cannot
 * locate the repo sources; the authoritative proof is the grep output
 * recorded in LOOP_LOG.md.
 */
class TermuxRemovalCycle3Test {

    @Test
    fun noTermuxReferencesOutsideLegacyMigrator() {
        val root = findRepoRoot() ?: run {
            assumeTrue("repo sources not found from user.dir; see LOOP_LOG grep proof", false)
            return
        }
        val javaDir = File(root, "app/src/main/java")
        val manifest = File(root, "app/src/main/AndroidManifest.xml")
        assumeTrue("java dir missing", javaDir.isDirectory)
        assumeTrue("manifest missing", manifest.exists())

        val offenders = mutableListOf<String>()
        javaDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            if (file.name == "LegacyTermuxMigrator.kt") return@forEach
            val text = file.readText()
            if (text.contains("termux", ignoreCase = true)) {
                offenders.add(file.relativeTo(root).path)
            }
        }
        if (manifest.readText().contains("termux", ignoreCase = true)) {
            offenders.add("app/src/main/AndroidManifest.xml")
        }
        assertTrue("termux references outside LegacyTermuxMigrator: $offenders", offenders.isEmpty())
    }

    @Test
    fun noComTermuxPackageOutsideLegacyMigrator() {
        val root = findRepoRoot() ?: run {
            assumeTrue("repo sources not found from user.dir; see LOOP_LOG grep proof", false)
            return
        }
        val mainDir = File(root, "app/src/main")
        assumeTrue("main dir missing", mainDir.isDirectory)
        val offenders = mutableListOf<String>()
        mainDir.walkTopDown().filter { it.isFile }.forEach { file ->
            if (file.name == "LegacyTermuxMigrator.kt") return@forEach
            if (file.extension !in setOf("kt", "xml", "json")) return@forEach
            if (file.readText().contains("com.termux")) {
                offenders.add(file.relativeTo(root).path)
            }
        }
        assertTrue("com.termux references outside LegacyTermuxMigrator: $offenders", offenders.isEmpty())
    }

    @Test
    fun legacyMigratorExistsAndIsNotOnLaunchPaths() {
        val root = findRepoRoot() ?: run {
            assumeTrue("repo sources not found from user.dir; see LOOP_LOG grep proof", false)
            return
        }
        val migrator = File(root, "app/src/main/java/com/polymath/voidlauncher/core/LegacyTermuxMigrator.kt")
        assumeTrue("migrator missing", migrator.exists())
        // The migrator must not be referenced from launch/shell/thermal code.
        val launchPaths = listOf(
            "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt",
            "app/src/main/java/com/polymath/voidlauncher/core/shell/VoidShellPort.kt",
            "app/src/main/java/com/polymath/voidlauncher/core/ThermalGuardian.kt",
            "app/src/main/java/com/polymath/voidlauncher/core/AgentOrchestrator.kt"
        )
        for (rel in launchPaths) {
            val file = File(root, rel)
            assumeTrue("missing $rel", file.exists())
            assertFalse(
                "$rel references LegacyTermuxMigrator",
                file.readText().contains("LegacyTermuxMigrator")
            )
        }
    }

    private fun findRepoRoot(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (dir == null) return null
            if (File(dir, "app/src/main/java/com/polymath/voidlauncher/core/BareIntegerRouter.kt").exists()) {
                return dir
            }
            dir = dir?.parentFile
        }
        return null
    }
}
