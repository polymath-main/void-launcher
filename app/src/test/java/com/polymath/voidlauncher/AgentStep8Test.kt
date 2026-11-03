package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.PersonaEditorRules
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** impl-06 step 8: JSON-free editors — pure rules + wiring. */
class AgentStep8Test {

    @Test
    fun personaIdValidation() {
        val existing = setOf("void", "work")
        assertNull(PersonaEditorRules.validatePersonaId("home", existing))
        assertTrue(PersonaEditorRules.validatePersonaId("", existing) != null)
        assertTrue(PersonaEditorRules.validatePersonaId("Work Space", existing) != null)
        assertTrue(PersonaEditorRules.validatePersonaId("work", existing) != null)
        // Editing the same id is not a collision.
        assertNull(PersonaEditorRules.validatePersonaId("work", existing, originalId = "work"))
    }

    @Test
    fun hexColorValidation() {
        assertTrue(PersonaEditorRules.isValidHexColor("#E11D48"))
        assertTrue(PersonaEditorRules.isValidHexColor("#e11d48ff"))
        assertFalse(PersonaEditorRules.isValidHexColor("E11D48"))
        assertFalse(PersonaEditorRules.isValidHexColor("#ZZZZZZ"))
        assertFalse(PersonaEditorRules.isValidHexColor(""))
        assertTrue(PersonaEditorRules.validatePersonaFields("", "#E11D48", "#06B6D4") != null)
        assertTrue(PersonaEditorRules.validatePersonaFields("Work", "red", "#06B6D4") != null)
        assertNull(PersonaEditorRules.validatePersonaFields("Work", "#E11D48", "#06B6D4"))
    }

    @Test
    fun dayLabelsShowCalendarMapping() {
        assertEquals(7, PersonaEditorRules.DAY_LABELS.size)
        assertEquals("Sunday (1)", PersonaEditorRules.DAY_LABELS[0])
        assertEquals("Saturday (7)", PersonaEditorRules.DAY_LABELS[6])
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), PersonaEditorRules.DAY_VALUES)
    }

    @Test
    fun editorsWireValidateFirstAndTempRename() {
        val root = findRepoRoot()
        assumeTrue("repo sources not found", root != null)
        val personaSvc = File(root, "app/src/main/java/com/polymath/voidlauncher/core/services/PersonaEditorService.kt").readText()
        assertTrue(personaSvc.contains("PersonaEditorRules.validatePersonaId"))
        assertTrue(personaSvc.contains("customDockSlots"))
        assertTrue(personaSvc.contains("identityStillLabel"))
        val focusSvc = File(root, "app/src/main/java/com/polymath/voidlauncher/core/services/FocusScheduleEditorService.kt").readText()
        assertTrue(focusSvc.contains("FocusRules.validate"))
        assertTrue(focusSvc.contains(".tmp"))
        assertTrue(focusSvc.contains("renameTo"))
        assertTrue(focusSvc.contains("DAY_LABELS"))
        val manager = File(root, "app/src/main/java/com/polymath/voidlauncher/core/PersonaManager.kt").readText()
        assertTrue(manager.contains("fun syncConfigProfiles"))
        val activity = File(root, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt").readText()
        assertTrue(activity.contains("command == \"persona edit\""))
        assertTrue(activity.contains("command == \"focus edit\""))
        assertTrue(activity.contains("fun commitPersonaConfig"))
        // Built-in label override extended to still (plan: "if desired" — done).
        assertTrue(activity.contains("identityStillLabel") ||
            File(root, "app/src/main/java/com/polymath/voidlauncher/core/services/PersonaIdentityMicroService.kt").readText().contains("identityStillLabel"))
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
