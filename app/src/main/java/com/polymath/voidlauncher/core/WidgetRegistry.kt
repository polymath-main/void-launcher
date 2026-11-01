package com.polymath.voidlauncher.core

import android.content.Context
import android.util.Log
import com.polymath.voidlauncher.model.WidgetDescriptor
import org.json.JSONObject
import java.io.File

/**
 * VoidWidget Registry (F-04).
 *
 * Discovers and coordinates pluggable card widgets from ~/.voidlauncher/widgets/*.widget.json.
 */
class WidgetRegistry(private val context: Context) {

    companion object {
        private const val TAG = "WidgetRegistry"
    }

    private val widgets = mutableListOf<WidgetDescriptor>()

    fun loadWidgets(directoryPath: String): List<WidgetDescriptor> {
        widgets.clear()
        try {
            val dir = File(directoryPath)
            if (!dir.exists() || !dir.isDirectory) return emptyList()

            val files = dir.listFiles { f -> f.name.endsWith(".widget.json") } ?: return emptyList()
            for (file in files) {
                try {
                    val json = JSONObject(file.readText())
                    val id = json.optString("id", file.nameWithoutExtension)
                    val descriptor = WidgetDescriptor(
                        id = id,
                        title = json.optString("title", id),
                        type = json.optString("type", "SCRIPT"),
                        source = json.optString("source", ""),
                        refreshIntervalMs = json.optLong("refresh_ms", 5000L),
                        heightDp = json.optInt("height_dp", 48),
                        actionOnClick = json.optString("action_on_click", ""),
                        isEnabled = json.optBoolean("enabled", true)
                    )
                    if (descriptor.isEnabled) {
                        widgets.add(descriptor)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing widget file ${file.name}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot load widgets from $directoryPath: ${e.message}")
        }
        return widgets
    }

    fun getActiveWidgets(): List<WidgetDescriptor> = widgets

    fun formatWidgetContent(descriptor: WidgetDescriptor): String {
        return when (descriptor.type.uppercase()) {
            "STATIC" -> descriptor.source
            "SCRIPT" -> "[Widget: ${descriptor.title} ready]"
            else -> descriptor.source
        }
    }
}
