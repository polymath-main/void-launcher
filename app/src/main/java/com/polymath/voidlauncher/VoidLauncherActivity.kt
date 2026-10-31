package com.polymath.voidlauncher

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.polymath.voidlauncher.core.AppIndexer
import com.polymath.voidlauncher.core.BareIntegerRouter
import com.polymath.voidlauncher.core.NodeOsBridge
import com.polymath.voidlauncher.core.NothingGlyphManager
import com.polymath.voidlauncher.core.TelemetryMonitor
import com.polymath.voidlauncher.core.TermuxIpcDispatcher
import com.polymath.voidlauncher.databinding.ActivityVoidLauncherBinding
import com.polymath.voidlauncher.model.AppEntry
import com.polymath.voidlauncher.ui.AppFilterAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoidLauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoidLauncherBinding

    private lateinit var telemetryMonitor: TelemetryMonitor
    private lateinit var termuxIpc: TermuxIpcDispatcher
    private lateinit var nodeOsBridge: NodeOsBridge
    private lateinit var appIndexer: AppIndexer
    private lateinit var bareIntegerRouter: BareIntegerRouter
    private lateinit var glyphManager: NothingGlyphManager

    private lateinit var appAdapter: AppFilterAdapter
    private var telemetryJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge-to-edge true AMOLED black configuration
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.pitch_black)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.pitch_black)

        binding = ActivityVoidLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initSubsystems()
        setupListeners()
        startTelemetryFlow()
        refreshIndexAsync()
    }

    private fun initSubsystems() {
        telemetryMonitor = TelemetryMonitor(this)
        termuxIpc = TermuxIpcDispatcher(this)
        nodeOsBridge = NodeOsBridge(this)
        appIndexer = AppIndexer(this)
        bareIntegerRouter = BareIntegerRouter(this, termuxIpc)
        glyphManager = NothingGlyphManager(this)

        appAdapter = AppFilterAdapter { appEntry ->
            launchApp(appEntry)
        }

        binding.rvAppFilter.layoutManager = LinearLayoutManager(this)
        binding.rvAppFilter.adapter = appAdapter

        updateIntentView()
    }

    private fun setupListeners() {
        // Bare integer fast slots 1..5
        binding.btnSlot1.setOnClickListener { bareIntegerRouter.routeInteger(1) }
        binding.btnSlot2.setOnClickListener { bareIntegerRouter.routeInteger(2) }
        binding.btnSlot3.setOnClickListener { bareIntegerRouter.routeInteger(3) }
        binding.btnSlot4.setOnClickListener { bareIntegerRouter.routeInteger(4) }
        binding.btnSlot5.setOnClickListener { bareIntegerRouter.routeInteger(5) }

        // Dual identity toggle [VOID] <-> [MAIN]
        binding.tvIdentityBadge.setOnClickListener {
            val newMode = nodeOsBridge.toggleIdentityMode()
            binding.tvIdentityBadge.text = "[$newMode]"
            val colorRes = if (newMode == NodeOsBridge.IDENTITY_VOID) R.color.void_crimson else R.color.cyan_hud
            binding.tvIdentityBadge.setTextColor(ContextCompat.getColor(this, colorRes))
            Toast.makeText(this, "Identity Switched: $newMode", Toast.LENGTH_SHORT).show()
        }

        // Omni Input Text Watcher for fast fuzzy app filter
        binding.etOmniInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                handleOmniTextChange(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Omni Input Keyboard Action (Go / Enter)
        binding.etOmniInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                handleOmniSubmit(binding.etOmniInput.text.toString())
                true
            } else {
                false
            }
        }

        // Run button
        binding.btnExecute.setOnClickListener {
            handleOmniSubmit(binding.etOmniInput.text.toString())
        }
    }

    private fun handleOmniTextChange(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            binding.rvAppFilter.visibility = View.GONE
            binding.tvTerminalOutput.visibility = View.GONE
            binding.bareIntegerDock.visibility = View.VISIBLE
            return
        }

        // If command mode starts with ':', hide app filter
        if (trimmed.startsWith(":")) {
            binding.rvAppFilter.visibility = View.GONE
            binding.bareIntegerDock.visibility = View.VISIBLE
            return
        }

        // App search mode
        val matches = appIndexer.filterApps(trimmed)
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

        // 1. Bare integer check (1..5)
        val singleDigit = query.toIntOrNull()
        if (singleDigit != null && singleDigit in 1..5) {
            bareIntegerRouter.routeInteger(singleDigit)
            resetOmniInput()
            return
        }

        // 2. Command mode (:command)
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
                showTerminalOutput("[DISPATCHED HEADLESS]: $shellCmd")
            }
            command.startsWith("work ") -> {
                val projectName = command.removePrefix("work ").trim()
                termuxIpc.openProjectInTermux(projectName)
                showTerminalOutput("[NAVIGATING WORKSPACE]: ~/Projects/$projectName")
            }
            command == "clip" -> {
                val clipText = termuxIpc.getClipboardText()
                showTerminalOutput("[CLIPBOARD]:\n$clipText")
            }
            command == "nodeos" || command == "ping" -> {
                termuxIpc.dispatchCommand("nodeos -c ping", inBackground = true)
                showTerminalOutput("[PING EMITTED]: nodeos -c ping")
            }
            command == "hud" || command == "status" -> {
                termuxIpc.dispatchCommand("pmc status", inBackground = false)
            }
            else -> {
                // Default dispatch to Termux interactive
                termuxIpc.dispatchCommand(command, inBackground = false)
            }
        }
    }

    private fun showTerminalOutput(text: String) {
        binding.tvTerminalOutput.text = text
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
        binding.bareIntegerDock.visibility = View.VISIBLE
    }

    private fun refreshIndexAsync() {
        lifecycleScope.launch {
            appIndexer.refreshIndex()
        }
    }

    private fun startTelemetryFlow() {
        telemetryJob = lifecycleScope.launch {
            telemetryMonitor.monitorTelemetry(pollIntervalMs = 1500L).collectLatest { snap ->
                binding.tvClock.text = "${snap.timestampFormatted} UTC+6"

                val tempStr = "▲ ${snap.batteryPercent}% [${snap.batteryTemperatureC}°C]"
                binding.tvBatteryTemp.text = tempStr

                // Thermal enforcement (<39°C guard)
                if (snap.isThermalSafe) {
                    binding.tvBatteryTemp.setTextColor(ContextCompat.getColor(this@VoidLauncherActivity, R.color.phosphor_green))
                } else {
                    binding.tvBatteryTemp.setTextColor(ContextCompat.getColor(this@VoidLauncherActivity, R.color.void_crimson))
                    glyphManager.triggerThermalWarning()
                }

                binding.tvMemory.text = "RAM: ${snap.ramUsedMb}M/${snap.ramTotalMb}M"
            }
        }
    }

    private fun updateIntentView() {
        lifecycleScope.launch(Dispatchers.IO) {
            val intentStatus = nodeOsBridge.pollIntentStatus()
            withContext(Dispatchers.Main) {
                binding.tvActiveIntent.text = intentStatus.activeTaskTitle
                binding.tvDaemonStatus.text = if (intentStatus.isDaemonAlive) "● NODEOS: ALIVE" else "● NODEOS: OFFLINE"
                val daemonColor = if (intentStatus.isDaemonAlive) R.color.cyan_hud else R.color.amber_warning
                binding.tvDaemonStatus.setTextColor(ContextCompat.getColor(this@VoidLauncherActivity, daemonColor))
                binding.tvAstCount.text = "AST: ${intentStatus.astNodeCount} nodes"
                binding.tvWorkspace.text = "CWD: ${intentStatus.workspacePath}"
                binding.tvGitBranch.text = "git: ${intentStatus.gitBranch}"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resetOmniInput()
        updateIntentView()
    }

    override fun onBackPressed() {
        if (binding.etOmniInput.text.isNotEmpty()) {
            resetOmniInput()
        } else {
            // Stay in launcher
        }
    }
}
