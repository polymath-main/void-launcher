package com.polymath.voidlauncher.core

import android.os.Build

/**
 * Single owner of Nothing-hardware detection (impl-05 step 5).
 * Previously GlyphChoreographer and NothingGlyphManager each ran their own
 * duplicate detection at init; both now delegate here. Detection is
 * manufacturer == "Nothing" OR model containing "A142" (Phone 2a).
 * This answers "is this a Nothing phone", NOT "is the GDK present" —
 * GDK presence is only learned when reflective calls succeed, and real
 * LED behaviour remains UNKNOWN until a device verification pass exists.
 */
object NothingHardware {
    fun isNothingPhone(): Boolean =
        Build.MANUFACTURER.equals("Nothing", ignoreCase = true) ||
            Build.MODEL.contains("A142", ignoreCase = true)
}
