package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.PersonaOrder
import com.polymath.voidlauncher.model.DockSlot
import com.polymath.voidlauncher.model.PersonaProfile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** impl-06 step 3: pinned cycle order + stored persona fields applied. */
class AgentStep3Test {

    private fun persona(id: String, slots: List<DockSlot>? = null, workspace: String = "") =
        PersonaProfile(id = id, label = id, customDockSlots = slots, workspacePath = workspace)

    @Test
    fun pinnedOrderIsBuiltinsThenConfigThenExternal() {
        val ordered = PersonaOrder.order(
            builtins = listOf(persona("client"), persona("void"), persona("agent")),
            configProfiles = listOf(persona("mywork"), persona("void")),
            externalProfiles = listOf(persona("zext"), persona("mywork"))
        )
        // Built-ins in fixed order (void, agent, client — main/still absent),
        // then config, then external; duplicates keep first occurrence.
        assertEquals(
            listOf("void", "agent", "client", "mywork", "zext"),
            ordered.map { it.id }
        )
    }

    @Test
    fun fullBuiltinOrderIsVoidMainStillAgentClient() {
        val ordered = PersonaOrder.order(
            builtins = listOf(
                persona("client"), persona("still"), persona("main"),
                persona("agent"), persona("void")
            ),
            configProfiles = emptyList(),
            externalProfiles = emptyList()
        )
        assertEquals(
            listOf("void", "main", "still", "agent", "client"),
            ordered.map { it.id }
        )
    }

    @Test
    fun personaDockSlotsWinOverConfigDock() {
        val custom = listOf(DockSlot(1, "MINE", "", "com.example.x", "launch_package"))
        val configDock = listOf(DockSlot(1, "CONFIG", "", "", "launch_package"))
        assertEquals(custom, PersonaOrder.effectiveDockSlots(persona("p", slots = custom), configDock))
        assertEquals(configDock, PersonaOrder.effectiveDockSlots(persona("p"), configDock))
    }

    @Test
    fun personaWorkspaceWinsWhenNonBlank() {
        assertEquals("/ws", PersonaOrder.effectiveFilesPath(persona("p", workspace = "/ws"), "/cur"))
        assertEquals("/cur", PersonaOrder.effectiveFilesPath(persona("p", workspace = ""), "/cur"))
    }

    @Test
    fun activityAppliesPersonaFieldsAndManagerPinsOrder() {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val activity = File(root, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt").readText()
        assertTrue(activity.contains("fun applyPersonaSwitch"))
        assertTrue(activity.contains("personaDockOverride"))
        assertTrue(activity.contains("effectiveDockSlots()"))
        val manager = File(root, "app/src/main/java/com/polymath/voidlauncher/core/PersonaManager.kt").readText()
        assertTrue(manager.contains("PersonaOrder.order"))
        assertTrue(manager.contains("sortedBy { it.name }"))
        val config = File(root, "app/src/main/java/com/polymath/voidlauncher/core/ConfigManager.kt").readText()
        assertTrue(config.contains("custom_dock_slots"))
        assertTrue(config.contains("if (json.has(\"active_persona\"))"))
    }

    private fun findRepoRoot(): File? {
        var dir: File? = File("").absoluteFile
        var hops = 0
        while (dir != null && hops < 6) {
            if (File(dir, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt").exists()) return dir
            dir = dir.parentFile
            hops++
        }
        return null
    }
}
