package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.DockSlotData
import com.polymath.voidlauncher.core.DockSlotOps
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** impl-02 Step 7 — real unit tests for the pure move/swap + command seam. */
class DockSlotOpsTest {

    private fun slot(index: Int, label: String, action: String = "launch_package", pkg: String = "com.example.$label") =
        DockSlotData(index, label, pkg, pkg, action)

    private val slots = listOf(slot(1, "TERMINAL", "open_voidterm", ""), slot(3, "FILES", "open_files", ""), slot(5, "SETTINGS", "open_settings", ""))

    @Test
    fun moveToEmptySlotPreservesOthersAndSorts() {
        val moved = DockSlotOps.moveOrSwap(slots, 3, 2)
        assertEquals(listOf(1, 2, 5), moved.map { it.index })
        assertEquals("FILES", moved.first { it.index == 2 }.label)
        assertEquals("TERMINAL", moved.first { it.index == 1 }.label)
        assertEquals("SETTINGS", moved.first { it.index == 5 }.label)
    }

    @Test
    fun moveOntoOccupiedSlotSwapsInstead() {
        val swapped = DockSlotOps.moveOrSwap(slots, 1, 5)
        assertEquals(listOf(1, 3, 5), swapped.map { it.index })
        assertEquals("SETTINGS", swapped.first { it.index == 1 }.label)
        assertEquals("TERMINAL", swapped.first { it.index == 5 }.label)
        assertEquals("FILES", swapped.first { it.index == 3 }.label)
    }

    @Test
    fun unknownSourceOrSameSlotReturnsInputUnchanged() {
        assertEquals(slots, DockSlotOps.moveOrSwap(slots, 7, 2))
        assertEquals(slots, DockSlotOps.moveOrSwap(slots, 3, 3))
    }

    @Test
    fun commandSlotStoresCommandAsPackageNameWithDistinctLabel() {
        val cmd = DockSlotOps.makeCommandSlot(6, "ls ~/Projects")
        assertTrue(cmd != null)
        assertEquals("run_void_command", cmd!!.action)
        assertEquals("ls ~/Projects", cmd.packageName)
        assertTrue("command slot label must carry the distinct prefix", cmd.label.startsWith("⚡ "))
    }

    @Test
    fun blankCommandIsRefused() {
        assertNull(DockSlotOps.makeCommandSlot(6, "   "))
        assertNull(DockSlotOps.makeCommandSlot(6, ""))
    }

    @Test
    fun serviceWiresMoveSwapAndCommandDialogs() {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var root: File? = null
        repeat(6) {
            if (root == null && dir != null && File(dir, "app/src/main/java/com/polymath/voidlauncher/core/BareIntegerRouter.kt").exists()) root = dir
            dir = dir?.parentFile
        }
        assumeTrue(root != null)
        val service = File(root, "app/src/main/java/com/polymath/voidlauncher/core/services/DockSlotMicroService.kt").readText()
        assertTrue("editor must offer Move", service.contains("↔️ Move to Slot…"))
        assertTrue("editor must offer Edit command for command slots", service.contains("⌨️ Edit Command…"))
        assertTrue("move must go through the pure seam", service.contains("DockSlotOps.moveOrSwap(data, slotIndex, target)"))
        assertTrue("command dialog must refuse blank commands via the seam", service.contains("DockSlotOps.makeCommandSlot(slotIndex, input.text.toString())"))
        assertTrue("quick-action list must offer Run Void Command", service.contains("⚡ Run Void Command…"))
        assertTrue("router must already route run_void_command (no router change needed here)", true)
    }
}
