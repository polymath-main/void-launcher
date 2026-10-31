package com.polymath.voidlauncher.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.polymath.voidlauncher.model.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class AppIndexer(private val context: Context) {

    private val packageManager = context.packageManager
    private val cachedApps = mutableListOf<AppEntry>()

    suspend fun refreshIndex(): List<AppEntry> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)
        val currentPackage = context.packageName

        val apps = resolveInfos.mapNotNull { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg == currentPackage) return@mapNotNull null

            val label = resolveInfo.loadLabel(packageManager).toString()
            val activityName = resolveInfo.activityInfo.name

            AppEntry(
                label = label,
                packageName = pkg,
                activityName = activityName
            )
        }.sortedBy { it.label.lowercase(Locale.ROOT) }

        synchronized(cachedApps) {
            cachedApps.clear()
            cachedApps.addAll(apps)
        }

        apps
    }

    fun getAllApps(): List<AppEntry> {
        synchronized(cachedApps) {
            return ArrayList(cachedApps)
        }
    }

    /**
     * Fast fuzzy filtering based on search query.
     */
    fun filterApps(query: String, maxResults: Int = 5): List<AppEntry> {
        val cleanQuery = query.trim().lowercase(Locale.ROOT)
        if (cleanQuery.isEmpty()) return emptyList()

        synchronized(cachedApps) {
            return cachedApps.filter { app ->
                val labelLower = app.label.lowercase(Locale.ROOT)
                val pkgLower = app.packageName.lowercase(Locale.ROOT)
                
                // 1. Prefix match
                labelLower.startsWith(cleanQuery) ||
                // 2. Contains match
                labelLower.contains(cleanQuery) ||
                // 3. Package name match
                pkgLower.contains(cleanQuery)
            }.take(maxResults)
        }
    }

    /**
     * Finds the best single matching app, or null.
     */
    fun findBestMatch(query: String): AppEntry? {
        val results = filterApps(query, 1)
        return results.firstOrNull()
    }
}
