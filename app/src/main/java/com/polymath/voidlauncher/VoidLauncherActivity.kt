package com.polymath.voidlauncher

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.polymath.voidlauncher.core.AppIndexer
import com.polymath.voidlauncher.core.BareIntegerRouter
import com.polymath.voidlauncher.core.ConfigManager
import com.polymath.voidlauncher.core.NodeOsBridge
import com.polymath.voidlauncher.core.NothingGlyphManager
import com.polymath.voidlauncher.core.TelemetryMonitor
import com.polymath.voidlauncher.core.TermuxIpcDispatcher
import com.polymath.voidlauncher.databinding.ActivityVoidLauncherBinding
import com.polymath.voidlauncher.model.AppEntry
import com.polymath.voidlauncher.model.LauncherConfig
import com.polymath.voidlauncher.ui.AppFilterAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoidLauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoidLauncherBinding

    private lateinit var configManager: ConfigManager
    private lateinit var telemetryMonitor: TelemetryMonitor
    private lateinit var termuxIpc: TermuxIpcDispatcher
    private lateinit var nodeOsBridge: NodeOsBridge
    private lateinit var appIndexer: AppIndexer
    private lateinit var bareIntegerRouter: BareIntegerRouter
    private lateinit var glyphManager: NothingGlyphManager

    private lateinit var appAdapter: AppFilterAdapter
    private var telemetryJob: Job? = null
    private var config: LauncherConfig = LauncherConfig()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge with transparent system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        binding = ActivityVoidLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply window insets for edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = insets.left + dpToPx(16),
                top = insets.top + dpToPx(12),
                right = insets.right + dpToPx(16),
                bottom = insets.bottom + dpToPx(12)
            )
            WindowInsetsCompat.CONSUMED
        }

        initSubsystems()
        setupListeners()
        applyConfig()
        startTelemetryFlow()
        refreshIndexAsync()
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

        // Push config into subsystems
        nodeOsBridge.workflowPath = config.workflowJsonPath
        nodeOsBridge.nodeosDbPath = config.nodeosDbPath
        nodeOsBridge.setDefaultIdentity(config.defaultIdentity)
        bareIntegerRouter.updateSlots(config.dockSlots)

        appAdapter = AppFilterAdapter { appEntry ->
            launchApp(appEntry)
        }

        binding.rvAppFilter.layoutManager = LinearLayoutManager(this)
        binding.rvAppFilter.adapter = appAdapter
    }

    // ── Config Application ──────────────────────────────────

    private fun applyConfig() {
        val c = config.colors

        // Root background
        binding.rootLayout.setBackgroundColor(c.backgroundInt)

        // Telemetry status bar visibility
        val telemetryBarViews = listOf(binding.tvClock, binding.tvBatteryTemp, binding.tvMemory)
        val telemetryBarParent = binding.tvClock.parent as? View
        telemetryBarParent?.visibility = if (config.showTelemetryBar) View.VISIBLE else View.GONE

        // NodeOS card visibility
        binding.telemetryCard.visibility = if (config.showNodeOsCard) View.VISIBLE else View.GONE

        // Intent card visibility
        binding.intentCard.visibility = if (config.showIntentCard) View.VISIBLE else View.GONE

        // Digit keypad visibility
        binding.digitKeypadRow.visibility = if (config.showDigitKeypad) View.VISIBLE else View.GONE

        // Apply colors to HUD elements
        binding.tvClock.setTextColor(c.textPrimaryInt)
        binding.tvMemory.setTextColor(c.textMutedInt)
        binding.tvDaemonStatus.setTextColor(c.secondaryInt)
        binding.tvAstCount.setTextColor(c.textSecondaryInt)
        binding.tvWorkspace.setTextColor(c.textMutedInt)
        binding.tvGitBranch.setTextColor(c.textSecondaryInt)
        binding.tvActiveIntent.setTextColor(c.textPrimaryInt)

        // Identity badge colors
        applyIdentityBadge()

        // Apply card backgrounds dynamically
        applyCardBackground(binding.telemetryCard, c.surfaceInt, c.borderInt)
        applyCardBackground(binding.intentCard, c.surfaceInt, c.borderInt)

        // Omni bar colors
        binding.etOmniInput.setTextColor(c.textPrimaryInt)
        binding.etOmniInput.setHintTextColor(c.textMutedInt)
        binding.etOmniInput.hint = config.omniBarHint
        binding.btnExecute.setTextColor(c.secondaryInt)

        // Omni prompt symbol
        val promptView = (binding.etOmniInput.parent as? LinearLayout)?.getChildAt(0) as? TextView
        promptView?.text = config.omniPromptSymbol
        promptView?.setTextColor(c.accentInt)

        // Font sizes
        binding.tvClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.hudFontSizeSp)
        binding.tvBatteryTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.hudFontSizeSp + 1f)
        binding.tvMemory.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.hudFontSizeSp)
        binding.etOmniInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.omniFontSizeSp)

        // Font family
        val tf = resolveTypeface(config.fontFamily)
        binding.tvClock.typeface = tf
        binding.tvBatteryTemp.typeface = tf
        binding.etOmniInput.typeface = tf

        // Rebuild dock slots from config
        rebuildDockSlots()

        // Apply digit keypad colors
        applyDigitKeypadColors()

        // Update intent view
        updateIntentView()
    }

    private fun applyIdentityBadge() {
        val c = config.colors
        val mode = nodeOsBridge.getIdentityMode()
        if (mode == NodeOsBridge.IDENTITY_VOID) {
            binding.tvIdentityBadge.text = config.identityVoidLabel
            binding.tvIdentityBadge.setTextColor(c.accentInt)
            applyBadgeBackground(binding.tvIdentityBadge, c.accentSurfaceInt, c.accentInt)
        } else {
            binding.tvIdentityBadge.text = config.identityMainLabel
            binding.tvIdentityBadge.setTextColor(c.secondaryInt)
            applyBadgeBackground(binding.tvIdentityBadge, c.secondarySurfaceInt, c.secondaryInt)
        }
    }

    private fun rebuildDockSlots() {
        val c = config.colors
        val slotViews = listOf(
            binding.btnSlot1, binding.btnSlot2, binding.btnSlot3,
            binding.btnSlot4, binding.btnSlot5
        )

        for ((i, slotView) in slotViews.withIndex()) {
            if (i < config.dockSlots.size) {
                val slot = config.dockSlots[i]
                slotView.visibility = View.VISIBLE

                // First child = label, Second child = subtitle
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

    private fun applyDigitKeypadColors() {
        val c = config.colors
        val digitViews = listOf(
            binding.keyDigit1, binding.keyDigit2, binding.keyDigit3,
            binding.keyDigit4, binding.keyDigit5
        )
        for (v in digitViews) {
            v.setTextColor(c.textSecondaryInt)
        }
        binding.keyColon.setTextColor(c.accentInt)
        binding.keyBackspace.setTextColor(c.textMutedInt)
    }

    private fun applyCardBackground(view: View, surfaceColor: Int, borderColor: Int) {
        val drawable = GradientDrawable().apply {
            setColor(surfaceColor)
            setStroke(dpToPx(1), borderColor)
            cornerRadius = dpToPxF(14f)
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

    private fun resolveTypeface(family: String): Typeface {
        return when (family.lowercase()) {
            "monospace"   -> Typeface.MONOSPACE
            "sans-serif"  -> Typeface.SANS_SERIF
            "serif"       -> Typeface.SERIF
            else          -> Typeface.MONOSPACE
        }
    }

    // ── Listeners ──────────────────────────────────────────

    private fun setupListeners() {
        // Bare integer fast slots 1..5
        binding.btnSlot1.setOnClickListener { handleDigitKey(it, 1) }
        binding.btnSlot2.setOnClickListener { handleDigitKey(it, 2) }
        binding.btnSlot3.setOnClickListener { handleDigitKey(it, 3) }
        binding.btnSlot4.setOnClickListener { handleDigitKey(it, 4) }
        binding.btnSlot5.setOnClickListener { handleDigitKey(it, 5) }

        // Fast numerical jump row
        binding.keyDigit1.setOnClickListener { handleDigitKey(it, 1) }
        binding.keyDigit2.setOnClickListener { handleDigitKey(it, 2) }
        binding.keyDigit3.setOnClickListener { handleDigitKey(it, 3) }
        binding.keyDigit4.setOnClickListener { handleDigitKey(it, 4) }
        binding.keyDigit5.setOnClickListener { handleDigitKey(it, 5) }
        binding.keyColon.setOnClickListener {
            haptic(it)
            binding.etOmniInput.setText(":")
            binding.etOmniInput.setSelection(1)
            binding.etOmniInput.requestFocus()
        }
        binding.keyBackspace.setOnClickListener {
            haptic(it)
            val text = binding.etOmniInput.text.toString()
            if (text.isNotEmpty()) {
                binding.etOmniInput.setText(text.substring(0, text.length - 1))
                binding.etOmniInput.setSelection(binding.etOmniInput.text.length)
            }
        }

        // Dual identity toggle
        binding.tvIdentityBadge.setOnClickListener {
            val newMode = nodeOsBridge.toggleIdentityMode()
            applyIdentityBadge()
            Toast.makeText(this, "Identity: $newMode", Toast.LENGTH_SHORT).show()
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

        // Run button
        binding.btnExecute.setOnClickListener {
            handleOmniSubmit(binding.etOmniInput.text.toString())
        }
    }

    // ── Omni Input ──────────────────────────────────────────

    private fun handleOmniTextChange(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            binding.rvAppFilter.visibility = View.GONE
            binding.tvTerminalOutput.visibility = View.GONE
            binding.bareIntegerDock.visibility = View.VISIBLE
            return
        }

        if (trimmed.startsWith(":")) {
            binding.rvAppFilter.visibility = View.GONE
            binding.bareIntegerDock.visibility = View.VISIBLE
            return
        }

        // App search mode
        val matches = appIndexer.filterApps(trimmed, config.omniFuzzyMaxResults)
        if (matches.isNotEmpty()) {
            binding.rvAppFilter.visibility = View.VISIBLE
            binding.bareIntegerDock.visibility = View.GONE
            binding.tvTerminalOutput.visibility = View.GONE
            appAdapter.submitList(matches)
        } else {
            binding.rvAppFilter.visibility = View.GONE
            binding.bareIntegerDock.visibility = View.VISIBLE
        }
    }

    private fun handleOmniSubmit(rawInput: String) {
        val query = rawInput.trim()
        if (query.isEmpty()) return

        // 1. Bare integer
        val digit = query.toIntOrNull()
        if (digit != null && digit in 1..config.dockSlots.size) {
            bareIntegerRouter.routeInteger(digit)
            resetOmniInput()
            return
        }

        // 2. Command mode
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
            command == "config" || command == "cfg" -> {
                // Export default config and show path
                configManager.exportDefaultConfig()
                showTerminalOutput("[CONFIG EXPORTED]:\n~/.voidlauncher/config.json\n\nEdit from Termux:\n  nano ~/.voidlauncher/config.json\n\nReload: return to home screen")
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

    // ── Helpers ──────────────────────────────────────────────

    private fun handleDigitKey(view: View, digit: Int) {
        haptic(view)
        bareIntegerRouter.routeInteger(digit)
        resetOmniInput()
    }

    private fun haptic(view: View) {
        if (config.enableHaptics) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun showTerminalOutput(text: String) {
        binding.tvTerminalOutput.text = text
        binding.tvTerminalOutput.setTextColor(config.colors.secondaryInt)
        binding.tvTerminalOutput.visibility = View.VISIBLE
        binding.rvAppFilter.visibility = View.GONE
    }

    private fun launchApp(entry: AppEntry) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(entry.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                resetOmniInput()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Launch error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetOmniInput() {
        binding.etOmniInput.setText("")
        binding.rvAppFilter.visibility = View.GONE
        binding.tvTerminalOutput.visibility = View.GONE
        binding.bareIntegerDock.visibility = View.VISIBLE
    }

    private fun refreshIndexAsync() {
        lifecycleScope.launch { appIndexer.refreshIndex() }
    }

    // ── Telemetry Flow ──────────────────────────────────────

    private fun startTelemetryFlow() {
        telemetryJob?.cancel()
        telemetryJob = lifecycleScope.launch {
            telemetryMonitor.monitorTelemetry(pollIntervalMs = config.telemetryPollMs).collectLatest { snap ->
                binding.tvClock.text = "${snap.timestampFormatted}${config.clockSuffix}"

                binding.tvBatteryTemp.text = "▲ ${snap.batteryPercent}% [${snap.batteryTemperatureC}°C]"

                val isSafe = snap.batteryTemperatureC <= config.thermalLimitCelsius
                binding.tvBatteryTemp.setTextColor(
                    if (isSafe) config.colors.safeInt else config.colors.criticalInt
                )
                if (!isSafe && config.enableGlyphPulse) {
                    glyphManager.triggerThermalWarning()
                }

                binding.tvMemory.text = "RAM: ${snap.ramUsedMb}M/${snap.ramTotalMb}M"
            }
        }
    }

    private fun updateIntentView() {
        lifecycleScope.launch(Dispatchers.IO) {
            val status = nodeOsBridge.pollIntentStatus()
            withContext(Dispatchers.Main) {
                binding.tvActiveIntent.text = status.activeTaskTitle

                binding.tvDaemonStatus.text = if (status.isDaemonAlive) "● NODEOS: ALIVE" else "● NODEOS: OFFLINE"
                binding.tvDaemonStatus.setTextColor(
                    if (status.isDaemonAlive) config.colors.secondaryInt else config.colors.warningInt
                )

                binding.tvAstCount.text = "AST: ${status.astNodeCount} nodes"
                binding.tvWorkspace.text = "CWD: ${status.workspacePath}"
                binding.tvGitBranch.text = "git: ${status.gitBranch}"
            }
        }
    }

    // ── Lifecycle ────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        // Hot-reload config on every resume
        val newConfig = configManager.loadConfig()
        if (newConfig != config) {
            config = newConfig
            nodeOsBridge.workflowPath = config.workflowJsonPath
            nodeOsBridge.nodeosDbPath = config.nodeosDbPath
            bareIntegerRouter.updateSlots(config.dockSlots)
            applyConfig()
            startTelemetryFlow()
        }
        resetOmniInput()
        updateIntentView()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (binding.etOmniInput.text.isNotEmpty()) {
            resetOmniInput()
        }
        // Stay in launcher — do not call super
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }

    private fun dpToPxF(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }
}
