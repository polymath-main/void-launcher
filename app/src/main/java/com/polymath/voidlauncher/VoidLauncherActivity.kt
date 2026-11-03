package com.polymath.voidlauncher

import android.app.role.RoleManager
import android.content.Context
import android.content.ActivityNotFoundException
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
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.polymath.voidlauncher.core.AgentOrchestrator
import com.polymath.voidlauncher.core.AndroidClipboard
import com.polymath.voidlauncher.core.AppIndexer
import com.polymath.voidlauncher.core.AstGraphRenderer
import com.polymath.voidlauncher.core.BareIntegerRouter
import com.polymath.voidlauncher.core.CommandPalette
import com.polymath.voidlauncher.core.CompoundGestureEngine
import com.polymath.voidlauncher.core.ConfigManager
import com.polymath.voidlauncher.core.ConfigSyncService
import com.polymath.voidlauncher.core.FileListingOrder
import com.polymath.voidlauncher.core.FileManager
import com.polymath.voidlauncher.core.FileSortMode
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
import com.polymath.voidlauncher.core.TelemetryHistory
import com.polymath.voidlauncher.core.ThermalStatusLabel
import com.polymath.voidlauncher.core.ThemeEngine
import com.polymath.voidlauncher.core.ThermalGuardian
import com.polymath.voidlauncher.core.VoidGestureDetector
import com.polymath.voidlauncher.core.VoidNotificationListenerService
import com.polymath.voidlauncher.core.VoidTermPanel
import com.polymath.voidlauncher.core.WidgetRegistry
import com.polymath.voidlauncher.core.AstProvenance
import com.polymath.voidlauncher.core.formatAstLine
import com.polymath.voidlauncher.core.voidTaskLabel
import com.polymath.voidlauncher.core.services.AppActionsMicroService
import com.polymath.voidlauncher.core.services.CommandHelpMicroService
import com.polymath.voidlauncher.core.services.DockSlotMicroService
import com.polymath.voidlauncher.core.services.FileOperationsMicroService
import com.polymath.voidlauncher.core.services.PersonaIdentityMicroService
import com.polymath.voidlauncher.core.services.WallpaperMicroService
import com.polymath.voidlauncher.core.shell.DefaultProcessRunner
import com.polymath.voidlauncher.core.shell.ShellResult
import com.polymath.voidlauncher.core.shell.VoidRoots
import com.polymath.voidlauncher.core.shell.VoidShell
import com.polymath.voidlauncher.core.process.VoidProcessManager
import com.polymath.voidlauncher.core.shell.VoidShellPort
import com.polymath.voidlauncher.core.terminal.VoidShellRunner
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
    private lateinit var clipboard: AndroidClipboard
    private lateinit var voidShell: VoidShell
    private lateinit var voidShellPort: VoidShellPort
    private lateinit var voidProcessManager: VoidProcessManager
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
    // impl-05 step 6c: display/history only — never a guard input.
    private val telemetryHistory = TelemetryHistory()
    // impl-05 step 7: in count-line mode the header tap toggles the list.
    // Default expanded = false in count-line mode (count line only until
    // tapped); inline mode always shows the list when non-empty.
    private var notificationsListExpanded = false
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

    /** Command audit log (impl-04 step 8); failures never break dispatch. */
    private lateinit var commandAudit: com.polymath.voidlauncher.core.CommandAudit

    /** JS macro runner (impl-04 step 7b); engine may report unavailable. */
    private lateinit var jsMacroRunner: com.polymath.voidlauncher.core.js.JsMacroRunner

    /** Depth of nested snippet-alias re-entry (impl-04 step 1). */
    private var snippetChainDepth: Int = 0

    // Modular Micro-Services
    private lateinit var wallpaperService: WallpaperMicroService
    private lateinit var dockSlotService: DockSlotMicroService
    private lateinit var appActionsService: AppActionsMicroService
    private lateinit var personaIdentityService: PersonaIdentityMicroService
    private lateinit var fileOperationsService: FileOperationsMicroService
    private lateinit var commandHelpService: CommandHelpMicroService

    private lateinit var appAdapter: AppFilterAdapter
    private lateinit var drawerAdapter: DrawerAppAdapter
    private lateinit var fileAdapter: FileBrowserAdapter
    private lateinit var notificationAdapter: NotificationAdapter

    private var telemetryJob: Job? = null
    private var notificationJob: Job? = null
    private var config: LauncherConfig = LauncherConfig()
    private var currentFilesPath: String = ""
    // impl-02 step 4: files search/sort state. The search query survives
    // directory changes inside one browser session; the last loaded
    // (unfiltered) listing is kept so search/sort re-apply without I/O.
    private var filesSearchQuery: String = ""
    private var filesSortMode: FileSortMode = FileSortMode.NAME_ASC
    private var lastFilesItems: List<FileItem> = emptyList()
    private var lastFilesHasStorage: Boolean = true
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
        handleThemeIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleThemeIntent(intent)
    }

    private fun handleThemeIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme.equals("void", ignoreCase = true) &&
            data.host.equals("theme", ignoreCase = true)
        ) {
            val result = ThemeEngine.importFromUriOrString(data.toString())
            result.onSuccess { manifest ->
                config = ThemeEngine.applyTheme(manifest, config, configManager, wallpaperService, this)
                applyConfig()
                showTerminalOutput("[THEME IMPORTED & APPLIED]: ${manifest.name} (${manifest.id})")
                Toast.makeText(this, "Theme imported: ${manifest.name}", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                showTerminalOutput("[THEME IMPORT ERROR]: ${err.message}")
                Toast.makeText(this, "Theme import error: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        configSyncService.unregister()
        widgetRegistry.shutdown()
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
        clipboard = AndroidClipboard(this)
        // VoidShell (Phase 1 + migration Cycles 1-2): /system/bin/sh
        // on device, filesDir as default workdir. :run, :sh, :term, the
        // dock TERMINAL slot, agent dispatch and thermal throttling all
        // run through Void-owned code now; no external-terminal IPC remains here.
        // impl-01 step 2: timeout/output cap come from config, clamped
        // in VoidShell; no Settings UI for these keys yet (area 7).
        voidShell = VoidShell(
            DefaultProcessRunner(VoidShell.clampTimeoutSeconds(config.shellTimeoutSeconds)),
            "/system/bin/sh",
            filesDir.absolutePath,
            VoidShell.clampMaxOutputChars(config.shellMaxOutputChars)
        )
        // v2 Cycle 3: ShellShim migrated off — the port is built
        // directly over VoidShell again (same type, same callback), so
        // every downstream caller behaves exactly as before.
        // impl-01 step 6: VoidShim deleted entirely (see log) — the
        // task registry is VoidProcessManager directly, not via a shim.
        // impl-01 step 5: openProject resolves project names under
        // the files home (filesDir/void) before invoking the hook.
        voidShellPort = VoidShellPort(
            voidShell,
            File(filesDir, "void").absolutePath
        ) { openVoidTerm() }
        voidProcessManager = VoidProcessManager()
        nodeOsBridge = NodeOsBridge(this)
        appIndexer = AppIndexer(this)
        bareIntegerRouter = BareIntegerRouter(this, voidShellPort).apply {
            onOpenVoidTermRequested = { openVoidTerm() }
        }
        glyphManager = NothingGlyphManager(this)
        fileManager = FileManager(this)

        // Initialize Blueprint v2 engines
        commandPalette = CommandPalette(this)
        thermalGuardian = ThermalGuardian(voidProcessManager)
        powerProfileAdaptor = PowerProfileAdaptor()
        notifIntelligence = NotifIntelligence()
        personaManager = PersonaManager(this).apply {
            registerConfigProfiles(config.personas)
            if (config.activePersona.isNotBlank()) selectPersona(config.activePersona)
        }
        glyphChoreographer = GlyphChoreographer(this).apply {
            enabled = config.enableGlyphPulse
            loadCustomPatterns(config.glyphPatternsPath)
        }
        glyphManager.choreographer = glyphChoreographer
        astGraphRenderer = AstGraphRenderer()
        liveScriptEngine = LiveScriptEngine(this)
        agentOrchestrator = AgentOrchestrator(this, voidShellPort, voidProcessManager).apply {
            onTaskCompleted = {
                glyphChoreographer.play("TASK_SUCCESS")
                // impl-06 step 2: intent line refreshes on completion, not
                // only on the next telemetry tick.
                updateNodeOsHudView()
                if (isWidgetDeckOpen && !isFinishing) refreshWidgetDeckContent()
            }
            onTaskFailed = {
                glyphChoreographer.play("TASK_FAIL")
                updateNodeOsHudView()
                if (isWidgetDeckOpen && !isFinishing) refreshWidgetDeckContent()
            }
        }
        widgetRegistry = WidgetRegistry(this, voidShell).apply {
            if (config.widgetsEnabled) loadWidgets(config.widgetsDirectory)
        }
        focusScheduler = FocusScheduler(this, personaManager, glyphChoreographer).apply {
            if (config.focusSchedulerEnabled) loadSchedule(config.focusSchedulePath)
        }
        voidTermPanel = VoidTermPanel(this, voidShellPort).apply {
            rows = config.voidTermRows
            cols = config.voidTermCols
            // :term runs through VoidShell with real output (Cycle 1).
            terminalRunner = VoidShellRunner(voidShell)
        }
        macroRecorder = MacroRecorder(this)
        commandAudit = com.polymath.voidlauncher.core.CommandAudit(
            java.io.File(com.polymath.voidlauncher.model.VoidPaths.AUDIT)
        )
        jsMacroRunner = com.polymath.voidlauncher.core.js.JsMacroRunner(
            com.polymath.voidlauncher.core.js.QuickJsEngine()
        ) { _, name ->
            com.polymath.voidlauncher.core.js.AndroidJsAdapters(
                activity = this,
                voidShell = voidShell,
                filesHome = fileManager.voidHomePath(),
                jsMacrosDir = config.jsMacrosDirectory,
                macroName = name,
                onMode = { mode -> setHomeMode(mode) }
            )
        }

        // Initialize Modular Micro-Services
        wallpaperService = WallpaperMicroService(this)
        dockSlotService = DockSlotMicroService(this)
        appActionsService = AppActionsMicroService(this)
        personaIdentityService = PersonaIdentityMicroService(this, personaManager)
        fileOperationsService = FileOperationsMicroService(this, fileManager, wallpaperService)
        commandHelpService = CommandHelpMicroService(this, commandPalette, wallpaperService)

        configSyncService = ConfigSyncService(this) {
            reloadConfigFromSync()
        }

        currentFilesPath = fileManager.resolveAccessiblePath(config.filesDefaultPath)

        // Push config into subsystems
        syncAgentAndNodeOsPaths()
        nodeOsBridge.setDefaultIdentity(config.defaultIdentity)
        bareIntegerRouter.updateSlots(config.dockSlots)

        // Wire bare integer custom callbacks
        bareIntegerRouter.onOpenFilesRequested = { toggleFilesBrowser() }
        bareIntegerRouter.onOpenDrawerRequested = { toggleRightDrawer() }
        bareIntegerRouter.onOpenSettingsRequested = { openSettings() }

        // Thermal throttle guardian callbacks
        thermalGuardian.onThrottleTriggered = { tempC, summary ->
            binding.liquidSurface.isThermalThrottled = true
            glyphChoreographer.play("THERMAL_WARNING")
            // impl-06 step 2: sync the orchestrator's copy + workflow.json
            // so a thermally-cancelled task is not left "running" there.
            agentOrchestrator.markExternalCancellation(
                thermalGuardian.lastTripCancelledTaskIds, "thermal"
            )
            Toast.makeText(this, "⚠ $summary", Toast.LENGTH_LONG).show()
        }
        thermalGuardian.onThrottleCleared = { _ ->
            binding.liquidSurface.isThermalThrottled = false
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
        // impl-01 step 7: own-save guard — the FileObserver also fires
        // for saves the app itself made; if the parsed config equals
        // the one already in effect, skip the re-apply (and the noise
        // message) instead of redrawing for nothing.
        val loaded = configManager.loadConfig()
        if (loaded == config) return
        config = loaded
        applyConfig()
        showTerminalOutput("[CONFIG AUTO-RELOADED VIA VOID CONFIG SYNC]")
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
        // v2 COCKPIT: HUD line is text_secondary (clock stays primary).
        binding.tvMemory.setTextColor(c.textSecondaryInt)
        binding.tvDaemonStatus.setTextColor(c.secondaryInt)
        binding.tvAstCount.setTextColor(c.textSecondaryInt)
        binding.tvWorkspace.setTextColor(c.textMutedInt)
        binding.tvGitBranch.setTextColor(c.textSecondaryInt)
        binding.tvActiveIntent.setTextColor(c.textPrimaryInt)

        // Theme synchronization across STILL, Drawer, Files, and Command Center
        binding.tvStillClock.setTextColor(c.textPrimaryInt)
        binding.tvStillDate.setTextColor(c.textSecondaryInt)
        binding.tvStillStatus.setTextColor(c.textMutedInt)
        binding.tvDrawerAppCount.setTextColor(c.textPrimaryInt)
        binding.etDrawerSearch.setTextColor(c.textPrimaryInt)
        binding.etDrawerSearch.setHintTextColor(c.textMutedInt)
        binding.tvFilesCurrentPath.setTextColor(c.textPrimaryInt)
        binding.etCommandCenterInput.setTextColor(c.textPrimaryInt)
        binding.etCommandCenterInput.setHintTextColor(c.textMutedInt)
        binding.tvCommandCenterOutput.setTextColor(c.secondaryInt)
        binding.tvTerminalOutput.setTextColor(c.secondaryInt)
        binding.stillCommandCenterLayout.setBackgroundColor(c.backgroundInt)
        binding.stillWidgetDeckLayout.setBackgroundColor(c.backgroundInt)

        // Card backgrounds. v2 COCKPIT: the intent line and the
        // notification count line are borderless (no card), so no card
        // background is applied to them any more; the telemetry card
        // (config-gated, off by default in v2) and the files browser
        // (not ported in this cycle) keep theirs.
        applyCardBackground(binding.telemetryCard, c.surfaceInt, c.borderInt)
        // UI v2 files: flat, no card background at runtime either (the
        // XML card background was removed in the same cycle).

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
        ThemeEngine.syncFromConfig(config)
        val activeTheme = ThemeEngine.currentTheme

        binding.liquidSurface.isLiquidEffectEnabled = config.liquidTouchEnabled
        binding.liquidSurface.shaderType = activeTheme.overlayShader
        binding.liquidSurface.accentColor = c.accentInt
        binding.liquidSurface.secondaryColor = c.secondaryInt
        binding.liquidSurface.isLightSurface = activeTheme.isLightSurface
        binding.liquidSurface.rippleColor = c.accentInt
        binding.liquidSurface.visibility = if (config.liquidTouchEnabled) View.VISIBLE else View.GONE

        // VoidTerm viewport (config: voidterm_rows / voidterm_cols)
        voidTermPanel.rows = config.voidTermRows
        voidTermPanel.cols = config.voidTermCols

        // Glyph feedback gate (config: enable_glyph_pulse)
        glyphChoreographer.enabled = config.enableGlyphPulse

        // Apply app icon customizations to adapters (with tint + live refresh)
        appAdapter.showIcons = config.showAppIcons
        drawerAdapter.showIcons = config.showAppIcons
        appAdapter.iconStyle = config.appIconStyle
        drawerAdapter.iconStyle = config.appIconStyle
        appAdapter.tintColor = c.accentInt
        drawerAdapter.tintColor = c.accentInt
        appAdapter.refreshAppearance()
        drawerAdapter.refreshAppearance()

        // Scrubber visibility
        binding.alphabetScrubber.visibility = if (config.enableAtoZScrubber) View.VISIBLE else View.GONE

        // Wallpaper state (Window DecorView + OLED scrim + custom image override)
        wallpaperService.applyWallpaperState(this, binding.rootLayout, binding.stillHomeLayout, config)

        // Status bar element visibility
        binding.tvClock.visibility = if (config.showStatusBarTime) View.VISIBLE else View.GONE
        binding.tvMemory.visibility = if (config.showStatusBarRam) View.VISIBLE else View.GONE
        binding.tvBatteryTemp.visibility = if (config.showStatusBarBattery) View.VISIBLE else View.GONE

        applyHomeMode()
        checkDefaultLauncherStatus()
    }

    private fun checkDefaultLauncherStatus() {
        val isDefault = isDefaultLauncher()
        val mode = config.defaultHomeMode.uppercase()
        val hideInCalmModes = mode == "STILL" || mode == "MINIMAL" || isMinimalMode
        binding.bannerDefaultLauncher.visibility = if (!isDefault && !hideInCalmModes) View.VISIBLE else View.GONE
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
                // v2 STILL: no HUD line (the STILL clock is the time
                // display); the [123] toggle follows the same rule as
                // COCKPIT, and the keypad row stays hidden until tapped.
                // The omni bar itself stays: [123] lives inside it, and
                // :commands must keep working in STILL.
                (binding.tvClock.parent as? View)?.visibility = View.GONE
                isKeypadVisible = false
                binding.digitKeypadRow.visibility = View.GONE
                binding.btnToggleKeypad.visibility = if (config.showDigitKeypad) View.VISIBLE else View.GONE
                binding.filesBrowserLayout.visibility = View.GONE
                binding.stillHomeLayout.visibility = View.VISIBLE
                rebuildStillFavorites()
                updateStillClockAndVitals()
            }
            "MINIMAL" -> {
                binding.telemetryCard.visibility = View.GONE
                binding.intentCard.visibility = View.GONE
                binding.notificationsCard.visibility = View.GONE
                // v2 MINIMAL: omni + dock only. No HUD line, no cards.
                (binding.tvClock.parent as? View)?.visibility = View.GONE
                binding.bareIntegerDock.visibility = if (config.showDock) View.VISIBLE else View.GONE
                isKeypadVisible = false
                binding.digitKeypadRow.visibility = View.GONE
                binding.btnToggleKeypad.visibility = if (config.showDigitKeypad) View.VISIBLE else View.GONE
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

        // v2 STILL favorites: slots 1/3/5 only, as a text list (no card
        // background), 48dp rows, "index · LABEL" with a muted sub-line.
        for (slot in config.dockSlots.filter { it.index in setOf(1, 3, 5) }) {
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(48)
                ).apply {
                    bottomMargin = dpToPx(6)
                }
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
                isClickable = true
                isFocusable = true

                val tvLabel = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    text = "${slot.index} · ${slot.label}"
                    setTextColor(c.textPrimaryInt)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                }

                val tvSub = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    text = slot.subtitle.ifEmpty { slot.action }
                    setTextColor(c.textMutedInt)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    typeface = Typeface.MONOSPACE
                }

                addView(tvLabel)
                addView(tvSub)

                setOnClickListener {
                    haptic(it)
                    bareIntegerRouter.routeSlot(slot)
                }
                setOnLongClickListener {
                    haptic(it)
                    showEditDockSlotDialog(slot.index)
                    true
                }
            }
            container.addView(row)
        }
    }

    private fun updateStillClockAndVitals(snap: com.polymath.voidlauncher.model.TelemetrySnapshot? = null) {
        val now = java.util.Date()
        binding.tvStillClock.text = stillTimeFormat.format(now)
        binding.tvStillDate.text = stillDateFormat.format(now).uppercase(java.util.Locale.US)

        val s = snap ?: telemetryMonitor.captureSnapshot(config.thermalLimitCelsius)
        val audioMode = getAudioModeString()
        val audioPart = if (audioMode.isNotEmpty()) " · $audioMode" else ""
        binding.tvStillStatus.text = "▲ ${if (s.batteryPercent < 0) "?" else s.batteryPercent.toString()}% [${s.batteryTemperatureC}°C]$audioPart"
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
        val snap = telemetryMonitor.captureSnapshot(config.thermalLimitCelsius)
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
                // impl-05 step 3: CRITICAL suppresses SCRIPT live refresh
                // (shouldRenderNonEssentialWidgets gate). Static descriptor
                // content, vitals and task lists are never gated.
                if (powerProfileAdaptor.shouldRenderNonEssentialWidgets(
                        powerProfileAdaptor.evaluate(snap, config.thermalLimitCelsius)
                    )) {
                    widgetRegistry.refreshWidget(w) { live -> content.text = live }
                }
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
            // UI v2: output only when present — a fresh open shows no
            // stale output; a command submit repopulates it below.
            binding.tvCommandCenterOutput.text = ""
            binding.scrollCommandCenterOutput.visibility = View.GONE
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
        // Chips submit programmatically: their submissions are NOT
        // recorded into macros (recording a chip press would replay
        // :still etc. into macros the user did not type — step 3).
        handleCommandCenterSubmit(cmd, fromChip = true)
    }

    private fun handleCommandCenterTextChange(text: String) {
        if (text.startsWith(":")) {
            // impl-04 step 3: same command suggestions as omni.
            binding.rvCommandCenterApps.visibility = View.GONE
            val typed = text.substring(1).trim().split(" ").firstOrNull().orEmpty()
            val suggestions = if (typed.isEmpty()) {
                com.polymath.voidlauncher.core.CommandGuide.ALL_NAMES.distinct().take(10)
            } else {
                com.polymath.voidlauncher.core.CommandGuide.suggest(typed, limit = 10)
            }
            if (suggestions.isNotEmpty()) {
                binding.tvCommandCenterOutput.text = suggestions.joinToString("  ") { ":$it" }
                binding.scrollCommandCenterOutput.visibility = View.VISIBLE
            } else {
                binding.scrollCommandCenterOutput.visibility = View.GONE
            }
            return
        }
        if (text.isEmpty()) {
            binding.rvCommandCenterApps.visibility = View.GONE
            // UI v2: output only when present — do not show an empty
            // output pane for blank input.
            binding.scrollCommandCenterOutput.visibility =
                if (binding.tvCommandCenterOutput.text.isNullOrEmpty()) View.GONE else View.VISIBLE
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

    private fun handleCommandCenterSubmit(query: String, fromChip: Boolean = false) {
        // impl-04 step 3: parity with omni — inline ## alias
        // extraction (was missing) and macro recording for typed
        // input only (chips excluded, see handleCommandCenterChip).
        // :stop is excluded here already; step 6 applies the same
        // exclusion to the omni path and adds persistence/caps.
        val q = commandHelpService.extractInlineSnippet(query)
        if (q.isEmpty()) return
        recordCommandHistory(q)
        if (macroRecorder.isRecording && !fromChip && q != ":stop") {
            macroRecorder.recordStep(q)
        }
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
        personaIdentityService.applyIdentityBadge(binding.tvIdentityBadge, config)
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
        val theme = ThemeEngine.currentTheme
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
                slotView.minimumHeight = dpToPx(theme.touchTargetMinDp.coerceAtLeast(48))

                // impl-02 step 2: dock rows have a single TextView child (the
                // label). The old second-child subtitle assignment was dead
                // code — subtitles are not rendered on home (UI v2 spec cut
                // them). DockSlot.subtitle stays in the model: the editor
                // and settings still display it; home just does not.
                val labelView = (slotView as? LinearLayout)?.getChildAt(0) as? TextView

                val formatted = dockSlotService.formatSlotDisplay(slot, theme.dockStyle)
                labelView?.text = formatted.first
                labelView?.setTextColor(c.textPrimaryInt)
                labelView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.dockFontSizeSp)
                // Merge: origin's second-child subtitle assignment dropped —
                // impl-02 step 2 deleted it as dead code (dock rows have a
                // single TextView child; no second-child view exists here).
                // formatSlotDisplay's theme-aware label (.first) is kept.
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
            // v2: keypad keys are 48dp (minimum touch target), surface + 1px border.
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(48), 1f).apply {
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

        binding.btnSlot1.setOnLongClickListener { haptic(it); showEditDockSlotDialog(1); true }
        binding.btnSlot2.setOnLongClickListener { haptic(it); showEditDockSlotDialog(2); true }
        binding.btnSlot3.setOnLongClickListener { haptic(it); showEditDockSlotDialog(3); true }
        binding.btnSlot4.setOnLongClickListener { haptic(it); showEditDockSlotDialog(4); true }
        binding.btnSlot5.setOnLongClickListener { haptic(it); showEditDockSlotDialog(5); true }
        binding.btnSlot6.setOnLongClickListener { haptic(it); showEditDockSlotDialog(6); true }
        binding.btnSlot7.setOnLongClickListener { haptic(it); showEditDockSlotDialog(7); true }

        // Dynamic digit keypad toggle (keys themselves are built in rebuildKeypad)
        binding.btnToggleKeypad.setOnClickListener { toggleKeypad() }

        // Smart Persona cycle toggle (F-02) + Long-press interactive selector
        binding.tvIdentityBadge.setOnClickListener {
            val newPersona = personaManager.cyclePersona()
            config = config.copy(activePersona = newPersona.id)
            configManager.saveConfig(config)
            applyIdentityBadge()
            glyphChoreographer.play("FOCUS_MODE_ON")
            Toast.makeText(this, "Persona: ${newPersona.label} (${newPersona.subtitle})", Toast.LENGTH_SHORT).show()
        }
        binding.tvIdentityBadge.setOnLongClickListener {
            haptic(it)
            showPersonaPickerDialog()
            true
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

        // UI v2 drawer search: typing only, no launch tap added.
        binding.etDrawerSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isDrawerOpen) applyDrawerFilter(appIndexer.getAllApps())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

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

        // impl-02 step 3: +FILE wires the previously dead createFile().
        binding.btnFilesNewFile.setOnClickListener {
            haptic(it)
            showCreateFileDialog()
        }

        // impl-02 step 4: files search (typing-only, like drawer search)
        // and sort (button cycles modes). Neither launches anything.
        binding.etFilesSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filesSearchQuery = s?.toString().orEmpty()
                applyFilesSearchAndSort()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        binding.btnFilesSort.setOnClickListener {
            haptic(it)
            filesSortMode = filesSortMode.next()
            binding.btnFilesSort.text = filesSortMode.label
            applyFilesSearchAndSort()
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

        binding.btnChipVoid.setOnClickListener {
            haptic(it)
            loadFilesDirectory(fileManager.voidHomePath())
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

    // UI v2 drawer search: typing filters the already-loaded list by
    // label or package and updates the count header. It never launches:
    // tap on a row still launches, long-press still shows the app menu.
    private fun applyDrawerFilter(apps: List<AppEntry>) {
        val query = binding.etDrawerSearch.text?.toString()?.trim().orEmpty()
        val filtered = if (query.isBlank()) apps else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
        drawerAdapter.submitList(filtered)
        binding.tvDrawerAppCount.text = "${filtered.size} APPS"
    }

    private fun openRightDrawer(open: Boolean) {
        isDrawerOpen = open
        if (open) {
            binding.etDrawerSearch.setText("")
            val allApps = appIndexer.getAllApps()
            applyDrawerFilter(allApps)
            // impl-02 step 5: dim scrubber letters with no app section.
            binding.alphabetScrubber.setAvailableLetters(appIndexer.getSectionPositions().keys)
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
        binding.tvFilesCurrentPath.text = targetPath

        val hasStorage = fileManager.hasStoragePermission()
        binding.storagePermissionCard.visibility = if (hasStorage) View.GONE else View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val items = fileManager.listDirectory(targetPath, showHidden = config.filesShowHidden)
            withContext(Dispatchers.Main) {
                lastFilesItems = items
                lastFilesHasStorage = hasStorage
                applyFilesSearchAndSort()
            }
        }
    }

    // impl-02 step 4: search first, then sort (they compose); the
    // count line reports filtered vs total so a search is never silent.
    private fun applyFilesSearchAndSort() {
        val filtered = FileListingOrder.apply(lastFilesItems, filesSearchQuery, filesSortMode)
        fileAdapter.submitList(filtered)
        binding.tvFilesCount.text = if (filesSearchQuery.isBlank()) {
            "${lastFilesItems.size} items"
        } else {
            "${filtered.size} of ${lastFilesItems.size} items"
        }
        if (filtered.isEmpty()) {
            binding.tvFilesEmptyState.visibility = View.VISIBLE
            binding.tvFilesEmptyState.text = when {
                !lastFilesHasStorage -> "All Files Access permission required to list /sdcard contents."
                filesSearchQuery.isNotBlank() -> "No files match: $filesSearchQuery"
                else -> "Directory is empty: $currentFilesPath"
            }
        } else {
            binding.tvFilesEmptyState.visibility = View.GONE
        }
    }

    private fun handleFileItemClick(item: FileItem) {
        if (item.isDirectory) {
            loadFilesDirectory(item.path)
        } else if (wallpaperService.isImageFile(item.path)) {
            showFileOperationsDialog(item)
        } else {
            val preview = fileManager.readTextPreview(item.path, maxLines = 40)
            showTerminalOutput("[FILE PREVIEW: ${item.name}]\n\n$preview")
        }
    }

    private fun showFileOperationsDialog(item: FileItem) {
        fileOperationsService.showFileOperationsDialog(
            item = item,
            // impl-02 step 6: Open in VoidTerm now really lands in the
            // file's directory (a directory item opens in itself).
            // The old code only printed a hint and ran :term in
            // filesDir, despite the "run here" label. Invalid paths
            // fall back to Void home, never to a crash.
            onOpenInVoidTerm = {
                val dir = if (item.isDirectory) {
                    item.path
                } else {
                    File(item.path).parentFile?.absolutePath ?: fileManager.voidHomePath()
                }
                val safeDir = if (File(dir).isDirectory) dir else fileManager.voidHomePath()
                voidTermPanel.openDirectorySession(safeDir)
                openVoidTerm()
                showTerminalOutput("[VOIDTERM] $safeDir\nType :term <command> to run here.")
            },
            onShowPreview = { text -> showTerminalOutput(text) },
            onCopyPath = { path -> clipboard.setText(path) },
            onApplyWallpaperFromFile = { path ->
                val ok = wallpaperService.setWallpaperFromFile(path, alsoSetSystemWallpaper = true)
                if (ok) {
                    config = config.copy(
                        useSystemWallpaper = true,
                        customWallpaperPath = wallpaperService.lastOwnedCopyPath ?: config.customWallpaperPath
                    )
                    configManager.saveConfig(config)
                    applyConfig()
                    Toast.makeText(this, "Wallpaper set: ${item.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show()
                }
            },
            onRefreshDirectory = { loadFilesDirectory(currentFilesPath) },
            // Merge: BOTH callbacks — impl-02 step 3 "Open with…" via a
            // read-only FileProvider URI (file:// rejected since API 24;
            // tap behaviour unchanged) AND origin's theme import.
            onOpenWith = { fileItem ->
                try {
                    val mime = fileManager.getMimeType(fileItem.path)
                    val uri = FileProvider.getUriForFile(
                        this, "$packageName.fileprovider", File(fileItem.path)
                    )
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(viewIntent, "Open with…"))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, "No app can open this file", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot open this file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            onApplyThemeFromFile = { path ->
                val result = ThemeEngine.importFromUriOrString(path)
                result.onSuccess { manifest ->
                    config = ThemeEngine.applyTheme(manifest, config, configManager, wallpaperService, this)
                    applyConfig()
                    Toast.makeText(this, "Theme applied: ${manifest.name}", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(this, "Failed to apply theme: ${err.message}", Toast.LENGTH_SHORT).show()
                }
                }
        )
    }

    private fun showCreateFileDialog() {
        fileOperationsService.showCreateFileDialog(currentFilesPath) {
            loadFilesDirectory(currentFilesPath)
        }
    }

    private fun showCreateDirectoryDialog() {
        fileOperationsService.showCreateDirectoryDialog(currentFilesPath) {
            loadFilesDirectory(currentFilesPath)
        }
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
                telemetryHistory.add(snap)

                binding.tvClock.text = cockpitTimeFormat.format(java.util.Date()) + config.clockSuffix
                binding.tvBatteryTemp.text = "▲ ${if (snap.batteryPercent < 0) "?" else snap.batteryPercent.toString()}% [${snap.batteryTemperatureC}°C]" + if (thermalGuardian.isThrottled || snap.batteryTemperatureC >= config.thermalLimitCelsius) " ⚠ THROTTLED" else ""
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
                binding.liquidSurface.isThermalThrottled = thermalGuardian.isThrottled || (snap.batteryTemperatureC >= config.thermalLimitCelsius)

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
                // impl-05 step 7: count-line mode — the header is the count
                // line; tapping it expands the list in place, tapping again
                // collapses back to the count line. Scoring, filtering,
                // permission prompt and showNotificationsCard are untouched.
                // Thermal evaluation never depends on this visibility.
                binding.tvNotifHeader.setOnClickListener {
                    if (config.notificationsCountLineOnly && filtered.isNotEmpty()) {
                        notificationsListExpanded = !notificationsListExpanded
                        binding.rvNotifications.visibility =
                            if (notificationsListExpanded) View.VISIBLE else View.GONE
                    }
                }

                if (filtered.isEmpty()) {
                    binding.tvNotifEmpty.visibility = View.VISIBLE
                    binding.rvNotifications.visibility = View.GONE
                    notificationAdapter.submitList(emptyList())
                } else {
                    binding.tvNotifEmpty.visibility = View.GONE
                    binding.rvNotifications.visibility =
                        if (config.notificationsCountLineOnly && !notificationsListExpanded) View.GONE else View.VISIBLE
                    notificationAdapter.submitList(filtered)
                }
            }
        }
    }

    /**
     * Migration Cycle 2: fresh install shows Void daemon /
     * VoidTaskStore status. The legacy NodeOS card appears only when
     * the opt-in legacy import flag is on.
     */
    private fun updateNodeOsHudView() {
        if (!config.nodeOsLegacyImportEnabled) {
            val running = voidProcessManager.getRunning()
            val latest = running.firstOrNull()
            binding.tvDaemonStatus.text =
                if (running.isEmpty()) "○ VOID: IDLE" else "● VOID: ${running.size} TASK(S)"
            binding.tvDaemonStatus.setTextColor(
                if (running.isEmpty()) config.colors.criticalInt else config.colors.secondaryInt
            )
            binding.tvAstCount.text = "TASKS: ${voidProcessManager.getAll().size}"
            binding.tvWorkspace.text = "CWD: Void home"
            // impl-06 step 1: this is task state, never a git branch.
            binding.tvGitBranch.text = voidTaskLabel(latest != null)
            binding.tvActiveIntent.text =
                if (latest != null) "[RUNNING] ${latest.title}" else "[IDLE] Void daemon ready"
            return
        }
        val status = nodeOsBridge.pollIntentStatus()
        binding.tvDaemonStatus.text = if (status.isDaemonAlive) "● NODEOS: ALIVE" else "○ NODEOS: OFFLINE"
        binding.tvDaemonStatus.setTextColor(
            if (status.isDaemonAlive) config.colors.secondaryInt else config.colors.criticalInt
        )

        // impl-06 step 1: AST provenance is shown, and the git line is
        // "—" — IntentStatus.gitBranch was a hardcoded "main", never a
        // branch read from any repo, so it is no longer displayed.
        val provenance = when (status.astProvenance) {
            "exact" -> AstProvenance.EXACT
            "estimate" -> AstProvenance.ESTIMATE
            else -> AstProvenance.NONE
        }
        binding.tvAstCount.text = formatAstLine(status.astNodeCount, provenance)
        binding.tvWorkspace.text = "CWD: ${status.workspacePath}"
        binding.tvGitBranch.text = "git: —"

        binding.tvActiveIntent.text = "[${status.taskStatus.uppercase()}] ${status.activeTaskTitle}"
    }

    /** Shared Void status output for :hud/:status and the OPEN_HUD gesture. */
    private fun showVoidStatus() {
        val running = voidProcessManager.getRunning()
        val all = voidProcessManager.getAll()
        val lines = StringBuilder("[VOID STATUS]: ${running.size} running / ${all.size} tracked")
        // impl-05 step 4: throttle state is persistent in :status (the HUD
        // token above is transient per tick; this line survives recovery).
        // Thermally-cancelled tasks already surface in :tasks as "cancelled"
        // via AgentOrchestrator.getAllTasks() mapping VoidProcessManager
        // status; their ids are listed here too, for the record. No auto-retry.
        if (thermalGuardian.isThrottled) {
            lines.append("\n[THERMAL]: THROTTLED at ${thermalGuardian.currentTempC}°C (limit ${config.thermalLimitCelsius}°C)")
        } else {
            lines.append("\n[THERMAL]: OK")
        }
        if (thermalGuardian.thermallyCancelledTaskIds.isNotEmpty()) {
            lines.append("\n[THERMAL CANCELLED]: ${thermalGuardian.thermallyCancelledTaskIds.joinToString()}")
        }
        // impl-05 step 6b: power level + poll interval in :status (the OS
        // thermal status below is DISPLAY-ONLY — it never feeds the guard).
        val statusSnap = telemetryMonitor.captureSnapshot(config.thermalLimitCelsius)
        val statusLevel = powerProfileAdaptor.evaluate(statusSnap, config.thermalLimitCelsius)
        lines.append("\n[POWER]: ${statusLevel.label} poll ${powerProfileAdaptor.getRecommendedPollIntervalMs()}ms")
        lines.append("\n[OS THERMAL STATUS]: ${osThermalStatusLabel()} (display only, not a guard input)")
        lines.append("\n[TELEMETRY HISTORY]: ${telemetryHistory.size} snapshots")
        for (task in running) lines.append("\n- [RUNNING] ${task.title}")
        if (!config.nodeOsLegacyImportEnabled) {
            lines.append("\n[NodeOS legacy import: off — opt-in via config]")
        }
        showTerminalOutput(lines.toString())
    }

    /**
     * impl-05 step 6a: OS thermal status as a display-only second signal.
     * Coarse OS status, not a sensor reading; never wired into the guard
     * without a threshold decision and a soak period. UNKNOWN when the API
     * is unavailable (below API 29) or throws.
     */
    private fun osThermalStatusLabel(): String {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                ThermalStatusLabel.fromCode(pm.currentThermalStatus)
            } else {
                "UNKNOWN"
            }
        } catch (_: Exception) {
            "UNKNOWN"
        }
    }

    private fun refreshIndexAsync() {
        lifecycleScope.launch(Dispatchers.IO) {
            appIndexer.refreshIndex()
            val all = appIndexer.getAllApps()
            withContext(Dispatchers.Main) {
                if (isDrawerOpen) {
                    // Keeps an active drawer search filter across refresh.
                    applyDrawerFilter(all)
                    binding.alphabetScrubber.setAvailableLetters(appIndexer.getSectionPositions().keys)
                }
            }
        }
    }

    // ── Omni-Bar Operations ─────────────────────────────────

    private fun handleOmniTextChange(text: String) {
        if (text.startsWith(":")) {
            // impl-04 step 3: ":" input suggests commands instead of
            // hiding results (plan: typing :th suggests :theme etc.).
            // Tap-to-insert is NOT implemented: suggestions are shown
            // as text in the terminal output area; typing the full
            // command still works. The app-result adapter takes
            // AppEntry items, not command strings, so a tap-insert
            // would need an adapter change — recorded as a follow-up.
            binding.rvAppFilter.visibility = View.GONE
            val typed = text.substring(1).trim().split(" ").firstOrNull().orEmpty()
            val suggestions = if (typed.isEmpty()) {
                com.polymath.voidlauncher.core.CommandGuide.ALL_NAMES.distinct().take(10)
            } else {
                com.polymath.voidlauncher.core.CommandGuide.suggest(typed, limit = 10)
            }
            if (suggestions.isNotEmpty()) {
                binding.tvTerminalOutput.text = suggestions.joinToString("  ") { ":$it" }
                binding.tvTerminalOutput.visibility = View.VISIBLE
            } else {
                binding.tvTerminalOutput.visibility = View.GONE
            }
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
        val query = commandHelpService.extractInlineSnippet(rawInput)
        if (query.isEmpty()) return

        // Record history & macro step (F-09, F-15). impl-04
        // step 6: :stop is not recorded as a step (it used to be,
        // so every saved macro ended with a nested :stop).
        recordCommandHistory(query)
        if (macroRecorder.isRecording && query != ":stop") {
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
            command == "help" || command == "?" -> {
                showTerminalOutput(commandHelpService.formatQuickGuide())
            }
            command == "wallpaper" || command == "wp" -> {
                commandHelpService.handleWallpaperCommand(
                    subCommand = "",
                    config = config,
                    onUpdateConfig = { updated ->
                        config = updated
                        configManager.saveConfig(updated)
                        applyConfig()
                    },
                    onShowOutput = { showTerminalOutput(it) }
                )
            }
            command.startsWith("wallpaper ") || command.startsWith("wp ") -> {
                val sub = command.removePrefix("wallpaper ").removePrefix("wp ").trim()
                commandHelpService.handleWallpaperCommand(
                    subCommand = sub,
                    config = config,
                    onUpdateConfig = { updated ->
                        config = updated
                        configManager.saveConfig(updated)
                        applyConfig()
                    },
                    onShowOutput = { showTerminalOutput(it) }
                )
            }
            // F-01 LiveScript Eval
            command.startsWith("eval ") -> {
                val expr = command.removePrefix("eval ").trim()
                val result = liveScriptEngine.evaluate(expr)
                commandAudit.record("OMNI", ":eval $expr", if (result.success) "OK" else "FAILED", result.executionTimeMs)
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
                        // impl-06 step 2: real output + exit code, when
                        // the task produced any (tracked runs only).
                        sb.appendLine("[${t.status.uppercase()}] ${t.title} (id: ${t.id}, exit: ${t.exitCode})" +
                            if (t.cancelReason.isNotEmpty()) " [cancel: ${t.cancelReason}]" else "")
                        if (t.outputPreview.isNotBlank()) {
                            sb.appendLine("  ${t.outputPreview.take(500)}")
                        }
                    }
                    showTerminalOutput(sb.toString().trimEnd())
                }
            }
            command.startsWith("kill ") -> {
                // impl-04 step 1: :kill was ungated while :task/:tasks
                // were gated (plan gap G-C4) — same gate, same message.
                if (!config.agentOrchestratorEnabled) {
                    showTerminalOutput("[AGENT GATE] Orchestrator disabled (agent_orchestrator_enabled=false)")
                } else {
                    val taskId = command.removePrefix("kill ").trim()
                    agentOrchestrator.cancelTask(taskId)
                    showTerminalOutput("[TASK CANCELLED]: $taskId")
                }
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
                    if (macroRecorder.startRecording(name, config.macrosDirectory)) {
                        showTerminalOutput("[MACRO RECORDING STARTED]: $name\nType :stop to finish")
                    } else {
                        showTerminalOutput("[MACRO ERROR]: cannot start recording '${name}': ${macroRecorder.lastError ?: "already recording"}")
                    }
                }
            }
            command == "stop" -> {
                if (!config.macrosEnabled) {
                    showTerminalOutput("[MACRO GATE] Disabled (macros_enabled=false)")
                } else {
                    val saved = macroRecorder.stopRecording(config.macrosDirectory)
                    showTerminalOutput(
                        if (saved != null) "[MACRO SAVED]: $saved"
                        else macroRecorder.lastError?.let { "[MACRO ERROR]: $it" } ?: "[MACRO]: Not recording"
                    )
                }
            }
            command.startsWith("play ") -> {
                if (!config.macrosEnabled) {
                    showTerminalOutput("[MACRO GATE] Disabled (macros_enabled=false)")
                } else {
                    val name = command.removePrefix("play ").trim()
                    // impl-04 step 6 (plan gate: approval before a
                    // macro runs shell): playback is synchronous, so
                    // a mid-play dialog cannot pause it — instead the
                    // steps are pre-scanned and, unless auto-allow is
                    // on, the user approves before ANY step runs.
                    // "Allow macro" plays everything; "Abort" plays
                    // nothing. The classifier is pure and JVM-tested.
                    val steps = macroRecorder.loadMacro(name, config.macrosDirectory)
                    val shellSteps = com.polymath.voidlauncher.core.MacroStepClassifier.shellSteps(steps)
                    if (steps.isEmpty()) {
                        showTerminalOutput("[ERROR]: Macro $name not found")
                    } else if (shellSteps.isNotEmpty() && !config.macroShellAutoAllow) {
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Macro wants to run shell")
                            .setMessage("Macro '$name' contains ${shellSteps.size} shell step(s), first: ${shellSteps.first()}\n\nAllow the whole macro, or abort (nothing runs)?")
                            .setPositiveButton("Allow macro") { _, _ ->
                                val ok = macroRecorder.playMacro(name, config.macrosDirectory) { step -> handleOmniSubmit(step) }
                                commandAudit.record("MACRO", ":play $name", if (ok) "OK" else "FAILED")
                                showTerminalOutput(if (ok) "[MACRO EXECUTED]: $name" else "[MACRO ERROR]: ${macroRecorder.lastError ?: "playback failed"}")
                            }
                            .setNegativeButton("Abort", null)
                            .show()
                    } else {
                        val ok = macroRecorder.playMacro(name, config.macrosDirectory) { step -> handleOmniSubmit(step) }
                        commandAudit.record("MACRO", ":play $name", if (ok) "OK" else "FAILED")
                        showTerminalOutput(
                            if (ok) "[MACRO EXECUTED]: $name"
                            else macroRecorder.lastError?.let { "[MACRO ERROR]: $it" } ?: "[ERROR]: Macro $name not found"
                        )
                    }
                }
            }
            // F-09 History & Snippets
            // impl-04 step 3: macro management.
            command == "macros" -> {
                if (!config.macrosEnabled) {
                    showTerminalOutput("[MACRO GATE] Disabled (macros_enabled=false)")
                } else {
                    val names = macroRecorder.listMacros(config.macrosDirectory)
                    if (names.isEmpty()) {
                        showTerminalOutput("=== Macros ===\n\n(none recorded yet — :record <name>, then :stop)")
                    } else {
                        val sb = StringBuilder("=== Macros (${names.size}) ===\n\n")
                        for (n in names) {
                            val steps = macroRecorder.loadMacro(n, config.macrosDirectory).size
                            sb.appendLine("• $n ($steps steps)")
                        }
                        sb.append("\n:play <name> to run · :macros delete <name> to remove")
                        showTerminalOutput(sb.toString().trimEnd())
                    }
                }
            }
            command.startsWith("macros delete ") -> {
                if (!config.macrosEnabled) {
                    showTerminalOutput("[MACRO GATE] Disabled (macros_enabled=false)")
                } else {
                    val name = command.removePrefix("macros delete ").trim()
                    val ok = macroRecorder.deleteMacro(name, config.macrosDirectory)
                    showTerminalOutput(if (ok) "[MACRO DELETED]: $name" else "[MACRO ERROR]: cannot delete '$name' (not found or unsafe name)")
                }
            }
            command.startsWith("history delete ") -> {
                if (!config.commandHistoryEnabled) {
                    showTerminalOutput("[HISTORY GATE] Disabled (command_history_enabled=false)")
                } else {
                    val id = command.removePrefix("history delete ").trim().toLongOrNull()
                    val ok = id != null && commandPalette.delete(id)
                    showTerminalOutput(if (ok) "[HISTORY DELETED]: $id" else "[HISTORY ERROR]: no history entry with that id")
                }
            }
            command.startsWith("snippet delete ") -> {
                val alias = command.removePrefix("snippet delete ").trim()
                val ok = commandPalette.deleteSnippet(alias)
                showTerminalOutput(if (ok) "[SNIPPET DELETED]: $alias" else "[SNIPPET ERROR]: no snippet named '$alias'")
            }
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
                    val result = commandPalette.saveSnippetUnique(parts[0], parts[1])
                    val note = if (result == com.polymath.voidlauncher.core.CommandPalette.SnippetSaveResult.REPLACED) " (replaced the previous command for this alias)" else ""
                    showTerminalOutput("[SNIPPET SAVED]: ${parts[0]} -> ${parts[1]}$note")
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
                val snap = telemetryMonitor.captureSnapshot(config.thermalLimitCelsius)
                val lvl = powerProfileAdaptor.evaluate(snap, config.thermalLimitCelsius)
                showTerminalOutput("[POWER PROFILE]: ${lvl.label}\nBattery: ${snap.batteryPercent}% (battery temp ${snap.batteryTemperatureC}°C — battery sensor, not SoC temp)\nPoll Cadence: ${powerProfileAdaptor.getRecommendedPollIntervalMs()}ms\nThermal: ${if (thermalGuardian.isThrottled) "THROTTLED" else "OK"} · OS thermal status: ${osThermalStatusLabel()} (display only)")
            }
            command == "focus" -> {
                val activeBlock = focusScheduler.checkAndApplySchedule()
                showTerminalOutput(if (activeBlock != null) "[FOCUS ACTIVE]: ${activeBlock.label} (${activeBlock.personaId})" else "[FOCUS]: No active scheduled focus block")
            }
            // Appearance Studio: theme engine suite (:theme list, apply, save, export, import)
            command == "themes" || command == "theme" -> {
                commandHelpService.handleThemeCommand(
                    subCommand = "",
                    config = config,
                    configManager = configManager,
                    onUpdateConfig = { updated ->
                        config = updated
                        applyConfig()
                    },
                    onShowOutput = { showTerminalOutput(it) }
                )
            }
            command.startsWith("theme ") -> {
                val sub = command.removePrefix("theme ").trim()
                commandHelpService.handleThemeCommand(
                    subCommand = sub,
                    config = config,
                    configManager = configManager,
                    onUpdateConfig = { updated ->
                        config = updated
                        applyConfig()
                    },
                    onShowOutput = { showTerminalOutput(it) }
                )
            }
            // View mode switching
            command.startsWith("mode ") -> {
                val mode = command.removePrefix("mode ").trim().uppercase()
                if (mode in listOf("COCKPIT", "STILL", "MINIMAL", "FILES")) {
                    setHomeMode(mode)
                    showTerminalOutput("[MODE SWITCHED]: $mode")
                } else {
                    showTerminalOutput("[ERROR]: mode must be COCKPIT|STILL|MINIMAL|FILES")
                }
            }
            command.startsWith("run ") -> {
                // Void-owned headless shell (Phase 1). :run is the
                // VoidShell path, run off the UI thread.
                val runCmd = command.removePrefix("run ").trim()
                if (runCmd.isBlank()) {
                    showTerminalOutput("[RUN]: empty command")
                } else {
                    showTerminalOutput("[RUNNING]: $runCmd")
                    lifecycleScope.launch(Dispatchers.IO) {
                        val result = voidShell.run(runCmd)
                        withContext(Dispatchers.Main) {
                            auditShellResult(":run $runCmd", result)
                            when (result) {
                                is ShellResult.Success -> showTerminalOutput(
                                    "[RUN OK]:\n" + result.output.ifBlank { "(no output)" } +
                                        if (result.error.isNotBlank()) "\nstderr: " + result.error else ""
                                )
                                is ShellResult.Failed -> showTerminalOutput("[RUN FAILED]: " + result.reason)
                            }
                        }
                    }
                }
            }
            command.startsWith("sh ") -> {
                // :sh is an alias for :run (migration Cycle 1):
                // same VoidShell run, same real output, off the UI thread.
                val shellCmd = command.removePrefix("sh ").trim()
                if (shellCmd.isBlank()) {
                    showTerminalOutput("[SH]: empty command")
                } else {
                    showTerminalOutput("[RUNNING]: $shellCmd")
                    lifecycleScope.launch(Dispatchers.IO) {
                        val result = voidShell.run(shellCmd)
                        withContext(Dispatchers.Main) {
                            auditShellResult(":sh $shellCmd", result)
                            when (result) {
                                is ShellResult.Success -> showTerminalOutput(
                                    "[SH OK]:\n" + result.output.ifBlank { "(no output)" } +
                                        if (result.error.isNotBlank()) "\nstderr: " + result.error else ""
                                )
                                is ShellResult.Failed -> showTerminalOutput("[SH FAILED]: " + result.reason)
                            }
                        }
                    }
                }
            }
            command.startsWith("work ") -> {
                val projectName = command.removePrefix("work ").trim()
                // Void workspace project folder, opened in the files browser.
                // Same resolution as VoidShellPort.openProject: names
                // that escape the files home are refused, not opened.
                val resolved = VoidRoots.resolveProject(filesDir, projectName)
                if (resolved == null) {
                    showTerminalOutput("[WORKSPACE]: invalid project name: $projectName")
                } else {
                    loadFilesDirectory(resolved.absolutePath)
                    showTerminalOutput("[WORKSPACE]: Void home/$projectName")
                }
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
                val clipText = clipboard.getText()
                showTerminalOutput("[CLIPBOARD]:\n$clipText")
            }
            command == "nodeos" || command == "ping" -> {
                // Cycle 2: no NodeOS daemon to ping without the opt-in
                // legacy import; report Void daemon / task status instead.
                val running = voidProcessManager.getRunning()
                showTerminalOutput(
                    "[VOID DAEMON]: ${running.size} task(s) running" +
                        if (config.nodeOsLegacyImportEnabled) "\n[NodeOS legacy import: enabled]" else "\n[NodeOS legacy import: off — opt-in via config]"
                )
            }
            command == "hud" || command == "status" -> {
                showVoidStatus()
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
            command == "audit" -> {
                val entries = commandAudit.recent(20)
                if (entries.isEmpty()) {
                    showTerminalOutput("=== Command Audit ===\n\n(no audited commands yet — shell, :eval, macro play, and JS runs are audited)")
                } else {
                    val sb = StringBuilder("=== Command Audit (newest first) ===\n\n")
                    for (e in entries) {
                        val exit = e.exitCode?.let { " exit=$it" } ?: ""
                        sb.appendLine("[${e.surface}] ${e.input} -> ${e.outcome} (${e.durationMs}ms$exit)")
                    }
                    showTerminalOutput(sb.toString().trimEnd())
                }
            }
            // impl-04 step 7b: QuickJS macro surface. Runs on an
            // IO coroutine (the runner joins the engine thread for
            // the manifest timeout); output returns to the UI thread.
            // If the native library is absent the runner reports
            // Unavailable — that message is shown, not hidden.
            command == "void-js" || command.startsWith("void-js ") -> {
                val sub = command.removePrefix("void-js").trim()
                val dir = config.jsMacrosDirectory
                when {
                    sub.isEmpty() || sub == "list" -> {
                        val names = jsMacroRunner.listMacros(dir)
                        if (names.isEmpty()) {
                            showTerminalOutput("=== JS Macros ===\n\n(none in $dir — .js files with a void-macro manifest comment)")
                        } else {
                            val sb = StringBuilder("=== JS Macros (${names.size}) ===\n\n")
                            for (n in names) {
                                val mark = if (jsMacroRunner.isRunning(n)) "● running" else "○"
                                sb.appendLine("$mark $n")
                            }
                            sb.append("\n:void-js run <name> [--trigger <event>] · :void-js stop <name> · :void-js triggers <name> · :void-js trigger attach|detach <name> <trigger> · :void-js fire <name> <trigger>")
                            showTerminalOutput(sb.toString().trimEnd())
                        }
                    }
                    sub.startsWith("run ") -> {
                        val args = sub.removePrefix("run ").trim().split(Regex("\\s+"))
                        val name = args.getOrNull(0).orEmpty()
                        val triggerIdx = args.indexOf("--trigger")
                        val trigger = if (triggerIdx >= 0) args.getOrNull(triggerIdx + 1) ?: "manual" else "manual"
                        if (name.isEmpty()) {
                            showTerminalOutput("Usage: :void-js run <name> [--trigger <event>]")
                        } else {
                            runJsMacroWithApproval(name, trigger)
                        }
                    }
                    // impl-04 step 7c: fire an ATTACHED trigger only.
                    // There is no daemon yet, so this is the only
                    // trigger-firing path: an explicit user command.
                    // Unattached triggers never fire.
                    sub.startsWith("fire ") -> {
                        val args = sub.removePrefix("fire ").trim().split(Regex("\\s+"))
                        val name = args.getOrNull(0).orEmpty()
                        val trigger = args.getOrNull(1).orEmpty()
                        if (name.isEmpty() || trigger.isEmpty()) {
                            showTerminalOutput("Usage: :void-js fire <name> <trigger>")
                        } else if (!com.polymath.voidlauncher.core.js.JsTriggerStore.isAttached(config.jsMacrosDirectory, name, trigger)) {
                            showTerminalOutput("[JS TRIGGER]: trigger '$trigger' is not attached to '$name' — attach it first (:void-js trigger attach $name $trigger)")
                        } else {
                            runJsMacroWithApproval(name, trigger)
                        }
                    }
                    sub == "triggers" || sub.startsWith("triggers ") -> {
                        val name = sub.removePrefix("triggers").trim()
                        if (name.isEmpty()) {
                            showTerminalOutput("Usage: :void-js triggers <name>")
                        } else {
                            val triggers = com.polymath.voidlauncher.core.js.JsTriggerStore.list(config.jsMacrosDirectory, name)
                            showTerminalOutput(
                                if (triggers.isEmpty()) "[JS TRIGGERS]: '$name' has no attached triggers"
                                else "=== JS Triggers for $name ===\n\n" + triggers.joinToString("\n") { "• $it" }
                            )
                        }
                    }
                    sub.startsWith("trigger attach ") -> {
                        val args = sub.removePrefix("trigger attach ").trim().split(Regex("\\s+"))
                        val name = args.getOrNull(0).orEmpty()
                        val trigger = args.getOrNull(1).orEmpty()
                        if (name.isEmpty() || trigger.isEmpty()) {
                            showTerminalOutput("Usage: :void-js trigger attach <name> <trigger>")
                        } else {
                            val ok = com.polymath.voidlauncher.core.js.JsTriggerStore.attach(config.jsMacrosDirectory, name, trigger)
                            showTerminalOutput(if (ok) "[JS TRIGGER ATTACHED]: $name <- $trigger" else "[JS TRIGGER ERROR]: cannot attach '$trigger' to '$name'")
                        }
                    }
                    sub.startsWith("trigger detach ") -> {
                        val args = sub.removePrefix("trigger detach ").trim().split(Regex("\\s+"))
                        val name = args.getOrNull(0).orEmpty()
                        val trigger = args.getOrNull(1).orEmpty()
                        if (name.isEmpty() || trigger.isEmpty()) {
                            showTerminalOutput("Usage: :void-js trigger detach <name> <trigger>")
                        } else {
                            val ok = com.polymath.voidlauncher.core.js.JsTriggerStore.detach(config.jsMacrosDirectory, name, trigger)
                            showTerminalOutput(if (ok) "[JS TRIGGER DETACHED]: $name -/ $trigger" else "[JS TRIGGER ERROR]: '$trigger' was not attached to '$name'")
                        }
                    }
                    sub.startsWith("stop ") -> {
                        val name = sub.removePrefix("stop ").trim()
                        val stopped = jsMacroRunner.stop(name)
                        showTerminalOutput(if (stopped) "[JS MACRO STOPPED]: $name" else "[JS MACRO]: $name is not running")
                    }
                    else -> {
                        showTerminalOutput("Usage: :void-js list | :void-js run <name> [--trigger <event>] | :void-js stop <name> | :void-js triggers <name> | :void-js trigger attach|detach <name> <trigger> | :void-js fire <name> <trigger>")
                    }
                }
            }
            // impl-04 step 1 (plan gate S2): the ONLY shell escape
            // hatch for typed input. Unknown :commands no longer fall
            // through to shell — see the else branch below.
            command.startsWith("! ") || command == "!" -> {
                val shellCmd = command.removePrefix("!").trim()
                if (shellCmd.isBlank()) {
                    showTerminalOutput("[CMD]: usage: :! <shell command>")
                } else {
                    showTerminalOutput("[RUNNING]: $shellCmd")
                    lifecycleScope.launch(Dispatchers.IO) {
                        val result = voidShell.run(shellCmd)
                        withContext(Dispatchers.Main) {
                            auditShellResult(":! $shellCmd", result)
                            when (result) {
                                is ShellResult.Success -> showTerminalOutput(
                                    "[CMD OK]:\n" + result.output.ifBlank { "(no output)" } +
                                        if (result.error.isNotBlank()) "\nstderr: " + result.error else ""
                                )
                                is ShellResult.Failed -> showTerminalOutput("[CMD FAILED]: " + result.reason)
                            }
                        }
                    }
                }
            }
            else -> {
                // Check saved snippets first so :alias runs the saved command.
                // Snippet chains are depth-guarded (plan gap: only
                // self-reference was guarded; A->B->A was not).
                val snippet = commandPalette.getSnippets().firstOrNull {
                    it.snippetAlias.equals(command, ignoreCase = true)
                }
                if (snippet != null) {
                    if (snippetChainDepth >= 3) {
                        showTerminalOutput("[SNIPPET ERROR]: snippet recursion limit (chain depth exceeded)")
                        return
                    }
                    val target = snippet.commandText.trim()
                    if (target.startsWith(":")) {
                        val inner = target.substring(1).trim()
                        if (!inner.equals(command, ignoreCase = true)) {
                            snippetChainDepth++
                            try { handleCommandMode(inner) } finally { snippetChainDepth-- }
                            return
                        }
                    } else {
                        handleOmniSubmit(target)
                        return
                    }
                }
                // impl-04 step 1 (plan gate S2): unknown :commands do
                // NOT run shell any more. A typo must not execute.
                // :run / :sh / :! are the explicit shell paths.
                if (command.isBlank()) {
                    showTerminalOutput("[CMD]: empty command (type :help for commands)")
                } else {
                    commandAudit.record("OMNI", ":$command", "UNKNOWN")
                    val firstWord = command.split(" ", limit = 2).first()
                    val suggestions = com.polymath.voidlauncher.core.CommandGuide.suggest(firstWord)
                    val hint = if (suggestions.isNotEmpty()) "\nDid you mean: " + suggestions.joinToString(", ") { ":$it" } else ""
                    showTerminalOutput("[UNKNOWN COMMAND]: :$firstWord$hint\nUse :! <cmd> to run shell explicitly, or :help for commands.")
                }
            }
        }
    }

    /**
     * impl-04 step 7b/7c: runs a JS macro with the shell-approval
     * prompt (unless auto-allow) and IO-thread execution, shared by
     * `:void-js run` and `:void-js fire` so both paths enforce the
     * same gates. The runner reports Unavailable when the native
     * library is absent; that message is shown, not hidden.
     */
    private fun runJsMacroWithApproval(name: String, trigger: String) {
        val dir = config.jsMacrosDirectory
        val manifest = jsMacroRunner.parseManifest(name, dir)
        val needsShellApproval = manifest != null &&
            com.polymath.voidlauncher.core.js.JsMacroManifest.Permission.SHELL in manifest.permissions &&
            !config.macroShellAutoAllow
        val runIt = {
            showTerminalOutput("[JS MACRO RUNNING]: $name (trigger: $trigger)")
            lifecycleScope.launch(Dispatchers.IO) {
                val outcome = jsMacroRunner.run(name, dir, trigger)
                commandAudit.record(
                    "JS_MACRO", ":void-js run $name",
                    when (outcome.result) {
                        is com.polymath.voidlauncher.core.js.JsResult.Success -> "OK"
                        is com.polymath.voidlauncher.core.js.JsResult.PermissionDenied -> "DENIED"
                        is com.polymath.voidlauncher.core.js.JsResult.Timeout -> "TIMEOUT"
                        is com.polymath.voidlauncher.core.js.JsResult.Unavailable -> "UNAVAILABLE"
                        is com.polymath.voidlauncher.core.js.JsResult.Failure -> "FAILED"
                    },
                    outcome.durationMs
                )
                withContext(Dispatchers.Main) {
                    val text = when (val r = outcome.result) {
                        is com.polymath.voidlauncher.core.js.JsResult.Success -> "[JS MACRO OK]: ${outcome.name} (${outcome.durationMs}ms)\n${r.output}"
                        is com.polymath.voidlauncher.core.js.JsResult.PermissionDenied -> "[JS MACRO DENIED]: permission '${r.permission}' is not in this macro's manifest"
                        is com.polymath.voidlauncher.core.js.JsResult.Timeout -> "[JS MACRO TIMEOUT]: ${outcome.name} after ${r.timeoutMs}ms"
                        is com.polymath.voidlauncher.core.js.JsResult.Unavailable -> "[JS MACRO UNAVAILABLE]: ${r.reason}"
                        is com.polymath.voidlauncher.core.js.JsResult.Failure -> "[JS MACRO FAILED]: ${r.reason}"
                    }
                    showTerminalOutput(text)
                }
            }
        }
        if (needsShellApproval) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("JS macro wants shell access")
                .setMessage("Macro '$name' has the shell permission. Allow it to run, or abort (nothing runs)?")
                .setPositiveButton("Allow") { _, _ -> runIt() }
                .setNegativeButton("Abort", null)
                .show()
        } else {
            runIt()
        }
    }

    private fun openSettings() {
        val intent = Intent(this, VoidSettingsActivity::class.java)
        startActivity(intent)
    }

    /**
     * Migration Cycle 3: agent workflow sync uses the configured path,
     * whose default is now Void-owned (VoidPaths.WORKFLOW_JSON). A
     * user-set path into another app's private directory would simply
     * be unreadable under Android DAC and is not special-cased any more.
     * NodeOS paths are wired only when the opt-in legacy import flag
     * is on; otherwise the bridge stays blank and reports no NodeOS.
     */
    private fun syncAgentAndNodeOsPaths() {
        agentOrchestrator.workflowPath = config.workflowJsonPath
        nodeOsBridge.isLegacyImportEnabled = config.nodeOsLegacyImportEnabled
        if (config.nodeOsLegacyImportEnabled) {
            nodeOsBridge.workflowPath = config.workflowJsonPath
            nodeOsBridge.nodeosDbPath = config.nodeosDbPath
        } else {
            nodeOsBridge.workflowPath = ""
            nodeOsBridge.nodeosDbPath = ""
        }
    }

    /**
     * Migration Cycle 1: the in-app VoidTerm panel replaces the
     * external terminal launch for the dock TERMINAL slot, the
     * OPEN_TERMINAL gesture, and the VoidShellPort interactive hook.
     * The panel has no separate window yet, so opening it shows its
     * buffer in the terminal output area; :term <command> runs there.
     */
    private fun openVoidTerm() {
        voidTermPanel.isVisible = true
        val rendered = voidTermPanel.getRenderedBuffer()
        showTerminalOutput(
            if (rendered.isBlank()) "[VOIDTERM] Type :term <command> to run in VoidShell"
            else rendered
        )
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun handleDigitKey(view: View, digit: Int) {
        haptic(view)
        bareIntegerRouter.routeInteger(digit)
        resetOmniInput()
    }

    /** Audits one shell result (impl-04 step 8): outcome + exit code only, never output text. */
    private fun auditShellResult(input: String, result: ShellResult) {
        when (result) {
            is ShellResult.Success -> commandAudit.record("SHELL", input, "OK", 0, result.exitCode)
            is ShellResult.Failed -> commandAudit.record("SHELL", input, "FAILED", 0, null)
        }
    }

    /** Records command history honoring `command_history_enabled` / `command_history_limit`. */
    private fun recordCommandHistory(text: String) {
        if (!config.commandHistoryEnabled) return
        commandPalette.recordCommand(text)
        commandPalette.pruneToLimit(config.commandHistoryLimit)
    }

    // ── Long-press app menu & Dock / Persona Micro-Service Delegation ─────

    private fun showAppMenu(entry: AppEntry) {
        haptic(binding.root)
        appActionsService.showAppContextMenu(
            entry = entry,
            onLaunch = {
                launchApp(it)
                openRightDrawer(false)
                if (isCommandCenterOpen) openCommandCenter(false)
            },
            onAddToDock = { showDockSlotPicker(it) }
        )
    }

    private fun showDockSlotPicker(entry: AppEntry) {
        dockSlotService.showSlotPickerForApp(entry, config) { updated ->
            commitUpdatedDockConfig(updated)
        }
    }

    private fun showEditDockSlotDialog(slotIndex: Int) {
        dockSlotService.showEditDockSlotDialog(
            slotIndex = slotIndex,
            config = config,
            appsProvider = { appIndexer.getAllApps() },
            onCommit = { updated -> commitUpdatedDockConfig(updated) }
        )
    }

    private fun commitUpdatedDockConfig(updated: LauncherConfig) {
        config = updated
        configManager.saveConfig(updated)
        bareIntegerRouter.updateSlots(updated.dockSlots)
        rebuildDockSlots()
        rebuildStillFavorites()
        rebuildKeypad()
    }

    private fun showPersonaPickerDialog() {
        personaIdentityService.showPersonaAndModeDialog(
            config = config,
            onPersonaSelected = { _, updated ->
                config = updated
                configManager.saveConfig(config)
                applyIdentityBadge()
                glyphChoreographer.play("FOCUS_MODE_ON")
            },
            onSetHomeMode = { setHomeMode(it) },
            onOpenCommandCenter = { openCommandCenter(true) },
            onOpenWidgetDeck = { openWidgetDeck(true) },
            onOpenWallpaperStudio = {
                wallpaperService.showWallpaperStudioDialog(this, config) { updated ->
                    config = updated
                    configManager.saveConfig(updated)
                    applyConfig()
                }
            }
        )
    }

    private fun launchApp(entry: AppEntry) {
        appActionsService.launchApp(entry)
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
            syncAgentAndNodeOsPaths()
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
            binding.rvCommandCenterApps,
            binding.bareIntegerDock,
            binding.layoutStillFavorites,
            binding.tvIdentityBadge
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
            "OPEN_TERMINAL"       -> openVoidTerm()
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
            "OPEN_HUD"            -> showVoidStatus()
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
            // v2 MINIMAL (gesture path): omni + dock only, same as the
            // MINIMAL mode branch above; HUD line hidden with the cards.
            (binding.tvClock.parent as? View)?.visibility = View.GONE
            binding.bareIntegerDock.visibility = if (config.showDock) View.VISIBLE else View.GONE
            isKeypadVisible = false
            binding.digitKeypadRow.visibility = View.GONE
            binding.btnToggleKeypad.visibility = if (config.showDigitKeypad) View.VISIBLE else View.GONE
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
