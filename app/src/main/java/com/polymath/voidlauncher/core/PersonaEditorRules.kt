package com.polymath.voidlauncher.core

/**
 * impl-06 step 8 — pure validation for the persona/focus editors
 * (JVM-testable, no Android). Editors validate BEFORE saving and
 * never write a partially-valid file; these rules are the gate.
 */
object PersonaEditorRules {

    private val ID_PATTERN = Regex("^[a-z0-9][a-z0-9_-]*$")
    private val HEX_PATTERN = Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")

    /** Null = valid; otherwise a human-readable reason. */
    fun validatePersonaId(id: String, existingIds: Set<String>, originalId: String? = null): String? {
        val trimmed = id.trim().lowercase()
        if (trimmed.isEmpty()) return "Persona id must not be empty"
        if (!ID_PATTERN.matches(trimmed)) return "Persona id must be lowercase letters, digits, '-' or '_' (must start with a letter or digit)"
        if (trimmed != originalId && existingIds.contains(trimmed)) return "Persona id '$trimmed' already exists"
        return null
    }

    fun isValidHexColor(hex: String): Boolean = HEX_PATTERN.matches(hex.trim())

    fun validatePersonaFields(label: String, accent: String, secondary: String): String? {
        if (label.isBlank()) return "Label must not be empty"
        if (!isValidHexColor(accent)) return "Accent must be a hex colour like #E11D48"
        if (!isValidHexColor(secondary)) return "Secondary must be a hex colour like #06B6D4"
        return null
    }

    /** Focus day labels with the Calendar.DAY_OF_WEEK mapping explicit (plan step 8). */
    val DAY_LABELS = listOf(
        "Sunday (1)", "Monday (2)", "Tuesday (3)", "Wednesday (4)",
        "Thursday (5)", "Friday (6)", "Saturday (7)"
    )
    val DAY_VALUES = listOf(1, 2, 3, 4, 5, 6, 7)
}
