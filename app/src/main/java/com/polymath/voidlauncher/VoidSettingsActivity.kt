package com.polymath.voidlauncher

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.polymath.voidlauncher.core.ConfigManager
import com.polymath.voidlauncher.databinding.ActivityVoidSettingsBinding
import com.polymath.voidlauncher.model.ColorPalette
import com.polymath.voidlauncher.model.LauncherConfig

class VoidSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoidSettingsBinding
    private lateinit var configManager: ConfigManager
    private var currentConfig: LauncherConfig = LauncherConfig()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoidSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configManager = ConfigManager(this)
        currentConfig = configManager.loadConfig()

        setupSpinners()
        populateUiFromConfig(currentConfig)
        setupListeners()
    }

    private fun setupSpinners() {
        val iconStyles = arrayOf("original", "monochrome", "tinted")
        val iconAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, iconStyles)
        binding.spnIconStyle.adapter = iconAdapter

        val homeModes = arrayOf("COCKPIT", "MINIMAL", "FILES")
        val homeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, homeModes)
        binding.spnHomeMode.adapter = homeAdapter
    }

    private fun populateUiFromConfig(cfg: LauncherConfig) {
        val c = cfg.colors

        binding.etAccentColor.setText(c.accent)
        binding.etSecondaryColor.setText(c.secondary)
        binding.etSurfaceColor.setText(c.surface)

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
            "MINIMAL" -> 1
            "FILES" -> 2
            else -> 0
        }
        binding.spnHomeMode.setSelection(homeModeIdx)

        binding.switchShowTelemetry.isChecked = cfg.showTelemetryBar
        binding.switchShowNodeOs.isChecked = cfg.showNodeOsCard
        binding.switchShowIntent.isChecked = cfg.showIntentCard
        binding.switchShowDock.isChecked = cfg.showDock
        binding.switchShowDigitKeypad.isChecked = cfg.showDigitKeypad

        binding.etFilesDefaultPath.setText(cfg.filesDefaultPath)
        binding.switchFilesShowHidden.isChecked = cfg.filesShowHidden

        binding.etThermalLimit.setText(cfg.thermalLimitCelsius.toString())
        binding.switchGlyphPulse.isChecked = cfg.enableGlyphPulse
        binding.switchHaptics.isChecked = cfg.enableHaptics
    }

    private fun setupListeners() {
        binding.btnSettingsBack.setOnClickListener { finish() }

        // Presets
        binding.presetCrimson.setOnClickListener {
            binding.etAccentColor.setText("#E11D48")
            binding.etSecondaryColor.setText("#06B6D4")
        }
        binding.presetCyan.setOnClickListener {
            binding.etAccentColor.setText("#06B6D4")
            binding.etSecondaryColor.setText("#E11D48")
        }
        binding.presetGreen.setOnClickListener {
            binding.etAccentColor.setText("#10B981")
            binding.etSecondaryColor.setText("#06B6D4")
        }
        binding.presetAmber.setOnClickListener {
            binding.etAccentColor.setText("#F59E0B")
            binding.etSecondaryColor.setText("#E11D48")
        }

        binding.seekDrawerOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvDrawerOpacityLabel.text = "Drawer Opacity: $progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.btnSettingsSave.setOnClickListener {
            saveAndApply()
        }

        binding.btnResetDefaults.setOnClickListener {
            val defaults = LauncherConfig()
            populateUiFromConfig(defaults)
            Toast.makeText(this, "Reset to defaults preview. Tap SAVE to apply.", Toast.LENGTH_SHORT).show()
        }

        binding.btnExportJson.setOnClickListener {
            configManager.exportDefaultConfig()
            Toast.makeText(this, "Exported template to ~/.voidlauncher/config.json", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveAndApply() {
        val accent = binding.etAccentColor.text.toString().trim().ifEmpty { "#E11D48" }
        val secondary = binding.etSecondaryColor.text.toString().trim().ifEmpty { "#06B6D4" }
        val surface = binding.etSurfaceColor.text.toString().trim().ifEmpty { "#080808" }

        val newColors = currentConfig.colors.copy(
            accent = accent,
            secondary = secondary,
            surface = surface
        )

        val opacity = binding.seekDrawerOpacity.progress / 100.0f
        val iconStyle = binding.spnIconStyle.selectedItem?.toString() ?: "original"
        val homeMode = binding.spnHomeMode.selectedItem?.toString() ?: "COCKPIT"
        val thermal = binding.etThermalLimit.text.toString().toFloatOrNull() ?: 39.0f
        val filesPath = binding.etFilesDefaultPath.text.toString().trim().ifEmpty { currentConfig.filesDefaultPath }

        val updated = currentConfig.copy(
            colors = newColors,
            drawerAlpha = opacity,
            drawerEdgeTransparent = binding.switchDrawerEdge.isChecked,
            showAppIcons = binding.switchShowAppIcons.isChecked,
            appIconStyle = iconStyle,
            enableAtoZScrubber = binding.switchAtoZScrubber.isChecked,
            defaultHomeMode = homeMode,
            showTelemetryBar = binding.switchShowTelemetry.isChecked,
            showNodeOsCard = binding.switchShowNodeOs.isChecked,
            showIntentCard = binding.switchShowIntent.isChecked,
            showDock = binding.switchShowDock.isChecked,
            showDigitKeypad = binding.switchShowDigitKeypad.isChecked,
            filesDefaultPath = filesPath,
            filesShowHidden = binding.switchFilesShowHidden.isChecked,
            thermalLimitCelsius = thermal,
            enableGlyphPulse = binding.switchGlyphPulse.isChecked,
            enableHaptics = binding.switchHaptics.isChecked
        )

        configManager.saveConfig(updated)
        Toast.makeText(this, "Configuration Saved & Applied", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }
}
