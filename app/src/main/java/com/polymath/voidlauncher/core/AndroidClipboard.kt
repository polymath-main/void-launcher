package com.polymath.voidlauncher.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/**
 * AndroidClipboard — clipboard access extracted out of
 * TermuxIpcDispatcher (refined plan, Phase 1).
 *
 * Clipboard access was never a Termux feature; it is Android
 * ClipboardManager. Behaviour is identical to the old code, including
 * the "Copied to clipboard" toast on set, so existing call sites
 * behave the same after the move.
 */
class AndroidClipboard(private val context: Context) {

    fun getText(): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip != null && clip.itemCount > 0) {
            return clip.getItemAt(0).text?.toString() ?: ""
        }
        return ""
    }

    fun setText(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("VoidLauncher", text)
        cm.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
