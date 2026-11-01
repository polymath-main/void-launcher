package com.polymath.voidlauncher

import android.app.Application
import com.polymath.voidlauncher.model.VoidPaths

/**
 * Process entry point.
 *
 * Initializes the app-owned config folder before any Activity/ConfigManager is
 * constructed so [VoidPaths] always resolves to a real, writable directory.
 */
class VoidLauncherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        VoidPaths.init(this)
    }
}
