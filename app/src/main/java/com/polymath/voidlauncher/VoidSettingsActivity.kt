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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoidSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configManager = ConfigManager(this)
        fileManager = FileManager(this)
        currentConfig = configManager.loadConfig()

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

        refreshSystemStatus()
    }

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

        binding.btnSettingsSave.setOnClickListener {
            saveAndApply()
        }

        binding.btnCustomizeDockSlots.setOnClickListener {
            DockSlotMicroService(this).showAllSlotsManagerDialog(
                configProvider = { currentConfig },
                onCommit = { updated ->
                    currentConfig = updated
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
                    binding.switchUseWallpaper.isChecked = updated.useSystemWallpaper
                    configManager.saveConfig(currentConfig)
                }
            )
        }

        binding.btnResetDefaults.setOnClickListener {
            val defaults = LauncherConfig()
            populateUiFromConfig(defaults)
            Toast.makeText(this, "Reset to defaults preview. Tap SAVE to apply.", Toast.LENGTH_SHORT).show()
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
            enableHaptics = binding.switchHaptics.isChecked
        )
    }

    /** Persists the on-screen form. Called by SAVE and automatically on leave (instant apply). */
    private fun persist(finishAfter: Boolean) {
        val updated = buildUpdatedConfig()
        currentConfig = updated
        configManager.saveConfig(updated)
        setResult(RESULT_OK)
        if (finishAfter) finish()
    }

    private fun saveAndApply() {
        persist(true)
        Toast.makeText(this, "Configuration Saved & Applied", Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        // Instant apply: leaving Settings persists the current form, no SAVE required.
        persist(false)
    }
}
