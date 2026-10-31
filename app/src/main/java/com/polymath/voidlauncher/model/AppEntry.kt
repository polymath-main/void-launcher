package com.polymath.voidlauncher.model

import android.graphics.drawable.Drawable

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable? = null
) {
    val sectionHeader: Char
        get() {
            val first = label.firstOrNull()?.uppercaseChar() ?: '#'
            return if (first in 'A'..'Z') first else '#'
        }
}
