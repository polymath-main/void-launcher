package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.shell.DefaultProcessRunner
import com.polymath.voidlauncher.core.shell.ShellResult
import com.polymath.voidlauncher.core.shell.VoidRoots
import com.polymath.voidlauncher.core.shell.VoidShell
import com.polymath.voidlauncher.core.shell.VoidShellPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoidRootsTest {

    @Test
    fun filesHomeIsFilesDirVoid() {
        val tmp = createTempDir("voidroots")
        try {
            assertEquals(File(tmp, "void").absolutePath, VoidRoots.filesHome(tmp).absolutePath)
            assertTrue(File(tmp, "void").isDirectory)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun resolveProjectAcceptsSimpleNamesAndNestedNames() {
        val tmp = createTempDir("voidroots")
        try {
            val resolved = VoidRoots.resolveProject(tmp, "my-proj")!!
            assertEquals(File(tmp, "void/my-proj").absolutePath, resolved.absolutePath)
            val nested = VoidRoots.resolveProject(tmp, "a/b")!!
            assertTrue(nested.absolutePath.startsWith(File(tmp, "void").absolutePath))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun resolveProjectRefusesEscapesAndBlank() {
        val tmp = createTempDir("voidroots")
        try {
            assertNull(VoidRoots.resolveProject(tmp, ".."))
            assertNull(VoidRoots.resolveProject(tmp, "../outside"))
            assertNull(VoidRoots.resolveProject(tmp, "a/../../outside"))
            // An absolute-looking name is treated as a relative
            // segment by File(parent, child) on Unix, so it lands
            // under the files home (safe) rather than escaping it.
            val abs = VoidRoots.resolveProject(tmp, "/etc")!!
            assertTrue(abs.absolutePath.startsWith(File(tmp, "void").absolutePath))
            assertNull(VoidRoots.resolveProject(tmp, "   "))
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun shellRunWithNonexistentWorkdirFailsLoudly() {
        val shell = VoidShell(DefaultProcessRunner(), "/bin/sh", ".")
        val result = shell.run("echo hi", "/definitely/not/a/void-dir-xyz")
        assertTrue(result is ShellResult.Failed)
        assertTrue((result as ShellResult.Failed).reason.contains("workdir is not a directory"))
        assertNull(shell.start("echo hi", "/definitely/not/a/void-dir-xyz"))
    }

    @Test
    fun shellRunWithFileAsWorkdirFails() {
        val tmp = File.createTempFile("voidroots", ".txt")
        try {
            val result = VoidShell(DefaultProcessRunner(), "/bin/sh", ".").run("echo hi", tmp.absolutePath)
            assertTrue(result is ShellResult.Failed)
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun openProjectResolvesUnderFilesHome() {
        val tmp = createTempDir("voidroots")
        try {
            var opened = "<none>"
            val port = VoidShellPort(
                VoidShell(DefaultProcessRunner(), "/bin/sh", "."),
                File(tmp, "void").absolutePath
            ) { workDir -> opened = workDir }
            port.openProject("my-proj")
            assertEquals(File(tmp, "void/my-proj").absolutePath, opened)
            port.openProject("../escape")
            // Escaping name: hook not invoked again, opened stays.
            assertEquals(File(tmp, "void/my-proj").absolutePath, opened)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun openProjectWithoutFilesHomeKeepsOldBehaviour() {
        var opened = "<none>"
        val port = VoidShellPort(VoidShell(DefaultProcessRunner(), "/bin/sh", ".")) { workDir -> opened = workDir }
        port.openProject("my-project")
        assertEquals("my-project", opened)
    }
}
