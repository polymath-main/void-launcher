package com.polymath.voidlauncher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.polymath.voidlauncher.core.ConfigManager
import com.polymath.voidlauncher.core.FileManager
import com.polymath.voidlauncher.core.ThemeEngine
import com.polymath.voidlauncher.core.VoidNotificationListenerService
import com.polymath.voidlauncher.core.services.DockSlotMicroService
import com.polymath.voidlauncher.core.services.WallpaperMicroService
import com.polymath.voidlauncher.databinding.ActivityVoidSettingsBinding
import com.polymath.voidlauncher.model.ColorPalette
import com.polymath.voidlauncher.model.LauncherConfig
import com.polymath.voidlauncher.model.VoidPaths

class VoidSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoidSettingsBinding
    private lateinit var configManager: ConfigManager
    private lateinit var fileManager: FileManager
    private var currentConfig: LauncherConfig = LauncherConfig()
    private var pendingThemeId: String = "void"
    private var suppressThemeCallback: Boolean = false
    // Area 07 step 3: dirty-check state. savedConfigAtLoad is the
    // last SAVED config (not the form); resetPreviewPending marks a
    // Reset / group-reset preview that must NOT be saved by onPause.
    private var savedConfigAtLoad: LauncherConfig = LauncherConfig()
    private var resetPreviewPending: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoidSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configManager = ConfigManager(this)
        fileManager = FileManager(this)
        currentConfig = configManager.loadConfig()
        savedConfigAtLoad = currentConfig

        setupSpinners()
        populateUiFromConfig(currentConfig)
        attachThemeListener()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshSystemStatus()
    }

    private fun refreshSystemStatus() {
        // 1. Default Launcher Status
        val isDefault = isDefaultLauncher()
        if (isDefault) {
            binding.tvDefaultLauncherStatus.text = "Default Launcher: [ACTIVE]"
            binding.tvDefaultLauncherStatus.setTextColor(Color.parseColor("#10B981"))
            binding.btnSetDefaultLauncherSettings.text = "ACTIVE"
            binding.btnSetDefaultLauncherSettings.isEnabled = false
        } else {
            binding.tvDefaultLauncherStatus.text = "Default Launcher: [NOT DEFAULT]"
            binding.tvDefaultLauncherStatus.setTextColor(Color.parseColor("#E11D48"))
            binding.btnSetDefaultLauncherSettings.text = "SET DEFAULT ➔"
            binding.btnSetDefaultLauncherSettings.isEnabled = true
        }

        // 2. Storage Permission Status
        val hasStorage = fileManager.hasStoragePermission()
        if (hasStorage) {
            binding.tvStoragePermissionStatus.text = "All Files Access: [GRANTED]"
            binding.tvStoragePermissionStatus.setTextColor(Color.parseColor("#10B981"))
            binding.btnGrantStorageSettings.text = "GRANTED"
            binding.btnGrantStorageSettings.isEnabled = false
        } else {
            binding.tvStoragePermissionStatus.text = "All Files Access: [REQUIRED]"
            binding.tvStoragePermissionStatus.setTextColor(Color.parseColor("#E11D48"))
            binding.btnGrantStorageSettings.text = "GRANT ➔"
            binding.btnGrantStorageSettings.isEnabled = true
        }

        // Area 07 step 2/3: config store + validation status.
        // This is the surface for lastSaveWasFallback (was silent)
        // and lastValidationWarnings (was log-only).
        val storeLine = if (configManager.lastSaveWasFallback) {
            "Config store: INTERNAL FALLBACK — external write failed" +
                (configManager.lastSaveError?.let { ": $it" } ?: "")
        } else {
            "Config store: external (${VoidPaths.CONFIG})"
        }
        val warnings = configManager.lastValidationWarnings
        binding.tvConfigSaveStatus.text = if (warnings.isEmpty()) storeLine
        else storeLine + "\nConfig warnings (${warnings.size}): " +
            warnings.take(3).joinToString("; ") { "${it.key}: ${it.message}" }

        // 3. Notification Listener Status
        val hasNotif = VoidNotificationListenerService.isAccessGranted(this)
        if (hasNotif) {
            binding.tvNotifListenerStatus.text = "Notification Listener: [ACTIVE]"
            binding.tvNotifListenerStatus.setTextColor(Color.parseColor("#10B981"))
            binding.btnConfigureNotifListener.text = "ACTIVE"
        } else {
            binding.tvNotifListenerStatus.text = "Notification Listener: [DISABLED]"
            binding.tvNotifListenerStatus.setTextColor(Color.parseColor("#F59E0B"))
            binding.btnConfigureNotifListener.text = "ENABLE ➔"
        }
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    @Suppress("DEPRECATION")
    private fun requestSetDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    startActivityForResult(intent, REQUEST_DEFAULT_LAUNCHER)
                    return
                }
            }
        }

        try {
            startActivityForResult(Intent(Settings.ACTION_HOME_SETTINGS), REQUEST_DEFAULT_LAUNCHER)
        } catch (_: Exception) {
            try {
                startActivityForResult(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS), REQUEST_DEFAULT_LAUNCHER)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DEFAULT_LAUNCHER) {
            refreshSystemStatus()
        }
    }

    companion object {
        private const val REQUEST_DEFAULT_LAUNCHER = 1002
    }

    private fun setupSpinners() {
        // Theme Engine presets (+ Custom)
        val themeNames = ThemeEngine.presets.map { it.name } + "Custom"
        binding.spnTheme.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, themeNames)

        val iconStyles = arrayOf("original", "monochrome", "tinted")
        val iconAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, iconStyles)
        binding.spnIconStyle.adapter = iconAdapter

        val homeModes = arrayOf("COCKPIT", "STILL", "MINIMAL", "FILES")
        val homeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, homeModes)
        binding.spnHomeMode.adapter = homeAdapter

        val gestureActions = arrayOf(
            "NONE", "OPEN_DRAWER", "OPEN_FILES", "OPEN_SEARCH",
            "OPEN_COMMAND_CENTER", "OPEN_WIDGET_DECK",
            "OPEN_SETTINGS", "OPEN_TERMINAL", "NOTIFICATIONS",
            "EXPAND_STATUS_BAR", "TOGGLE_MINIMAL", "TOGGLE_STILL",
            "COCKPIT_MODE", "FOCUS_OMNI", "OPEN_HUD", "TOGGLE_IDENTITY"
        )
        fun makeGestureAdapter() = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, gestureActions)

        binding.spnGestureSwipeUp.adapter    = makeGestureAdapter()
        binding.spnGestureSwipeDown.adapter  = makeGestureAdapter()
        binding.spnGestureSwipeLeft.adapter  = makeGestureAdapter()
        binding.spnGestureSwipeRight.adapter = makeGestureAdapter()
        binding.spnGestureDoubleTap.adapter  = makeGestureAdapter()
        binding.spnGestureLongPress.adapter  = makeGestureAdapter()
    }

    /** Attached AFTER the initial selection so restoring the spinner doesn't clobber colors. */
    private fun attachThemeListener() {
        binding.spnTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressThemeCallback) return
                val preset = ThemeEngine.presets.getOrNull(position)
                if (preset != null) {
                    pendingThemeId = preset.id
                    binding.etAccentColor.setText(preset.palette.accent)
                    binding.etSecondaryColor.setText(preset.palette.secondary)
                    binding.etSurfaceColor.setText(preset.palette.surface)
                } else {
                    pendingThemeId = ThemeEngine.CUSTOM_ID
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /** Marks the current colors as a custom scheme (deselects presets). */
    private fun markCustomTheme() {
        pendingThemeId = ThemeEngine.CUSTOM_ID
        suppressThemeCallback = true
        binding.spnTheme.setSelection(ThemeEngine.presets.size)
        suppressThemeCallback = false
    }

    private fun selectGestureAction(spinner: android.widget.Spinner, action: String) {
        val adapter = spinner.adapter ?: return
        for (i in 0 until adapter.count) {
            if (adapter.getItem(i).toString().equals(action, ignoreCase = true)) {
                spinner.setSelection(i)
                break
            }
        }
    }

    private fun populateUiFromConfig(cfg: LauncherConfig) {
        val c = cfg.colors

        binding.etAccentColor.setText(c.accent)
        binding.etSecondaryColor.setText(c.secondary)
        binding.etSurfaceColor.setText(c.surface)

        // Theme Engine selection
        pendingThemeId = cfg.themeId
        val themeIdx = ThemeEngine.presets.indexOfFirst { it.id.equals(cfg.themeId, ignoreCase = true) }
        suppressThemeCallback = true
        binding.spnTheme.setSelection(if (themeIdx >= 0) themeIdx else ThemeEngine.presets.size)
        suppressThemeCallback = false

        binding.switchDrawerEdge.isChecked = cfg.drawerEdgeTransparent
        val opacityPercent = (cfg.drawerAlpha * 100).toInt().coerceIn(0, 100)
        binding.seekDrawerOpacity.progress = opacityPercent
        binding.tvDrawerOpacityLabel.text = "Drawer Opacity: $opacityPercent%"

        binding.switchShowAppIcons.isChecked = cfg.showAppIcons
        val iconStyleIdx = when (cfg.appIconStyle.lowercase()) {
            "monochrome" -> 1
            "tinted" -> 2
            else -> 0
        }
        binding.spnIconStyle.setSelection(iconStyleIdx)
        binding.switchAtoZScrubber.isChecked = cfg.enableAtoZScrubber

        val homeModeIdx = when (cfg.defaultHomeMode.uppercase()) {
            "STILL" -> 1
            "MINIMAL" -> 2
            "FILES" -> 3
            else -> 0
        }
        binding.spnHomeMode.setSelection(homeModeIdx)

        binding.switchShowTelemetry.isChecked = cfg.showTelemetryBar
        binding.switchShowNodeOs.isChecked = cfg.showNodeOsCard
        binding.switchShowIntent.isChecked = cfg.showIntentCard
        binding.switchShowNotifications.isChecked = cfg.showNotificationsCard
        binding.switchFilterOngoing.isChecked = cfg.filterOngoingNotifications
        binding.switchShowDock.isChecked = cfg.showDock
        binding.switchShowDigitKeypad.isChecked = cfg.showDigitKeypad

        // Homescreen Customizations
        binding.switchUseWallpaper.isChecked = cfg.useSystemWallpaper
        binding.seekCardRadius.progress = cfg.cockpitCardRadiusDp.toInt().coerceIn(0, 40)
        binding.tvCardRadiusLabel.text = "Card Radius: ${cfg.cockpitCardRadiusDp.toInt()}dp"
        binding.switchShowStatusTime.isChecked = cfg.showStatusBarTime
        binding.switchShowStatusBattery.isChecked = cfg.showStatusBarBattery
        binding.switchShowStatusRam.isChecked = cfg.showStatusBarRam

        // Gesture Controls
        selectGestureAction(binding.spnGestureSwipeUp,    cfg.gestureSwipeUp)
        selectGestureAction(binding.spnGestureSwipeDown,  cfg.gestureSwipeDown)
        selectGestureAction(binding.spnGestureSwipeLeft,  cfg.gestureSwipeLeft)
        selectGestureAction(binding.spnGestureSwipeRight, cfg.gestureSwipeRight)
        selectGestureAction(binding.spnGestureDoubleTap,  cfg.gestureDoubleTap)
        selectGestureAction(binding.spnGestureLongPress,  cfg.gestureLongPress)

        binding.etFilesDefaultPath.setText(cfg.filesDefaultPath)
        binding.switchFilesShowHidden.isChecked = cfg.filesShowHidden

        binding.etThermalLimit.setText(cfg.thermalLimitCelsius.toString())
        binding.switchGlyphPulse.isChecked = cfg.enableGlyphPulse
        binding.switchHaptics.isChecked = cfg.enableHaptics

        // Area 07 step 3: font sizes as config edits. SeekBar
        // progress is sp x 2 (0.5sp steps), clamped 8–20sp.
        binding.seekHudFont.progress = (cfg.hudFontSizeSp * 2).toInt().coerceIn(16, 40)
        binding.seekDockFont.progress = (cfg.dockFontSizeSp * 2).toInt().coerceIn(16, 40)
        binding.seekOmniFont.progress = (cfg.omniFontSizeSp * 2).toInt().coerceIn(16, 40)
        binding.tvHudFontLabel.text = "HUD font: ${cfg.hudFontSizeSp}sp"
        binding.tvDockFontLabel.text = "Dock font: ${cfg.dockFontSizeSp}sp"
        binding.tvOmniFontLabel.text = "Omni font: ${cfg.omniFontSizeSp}sp"

        // impl-06 step 6 (area 06 subset only — broader Settings
        // redesign stays area 07): notification rules, widgets, agent,
        // AST, focus, legacy import. Lists are comma-separated text;
        // paths below are read-only displays, not editors.
        binding.etNotifWhitelist.setText(cfg.notifWhitelistPackages.joinToString(", "))
        binding.etNotifBlacklist.setText(cfg.notifBlacklistPackages.joinToString(", "))
        binding.etNotifKeywords.setText(cfg.notifPriorityKeywords.joinToString(", "))
        binding.etNotifMaxCount.setText(cfg.notificationsMaxCount.toString())
        binding.switchWidgetsEnabled.isChecked = cfg.widgetsEnabled
        binding.tvWidgetsDirectory.text = "Widgets directory (read-only):\n${cfg.widgetsDirectory}"
        binding.switchAgentOrchestrator.isChecked = cfg.agentOrchestratorEnabled
        binding.switchAstGraph.isChecked = cfg.astGraphEnabled
        binding.etAstNodeCap.setText(cfg.astGraphNodeCap.toString())
        binding.switchFocusScheduler.isChecked = cfg.focusSchedulerEnabled
        binding.tvFocusSchedulePath.text = "Focus schedule path (read-only; editor in step 8):\n${cfg.focusSchedulePath}"
        binding.switchNodeOsLegacyImport.isChecked = cfg.nodeOsLegacyImportEnabled
        binding.etWorkflowJsonPath.setText(cfg.workflowJsonPath)
        binding.etNodeosDbPath.setText(cfg.nodeosDbPath)

        refreshSystemStatus()
    }

    private fun parseCsvList(text: String): List<String> =
        text.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private fun setupListeners() {
        binding.btnSettingsBack.setOnClickListener { finish() }

        // System Roles & Permissions
        binding.btnSetDefaultLauncherSettings.setOnClickListener {
            requestSetDefaultLauncher()
        }

        binding.btnGrantStorageSettings.setOnClickListener {
            fileManager.requestStoragePermission(this)
        }

        binding.btnConfigureNotifListener.setOnClickListener {
            VoidNotificationListenerService.openAccessSettings(this)
        }

        // Presets (manual accents → custom scheme)
        binding.presetCrimson.setOnClickListener {
            binding.etAccentColor.setText("#E11D48")
            binding.etSecondaryColor.setText("#06B6D4")
            markCustomTheme()
        }
        binding.presetCyan.setOnClickListener {
            binding.etAccentColor.setText("#06B6D4")
            binding.etSecondaryColor.setText("#E11D48")
            markCustomTheme()
        }
        binding.presetGreen.setOnClickListener {
            binding.etAccentColor.setText("#10B981")
            binding.etSecondaryColor.setText("#06B6D4")
            markCustomTheme()
        }
        binding.presetAmber.setOnClickListener {
            binding.etAccentColor.setText("#F59E0B")
            binding.etSecondaryColor.setText("#E11D48")
            markCustomTheme()
        }

        binding.seekDrawerOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvDrawerOpacityLabel.text = "Drawer Opacity: $progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekCardRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvCardRadiusLabel.text = "Card Radius: ${progress}dp"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Area 07 step 3: font-size seek listeners (label only;
        // values are read in buildUpdatedConfig).
        fun fontListener(label: android.widget.TextView, name: String) =
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    label.text = "$name font: ${progress / 2f}sp"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        binding.seekHudFont.setOnSeekBarChangeListener(fontListener(binding.tvHudFontLabel, "HUD"))
        binding.seekDockFont.setOnSeekBarChangeListener(fontListener(binding.tvDockFontLabel, "Dock"))
        binding.seekOmniFont.setOnSeekBarChangeListener(fontListener(binding.tvOmniFontLabel, "Omni"))

        binding.btnSettingsSave.setOnClickListener {
            saveAndApply()
        }

        // impl-06 step 6: open the widgets directory in the launcher's
        // own files browser. Leaving Settings auto-persists (onPause),
        // so the toggle state above is saved before the main Activity
        // reloads config on resume.
        binding.btnOpenWidgetsFolder.setOnClickListener {
            val intent = Intent(this, com.polymath.voidlauncher.VoidLauncherActivity::class.java).apply {
                putExtra("void_open_files_path", currentConfig.widgetsDirectory)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }

        binding.btnCustomizeDockSlots.setOnClickListener {
            DockSlotMicroService(this).showAllSlotsManagerDialog(
                configProvider = { currentConfig },
                onCommit = { updated ->
                    currentConfig = updated
                    savedConfigAtLoad = updated
                    configManager.saveConfig(currentConfig)
                }
            )
        }

        binding.btnConfigureWallpaper.setOnClickListener {
            WallpaperMicroService(this).showWallpaperStudioDialog(
                activity = this,
                currentConfig = buildUpdatedConfig(),
                onUpdateConfig = { updated ->
                    currentConfig = updated
                    savedConfigAtLoad = updated
                    binding.switchUseWallpaper.isChecked = updated.useSystemWallpaper
                    populateUiFromConfig(updated)
                    configManager.saveConfig(currentConfig)
                }
            )
        }

        binding.btnResetDefaults.setOnClickListener {
            val defaults = LauncherConfig()
            populateUiFromConfig(defaults)
            // Area 07 step 3: a preview, not a save. onPause must
            // NOT persist it (the old bug: leaving after Reset
            // saved the previewed defaults anyway).
            resetPreviewPending = true
            Toast.makeText(this, "Reset to defaults preview. Tap SAVE to apply — leaving without SAVE discards it.", Toast.LENGTH_SHORT).show()
        }

        // Area 07 step 3: per-group reset previews (same semantics
        // as whole-config Reset: preview until SAVE, discarded on
        // leave without SAVE).
        binding.btnResetAppearance.setOnClickListener { resetGroup("appearance") }
        binding.btnResetHome.setOnClickListener { resetGroup("home") }
        binding.btnResetSystem.setOnClickListener { resetGroup("system") }
        binding.btnResetData.setOnClickListener { resetGroup("data") }

        // Area 07 step 3: Settings entry points for the area-06
        // editors (not rebuilt here). Leaving Settings auto-persists
        // only if dirty (see onPause), so the form state is saved
        // before the main Activity opens the editor.
        binding.btnOpenPersonaEditor.setOnClickListener {
            val intent = Intent(this, com.polymath.voidlauncher.VoidLauncherActivity::class.java).apply {
                putExtra("void_open_persona_editor", true)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }
        binding.btnOpenFocusEditor.setOnClickListener {
            val intent = Intent(this, com.polymath.voidlauncher.VoidLauncherActivity::class.java).apply {
                putExtra("void_open_focus_editor", true)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }

        binding.btnExportJson.setOnClickListener {
            // Cycle 3 fix: export the CURRENT (last-saved) config, never
            // defaults over the user's config.
            configManager.exportCurrentConfig(currentConfig)
            Toast.makeText(this, "Exported current config to ${VoidPaths.CONFIG}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildUpdatedConfig(): LauncherConfig {
        val accent = binding.etAccentColor.text.toString().trim().ifEmpty { "#E11D48" }
        val secondary = binding.etSecondaryColor.text.toString().trim().ifEmpty { "#06B6D4" }
        val surface = binding.etSurfaceColor.text.toString().trim().ifEmpty { "#080808" }

        // Theme engine: preset palette is the base, the three hex fields override it.
        val preset = ThemeEngine.byId(pendingThemeId)
        val basePalette = preset?.palette ?: currentConfig.colors
        val newColors = basePalette.copy(
            accent = accent,
            secondary = secondary,
            surface = surface
        )

        val opacity = binding.seekDrawerOpacity.progress / 100.0f
        val iconStyle = binding.spnIconStyle.selectedItem?.toString() ?: "original"
        val homeMode = binding.spnHomeMode.selectedItem?.toString() ?: "COCKPIT"
        val thermal = binding.etThermalLimit.text.toString().toFloatOrNull() ?: 39.0f
        val filesPath = binding.etFilesDefaultPath.text.toString().trim().ifEmpty { currentConfig.filesDefaultPath }

        return currentConfig.copy(
            colors = newColors,
            themeId = pendingThemeId,
            // Area 07 step 3: font sizes are config edits (8–20sp).
            hudFontSizeSp = (binding.seekHudFont.progress / 2f).coerceIn(8f, 20f),
            dockFontSizeSp = (binding.seekDockFont.progress / 2f).coerceIn(8f, 20f),
            omniFontSizeSp = (binding.seekOmniFont.progress / 2f).coerceIn(8f, 20f),
            drawerAlpha = opacity,
            drawerEdgeTransparent = binding.switchDrawerEdge.isChecked,
            showAppIcons = binding.switchShowAppIcons.isChecked,
            appIconStyle = iconStyle,
            enableAtoZScrubber = binding.switchAtoZScrubber.isChecked,
            defaultHomeMode = homeMode,
            showTelemetryBar = binding.switchShowTelemetry.isChecked,
            showNodeOsCard = binding.switchShowNodeOs.isChecked,
            showIntentCard = binding.switchShowIntent.isChecked,
            showNotificationsCard = binding.switchShowNotifications.isChecked,
            filterOngoingNotifications = binding.switchFilterOngoing.isChecked,
            showDock = binding.switchShowDock.isChecked,
            showDigitKeypad = binding.switchShowDigitKeypad.isChecked,
            // Homescreen Customizations
            useSystemWallpaper = binding.switchUseWallpaper.isChecked,
            cockpitCardRadiusDp = binding.seekCardRadius.progress.toFloat().coerceIn(0f, 40f),
            showStatusBarTime = binding.switchShowStatusTime.isChecked,
            showStatusBarBattery = binding.switchShowStatusBattery.isChecked,
            showStatusBarRam = binding.switchShowStatusRam.isChecked,
            // Gesture Controls
            gestureSwipeUp    = binding.spnGestureSwipeUp.selectedItem?.toString()    ?: "OPEN_DRAWER",
            gestureSwipeDown  = binding.spnGestureSwipeDown.selectedItem?.toString()  ?: "NOTIFICATIONS",
            gestureSwipeLeft  = binding.spnGestureSwipeLeft.selectedItem?.toString()  ?: "OPEN_DRAWER",
            gestureSwipeRight = binding.spnGestureSwipeRight.selectedItem?.toString() ?: "OPEN_FILES",
            gestureDoubleTap  = binding.spnGestureDoubleTap.selectedItem?.toString()  ?: "TOGGLE_MINIMAL",
            gestureLongPress  = binding.spnGestureLongPress.selectedItem?.toString()  ?: "OPEN_SETTINGS",
            filesDefaultPath = filesPath,
            filesShowHidden = binding.switchFilesShowHidden.isChecked,
            thermalLimitCelsius = thermal,
            enableGlyphPulse = binding.switchGlyphPulse.isChecked,
            enableHaptics = binding.switchHaptics.isChecked,
            // impl-06 step 6: area 06 subset. Max count clamps to the
            // plan's 1–20; node cap keeps any positive value.
            notifWhitelistPackages = parseCsvList(binding.etNotifWhitelist.text.toString()),
            notifBlacklistPackages = parseCsvList(binding.etNotifBlacklist.text.toString()),
            notifPriorityKeywords = parseCsvList(binding.etNotifKeywords.text.toString()),
            notificationsMaxCount = (binding.etNotifMaxCount.text.toString().toIntOrNull()
                ?: currentConfig.notificationsMaxCount).coerceIn(1, 20),
            widgetsEnabled = binding.switchWidgetsEnabled.isChecked,
            agentOrchestratorEnabled = binding.switchAgentOrchestrator.isChecked,
            astGraphEnabled = binding.switchAstGraph.isChecked,
            astGraphNodeCap = (binding.etAstNodeCap.text.toString().toIntOrNull()
                ?: currentConfig.astGraphNodeCap).coerceAtLeast(1),
            focusSchedulerEnabled = binding.switchFocusScheduler.isChecked,
            // Legacy import switch + paths: enabling writes NOTHING to
            // those files — the Activity only reads them when the flag
            // is on (copy-only import itself is step 9's SAF picker).
            nodeOsLegacyImportEnabled = binding.switchNodeOsLegacyImport.isChecked,
            workflowJsonPath = binding.etWorkflowJsonPath.text.toString().trim()
                .ifEmpty { currentConfig.workflowJsonPath },
            nodeosDbPath = binding.etNodeosDbPath.text.toString().trim()
                .ifEmpty { currentConfig.nodeosDbPath }
        )
    }

    /**
     * Area 07 step 3: resets ONE group to LauncherConfig() defaults
     * as a form preview (not a save). Group mapping, documented
     * here because the XML groups are visual only:
     * - appearance: colours, theme, drawer, icons, scrubber, card
     *   radius, font sizes;
     * - home: mode, telemetry/intent/notifications/dock/keypad
     *   toggles, wallpaper switch, status-bar toggles, widgets;
     * - system: gestures (simple + compound), thermal, glyph,
     *   haptics, agent, AST, focus toggles;
     * - data: files path/hidden, legacy-import switch + paths.
     * Telemetry/NodeOS rows stay in the Home card by design (they
     * are home-display toggles, not system roles) — the v3
     * Home-vs-System placement divergence is closed as intentional
     * here, not moved.
     */
    private fun resetGroup(group: String) {
        val defaults = LauncherConfig()
        val built = buildUpdatedConfig()
        val updated = when (group) {
            "appearance" -> built.copy(
                colors = defaults.colors, themeId = defaults.themeId,
                drawerAlpha = defaults.drawerAlpha,
                drawerEdgeTransparent = defaults.drawerEdgeTransparent,
                showAppIcons = defaults.showAppIcons,
                appIconStyle = defaults.appIconStyle,
                enableAtoZScrubber = defaults.enableAtoZScrubber,
                cockpitCardRadiusDp = defaults.cockpitCardRadiusDp,
                hudFontSizeSp = defaults.hudFontSizeSp,
                dockFontSizeSp = defaults.dockFontSizeSp,
                omniFontSizeSp = defaults.omniFontSizeSp
            )
            "home" -> built.copy(
                defaultHomeMode = defaults.defaultHomeMode,
                showTelemetryBar = defaults.showTelemetryBar,
                showNodeOsCard = defaults.showNodeOsCard,
                showIntentCard = defaults.showIntentCard,
                showNotificationsCard = defaults.showNotificationsCard,
                filterOngoingNotifications = defaults.filterOngoingNotifications,
                showDock = defaults.showDock,
                showDigitKeypad = defaults.showDigitKeypad,
                useSystemWallpaper = defaults.useSystemWallpaper,
                showStatusBarTime = defaults.showStatusBarTime,
                showStatusBarBattery = defaults.showStatusBarBattery,
                showStatusBarRam = defaults.showStatusBarRam,
                widgetsEnabled = defaults.widgetsEnabled
            )
            "system" -> built.copy(
                gestureSwipeUp = defaults.gestureSwipeUp,
                gestureSwipeDown = defaults.gestureSwipeDown,
                gestureSwipeLeft = defaults.gestureSwipeLeft,
                gestureSwipeRight = defaults.gestureSwipeRight,
                gestureDoubleTap = defaults.gestureDoubleTap,
                gestureLongPress = defaults.gestureLongPress,
                compoundGesturesEnabled = defaults.compoundGesturesEnabled,
                gestureTwoFingerUp = defaults.gestureTwoFingerUp,
                gesturePinchIn = defaults.gesturePinchIn,
                gesturePinchOut = defaults.gesturePinchOut,
                gestureLeftEdgeSwipe = defaults.gestureLeftEdgeSwipe,
                gestureRightEdgeSwipe = defaults.gestureRightEdgeSwipe,
                gestureBottomEdgeSwipe = defaults.gestureBottomEdgeSwipe,
                thermalLimitCelsius = defaults.thermalLimitCelsius,
                enableGlyphPulse = defaults.enableGlyphPulse,
                enableHaptics = defaults.enableHaptics,
                agentOrchestratorEnabled = defaults.agentOrchestratorEnabled,
                astGraphEnabled = defaults.astGraphEnabled,
                astGraphNodeCap = defaults.astGraphNodeCap,
                focusSchedulerEnabled = defaults.focusSchedulerEnabled
            )
            else -> built.copy(
                filesDefaultPath = defaults.filesDefaultPath,
                filesShowHidden = defaults.filesShowHidden,
                nodeOsLegacyImportEnabled = defaults.nodeOsLegacyImportEnabled,
                workflowJsonPath = defaults.workflowJsonPath,
                nodeosDbPath = defaults.nodeosDbPath
            )
        }
        populateUiFromConfig(updated)
        resetPreviewPending = true
        Toast.makeText(this, "Reset $group preview. Tap SAVE to apply — leaving without SAVE discards it.", Toast.LENGTH_SHORT).show()
    }

    /** Persists the on-screen form. Called by SAVE and automatically on leave (instant apply). */
    private fun persist(finishAfter: Boolean) {
        val updated = buildUpdatedConfig()
        currentConfig = updated
        savedConfigAtLoad = updated
        resetPreviewPending = false
        configManager.saveConfig(updated)
        setResult(RESULT_OK)
        if (finishAfter) finish()
    }

    private fun saveAndApply() {
        // Area 07 step 2: back up the store before a Reset-apply
        // overwrites it (import already backs up in area 01).
        if (resetPreviewPending) configManager.backupCurrentStore()
        persist(true)
        Toast.makeText(this, "Configuration Saved & Applied", Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        // Area 07 step 3 (plan G-05, decided as a BUG, not a
        // product choice): leaving Settings persists the form ONLY
        // if it differs from the last saved config, and NEVER when
        // a Reset/group-reset preview is pending without SAVE.
        // Clean form → leave does not rewrite the file (no mtime
        // churn, no silent Reset-apply). Ordinary edits still
        // instant-apply on leave, as before.
        if (resetPreviewPending) return
        val built = buildUpdatedConfig()
        if (built != savedConfigAtLoad) persist(false)
    }
}
