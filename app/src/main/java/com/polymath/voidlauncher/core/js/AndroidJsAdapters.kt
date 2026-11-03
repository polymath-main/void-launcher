package com.polymath.voidlauncher.core.js

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.polymath.voidlauncher.core.shell.ShellResult
import com.polymath.voidlauncher.core.shell.VoidShell
import java.io.File

/**
 * Android implementation of the `Void.*` host adapters (impl-04 step 7b).
 *
 * Android-bound: it cannot run in the JVM harness, so it is NOT in
 * the harness mains. CI/device verification is required for it to
 * be claimed working — the JVM tests cover the runner, manifest,
 * permission wrapper, and sandbox around it, not this class.
 *
 * - apps: PackageManager (real apps; the prototype's mock list
 *   is NOT copied here).
 * - telemetry: sticky battery intent + ActivityManager memory.
 * - notify: NotificationManager channel "void_js"; a
 *   SecurityException (notification permission not granted) falls
 *   back to a Toast instead of crashing the macro.
 * - files: [JsSandbox] under [filesHome] only.
 * - shell: [JsSandbox] allowlist first, then [VoidShell] — never a
 *   raw ProcessBuilder, never an external terminal app.
 * - mode: delegated to the Activity via [onMode] (setHomeMode).
 * - storage: [FileJsStorage] under the JS macros directory.
 * No DOM, no network globals exist here (plan gate S6).
 */
class AndroidJsAdapters(
    private val activity: Activity,
    private val voidShell: VoidShell,
    private val filesHome: String,
    jsMacrosDir: String,
    macroName: String,
    private val onMode: (String) -> Unit
) : JsAdapters {

    private val storage = FileJsStorage(jsMacrosDir, macroName)

    override fun log(message: String) {
        Log.i("VoidJs", message)
    }

    override fun notify(title: String, body: String) {
        try {
            val manager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "void_js"
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Void JS macros", NotificationManager.IMPORTANCE_DEFAULT)
            )
            val notification = NotificationCompat.Builder(activity, channelId)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
            manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        } catch (_: SecurityException) {
            Toast.makeText(activity, "$title: $body", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(activity, "$title: $body", Toast.LENGTH_SHORT).show()
        }
    }

    override fun setMode(name: String) = onMode(name.uppercase())

    override fun telemetry(): JsTelemetry {
        val batteryIntent = activity.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        val memInfo = android.app.ActivityManager.MemoryInfo()
        (activity.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
            .getMemoryInfo(memInfo)
        return JsTelemetry(
            batteryPct = pct,
            memFreeMB = memInfo.availMem / (1024 * 1024),
            memTotalMB = memInfo.totalMem / (1024 * 1024),
            uptimeSec = android.os.SystemClock.elapsedRealtime() / 1000
        )
    }

    override fun appsList(): List<JsApp> {
        return try {
            activity.packageManager.getInstalledApplications(0).map { app ->
                JsApp(app.packageName, activity.packageManager.getApplicationLabel(app)?.toString() ?: app.packageName)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun appsSearch(query: String): List<JsApp> {
        val q = query.lowercase()
        return appsList().filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
    }

    override fun appsLaunch(packageName: String): Boolean {
        return try {
            val intent = activity.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            activity.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun filesList(path: String): List<String> {
        val dir = JsSandbox.resolveFile(filesHome, path)
            ?: throw IllegalArgumentException("files path denied: $path")
        return dir.list()?.sorted() ?: emptyList()
    }

    override fun filesRead(path: String): String {
        val file = JsSandbox.resolveFile(filesHome, path)
            ?: throw IllegalArgumentException("files path denied: $path")
        if (!file.isFile) throw IllegalArgumentException("not a file: $path")
        val bytes = file.readBytes()
        if (bytes.size > JsSandbox.MAX_READ_BYTES) {
            throw IllegalArgumentException("file too large: $path")
        }
        return String(bytes, Charsets.UTF_8)
    }

    override fun filesWrite(path: String, content: String) {
        val file = JsSandbox.resolveFile(filesHome, path)
            ?: throw IllegalArgumentException("files path denied: $path")
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    override fun storageGet(key: String): String? = storage.get(key)

    override fun storageSet(key: String, value: String) = storage.set(key, value)

    override fun sleep(ms: Long) {
        // Runs on the runner's engine thread, never the UI thread.
        Thread.sleep(ms.coerceIn(0L, 5000L))
    }

    override fun shellRun(command: String): String {
        JsSandbox.checkShell(command)?.let { throw IllegalArgumentException(it) }
        return when (val result = voidShell.run(command, filesHome)) {
            is ShellResult.Success -> result.output
            is ShellResult.Failed -> throw IllegalStateException("shell failed: ${result.reason}")
        }
    }
}
