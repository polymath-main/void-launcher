package com.polymath.voidlauncher.core

/**
 * impl-02 step 7: pure dock-slot operations, kept model-free on purpose.
 * DockSlot itself lives in LauncherConfig.kt, which imports Android
 * classes and cannot compile in the JVM harness — so this seam works
 * on plain data and the (Android) DockSlotMicroService maps to/from
 * it. The router already interprets run_void_command slots by
 * dispatching packageName as the command, so a command slot stores
 * the command in packageName exactly as the router expects.
 */
data class DockSlotData(
    val index: Int,
    val label: String,
    val subtitle: String,
    val packageName: String,
    val action: String
)

object DockSlotOps {

    /**
     * Moves the slot at [from] to [to]. If [to] is occupied, the two
     * slots swap indices instead (no drag-and-drop in v1 — the picker
     * is the honest form). All other slots are preserved untouched,
     * and the result is sorted by index. Unknown [from], or
     * from == to, returns the input unchanged.
     */
    fun moveOrSwap(slots: List<DockSlotData>, from: Int, to: Int): List<DockSlotData> {
        if (from == to) return slots
        val source = slots.firstOrNull { it.index == from } ?: return slots
        val target = slots.firstOrNull { it.index == to }
        val moved = if (target == null) {
            slots.map { if (it.index == from) it.copy(index = to) else it }
        } else {
            slots.map {
                when (it.index) {
                    from -> it.copy(index = to)
                    to -> it.copy(index = from)
                    else -> it
                }
            }
        }
        return moved.sortedBy { it.index }
    }

    /**
     * Builds a run-command slot, or null for a blank command (blank
     * is refused like an empty rename — never committed). The label
     * carries a ⚡ prefix so a command slot is never mistaken for
     * an app slot on the dock.
     */
    fun makeCommandSlot(index: Int, command: String): DockSlotData? {
        val cmd = command.trim()
        if (cmd.isBlank()) return null
        return DockSlotData(
            index = index,
            label = "⚡ $cmd",
            subtitle = "Void command",
            packageName = cmd,
            action = "run_void_command"
        )
    }
}
