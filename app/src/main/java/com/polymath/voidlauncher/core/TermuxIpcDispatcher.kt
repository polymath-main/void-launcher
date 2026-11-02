package com.polymath.voidlauncher.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.polymath.voidlauncher.core.shell.ShellPort

class TermuxIpcDispatcher(private val context: Context) : ShellPort {

    private val clipboard = AndroidClipboard(context)

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"

        const val EXTRA_RUN_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        const val EXTRA_RUN_COMMAND_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_RUN_COMMAND_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        const val EXTRA_RUN_COMMAND_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        const val EXTRA_RUN_COMMAND_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"

        const val DEFAULT_PREFIX_BASH = "/data/data/com.termux/files/usr/bin/bash"
        const val DEFAULT_PROJECTS_DIR = "/data/data/com.termux/files/home/Projects"
    }

    /**
     * Dispatches a command directly to Termux's RunCommandService via IPC.
     */
    override fun dispatchCommand(
        command: String,
        workDir: String = DEFAULT_PROJECTS_DIR,
        inBackground: Boolean = true
    ): Boolean {
        val dir = workDir.ifBlank { DEFAULT_PROJECTS_DIR }
        return try {
            val intent = Intent(ACTION_RUN_COMMAND).apply {
                component = ComponentName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
                putExtra(EXTRA_RUN_COMMAND_PATH, DEFAULT_PREFIX_BASH)
                putExtra(EXTRA_RUN_COMMAND_ARGUMENTS, arrayOf("-c", command))
                putExtra(EXTRA_RUN_COMMAND_WORKDIR, dir)
                putExtra(EXTRA_RUN_COMMAND_BACKGROUND, inBackground)
                putExtra(EXTRA_RUN_COMMAND_SESSION_ACTION, "0")
            }
            context.startService(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Termux IPC Error: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Brings Termux to the foreground in interactive terminal mode.
     */
    fun launchTermuxInteractive(workDir: String = DEFAULT_PROJECTS_DIR) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(launchIntent)
            } else {
                Toast.makeText(context, "Termux package not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Launch failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Navigates directly into a project subdirectory inside ~/Projects/<subproject>
     */
    fun openProjectInTermux(projectName: String) {
        val targetDir = "$DEFAULT_PROJECTS_DIR/$projectName"
        dispatchCommand("cd \"$targetDir\" && exec bash", workDir = targetDir, inBackground = false)
    }

    // ShellPort wrappers around the legacy Termux method names.
    override fun launchInteractive(workDir: String = DEFAULT_PROJECTS_DIR) {
        launchTermuxInteractive(workDir.ifBlank { DEFAULT_PROJECTS_DIR })
    }

    override fun openProject(projectName: String) {
        openProjectInTermux(projectName)
    }

    /**
     * Clipboard access, now delegated to AndroidClipboard.
     * Kept here so existing call sites keep working during migration.
     */
    fun getClipboardText(): String = clipboard.getText()

    fun setClipboardText(text: String) = clipboard.setText(text)
}
