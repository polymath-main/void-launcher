package com.polymath.voidlauncher

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** impl-02 Step 5 — scrubber feedback, dimming only. Source-text tests
 *  (the view is Android-bound; same UiV2* pattern). Section-header
 *  divider rows were deliberately NOT added: they would shift the
 *  flat-list positions getSectionPositions() returns, breaking the
 *  scrubber scroll they are meant to help. */
class NavigationStep5Test {

    private fun read(rel: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var root: File? = null
        repeat(6) {
            if (root == null && dir != null && File(dir, "app/src/main/java/com/polymath/voidlauncher/core/BareIntegerRouter.kt").exists()) root = dir
            dir = dir?.parentFile
        }
        assumeTrue(root != null)
        val file = File(root, rel)
        assumeTrue("$rel missing", file.exists())
        return file.readText()
    }

    @Test
    fun scrubberDimsLettersWithNoSection() {
        val view = read("app/src/main/java/com/polymath/voidlauncher/ui/AlphabetScrubberView.kt")
        assertTrue("scrubber must accept the available-letter set", view.contains("fun setAvailableLetters(letters: Set<Char>)"))
        assertTrue("scrubber must draw unavailable letters dimmed", view.contains("letters[i] !in available -> dimPaint"))
        assertTrue("scrubber must keep the selected letter accented", view.contains("i == selectedIndex -> activePaint"))
        assertTrue("scrubber must not dim before the first index load", view.contains("availableLetters: Set<Char>? = null"))
    }

    @Test
    fun activityFeedsSectionKeysToScrubberOnOpenAndRefresh() {
        val activity = read("app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt")
        val count = Regex("setAvailableLetters\\(appIndexer\\.getSectionPositions\\(\\)\\.keys\\)").findAll(activity).count()
        assertTrue("scrubber must be fed on drawer open and on index refresh (found $count)", count >= 2)
    }

    @Test
    fun scrubberSelectionStillOnlyReportsTheLetter() {
        val view = read("app/src/main/java/com/polymath/voidlauncher/ui/AlphabetScrubberView.kt")
        assertTrue("scrubber tap must still report the letter", view.contains("onLetterSelectedListener?.invoke(letters[index])"))
    }
}
