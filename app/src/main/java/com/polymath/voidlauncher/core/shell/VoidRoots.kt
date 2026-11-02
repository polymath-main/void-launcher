package com.polymath.voidlauncher.core.shell

import java.io.File

/**
 * One documented Void root vocabulary (impl-01 step 5). Three roots
 * coexist and must not be conflated:
 *
 * - **Config home** — `VoidPaths.HOME` (app external files "config"):
 *   config.json, macros, widgets, personas, workflow.json. Android-only
 *   (needs Context to init), so it is NOT defined here.
 * - **Files home** — `filesDir/void`: the launcher-owned workspace the
 *   files browser shows and `:work <project>` opens. Defined here in
 *   pure form (same rule as FileManager.voidHomePath() and
 *   FileShim.voidHome), so it is unit-testable without Android.
 * - **Shell default cwd** — today the app's internal `filesDir`
 *   (Activity passes it to VoidShell). It is NOT the files home and
 *   NOT the config home. Moving it to the files home is a behaviour
 *   change for existing users and is deliberately NOT done in this
 *   step (plan S-5 risk: needs a config key / migration note and a
 *   Shuvo decision, plan §4.6 Q1); it is documented here instead of
 *   silently changed.
 */
object VoidRoots {

    /** Files home under [filesDir], created on first use. */
    fun filesHome(filesDir: File): File {
        val dir = File(filesDir, "void")
        try {
            if (!dir.exists()) dir.mkdirs()
        } catch (_: Exception) {
        }
        return dir
    }

    /** True when [path] is [root] itself or lives under it (canonical). */
    fun isUnder(path: String, root: File): Boolean {
        return try {
            val rootPath = root.canonicalPath
            val canonical = File(path).canonicalPath
            canonical == rootPath || canonical.startsWith(rootPath + File.separator)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Resolves a project name to a directory under the files home.
     * Returns null for a blank name or for any name that escapes the
     * files home (e.g. "..", "a/../..", absolute paths) — callers
     * must fail loudly on null instead of opening an arbitrary dir.
     * The directory need not exist yet: `:work` / openProject may
     * create it on first use, as the files browser does.
     */
    fun resolveProject(filesDir: File, projectName: String): File? {
        val name = projectName.trim()
        if (name.isEmpty()) return null
        val home = filesHome(filesDir)
        val resolved = File(home, name)
        return if (isUnder(resolved.absolutePath, home)) resolved else null
    }
}
