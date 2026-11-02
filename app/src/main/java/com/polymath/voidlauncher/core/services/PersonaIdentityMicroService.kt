package com.polymath.voidlauncher.core.services

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.polymath.voidlauncher.core.PersonaManager
import com.polymath.voidlauncher.model.LauncherConfig
import com.polymath.voidlauncher.model.PersonaProfile

/**
 * Micro-service managing Workspace Identity Badge rendering,
 * Persona cycling, and the interactive Workspace Identity & Mode Switcher dialog.
 */
class PersonaIdentityMicroService(
    private val activity: Activity,
    private val personaManager: PersonaManager
) {

    fun applyIdentityBadge(badgeView: TextView, config: LauncherConfig) {
        val c = config.colors
        val persona = personaManager.getActivePersona()
        val label = when (persona.id.lowercase()) {
            "void" -> config.identityVoidLabel.ifBlank { persona.label }
            "main" -> config.identityMainLabel.ifBlank { persona.label }
            else -> persona.label
        }
        badgeView.text = label
        val accentCol = try {
            Color.parseColor(persona.accentColor)
        } catch (_: Exception) {
            c.accentInt
        }
        badgeView.setTextColor(accentCol)
        badgeView.background = GradientDrawable().apply {
            setColor(c.surfaceInt)
            setStroke(dpToPx(1), accentCol)
            cornerRadius = dpToPxF(4f)
        }
    }

    fun cyclePersona(
        config: LauncherConfig,
        onPersonaSwitched: (PersonaProfile, LauncherConfig) -> Unit
    ) {
        val newPersona = personaManager.cyclePersona()
        val updated = config.copy(activePersona = newPersona.id)
        onPersonaSwitched(newPersona, updated)
        Toast.makeText(
            activity,
            "Persona: ${newPersona.label} (${newPersona.subtitle})",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun showPersonaAndModeDialog(
        config: LauncherConfig,
        onPersonaSelected: (PersonaProfile, LauncherConfig) -> Unit,
        onSetHomeMode: (String) -> Unit,
        onOpenCommandCenter: () -> Unit,
        onOpenWidgetDeck: () -> Unit,
        onOpenWallpaperStudio: () -> Unit
    ) {
        val personas = personaManager.getAllPersonas()
        val active = personaManager.getActivePersona()
        val modeItems = listOf(
            "🏠 Home Mode: STILL (Calm Minimal Clock & 3 Favorites)" to { onSetHomeMode("STILL") },
            "🎛 Home Mode: COCKPIT (HUD, Notifications & 5-Slot Dock)" to { onSetHomeMode("COCKPIT") },
            "⚡ Home Mode: MINIMAL (Omni-Bar & Dock Only)" to { onSetHomeMode("MINIMAL") },
            "📁 Home Mode: FILES (Workspace File Browser)" to { onSetHomeMode("FILES") },
            "🖼 Wallpaper Studio (Toggle / Pick / Scrim Dimming)" to { onOpenWallpaperStudio() },
            "🔍 Open Command Center (:cmd)" to { onOpenCommandCenter() },
            "🧩 Open Widget Deck (:deck)" to { onOpenWidgetDeck() }
        )
        val personaLabels = personas.map { p ->
            val mark = if (p.id.equals(active.id, ignoreCase = true)) "● " else "○ "
            "${mark}Persona ${p.label} — ${p.subtitle}"
        }
        val allLabels = (personaLabels + modeItems.map { it.first }).toTypedArray()

        MaterialAlertDialogBuilder(activity)
            .setTitle("Workspace Identity & Mode")
            .setItems(allLabels) { _, which ->
                if (which < personas.size) {
                    val chosen = personas[which]
                    personaManager.selectPersona(chosen.id)
                    val updated = config.copy(activePersona = chosen.id)
                    onPersonaSelected(chosen, updated)
                    Toast.makeText(activity, "Persona: ${chosen.label}", Toast.LENGTH_SHORT).show()
                } else {
                    val modeIdx = which - personas.size
                    modeItems.getOrNull(modeIdx)?.second?.invoke()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), activity.resources.displayMetrics).toInt()

    private fun dpToPxF(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, activity.resources.displayMetrics)
}
