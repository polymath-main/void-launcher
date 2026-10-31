package com.polymath.voidlauncher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.polymath.voidlauncher.core.AppIndexer
import com.polymath.voidlauncher.core.BareIntegerRouter
import com.polymath.voidlauncher.core.ConfigManager
import com.polymath.voidlauncher.core.FileManager
import com.polymath.voidlauncher.core.NodeOsBridge
import com.polymath.voidlauncher.core.NothingGlyphManager
import com.polymath.voidlauncher.core.TelemetryMonitor
import com.polymath.voidlauncher.core.TermuxIpcDispatcher
import com.polymath.voidlauncher.core.VoidNotificationListenerService
import com.polymath.voidlauncher.databinding.ActivityVoidLauncherBinding
import com.polymath.voidlauncher.model.AppEntry
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

    private lateinit var configManager: ConfigManager
    private lateinit var telemetryMonitor: TelemetryMonitor
    private lateinit var termuxIpc: TermuxIpcDispatcher
    private lateinit var nodeOsBridge: NodeOsBridge
    private lateinit var appIndexer: AppIndexer
    private lateinit var bareIntegerRouter: BareIntegerRouter
    private lateinit var glyphManager: NothingGlyphManager
    private lateinit var fileManager: FileManager

    private lateinit var appAdapter: AppFilterAdapter
    private lateinit var drawerAdapter: DrawerAppAdapter
    private lateinit var fileAdapter: FileBrowserAdapter
    private lateinit var notificationAdapter: NotificationAdapter

    private var telemetryJob: Job? = null
    private var notificationJob: Job? = null
    private var config: LauncherConfig = LauncherConfig()
    private var currentFilesPath: String = ""
    private var isDrawerOpen: Boolean = false

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
    }

    /**
     * Positions the omni-bar and keyboard input box smoothly directly on top of the keyboard space
     * when the IME soft keyboard opens, without jitter or clipping.
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

        // App filter adapter (omni search)
        appAdapter = AppFilterAdapter { appEntry ->
            launchApp(entry = appEntry)
        }
        binding.rvAppFilter.layoutManager = LinearLayoutManager(this)
        binding.rvAppFilter.adapter = appAdapter

        // Right drawer adapter (all apps)
        drawerAdapter = DrawerAppAdapter { appEntry ->
            launchApp(entry = appEntry)
            openRightDrawer(false)
        }
        binding.rvDrawerApps.layoutManager = LinearLayoutManager(this)
        binding.rvDrawerApps.adapter = drawerAdapter

        // File browser adapter
        fileAdapter = FileBrowserAdapter(
            fileManager = fileManager,
            onItemClick = { fileItem -> handleFileItemClick(fileItem) },
            onItemMoreClick = { fileItem, anchorView -> showFileOperationsDialog(fileItem) }
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
        binding.digitKeypadRow.visibility = if (config.showDigitKeypad) View.VISIBLE else View.GONE

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

        // Keypad buttons
        applyDigitKeypadColors()

        // Drawer background alpha
        val drawerAlphaInt = (config.drawerAlpha * 255).toInt().coerceIn(0, 255)
        binding.rightDrawerLayout.setBackgroundColor(Color.argb(drawerAlphaInt, 8, 8, 8))

        // Apply app icon customizations to adapters
        appAdapter.showIcons = config.showAppIcons
        drawerAdapter.showIcons = config.showAppIcons
        appAdapter.iconStyle = config.appIconStyle
        drawerAdapter.iconStyle = config.appIconStyle

        // Scrubber visibility
        binding.alphabetScrubber.visibility = if (config.enableAtoZScrubber) View.VISIBLE else View.GONE

        // Default home mode
        applyHomeMode()

        // Check default launcher status
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

    private fun requestSetDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    startActivity(intent)
                    return
                }
            }
        }

        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    private fun applyHomeMode() {
        when (config.defaultHomeMode.uppercase()) {
            "MINIMAL" -> {
                binding.telemetryCard.visibility = View.GONE
                binding.intentCard.visibility = View.GONE
                binding.notificationsCard.visibility = View.GONE
                binding.bareIntegerDock.visibility = View.GONE
                binding.filesBrowserLayout.visibility = View.GONE
            }
            "FILES" -> {
                binding.filesBrowserLayout.visibility = View.VISIBLE
                loadFilesDirectory(currentFilesPath)
            }
            else -> {
                // COCKPIT
                binding.filesBrowserLayout.visibility = View.GONE
            }
        }
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

    // ── Listeners ──────────────────────────────────────────

    private fun setupListeners() {
        // Default Launcher Banner Action
        binding.btnSetDefaultLauncher.setOnClickListener {
            haptic(it)
            requestSetDefaultLauncher()
        }

        // Built-in settings button in HUD
        binding.btnQuickSettings.setOnClickListener {
            haptic(it)
            openSettings()
        }

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

        // Run button
        binding.btnExecute.setOnClickListener {
            handleOmniSubmit(binding.etOmniInput.text.toString())
        }

        // Right Drawer Listeners
        binding.btnDrawerTabHandle.setOnClickListener {
            haptic(it)
            toggleRightDrawer()
        }
        binding.btnDrawerClose.setOnClickListener {
            openRightDrawer(false)
        }

        // Alphabet Scrubber Touch Scrubbing
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
            // Hide files browser if open
            binding.filesBrowserLayout.visibility = View.GONE
        } else {
            binding.rightDrawerLayout.visibility = View.GONE
            binding.btnDrawerTabHandle.text = "‹"
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
            loadFilesDirectory(currentFilesPath.ifEmpty { config.filesDefaultPath })
            openRightDrawer(false)
        } else {
            binding.filesBrowserLayout.visibility = View.GONE
            if (config.showDock) binding.bareIntegerDock.visibility = View.VISIBLE
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
            // Text preview or open
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
                        // Open in Termux
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
            telemetryMonitor.monitorTelemetry().collectLatest { snap ->
                binding.tvClock.text = snap.timestampFormatted
                binding.tvBatteryTemp.text = "▲ ${snap.batteryPercent}% [${snap.batteryTemperatureC}°C]"
                val usedGb = String.format(java.util.Locale.US, "%.1f", snap.ramUsedMb / 1024.0)
                binding.tvMemory.text = "RAM: ${usedGb}G"

                if (snap.batteryTemperatureC > config.thermalLimitCelsius) {
                    glyphManager.triggerThermalWarning()
                }

                // NodeOS Daemon and Ast update
                updateNodeOsHudView()
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
                val filtered = rawList.filter { notif ->
                    if (config.filterOngoingNotifications && notif.isOngoing) false else true
                }.take(config.notificationsMaxCount)

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
    }

    private fun resetOmniInput() {
        binding.etOmniInput.setText("")
        binding.rvAppFilter.visibility = View.GONE
        binding.tvTerminalOutput.visibility = View.GONE
        if (config.showDock && binding.filesBrowserLayout.visibility != View.VISIBLE) {
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
        // Hot-reload config on every resume
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
        if (isDrawerOpen) {
            openRightDrawer(false)
            return
        }
        if (binding.filesBrowserLayout.visibility == View.VISIBLE) {
            showFilesBrowser(false)
            return
        }
        if (binding.etOmniInput.text.isNotEmpty()) {
            resetOmniInput()
            return
        }
        // Stay in launcher
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }

    private fun dpToPxF(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }
}
