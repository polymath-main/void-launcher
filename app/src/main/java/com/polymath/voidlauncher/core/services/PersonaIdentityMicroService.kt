package com.polymath.voidlauncher.core.services

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.polymath.voidlauncher.core.PersonaManager
import com.polymath.voidlauncher.core.ThemeEngine
import com.polymath.voidlauncher.model.DockStyle
import com.polymath.voidlauncher.model.LauncherConfig
import com.polymath.voidlauncher.model.PersonaProfile
import com.polymath.voidlauncher.model.VoidThemeManifest
import java.util.Locale

/**
 * Micro-service managing Workspace Identity Badge rendering,
 * Persona cycling, theme-bound default persona selection, and the
 * interactive Workspace Identity & Mode Switcher dialog.
 */
class PersonaIdentityMicroService(
    private val activity: Activity,
    private val personaManager: PersonaManager
) {

    /**
     * Binds the theme's [defaultPersona] to the active workspace persona.
     */
    fun bindThemePersona(theme: VoidThemeManifest, config: LauncherConfig): LauncherConfig {
        val targetId = theme.defaultPersona.personaId
        personaManager.selectPersona(targetId)
        return config.copy(activePersona = targetId)
    }

    /**
     * Renders the identity badge matching the active theme's geometry,
     * typography, and dock style while enforcing a >=48dp touch target.
     */
    fun applyIdentityBadge(badgeView: TextView, config: LauncherConfig) {
        val theme = ThemeEngine.currentTheme
        val persona = personaManager.getActivePersona()
        val rawName = persona.id.uppercase(Locale.US)

        // Style badge text format according to active dock style
        val badgeText = when (theme.dockStyle) {
            DockStyle.NUMERIC_BRACKET -> when (persona.id.lowercase(Locale.US)) {
                "void" -> config.identityVoidLabel.ifBlank { "[$rawName]" }
                "main" -> config.identityMainLabel.ifBlank { "[$rawName]" }
                else -> "[$rawName]"
            }
            DockStyle.ASCII_TTY -> "::$rawName::"
            DockStyle.EDITORIAL_SERIF -> "• ${persona.id.replaceFirstChar { it.uppercase() }} •"
            DockStyle.CHAMFERED_HUD -> "// $rawName //"
            DockStyle.CAD_WIREFRAME -> "SYS::$rawName"
        }

        badgeView.text = badgeText
        badgeView.minHeight = dpToPx(theme.touchTargetMinDp.coerceAtLeast(48))

        // Typeface resolution based on theme header font
        val tf = when (theme.headerFont.lowercase(Locale.US)) {
            "serif" -> Typeface.SERIF
            "sans-serif", "sans" -> Typeface.SANS_SERIF
            else -> Typeface.MONOSPACE
        }
        badgeView.typeface = Typeface.create(tf, Typeface.BOLD)

        val accentCol = try {
            Color.parseColor(persona.accentColor)
        } catch (_: Exception) {
            theme.argb(theme.accentPrimaryHex)
        }

        badgeView.setTextColor(accentCol)

        // Theme-styled border, surface, and corner radius
        badgeView.background = GradientDrawable().apply {
            setColor(theme.argb(theme.surfaceElevatedHex))
            setStroke(dpToPx(theme.borderWidthDp.coerceAtLeast(1)), theme.argb(theme.borderHairlineHex))
            cornerRadius = dpToPxF(theme.cornerRadiusDp.toFloat())
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
            "🖼 Wallpaper & Appearance Studio" to { onOpenWallpaperStudio() },
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
