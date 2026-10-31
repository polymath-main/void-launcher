package com.polymath.voidlauncher.model

import android.graphics.drawable.Drawable

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable? = null
)
