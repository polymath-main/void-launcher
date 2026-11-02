package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.action.VoidAction
import org.junit.Assert.assertEquals
import org.junit.Test

class VoidActionTest {

    @Test
    fun knownActionsMapExactly() {
        // Termux-removal Cycle 1: legacy strings route to Void actions.
        assertEquals(VoidAction.OPEN_VOIDTERM, VoidAction.fromAction("launch_termux", "com.termux"))
        assertEquals(VoidAction.OPEN_VOIDTERM, VoidAction.fromAction("open_voidterm", ""))
        assertEquals(VoidAction.OPEN_FILES, VoidAction.fromAction("open_files", ""))
        assertEquals(VoidAction.OPEN_DRAWER, VoidAction.fromAction("open_drawer", ""))
        assertEquals(VoidAction.OPEN_SETTINGS, VoidAction.fromAction("open_settings", ""))
        assertEquals(VoidAction.LAUNCH_PACKAGE, VoidAction.fromAction("launch_package", "com.example"))
        assertEquals(VoidAction.OPEN_BROWSER, VoidAction.fromAction("open_browser", ""))
        assertEquals(VoidAction.OPEN_DIALER, VoidAction.fromAction("open_dialer", ""))
        assertEquals(VoidAction.RUN_VOID_COMMAND, VoidAction.fromAction("termux_command", "pmc status"))
        assertEquals(VoidAction.RUN_VOID_COMMAND, VoidAction.fromAction("run_void_command", "pmc status"))
    }

    @Test
    fun unknownActionWithPackageFallsBackToLaunchPackage() {
        // Preserves the old router's else-branch exactly.
        assertEquals(VoidAction.LAUNCH_PACKAGE, VoidAction.fromAction("something_new", "com.example"))
    }

    @Test
    fun unknownActionWithoutPackageIsUnknown() {
        assertEquals(VoidAction.UNKNOWN, VoidAction.fromAction("something_new", ""))
        assertEquals(VoidAction.UNKNOWN, VoidAction.fromAction("", ""))
    }

    @Test
    fun defaultDockSlotsStillMap() {
        // The 5 shipped default slots (see LauncherConfig.defaultDockSlots)
        // must all map to a real action, never UNKNOWN.
        val actions = listOf(
            VoidAction.fromAction("open_voidterm", ""),
            VoidAction.fromAction("open_files", ""),
            VoidAction.fromAction("open_drawer", ""),
            VoidAction.fromAction("open_browser", ""),
            VoidAction.fromAction("open_settings", "")
        )
        assertEquals(5, actions.size)
        assertEquals(0, actions.count { it == VoidAction.UNKNOWN })
    }
}
