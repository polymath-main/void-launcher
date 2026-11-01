package com.polymath.voidlauncher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.polymath.voidlauncher.core.AgentOrchestrator
import com.polymath.voidlauncher.core.AppIndexer
import com.polymath.voidlauncher.core.AstGraphRenderer
import com.polymath.voidlauncher.core.BareIntegerRouter
import com.polymath.voidlauncher.core.CommandPalette
import com.polymath.voidlauncher.core.CompoundGestureEngine
import com.polymath.voidlauncher.core.ConfigManager
import com.polymath.voidlauncher.core.ConfigSyncService
import com.polymath.voidlauncher.core.FileManager
import com.polymath.voidlauncher.core.FocusScheduler
import com.polymath.voidlauncher.core.GlyphChoreographer
import com.polymath.voidlauncher.core.LiveScriptEngine
import com.polymath.voidlauncher.core.MacroRecorder
import com.polymath.voidlauncher.core.NodeOsBridge
import com.polymath.voidlauncher.core.NothingGlyphManager
import com.polymath.voidlauncher.core.NotifIntelligence
import com.polymath.voidlauncher.core.PersonaManager
import com.polymath.voidlauncher.core.PowerProfileAdaptor
import com.polymath.voidlauncher.core.TelemetryMonitor
import com.polymath.voidlauncher.core.TermuxIpcDispatcher
import com.polymath.voidlauncher.core.ThermalGuardian
import com.polymath.voidlauncher.core.VoidGestureDetector
import com.polymath.voidlauncher.core.VoidNotificationListenerService
import com.polymath.voidlauncher.core.VoidTermPanel
import com.polymath.voidlauncher.core.WidgetRegistry
import com.polymath.voidlauncher.databinding.ActivityVoidLauncherBinding
import com.polymath.voidlauncher.model.AppEntry
import com.polymath.voidlauncher.model.DockSlot
import com.polymath.voidlauncher.model.FileItem
import com.polymath.voidlauncher.model.LauncherConfig
import com.polymath.voidlauncher.ui.AppFilterAdapter
import com.polymath.voidlauncher.ui.DrawerAppAdapter
import com.polymath.voidlauncher.ui.FileBrowserAdapter
import com.polymath.voidlauncher.ui.NotificationAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VoidLauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoidLauncherBinding

    // Core v1 subsystems
    private lateinit var configManager: ConfigManager
    private lateinit var telemetryMonitor: TelemetryMonitor
    private lateinit var termuxIpc: TermuxIpcDispatcher
    private lateinit var nodeOsBridge: NodeOsBridge
    private lateinit var appIndexer: AppIndexer
    private lateinit var bareIntegerRouter: BareIntegerRouter
    private lateinit var glyphManager: NothingGlyphManager
    private lateinit var fileManager: FileManager

    // Blueprint v2 subsystems
    private lateinit var commandPalette: CommandPalette
    private lateinit var thermalGuardian: ThermalGuardian
    private lateinit var configSyncService: ConfigSyncService
    private lateinit var powerProfileAdaptor: PowerProfileAdaptor
    private lateinit var notifIntelligence: NotifIntelligence
    private lateinit var personaManager: PersonaManager
    private lateinit var glyphChoreographer: GlyphChoreographer
    private lateinit var compoundGestureEngine: CompoundGestureEngine
    private lateinit var astGraphRenderer: AstGraphRenderer
    private lateinit var liveScriptEngine: LiveScriptEngine
    private lateinit var agentOrchestrator: AgentOrchestrator
    private lateinit var widgetRegistry: WidgetRegistry
    private lateinit var focusScheduler: FocusScheduler
    private lateinit var voidTermPanel: VoidTermPanel
    private lateinit var macroRecorder: MacroRecorder

    private lateinit var appAdapter: AppFilterAdapter
    private lateinit var drawerAdapter: DrawerAppAdapter
    private lateinit var fileAdapter: FileBrowserAdapter
    private lateinit var notificationAdapter: NotificationAdapter

    private var telemetryJob: Job? = null
    private var notificationJob: Job? = null
    private var config: LauncherConfig = LauncherConfig()
    private var currentFilesPath: String = ""
    private var isDrawerOpen: Boolean = false
    private var gestureSuppressedForTouch: Boolean = false
    private var isKeypadVisible: Boolean = false
    private var isMinimalMode: Boolean = false
    private var isWidgetDeckOpen: Boolean = false
    private var isCommandCenterOpen: Boolean = false

    // STILL Design System Formatters & Audio State
    private var cockpitTimeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
    private var stillTimeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
    private val stillDateFormat = java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.US)

    private fun getAudioModeString(): String {
        return try {
            val am = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return ""
            when (am.ringerMode) {
                android.media.AudioManager.RINGER_MODE_SILENT -> "SILENT"
                android.media.AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
                android.media.AudioManager.RINGER_MODE_NORMAL -> "RING"
                else -> ""
            }
        } catch (_: Exception) { "" }
    }

    companion object {
        private const val REQUEST_DEFAULT_LAUNCHER = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge with transparent system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        binding = ActivityVoidLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        initSubsystems()
        setupListeners()
        applyConfig()
        startTelemetryFlow()
        startNotificationsFlow()
        refreshIndexAsync()

        configSyncService.register()
    }

    override fun onDestroy() {
        super.onDestroy()
        configSyncService.unregister()
    }

    /**
     * Positions the omni-bar smoothly directly on top of the soft keyboard space.
     */
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val isImeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime())

            val bottomPadding = if (isImeVisible) {
                imeInsets.bottom + dpToPx(6)
            } else {
                systemBars.bottom + dpToPx(12)
            }

            binding.bottomAnchorLayout.updatePadding(bottom = bottomPadding)

            view.updatePadding(
                left = systemBars.left + dpToPx(16),
                top = systemBars.top + dpToPx(10),
                right = systemBars.right + dpToPx(16)
            )

            // Right drawer padding
            binding.rightDrawerLayout.updatePadding(
                top = systemBars.top + dpToPx(8),
                bottom = systemBars.bottom + dpToPx(8)
            )

            WindowInsetsCompat.CONSUMED
        }
    }

    private fun initSubsystems() {
        configManager = ConfigManager(this)
        config = configManager.loadConfig()

        telemetryMonitor = TelemetryMonitor(this)
        termuxIpc = TermuxIpcDispatcher(this)
        nodeOsBridge = NodeOsBridge(this)
        appIndexer = AppIndexer(this)
        bareIntegerRouter = BareIntegerRouter(this, termuxIpc)
        glyphManager = NothingGlyphManager(this)
        fileManager = FileManager(this)

        // Initialize Blueprint v2 engines
        commandPalette = CommandPalette(this)
        thermalGuardian = ThermalGuardian(termuxIpc)
        powerProfileAdaptor = PowerProfileAdaptor()
        notifIntelligence = NotifIntelligence()
        personaManager = PersonaManager(this).apply {
            registerConfigProfiles(config.personas)
            if (config.activePersona.isNotBlank()) selectPersona(config.activePersona)
        }
        glyphChoreographer = GlyphChoreographer(this).apply {
            loadCustomPatterns(config.glyphPatternsPath)
        }
        astGraphRenderer = AstGraphRenderer()
        liveScriptEngine = LiveScriptEngine(this)
        agentOrchestrator = AgentOrchestrator(this, termuxIpc).apply {
            workflowPath = config.workflowJsonPath
        }
        widgetRegistry = WidgetRegistry(this).apply {
            if (config.widgetsEnabled) loadWidgets(config.widgetsDirectory)
        }
        focusScheduler = FocusScheduler(this, personaManager, glyphChoreographer).apply {
            if (config.focusSchedulerEnabled) loadSchedule(config.focusSchedulePath)
        }
        voidTermPanel = VoidTermPanel(this, termuxIpc).apply {
            rows = config.voidTermRows
            cols = config.voidTermCols
        }
        macroRecorder = MacroRecorder(this)

        configSyncService = ConfigSyncService(this) {
            reloadConfigFromSync()
        }

        currentFilesPath = fileManager.resolveAccessiblePath(config.filesDefaultPath)

        // Push config into subsystems
        nodeOsBridge.workflowPath = config.workflowJsonPath
        nodeOsBridge.nodeosDbPath = config.nodeosDbPath
        nodeOsBridge.setDefaultIdentity(config.defaultIdentity)
        bareIntegerRouter.updateSlots(config.dockSlots)

        // Wire bare integer custom callbacks
        bareIntegerRouter.onOpenFilesRequested = { toggleFilesBrowser() }
        bareIntegerRouter.onOpenDrawerRequested = { toggleRightDrawer() }
        bareIntegerRouter.onOpenSettingsRequested = { openSettings() }

        // Thermal throttle guardian callbacks
        thermalGuardian.onThrottleTriggered = { tempC, summary ->
            glyphChoreographer.play("THERMAL_WARNING")
            Toast.makeText(this, "⚠ $summary", Toast.LENGTH_LONG).show()
        }

        // App filter adapter (omni search)
        appAdapter = AppFilterAdapter(
            onItemClick = { appEntry ->
                launchApp(entry = appEntry)
                if (isCommandCenterOpen) {
                    openCommandCenter(false)
                }
            },
            onItemLongClick = { appEntry -> showAppMenu(appEntry) }
        )
        binding.rvAppFilter.layoutManager = LinearLayoutManager(this)
        binding.rvAppFilter.adapter = appAdapter

        binding.rvCommandCenterApps.layoutManager = LinearLayoutManager(this)
        binding.rvCommandCenterApps.adapter = appAdapter

        // Right drawer adapter (all apps)
        drawerAdapter = DrawerAppAdapter(
            onItemClick = { appEntry ->
                launchApp(entry = appEntry)
                openRightDrawer(false)
            },
            onItemLongClick = { appEntry -> showAppMenu(appEntry) }
        )
        binding.rvDrawerApps.layoutManager = LinearLayoutManager(this)
        binding.rvDrawerApps.adapter = drawerAdapter

        // File browser adapter
        fileAdapter = FileBrowserAdapter(
            fileManager = fileManager,
            onItemClick = { fileItem -> handleFileItemClick(fileItem) },
            onItemMoreClick = { fileItem, _ -> showFileOperationsDialog(fileItem) }
        )
        binding.rvFiles.layoutManager = LinearLayoutManager(this)
        binding.rvFiles.adapter = fileAdapter

        // Notification cockpit adapter
        notificationAdapter = NotificationAdapter(
            onItemClick = { item ->
                try {
                    item.contentIntent?.send()
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot launch alert: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            onDismissClick = { item ->
                VoidNotificationListenerService.dismiss(item.key)
            }
        )
        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter = notificationAdapter

        // Compound Gesture Engine (F-07)
        compoundGestureEngine = CompoundGestureEngine(this, object : CompoundGestureEngine.CompoundGestureCallbacks {
            override fun onSwipeUp() {
                if (config.defaultHomeMode.uppercase() == "STILL") {
                    openCommandCenter(true)
                } else {
                    handleGestureAction(config.gestureSwipeUp)
                }
            }
            override fun onSwipeDown()          = handleGestureAction(config.gestureSwipeDown)
            override fun onSwipeLeft() {
                if (config.defaultHomeMode.uppercase() == "STILL") {
                    openRightDrawer(true)
                } else {
                    handleGestureAction(config.gestureSwipeLeft)
                }
            }
            override fun onSwipeRight() {
                if (config.defaultHomeMode.uppercase() == "STILL") {
                    openWidgetDeck(true)
                } else {
                    handleGestureAction(config.gestureSwipeRight)
                }
            }
            override fun onDoubleTap()          = handleGestureAction(config.gestureDoubleTap)
            override fun onLongPress()          = handleGestureAction(config.gestureLongPress)
            override fun onPinchIn()            = handleGestureAction(config.gesturePinchIn)
            override fun onPinchOut()           = handleGestureAction(config.gesturePinchOut)
            override fun onTwoFingerSwipeUp()   = handleGestureAction(config.gestureTwoFingerUp)
            override fun onLeftEdgeSwipe()      = handleGestureAction(config.gestureLeftEdgeSwipe)
            override fun onRightEdgeSwipe()     = handleGestureAction(config.gestureRightEdgeSwipe)
            override fun onBottomEdgeSwipe()    = handleGestureAction(config.gestureBottomEdgeSwipe)
        })
    }

    private fun reloadConfigFromSync() {
        config = configManager.loadConfig()
        applyConfig()
        showTerminalOutput("[CONFIG AUTO-RELOADED VIA TERMUX BRIDGE]")
    }

    // ── Visual & Configuration Application ──────────────────

    private fun applyConfig() {
        val c = config.colors

        // Root background
        binding.rootLayout.setBackgroundColor(c.backgroundInt)

        // Telemetry status bar
        val telemetryBarParent = binding.tvClock.parent as? View
        telemetryBarParent?.visibility = if (config.showTelemetryBar) View.VISIBLE else View.GONE

        // Cards visibility
        binding.telemetryCard.visibility = if (config.showNodeOsCard) View.VISIBLE else View.GONE
        binding.intentCard.visibility = if (config.showIntentCard) View.VISIBLE else View.GONE
        binding.notificationsCard.visibility = if (config.showNotificationsCard) View.VISIBLE else View.GONE
        binding.bareIntegerDock.visibility = if (config.showDock) View.VISIBLE else View.GONE
        // Digit keypad: the config toggle controls the [123] launcher button; the
        // keypad itself stays collapsed until that button is pressed.
        binding.btnToggleKeypad.visibility = if (config.showDigitKeypad) View.VISIBLE else View.GONE
        if (!config.showDigitKeypad) isKeypadVisible = false

        // Text colors
        binding.tvClock.setTextColor(c.textPrimaryInt)
        binding.tvMemory.setTextColor(c.textMutedInt)
        binding.tvDaemonStatus.setTextColor(c.secondaryInt)
        binding.tvAstCount.setTextColor(c.textSecondaryInt)
        binding.tvWorkspace.setTextColor(c.textMutedInt)
        binding.tvGitBranch.setTextColor(c.textSecondaryInt)
        binding.tvActiveIntent.setTextColor(c.textPrimaryInt)

        // Card backgrounds
        applyCardBackground(binding.telemetryCard, c.surfaceInt, c.borderInt)
        applyCardBackground(binding.intentCard, c.surfaceInt, c.borderInt)
        applyCardBackground(binding.notificationsCard, c.surfaceInt, c.borderInt)
        applyCardBackground(binding.filesBrowserLayout, c.surfaceInt, c.borderInt)

        // Identity badge
        applyIdentityBadge()

        // Omni bar
        binding.etOmniInput.setTextColor(c.textPrimaryInt)
        binding.etOmniInput.setHintTextColor(c.textMutedInt)
        binding.etOmniInput.hint = config.omniBarHint
        binding.btnExecute.setTextColor(c.secondaryInt)

        val promptView = (binding.etOmniInput.parent as? LinearLayout)?.getChildAt(0) as? TextView
        promptView?.text = config.omniPromptSymbol
        promptView?.setTextColor(c.accentInt)

        // Rebuild dock slots
        rebuildDockSlots()

        // Keypad buttons (dynamically generated from dock slots)
        rebuildKeypad()

        // Drawer background alpha
        val drawerAlphaInt = (config.drawerAlpha * 255).toInt().coerceIn(0, 255)
        binding.rightDrawerLayout.setBackgroundColor(Color.argb(drawerAlphaInt, 8, 8, 8))

        // Drawer width (config: drawer_width_percent)
        val drawerPct = config.drawerWidthPercent.coerceIn(30, 100)
        val screenWidth = resources.displayMetrics.widthPixels
        binding.rightDrawerLayout.layoutParams = binding.rightDrawerLayout.layoutParams.apply {
            width = (screenWidth * drawerPct / 100)
        }

        // Typography (config: font_family / hud_font_size_sp / omni_font_size_sp)
        applyTypography()

        // Clock formatters (config: clock_format / clock_suffix)
        cockpitTimeFormat = java.text.SimpleDateFormat(config.clockFormat, java.util.Locale.US)
        val stillPattern = if (config.clockFormat.contains(":ss")) config.clockFormat.replace(":ss", "") else config.clockFormat
        stillTimeFormat = java.text.SimpleDateFormat(stillPattern.ifBlank { "HH:mm" }, java.util.Locale.US)

        // Persona sync (config: active_persona)
        if (config.activePersona.isNotBlank() &&
            !personaManager.getActivePersona().id.equals(config.activePersona, ignoreCase = true)
        ) {
            personaManager.selectPersona(config.activePersona)
            applyIdentityBadge()
        }

        // Liquid touch surface (config: liquid_touch_enabled)
        binding.liquidSurface.isLiquidEffectEnabled = config.liquidTouchEnabled
        binding.liquidSurface.rippleColor = c.accentInt
        binding.liquidSurface.visibility = if (config.liquidTouchEnabled) View.VISIBLE else View.GONE

        // VoidTerm viewport (config: voidterm_rows / voidterm_cols)
        voidTermPanel.rows = config.voidTermRows
        voidTermPanel.cols = config.voidTermCols

        // Apply app icon customizations to adapters
        appAdapter.showIcons = config.showAppIcons
        drawerAdapter.showIcons = config.showAppIcons
        appAdapter.iconStyle = config.appIconStyle
        drawerAdapter.iconStyle = config.appIconStyle

        // Scrubber visibility
        binding.alphabetScrubber.visibility = if (config.enableAtoZScrubber) View.VISIBLE else View.GONE

        // Wallpaper flag
        if (config.useSystemWallpaper) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            binding.rootLayout.setBackgroundColor(Color.TRANSPARENT)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            binding.rootLayout.setBackgroundColor(c.backgroundInt)
        }

        // Status bar element visibility
        binding.tvClock.visibility = if (config.showStatusBarTime) View.VISIBLE else View.GONE
        binding.tvMemory.visibility = if (config.showStatusBarRam) View.VISIBLE else View.GONE
        binding.tvBatteryTemp.visibility = if (config.showStatusBarBattery) View.VISIBLE else View.GONE

        applyHomeMode()
        checkDefaultLauncherStatus()
    }

    private fun checkDefaultLauncherStatus() {
        val isDefault = isDefaultLauncher()
        binding.bannerDefaultLauncher.visibility = if (!isDefault) View.VISIBLE else View.GONE
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
            checkDefaultLauncherStatus()
        }
    }

    /** Switches the home/workspace mode and persists it so it survives restart. */
    private fun setHomeMode(mode: String) {
        config = config.copy(defaultHomeMode = mode.uppercase())
        configManager.saveConfig(config)
        applyConfig()
    }

    private fun applyHomeMode() {
        binding.stillWidgetDeckLayout.visibility = View.GONE
        binding.stillCommandCenterLayout.visibility = View.GONE
        isWidgetDeckOpen = false
        isCommandCenterOpen = false

        when (config.defaultHomeMode.uppercase()) {
            "STILL" -> {
                binding.telemetryCard.visibility = View.GONE
                binding.intentCard.visibility = View.GONE
                binding.notificationsCard.visibility = View.GONE
                binding.bareIntegerDock.visibility = View.GONE
                isKeypadVisible = false
                binding.digitKeypadRow.visibility = View.GONE
                binding.btnToggleKeypad.visibility = View.GONE
                binding.filesBrowserLayout.visibility = View.GONE
                binding.stillHomeLayout.visibility = View.VISIBLE
                rebuildStillFavorites()
                updateStillClockAndVitals()
            }
            "MINIMAL" -> {
                binding.telemetryCard.visibility = View.GONE
                binding.intentCard.visibility = View.GONE
                binding.notificationsCard.visibility = View.GONE
                binding.bareIntegerDock.visibility = View.GONE
                isKeypadVisible = false
                binding.digitKeypadRow.visibility = View.GONE
                binding.btnToggleKeypad.visibility = View.GONE
                binding.filesBrowserLayout.visibility = View.GONE
                binding.stillHomeLayout.visibility = View.GONE
            }
            "FILES" -> {
                binding.filesBrowserLayout.visibility = View.VISIBLE
                binding.stillHomeLayout.visibility = View.GONE
                loadFilesDirectory(currentFilesPath)
            }
            else -> {
                // COCKPIT
                binding.btnToggleKeypad.visibility = if (config.showDigitKeypad) View.VISIBLE else View.GONE
                binding.filesBrowserLayout.visibility = View.GONE
                binding.stillHomeLayout.visibility = View.GONE
            }
        }
    }

    private fun rebuildStillFavorites() {
        val container = binding.layoutStillFavorites
        container.removeAllViews()
        val c = config.colors

        for (slot in config.dockSlots) {
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(54)
                ).apply {
                    bottomMargin = dpToPx(6)
                }
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
                background = GradientDrawable().apply {
                    setColor(c.surfaceInt)
                    setStroke(dpToPx(1), c.borderInt)
                    cornerRadius = dpToPxF(config.cockpitCardRadiusDp)
                }
                isClickable = true
                isFocusable = true

                val tvLabel = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    text = slot.label
                    setTextColor(c.textPrimaryInt)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                }

                val tvSub = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    text = slot.subtitle.ifEmpty { slot.action }
                    setTextColor(c.textMutedInt)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    typeface = Typeface.MONOSPACE
                }

                addView(tvLabel)
                addView(tvSub)

                setOnClickListener {
                    haptic(it)
                    bareIntegerRouter.routeSlot(slot)
                }
            }
            container.addView(row)
        }
    }

    private fun updateStillClockAndVitals(snap: com.polymath.voidlauncher.model.TelemetrySnapshot? = null) {
        val now = java.util.Date()
        binding.tvStillClock.text = stillTimeFormat.format(now)
        binding.tvStillDate.text = stillDateFormat.format(now).uppercase(java.util.Locale.US)

        val s = snap ?: telemetryMonitor.captureSnapshot()
        val audioMode = getAudioModeString()
        val audioPart = if (audioMode.isNotEmpty()) " · $audioMode" else ""
        binding.tvStillStatus.text = "▲ ${s.batteryPercent}% [${s.batteryTemperatureC}°C]$audioPart"
    }

    private fun openWidgetDeck(open: Boolean) {
        isWidgetDeckOpen = open
        if (open) {
            binding.stillWidgetDeckLayout.visibility = View.VISIBLE
            binding.stillHomeLayout.visibility = View.GONE
            binding.filesBrowserLayout.visibility = View.GONE
            openRightDrawer(false)
            if (isCommandCenterOpen) openCommandCenter(false)
            refreshWidgetDeckContent()
        } else {
            binding.stillWidgetDeckLayout.visibility = View.GONE
            if (config.defaultHomeMode.uppercase() == "STILL") {
                binding.stillHomeLayout.visibility = View.VISIBLE
            }
        }
    }

    private fun refreshWidgetDeckContent() {
        val snap = telemetryMonitor.captureSnapshot()
        val audioMode = getAudioModeString()
        val audioPart = if (audioMode.isNotEmpty()) " · $audioMode" else ""
        val usedGb = String.format(java.util.Locale.US, "%.1f", snap.ramUsedMb / 1024.0)
        binding.tvDeckVitals.text = "▲ ${snap.batteryPercent}% [${snap.batteryTemperatureC}°C] · RAM: ${usedGb}GB"
        binding.tvDeckAudioMode.text = "RINGER: ${audioMode.ifEmpty { "NORMAL" }}"

        val tasks = agentOrchestrator.getAllTasks()
        if (tasks.isEmpty()) {
            binding.tvDeckTasks.text = "No active agent background tasks"
        } else {
            val sb = java.lang.StringBuilder()
            for (t in tasks.take(5)) {
                sb.appendLine("● [${t.status.uppercase()}] ${t.title}")
            }
            binding.tvDeckTasks.text = sb.toString().trimEnd()
        }

        // Pluggable widgets
        val widgets = widgetRegistry.loadWidgets(config.widgetsDirectory)
        binding.tvDeckWidgetCount.text = "${widgets.size} WIDGETS"
        val container = binding.layoutDeckWidgets
        container.removeAllViews()
        val c = config.colors
        for (w in widgets) {
            val card = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dpToPx(8) }
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
                background = GradientDrawable().apply {
                    setColor(c.surfaceInt)
                    setStroke(dpToPx(1), c.borderInt)
                    cornerRadius = dpToPxF(config.cockpitCardRadiusDp)
                }

                val title = TextView(context).apply {
                    text = "● ${w.title.uppercase()}"
                    setTextColor(Color.parseColor("#007AFF"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                    setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                }
                val content = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dpToPx(4) }
                    text = widgetRegistry.formatWidgetContent(w)
                    setTextColor(c.textPrimaryInt)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    typeface = Typeface.MONOSPACE
                }
                addView(title)
                addView(content)
                if (w.actionOnClick.isNotEmpty()) {
                    setOnClickListener {
                        haptic(it)
                        handleOmniSubmit(w.actionOnClick)
                    }
                }
            }
            container.addView(card)
        }
    }

    private fun openCommandCenter(open: Boolean) {
        isCommandCenterOpen = open
        if (open) {
            binding.stillCommandCenterLayout.visibility = View.VISIBLE
            binding.etCommandCenterInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.etCommandCenterInput, InputMethodManager.SHOW_IMPLICIT)
            if (isWidgetDeckOpen) openWidgetDeck(false)
            if (isDrawerOpen) openRightDrawer(false)
        } else {
            binding.stillCommandCenterLayout.visibility = View.GONE
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(binding.etCommandCenterInput.windowToken, 0)
            binding.etCommandCenterInput.setText("")
        }
    }

    private fun handleCommandCenterChip(cmd: String) {
        haptic(binding.root)
        binding.etCommandCenterInput.setText(cmd)
        handleCommandCenterSubmit(cmd)
    }

    private fun handleCommandCenterTextChange(text: String) {
        if (text.isEmpty() || text.startsWith(":")) {
            binding.rvCommandCenterApps.visibility = View.GONE
            binding.scrollCommandCenterOutput.visibility = View.VISIBLE
            return
        }
        val matches = appIndexer.filterApps(text, maxResults = 10)
        if (matches.isNotEmpty()) {
            binding.rvCommandCenterApps.visibility = View.VISIBLE
            binding.scrollCommandCenterOutput.visibility = View.GONE
            appAdapter.submitList(matches)
        } else {
            binding.rvCommandCenterApps.visibility = View.GONE
            binding.scrollCommandCenterOutput.visibility = View.VISIBLE
        }
    }

    private fun handleCommandCenterSubmit(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        recordCommandHistory(q)
        if (q.startsWith(":")) {
            handleCommandMode(q.substring(1).trim())
            binding.tvCommandCenterOutput.text = binding.tvTerminalOutput.text
            binding.scrollCommandCenterOutput.visibility = View.VISIBLE
            binding.rvCommandCenterApps.visibility = View.GONE
            return
        }
        val integerCommand = q.toIntOrNull()
        if (integerCommand != null) {
            bareIntegerRouter.routeInteger(integerCommand)
            openCommandCenter(false)
            return
        }
        val bestMatch = appIndexer.findBestMatch(q)
        if (bestMatch != null) {
            launchApp(bestMatch)
            openCommandCenter(false)
        } else {
            binding.tvCommandCenterOutput.text = "[NOT FOUND]: $q\nNo matching app or command."
            binding.scrollCommandCenterOutput.visibility = View.VISIBLE
            binding.rvCommandCenterApps.visibility = View.GONE
        }
    }

    private fun applyIdentityBadge() {
        val c = config.colors
        val persona = personaManager.getActivePersona()
        val label = when (persona.id.lowercase()) {
            "void" -> config.identityVoidLabel.ifBlank { persona.label }
            "main" -> config.identityMainLabel.ifBlank { persona.label }
            else -> persona.label
        }
        binding.tvIdentityBadge.text = label
        val accentCol = Color.parseColor(persona.accentColor)
        binding.tvIdentityBadge.setTextColor(accentCol)
        applyBadgeBackground(binding.tvIdentityBadge, c.surfaceInt, accentCol)
    }

    private fun resolveTypeface(family: String): Typeface = when (family.lowercase().trim()) {
        "sans-serif", "sans", "sansserif" -> Typeface.SANS_SERIF
        "serif" -> Typeface.SERIF
        "cursive" -> Typeface.SERIF
        else -> Typeface.MONOSPACE
    }

    private fun applyTypography() {
        val tf = resolveTypeface(config.fontFamily)
        val hudBold = Typeface.create(tf, Typeface.BOLD)
        val hudViews = listOf(
            binding.tvClock, binding.tvMemory, binding.tvBatteryTemp,
            binding.tvDaemonStatus, binding.tvAstCount, binding.tvWorkspace,
            binding.tvGitBranch, binding.tvActiveIntent
        )
        for (v in hudViews) {
            v.typeface = tf
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.hudFontSizeSp)
        }
        binding.tvClock.typeface = hudBold
        binding.tvBatteryTemp.typeface = hudBold
        binding.etOmniInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.omniFontSizeSp)
        binding.etCommandCenterInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.omniFontSizeSp)
    }

    private fun rebuildDockSlots() {
        val c = config.colors
        // IMPORTANT: bind by slot.index, not list position. The user can add a
        // high-index slot (e.g. 7) while leaving lower indices empty, so the list
        // is sparse and positional binding would paint the wrong view.
        val slotViews = listOf(
            binding.btnSlot1, binding.btnSlot2, binding.btnSlot3,
            binding.btnSlot4, binding.btnSlot5, binding.btnSlot6, binding.btnSlot7
        )

        for ((i, slotView) in slotViews.withIndex()) {
            val slot = config.dockSlots.firstOrNull { it.index == (i + 1) }
            if (slot != null) {
                slotView.visibility = View.VISIBLE

                val labelView = (slotView as? LinearLayout)?.getChildAt(0) as? TextView
                val subtitleView = (slotView as? LinearLayout)?.getChildAt(1) as? TextView

                labelView?.text = "[${slot.index}] ${slot.label}"
                labelView?.setTextColor(c.textPrimaryInt)
                labelView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.dockFontSizeSp)

                subtitleView?.text = slot.subtitle
                subtitleView?.setTextColor(c.textMutedInt)
            } else {
                slotView.visibility = View.GONE
            }
        }
    }

    /**
     * Digit keypad is generated in code from the configured dock slots (1..N, max 7).
     * One key per slot plus the `:` command prefix and backspace. The row stays
     * collapsed until the [123] toggle is pressed (isKeypadVisible).
     */
    private fun rebuildKeypad() {
        val row = binding.digitKeypadRow
        row.removeAllViews()
        val c = config.colors

        for (slot in config.dockSlots.sortedBy { it.index }) {
            row.addView(buildKeypadKey(slot.index.toString(), c.textSecondaryInt) { view ->
                handleDigitKey(view, slot.index)
            })
        }

        row.addView(buildKeypadKey(":", c.accentInt) { view ->
            haptic(view)
            binding.etOmniInput.setText(":")
            binding.etOmniInput.setSelection(1)
            binding.etOmniInput.requestFocus()
        })

        row.addView(buildKeypadKey("⌫", c.textMutedInt) { view ->
            haptic(view)
            val text = binding.etOmniInput.text.toString()
            if (text.isNotEmpty()) {
                binding.etOmniInput.setText(text.substring(0, text.length - 1))
                binding.etOmniInput.setSelection(binding.etOmniInput.text.length)
            }
        })

        row.visibility = if (isKeypadVisible && config.showDigitKeypad) View.VISIBLE else View.GONE
    }

    private fun buildKeypadKey(label: String, textColor: Int, onClick: (View) -> Unit): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(32), 1f).apply {
                marginEnd = dpToPx(3)
            }
            // Fresh drawable per key so pressed-state does not bleed across buttons.
            background = ContextCompat.getDrawable(this@VoidLauncherActivity, R.drawable.bg_digit_btn)
            gravity = android.view.Gravity.CENTER
            typeface = Typeface.MONOSPACE
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick(it) }
        }
    }

    private fun toggleKeypad() {
        haptic(binding.root)
        isKeypadVisible = !isKeypadVisible
        binding.digitKeypadRow.visibility = if (isKeypadVisible) View.VISIBLE else View.GONE
    }

    private fun applyCardBackground(view: View, surfaceColor: Int, borderColor: Int) {
        val drawable = GradientDrawable().apply {
            setColor(surfaceColor)
            setStroke(dpToPx(1), borderColor)
            cornerRadius = dpToPxF(config.cockpitCardRadiusDp)
        }
        view.background = drawable
    }

    private fun applyBadgeBackground(view: View, surfaceColor: Int, borderColor: Int) {
        val drawable = GradientDrawable().apply {
            setColor(surfaceColor)
            setStroke(dpToPx(1), borderColor)
            cornerRadius = dpToPxF(4f)
        }
        view.background = drawable
    }

    // ── Listeners ──────────────────────────────────────────

    private fun setupListeners() {
        binding.btnSetDefaultLauncher.setOnClickListener {
            haptic(it)
            requestSetDefaultLauncher()
        }

        binding.btnQuickSettings.setOnClickListener {
            haptic(it)
            openSettings()
        }

        binding.btnSlot1.setOnClickListener { handleDigitKey(it, 1) }
        binding.btnSlot2.setOnClickListener { handleDigitKey(it, 2) }
        binding.btnSlot3.setOnClickListener { handleDigitKey(it, 3) }
        binding.btnSlot4.setOnClickListener { handleDigitKey(it, 4) }
        binding.btnSlot5.setOnClickListener { handleDigitKey(it, 5) }
        binding.btnSlot6.setOnClickListener { handleDigitKey(it, 6) }
        binding.btnSlot7.setOnClickListener { handleDigitKey(it, 7) }

        // Dynamic digit keypad toggle (keys themselves are built in rebuildKeypad)
        binding.btnToggleKeypad.setOnClickListener { toggleKeypad() }

        // Smart Persona cycle toggle (F-02)
        binding.tvIdentityBadge.setOnClickListener {
            val newPersona = personaManager.cyclePersona()
            config = config.copy(activePersona = newPersona.id)
            configManager.saveConfig(config)
            applyIdentityBadge()
            glyphChoreographer.play("FOCUS_MODE_ON")
            Toast.makeText(this, "Persona: ${newPersona.label} (${newPersona.subtitle})", Toast.LENGTH_SHORT).show()
        }

        // Notification Cockpit Controls
        binding.btnNotifClearAll.setOnClickListener {
            haptic(it)
            VoidNotificationListenerService.dismissAll()
        }

        binding.btnGrantNotifAccess.setOnClickListener {
            haptic(it)
            VoidNotificationListenerService.openAccessSettings(this)
        }

        // Omni Input Text Watcher
        binding.etOmniInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                handleOmniTextChange(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Omni Input Keyboard Action
        binding.etOmniInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                handleOmniSubmit(binding.etOmniInput.text.toString())
                true
            } else false
        }

        binding.btnExecute.setOnClickListener {
            handleOmniSubmit(binding.etOmniInput.text.toString())
        }

        binding.btnDrawerTabHandle.setOnClickListener {
            haptic(it)
            toggleRightDrawer()
        }
        binding.btnDrawerClose.setOnClickListener {
            openRightDrawer(false)
        }

        binding.alphabetScrubber.setOnLetterSelectedListener { letter ->
            val sectionMap = appIndexer.getSectionPositions()
            val targetPos = sectionMap[letter]
            if (targetPos != null) {
                (binding.rvDrawerApps.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(targetPos, 0)
            }
        }

        // Files Browser Header Buttons & Quick Root Chips
        binding.btnFilesUp.setOnClickListener {
            haptic(it)
            val parent = File(currentFilesPath).parentFile
            if (parent != null && parent.exists() && parent.canRead()) {
                loadFilesDirectory(parent.absolutePath)
            } else {
                Toast.makeText(this, "At top directory", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnFilesNewFolder.setOnClickListener {
            haptic(it)
            showCreateDirectoryDialog()
        }

        binding.btnFilesClose.setOnClickListener {
            showFilesBrowser(false)
        }

        binding.btnChipSdcard.setOnClickListener {
            haptic(it)
            loadFilesDirectory(FileManager.PATH_SDCARD)
        }

        binding.btnChipDownload.setOnClickListener {
            haptic(it)
            loadFilesDirectory(FileManager.PATH_DOWNLOADS)
        }

        binding.btnChipProjects.setOnClickListener {
            haptic(it)
            loadFilesDirectory(FileManager.PATH_PROJECTS)
        }

        binding.btnChipTermux.setOnClickListener {
            haptic(it)
            loadFilesDirectory(FileManager.PATH_TERMUX_HOME)
        }

        binding.btnGrantStoragePermission.setOnClickListener {
            haptic(it)
            fileManager.requestStoragePermission(this)
        }

        // STILL Workspace: Widget Deck
        binding.btnDeckBackHome.setOnClickListener {
            haptic(it)
            openWidgetDeck(false)
        }

        // STILL Workspace: Command Center
        binding.btnCommandCenterClose.setOnClickListener {
            haptic(it)
            openCommandCenter(false)
        }
        binding.btnCommandCenterClear.setOnClickListener {
            haptic(it)
            binding.etCommandCenterInput.setText("")
        }

        binding.chipCmdStill.setOnClickListener { handleCommandCenterChip(":still") }
        binding.chipCmdCockpit.setOnClickListener { handleCommandCenterChip(":cockpit") }
        binding.chipCmdFiles.setOnClickListener { handleCommandCenterChip(":files") }
        binding.chipCmdTasks.setOnClickListener { handleCommandCenterChip(":tasks") }
        binding.chipCmdGraph.setOnClickListener { handleCommandCenterChip(":graph") }
        binding.chipCmdPower.setOnClickListener { handleCommandCenterChip(":power") }
        binding.chipCmdNotif.setOnClickListener { handleCommandCenterChip(":notif") }
        binding.chipCmdSettings.setOnClickListener { handleCommandCenterChip(":settings") }

        binding.etCommandCenterInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                handleCommandCenterTextChange(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etCommandCenterInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                handleCommandCenterSubmit(binding.etCommandCenterInput.text.toString())
                true
            } else false
        }
    }

    // ── Right Apps Drawer ───────────────────────────────────

    private fun toggleRightDrawer() {
        openRightDrawer(!isDrawerOpen)
    }

    private fun openRightDrawer(open: Boolean) {
        isDrawerOpen = open
        if (open) {
            val allApps = appIndexer.getAllApps()
            drawerAdapter.submitList(allApps)
            binding.tvDrawerAppCount.text = "${allApps.size} APPS"
            binding.rightDrawerLayout.visibility = View.VISIBLE
            binding.btnDrawerTabHandle.text = "›"
            binding.filesBrowserLayout.visibility = View.GONE
            binding.stillHomeLayout.visibility = View.GONE
        } else {
            binding.rightDrawerLayout.visibility = View.GONE
            binding.btnDrawerTabHandle.text = "‹"
            if (config.defaultHomeMode.uppercase() == "STILL" && binding.filesBrowserLayout.visibility != View.VISIBLE && binding.tvTerminalOutput.visibility != View.VISIBLE) {
                binding.stillHomeLayout.visibility = View.VISIBLE
            }
        }
    }

    // ── Files Browser & Operations ──────────────────────────

    private fun toggleFilesBrowser() {
        val willShow = binding.filesBrowserLayout.visibility != View.VISIBLE
        showFilesBrowser(willShow)
    }

    private fun showFilesBrowser(show: Boolean) {
        if (show) {
            binding.filesBrowserLayout.visibility = View.VISIBLE
            binding.rvAppFilter.visibility = View.GONE
            binding.tvTerminalOutput.visibility = View.GONE
            binding.stillHomeLayout.visibility = View.GONE
            loadFilesDirectory(currentFilesPath.ifEmpty { config.filesDefaultPath })
            openRightDrawer(false)
        } else {
            binding.filesBrowserLayout.visibility = View.GONE
            if (config.defaultHomeMode.uppercase() == "STILL") {
                binding.stillHomeLayout.visibility = View.VISIBLE
            } else if (config.showDock) {
                binding.bareIntegerDock.visibility = View.VISIBLE
            }
        }
    }

    private fun loadFilesDirectory(path: String) {
        val targetPath = fileManager.resolveAccessiblePath(path)
        currentFilesPath = targetPath
        binding.tvFilesCurrentPath.text = targetPath.replace("/data/data/com.termux/files/home", "~")

        val hasStorage = fileManager.hasStoragePermission()
        binding.storagePermissionCard.visibility = if (hasStorage) View.GONE else View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val items = fileManager.listDirectory(targetPath, showHidden = config.filesShowHidden)
            withContext(Dispatchers.Main) {
                fileAdapter.submitList(items)
                if (items.isEmpty()) {
                    binding.tvFilesEmptyState.visibility = View.VISIBLE
                    if (fileManager.isTermuxPrivatePath(targetPath)) {
                        binding.tvFilesEmptyState.text = "Termux private directory (~/Projects) is restricted by Android OS DAC.\nTap [OPEN IN TERMUX] or switch to /sdcard."
                    } else if (!hasStorage) {
                        binding.tvFilesEmptyState.text = "All Files Access permission required to list /sdcard contents."
                    } else {
                        binding.tvFilesEmptyState.text = "Directory is empty: $targetPath"
                    }
                } else {
                    binding.tvFilesEmptyState.visibility = View.GONE
                }
            }
        }
    }

    private fun handleFileItemClick(item: FileItem) {
        if (item.isDirectory) {
            loadFilesDirectory(item.path)
        } else {
            val preview = fileManager.readTextPreview(item.path, maxLines = 40)
            showTerminalOutput("[FILE PREVIEW: ${item.name}]\n\n$preview")
        }
    }

    private fun showFileOperationsDialog(item: FileItem) {
        val options = arrayOf(
            "⚡ Open in Termux (${if (item.isDirectory) "cd here" else "cat / run"})",
            "📄 Read Text Preview",
            "📋 Copy Full Path",
            "✏️ Rename",
            "🗑 Delete"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(item.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (item.isDirectory) {
                            termuxIpc.dispatchCommand("cd \"${item.path}\" && exec bash", workDir = item.path, inBackground = false)
                        } else {
                            val parentDir = File(item.path).parent ?: config.defaultWorkspacePath
                            termuxIpc.dispatchCommand("cd \"$parentDir\" && termux-open \"${item.path}\"", inBackground = false)
                        }
                    }
                    1 -> {
                        val preview = fileManager.readTextPreview(item.path, maxLines = 60)
                        showTerminalOutput("[PREVIEW: ${item.name}]\n\n$preview")
                    }
                    2 -> {
                        termuxIpc.setClipboardText(item.path)
                    }
                    3 -> {
                        showRenameDialog(item)
                    }
                    4 -> {
                        showDeleteConfirmDialog(item)
                    }
                }
            }
            .show()
    }

    private fun showRenameDialog(item: FileItem) {
        val input = EditText(this).apply {
            setText(item.name)
            setSelection(item.name.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Rename ${item.name}")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != item.name) {
                    val ok = fileManager.renameFile(item.path, newName)
                    if (ok) {
                        Toast.makeText(this, "Renamed to $newName", Toast.LENGTH_SHORT).show()
                        loadFilesDirectory(currentFilesPath)
                    } else {
                        Toast.makeText(this, "Rename failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmDialog(item: FileItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${item.name}?")
            .setMessage("This operation is permanent.")
            .setPositiveButton("Delete") { _, _ ->
                val ok = fileManager.deleteFile(item.path)
                if (ok) {
                    Toast.makeText(this, "Deleted: ${item.name}", Toast.LENGTH_SHORT).show()
                    loadFilesDirectory(currentFilesPath)
                } else {
                    Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCreateDirectoryDialog() {
        val input = EditText(this).apply {
            hint = "Folder name"
        }
        AlertDialog.Builder(this)
            .setTitle("Create New Directory")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val ok = fileManager.createFolder(currentFilesPath, name)
                    if (ok) {
                        Toast.makeText(this, "Created $name", Toast.LENGTH_SHORT).show()
                        loadFilesDirectory(currentFilesPath)
                    } else {
                        Toast.makeText(this, "Failed to create directory", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Telemetry & Polling Flows ───────────────────────────

    private fun startTelemetryFlow() {
        telemetryJob?.cancel()
        telemetryJob = lifecycleScope.launch {
            // Adaptive cadence (config: telemetry_poll_ms / power_profile_adaptive).
            // The provider is re-evaluated before every delay, so the interval reacts
            // to battery/thermal changes without restarting the flow.
            val intervalProvider: () -> Long = {
                if (config.powerProfileAdaptive) {
                    powerProfileAdaptor.getRecommendedPollIntervalMs()
                } else {
                    config.telemetryPollMs
                }
            }
            telemetryMonitor.monitorTelemetry(
                pollIntervalMs = config.telemetryPollMs,
                intervalProvider = intervalProvider
            ).collectLatest { snap ->
                val powerLevel = powerProfileAdaptor.evaluate(snap, config.thermalLimitCelsius)

                binding.tvClock.text = cockpitTimeFormat.format(java.util.Date()) + config.clockSuffix
                binding.tvBatteryTemp.text = "▲ ${snap.batteryPercent}% [${snap.batteryTemperatureC}°C]"
                val usedGb = String.format(java.util.Locale.US, "%.1f", snap.ramUsedMb / 1024.0)
                binding.tvMemory.text = "RAM: ${usedGb}G ${powerLevel.label}"

                // Thermal guardian envelope check (F-06)
                if (config.thermalGuardianEnabled) {
                    thermalGuardian.evaluateThermalEnvelope(
                        snap.batteryTemperatureC,
                        config.thermalLimitCelsius,
                        config.thermalHysteresisCelsius,
                        config.thermalKillCommands
                    )
                } else if (snap.batteryTemperatureC > config.thermalLimitCelsius) {
                    glyphManager.triggerThermalWarning()
                }

                // Focus mode schedule tick (F-10)
                if (config.focusSchedulerEnabled) {
                    focusScheduler.checkAndApplySchedule()
                }

                updateNodeOsHudView()

                // STILL clock & vitals live update
                if (config.defaultHomeMode.uppercase() == "STILL") {
                    updateStillClockAndVitals(snap)
                }
            }
        }
    }

    private fun startNotificationsFlow() {
        notificationJob?.cancel()
        notificationJob = lifecycleScope.launch {
            VoidNotificationListenerService.notificationsFlow.collect { rawList ->
                val isAccessGranted = VoidNotificationListenerService.isAccessGranted(this@VoidLauncherActivity)
                if (!isAccessGranted) {
                    binding.notifPermissionPrompt.visibility = View.VISIBLE
                    binding.tvNotifEmpty.visibility = View.GONE
                    binding.rvNotifications.visibility = View.GONE
                    binding.tvNotifHeader.text = "● NOTIFICATIONS (ACCESS OFF)"
                    return@collect
                }

                binding.notifPermissionPrompt.visibility = View.GONE

                // Notification Intelligence priority scoring & filtering (F-11)
                val filtered = notifIntelligence.scoreAndFilter(
                    items = rawList,
                    whitelist = config.notifWhitelistPackages,
                    blacklist = config.notifBlacklistPackages,
                    priorityKeywords = config.notifPriorityKeywords,
                    filterOngoing = config.filterOngoingNotifications,
                    maxCount = config.notificationsMaxCount
                )

                binding.tvNotifHeader.text = "● NOTIFICATIONS (${filtered.size})"

                if (filtered.isEmpty()) {
                    binding.tvNotifEmpty.visibility = View.VISIBLE
                    binding.rvNotifications.visibility = View.GONE
                    notificationAdapter.submitList(emptyList())
                } else {
                    binding.tvNotifEmpty.visibility = View.GONE
                    binding.rvNotifications.visibility = View.VISIBLE
                    notificationAdapter.submitList(filtered)
                }
            }
        }
    }

    private fun updateNodeOsHudView() {
        val status = nodeOsBridge.pollIntentStatus()
        binding.tvDaemonStatus.text = if (status.isDaemonAlive) "● NODEOS: ALIVE" else "○ NODEOS: OFFLINE"
        binding.tvDaemonStatus.setTextColor(
            if (status.isDaemonAlive) config.colors.secondaryInt else config.colors.criticalInt
        )

        binding.tvAstCount.text = "AST: ${status.astNodeCount}"
        binding.tvWorkspace.text = "CWD: ${status.workspacePath}"
        binding.tvGitBranch.text = "git: ${status.gitBranch}"

        binding.tvActiveIntent.text = "[${status.taskStatus.uppercase()}] ${status.activeTaskTitle}"
    }

    private fun refreshIndexAsync() {
        lifecycleScope.launch(Dispatchers.IO) {
            appIndexer.refreshIndex()
        }
    }

    // ── Omni-Bar Operations ─────────────────────────────────

    private fun handleOmniTextChange(text: String) {
        if (text.startsWith(":")) {
            binding.rvAppFilter.visibility = View.GONE
            binding.tvTerminalOutput.visibility = View.GONE
            return
        }

        if (text.isEmpty()) {
            binding.rvAppFilter.visibility = View.GONE
            if (config.showDock) binding.bareIntegerDock.visibility = View.VISIBLE
            return
        }

        // Fuzzy filter apps
        binding.bareIntegerDock.visibility = View.GONE
        binding.filesBrowserLayout.visibility = View.GONE
        binding.tvTerminalOutput.visibility = View.GONE

        val matches = appIndexer.filterApps(text, maxResults = config.omniFuzzyMaxResults)
        appAdapter.submitList(matches)
        binding.rvAppFilter.visibility = if (matches.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun handleOmniSubmit(rawInput: String) {
        val query = rawInput.trim()
        if (query.isEmpty()) return

        // Record history & macro step (F-09, F-15)
        recordCommandHistory(query)
        if (macroRecorder.isRecording) {
            macroRecorder.recordStep(query)
        }

        // 1. Bare integer fast intercept
        val integerCommand = query.toIntOrNull()
        if (integerCommand != null) {
            bareIntegerRouter.routeInteger(integerCommand)
            resetOmniInput()
            return
        }

        // 2. Colon commands
        if (query.startsWith(":")) {
            handleCommandMode(query.substring(1).trim())
            resetOmniInput()
            return
        }

        // 3. App fuzzy launch
        val bestMatch = appIndexer.findBestMatch(query)
        if (bestMatch != null) {
            launchApp(bestMatch)
            resetOmniInput()
        } else {
            Toast.makeText(this, "No matching app or command", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCommandMode(command: String) {
        when {
            // F-01 LiveScript Eval
            command.startsWith("eval ") -> {
                val expr = command.removePrefix("eval ").trim()
                val result = liveScriptEngine.evaluate(expr)
                showTerminalOutput("[EVAL: $expr] (${result.executionTimeMs}ms)\n\n= ${result.output}")
            }
            // F-08 Agent Task Orchestrator
            command.startsWith("task ") -> {
                if (!config.agentOrchestratorEnabled) {
                    showTerminalOutput("[AGENT GATE] Orchestrator disabled (agent_orchestrator_enabled=false)")
                } else {
                    val args = command.removePrefix("task ").trim()
                    val parts = args.split(" ", limit = 2)
                    val title = parts.getOrNull(0) ?: "Task"
                    val cmd = parts.getOrNull(1) ?: "echo 'no command'"
                    val task = agentOrchestrator.dispatchTask(title, cmd)
                    glyphChoreographer.play("TASK_SUCCESS")
                    showTerminalOutput("[TASK DISPATCHED]: ${task.title}\nCMD: ${task.command}")
                }
            }
            command == "tasks" -> {
                if (!config.agentOrchestratorEnabled) {
                    showTerminalOutput("[AGENT GATE] Orchestrator disabled (agent_orchestrator_enabled=false)")
                } else {
                    val tasks = agentOrchestrator.getAllTasks()
                    val sb = StringBuilder("=== Active Agent Tasks (${tasks.size}) ===\n\n")
                    for (t in tasks.take(10)) {
                        sb.appendLine("[${t.status.uppercase()}] ${t.title} (id: ${t.id})")
                    }
                    showTerminalOutput(sb.toString().trimEnd())
                }
            }
            command.startsWith("kill ") -> {
                val taskId = command.removePrefix("kill ").trim()
                agentOrchestrator.cancelTask(taskId)
                showTerminalOutput("[TASK CANCELLED]: $taskId")
            }
            // F-05 NodeOS AST Graph Query
            command == "graph" -> {
                if (!config.astGraphEnabled) {
                    showTerminalOutput("[AST GATE] Graph disabled (ast_graph_enabled=false)")
                } else {
                    val snapshot = astGraphRenderer.queryTopology(config.nodeosDbPath)
                    showTerminalOutput(astGraphRenderer.formatSummaryText(snapshot, config.astGraphNodeCap))
                }
            }
            // F-02 Persona Management
            command == "persona" || command == "personas" -> {
                val all = personaManager.getAllPersonas()
                val active = personaManager.getActivePersona()
                val sb = StringBuilder("=== Personas ===\nActive: ${active.label}\n\n")
                for (p in all) {
                    val mark = if (p.id == active.id) "●" else "○"
                    sb.appendLine("$mark ${p.label} - ${p.subtitle}")
                }
                sb.append("\nType :id <name> to switch")
                showTerminalOutput(sb.toString())
            }
            command.startsWith("id ") -> {
                val targetId = command.removePrefix("id ").trim()
                val switched = personaManager.selectPersona(targetId)
                if (switched != null) {
                    applyIdentityBadge()
                    glyphChoreographer.play("FOCUS_MODE_ON")
                    showTerminalOutput("[PERSONA SWITCHED]: ${switched.label}")
                } else {
                    showTerminalOutput("[ERROR]: Unknown persona '$targetId'")
                }
            }
            // F-15 Macro Recorder
            command.startsWith("record ") -> {
                if (!config.macrosEnabled) {
                    showTerminalOutput("[MACRO GATE] Disabled (macros_enabled=false)")
                } else {
                    val name = command.removePrefix("record ").trim()
                    macroRecorder.startRecording(name)
                    showTerminalOutput("[MACRO RECORDING STARTED]: $name\nType :stop to finish")
                }
            }
            command == "stop" -> {
                if (!config.macrosEnabled) {
                    showTerminalOutput("[MACRO GATE] Disabled (macros_enabled=false)")
                } else {
                    val saved = macroRecorder.stopRecording(config.macrosDirectory)
                    showTerminalOutput(if (saved != null) "[MACRO SAVED]: $saved" else "[MACRO]: Not recording")
                }
            }
            command.startsWith("play ") -> {
                if (!config.macrosEnabled) {
                    showTerminalOutput("[MACRO GATE] Disabled (macros_enabled=false)")
                } else {
                    val name = command.removePrefix("play ").trim()
                    val ok = macroRecorder.playMacro(name, config.macrosDirectory) { step -> handleOmniSubmit(step) }
                    showTerminalOutput(if (ok) "[MACRO EXECUTED]: $name" else "[ERROR]: Macro $name not found")
                }
            }
            // F-09 History & Snippets
            command == "history" || command == "hist" -> {
                if (!config.commandHistoryEnabled) {
                    showTerminalOutput("[HISTORY GATE] Disabled (command_history_enabled=false)")
                } else {
                    val limit = config.commandHistoryLimit.coerceIn(1, 15)
                    val hist = commandPalette.getRecentHistory(limit)
                    val sb = StringBuilder("=== OmniScript History ===\n\n")
                    for (h in hist) {
                        sb.appendLine("• ${h.commandText} (${h.useCount}x)")
                    }
                    showTerminalOutput(sb.toString().trimEnd())
                }
            }
            command.startsWith("snippet ") -> {
                val rest = command.removePrefix("snippet ").trim()
                val parts = rest.split(" ", limit = 2)
                if (parts.size == 2) {
                    commandPalette.saveSnippet(parts[0], parts[1])
                    showTerminalOutput("[SNIPPET SAVED]: ${parts[0]} -> ${parts[1]}")
                } else {
                    showTerminalOutput("Usage: :snippet <alias> <command>")
                }
            }
            command == "snippets" -> {
                val list = commandPalette.getSnippets()
                val sb = StringBuilder("=== Saved Snippets ===\n\n")
                for (s in list) {
                    sb.appendLine("${s.snippetAlias} := ${s.commandText}")
                }
                showTerminalOutput(sb.toString().trimEnd())
            }
            // F-14 Embedded Terminal Command
            command.startsWith("term ") -> {
                if (!config.voidTermEnabled) {
                    showTerminalOutput("[VOIDTERM GATE] Disabled (voidterm_enabled=false)")
                } else {
                    val termCmd = command.removePrefix("term ").trim()
                    voidTermPanel.execute(termCmd, inBackground = true)
                    showTerminalOutput(voidTermPanel.getRenderedBuffer())
                }
            }
            command == "power" -> {
                val snap = telemetryMonitor.captureSnapshot()
                val lvl = powerProfileAdaptor.evaluate(snap, config.thermalLimitCelsius)
                showTerminalOutput("[POWER PROFILE]: ${lvl.label}\nBattery: ${snap.batteryPercent}% (${snap.batteryTemperatureC}°C)\nPoll Cadence: ${powerProfileAdaptor.getRecommendedPollIntervalMs()}ms")
            }
            command == "focus" -> {
                val activeBlock = focusScheduler.checkAndApplySchedule()
                showTerminalOutput(if (activeBlock != null) "[FOCUS ACTIVE]: ${activeBlock.label} (${activeBlock.personaId})" else "[FOCUS]: No active scheduled focus block")
            }
            command.startsWith("sh ") -> {
                val shellCmd = command.removePrefix("sh ").trim()
                termuxIpc.dispatchCommand(shellCmd, inBackground = true)
                showTerminalOutput("[DISPATCHED]: $shellCmd")
            }
            command.startsWith("work ") -> {
                val projectName = command.removePrefix("work ").trim()
                termuxIpc.openProjectInTermux(projectName)
                showTerminalOutput("[WORKSPACE]: ~/Projects/$projectName")
            }
            command == "default" || command == "home" -> {
                requestSetDefaultLauncher()
            }
            command == "notif" || command == "notifications" || command == "alerts" -> {
                VoidNotificationListenerService.openAccessSettings(this)
            }
            command == "files" || command == "ls" -> {
                toggleFilesBrowser()
            }
            command == "apps" || command == "drawer" -> {
                toggleRightDrawer()
            }
            command == "settings" || command == "config" || command == "cfg" -> {
                openSettings()
            }
            command == "clip" -> {
                val clipText = termuxIpc.getClipboardText()
                showTerminalOutput("[CLIPBOARD]:\n$clipText")
            }
            command == "nodeos" || command == "ping" -> {
                termuxIpc.dispatchCommand("nodeos -c ping", inBackground = true)
                showTerminalOutput("[PING]: nodeos -c ping")
            }
            command == "hud" || command == "status" -> {
                termuxIpc.dispatchCommand("pmc status", inBackground = false)
            }
            command == "deck" || command == "widgets" -> {
                openWidgetDeck(true)
            }
            command == "cmd" || command == "cc" || command == "search" -> {
                openCommandCenter(true)
            }
            command == "still" -> {
                setHomeMode("STILL")
                showTerminalOutput("[MODE SWITCHED]: STILL Home Mode")
            }
            command == "cockpit" -> {
                setHomeMode("COCKPIT")
                showTerminalOutput("[MODE SWITCHED]: Cockpit Mode")
            }
            command == "minimal" -> {
                setHomeMode("MINIMAL")
                showTerminalOutput("[MODE SWITCHED]: Minimal Mode")
            }
            command == "reload" -> {
                config = configManager.loadConfig()
                applyConfig()
                showTerminalOutput("[CONFIG RELOADED]")
            }
            else -> {
                termuxIpc.dispatchCommand(command, inBackground = false)
            }
        }
    }

    private fun openSettings() {
        val intent = Intent(this, VoidSettingsActivity::class.java)
        startActivity(intent)
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun handleDigitKey(view: View, digit: Int) {
        haptic(view)
        bareIntegerRouter.routeInteger(digit)
        resetOmniInput()
    }

    /** Records command history honoring `command_history_enabled` / `command_history_limit`. */
    private fun recordCommandHistory(text: String) {
        if (!config.commandHistoryEnabled) return
        commandPalette.recordCommand(text)
        commandPalette.pruneToLimit(config.commandHistoryLimit)
    }

    // ── Long-press app menu (Step 1) ─────────────────────────────
    // Tap still launches. Long-press shows this menu in the drawer,
    // omni results, and command center (all three share these adapters).
    private fun showAppMenu(entry: AppEntry) {
        haptic(binding.root)
        val options = arrayOf(
            "Open",
            "App info",
            "Uninstall",
            "Add to dock…",
            "Pin shortcut"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(entry.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openAppFromMenu(entry)
                    1 -> openAppInfo(entry)
                    2 -> uninstallApp(entry)
                    3 -> showDockSlotPicker(entry)
                    4 -> pinShortcut(entry)
                }
            }
            .show()
    }

    private fun openAppFromMenu(entry: AppEntry) {
        launchApp(entry)
        openRightDrawer(false)
        if (isCommandCenterOpen) {
            openCommandCenter(false)
        }
    }

    private fun openAppInfo(entry: AppEntry) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", entry.packageName, null)
            )
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Cannot open app info for ${entry.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uninstallApp(entry: AppEntry) {
        try {
            // Android shows its own uninstall confirmation; the launcher
            // cannot force an uninstall, and system apps will be refused there.
            val intent = Intent(
                Intent.ACTION_DELETE,
                Uri.fromParts("package", entry.packageName, null)
            )
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Cannot uninstall ${entry.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDockSlotPicker(entry: AppEntry) {
        // The dock binds 7 buttons (btnSlot1..btnSlot7); offer all of them so a
        // choice here always has a visible effect.
        val slotLabels = (1..7).map { index ->
            val current = config.dockSlots.firstOrNull { it.index == index }
            "Slot $index — ${current?.label ?: "Empty"}"
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Add ${entry.label} to dock")
            .setItems(slotLabels) { _, which ->
                val index = which + 1
                val newSlot = DockSlot(
                    index = index,
                    label = entry.label,
                    subtitle = entry.packageName,
                    packageName = entry.packageName,
                    action = "launch_package"
                )
                val newSlots = config.dockSlots.toMutableList()
                val existingPos = newSlots.indexOfFirst { it.index == index }
                if (existingPos >= 0) {
                    newSlots[existingPos] = newSlot
                } else {
                    newSlots.add(newSlot)
                }
                val updated = config.copy(dockSlots = newSlots.sortedBy { it.index })
                config = updated
                configManager.saveConfig(updated)
                bareIntegerRouter.updateSlots(updated.dockSlots)
                rebuildDockSlots()
                rebuildStillFavorites()
                Toast.makeText(this, "${entry.label} added to dock slot $index", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun pinShortcut(entry: AppEntry) {
        val manager = getSystemService(ShortcutManager::class.java)
        if (manager == null || !manager.isRequestPinShortcutSupported) {
            Toast.makeText(this, "Pin shortcut is not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(entry.packageName)
        if (launchIntent == null) {
            Toast.makeText(this, "Cannot pin ${entry.label}", Toast.LENGTH_SHORT).show()
            return
        }
        val drawable = entry.icon ?: try {
            packageManager.getApplicationIcon(entry.packageName)
        } catch (_: Exception) {
            null
        }
        val shortcut = ShortcutInfo.Builder(this, "pinned_${entry.packageName}")
            .setShortLabel(entry.label)
            .setIntent(launchIntent)
            .apply {
                drawable?.let { setIcon(drawableToIcon(it)) }
            }
            .build()
        try {
            // A null success callback is allowed: the system shows its
            // own pin confirmation dialog when supported.
            manager.requestPinShortcut(shortcut, null)
        } catch (_: Exception) {
            Toast.makeText(this, "Cannot pin ${entry.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawableToIcon(drawable: android.graphics.drawable.Drawable): Icon {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Icon.createWithBitmap(drawable.bitmap)
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return Icon.createWithBitmap(bitmap)
    }

    private fun launchApp(entry: AppEntry) {
        val intent = packageManager.getLaunchIntentForPackage(entry.packageName)
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "Cannot launch ${entry.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTerminalOutput(text: String) {
        binding.tvTerminalOutput.text = text
        binding.tvTerminalOutput.visibility = View.VISIBLE
        binding.rvAppFilter.visibility = View.GONE
        binding.bareIntegerDock.visibility = View.GONE
        binding.stillHomeLayout.visibility = View.GONE
    }

    private fun resetOmniInput() {
        binding.etOmniInput.setText("")
        binding.rvAppFilter.visibility = View.GONE
        binding.tvTerminalOutput.visibility = View.GONE
        val isStill = config.defaultHomeMode.uppercase() == "STILL"
        if (isStill) {
            binding.stillHomeLayout.visibility = View.VISIBLE
            binding.bareIntegerDock.visibility = View.GONE
        } else if (config.showDock && binding.filesBrowserLayout.visibility != View.VISIBLE) {
            binding.bareIntegerDock.visibility = View.VISIBLE
        }
    }

    private fun haptic(view: View) {
        if (config.enableHaptics) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    override fun onResume() {
        super.onResume()
        val newConfig = configManager.loadConfig()
        if (newConfig != config) {
            config = newConfig
            nodeOsBridge.workflowPath = config.workflowJsonPath
            nodeOsBridge.nodeosDbPath = config.nodeosDbPath
            bareIntegerRouter.updateSlots(config.dockSlots)
            applyConfig()
            startTelemetryFlow()
            startNotificationsFlow()
        } else {
            checkDefaultLauncherStatus()
            val hasStorage = fileManager.hasStoragePermission()
            binding.storagePermissionCard.visibility = if (hasStorage) View.GONE else View.VISIBLE
        }
        VoidNotificationListenerService.triggerRefresh()
        resetOmniInput()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (isCommandCenterOpen) {
            openCommandCenter(false)
            return
        }
        if (isWidgetDeckOpen) {
            openWidgetDeck(false)
            return
        }
        if (isDrawerOpen) {
            openRightDrawer(false)
            return
        }
        if (binding.filesBrowserLayout.visibility == View.VISIBLE) {
            showFilesBrowser(false)
            return
        }
        if (binding.tvTerminalOutput.visibility == View.VISIBLE) {
            resetOmniInput()
            return
        }
        if (binding.etOmniInput.text.isNotEmpty()) {
            resetOmniInput()
            return
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }

    private fun dpToPxF(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }

    // ── Gesture Controls ─────────────────────────────────────

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Without this, the global gesture engine's long-press (default:
        // OPEN_SETTINGS) would fire on top of an app row's long-press
        // menu, because dispatchTouchEvent forwards every event to it
        // before the RecyclerView item sees it. Suppress the gesture
        // engine for a touch that starts inside an app list; the list
        // itself (scroll, tap, long-press) still receives the event
        // via super.dispatchTouchEvent below.
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                gestureSuppressedForTouch = isEventInsideAppList(ev)
                if (config.liquidTouchEnabled) {
                    binding.liquidSurface.spawnLiquidWave(ev.x, ev.y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                gestureSuppressedForTouch = false
            }
        }
        if (config.compoundGesturesEnabled &&
            !gestureSuppressedForTouch &&
            !binding.etOmniInput.isFocused && !binding.etCommandCenterInput.isFocused
        ) {
            compoundGestureEngine.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun isEventInsideAppList(ev: MotionEvent): Boolean {
        val lists = listOf(
            binding.rvDrawerApps,
            binding.rvAppFilter,
            binding.rvCommandCenterApps
        )
        val location = IntArray(2)
        for (list in lists) {
            if (list.visibility != View.VISIBLE) continue
            list.getLocationOnScreen(location)
            val left = location[0].toFloat()
            val top = location[1].toFloat()
            if (ev.rawX in left..(left + list.width) &&
                ev.rawY in top..(top + list.height)
            ) {
                return true
            }
        }
        return false
    }

    private fun handleGestureAction(action: String) {
        when (action.uppercase()) {
            "OPEN_DRAWER"         -> toggleRightDrawer()
            "OPEN_FILES"          -> toggleFilesBrowser()
            "OPEN_SEARCH"         -> {
                if (config.defaultHomeMode.uppercase() == "STILL") {
                    openCommandCenter(true)
                } else {
                    binding.etOmniInput.requestFocus()
                    showKeyboard()
                }
            }
            "OPEN_COMMAND_CENTER" -> openCommandCenter(true)
            "OPEN_WIDGET_DECK"    -> openWidgetDeck(true)
            "OPEN_SETTINGS"       -> openSettings()
            "OPEN_TERMINAL"       -> termuxIpc.launchTermuxInteractive()
            "NOTIFICATIONS"       -> VoidNotificationListenerService.openAccessSettings(this)
            "EXPAND_STATUS_BAR"   -> expandStatusBar()
            "TOGGLE_MINIMAL"      -> toggleMinimalMode()
            "TOGGLE_STILL"        -> {
                val nextMode = if (config.defaultHomeMode.uppercase() == "STILL") "COCKPIT" else "STILL"
                setHomeMode(nextMode)
                Toast.makeText(this, "Mode: $nextMode", Toast.LENGTH_SHORT).show()
            }
            "COCKPIT_MODE"        -> { isMinimalMode = false; applyConfig() }
            "FOCUS_OMNI"          -> { binding.etOmniInput.requestFocus(); showKeyboard() }
            "OPEN_HUD"            -> termuxIpc.dispatchCommand("pmc status", inBackground = false)
            "TOGGLE_IDENTITY"     -> {
                val newP = personaManager.cyclePersona()
                config = config.copy(activePersona = newP.id)
                configManager.saveConfig(config)
                applyIdentityBadge()
                Toast.makeText(this, "Persona: ${newP.label}", Toast.LENGTH_SHORT).show()
            }
            "NONE"              -> {}
        }
    }

    private fun toggleMinimalMode() {
        isMinimalMode = !isMinimalMode
        if (isMinimalMode) {
            binding.telemetryCard.visibility = View.GONE
            binding.intentCard.visibility = View.GONE
            binding.notificationsCard.visibility = View.GONE
            binding.bareIntegerDock.visibility = View.GONE
            isKeypadVisible = false
            binding.digitKeypadRow.visibility = View.GONE
            binding.btnToggleKeypad.visibility = View.GONE
            binding.stillHomeLayout.visibility = View.GONE
        } else {
            applyConfig()
        }
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(binding.etOmniInput, InputMethodManager.SHOW_IMPLICIT)
    }

    @Suppress("DEPRECATION")
    private fun expandStatusBar() {
        try {
            val sbService = getSystemService("statusbar")
            sbService?.let {
                val cls = Class.forName("android.app.StatusBarManager")
                cls.getMethod("expandNotificationsPanel").invoke(it)
            }
        } catch (_: Exception) {}
    }
}
