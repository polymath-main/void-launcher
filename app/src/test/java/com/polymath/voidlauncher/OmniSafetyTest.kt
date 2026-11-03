package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.CommandGuide
import com.polymath.voidlauncher.core.CommandPaletteMigration
import com.polymath.voidlauncher.core.MacroName
import com.polymath.voidlauncher.core.MacroRecorder
import com.polymath.voidlauncher.core.MacroStepClassifier
import com.polymath.voidlauncher.core.SearchPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * impl-04 step 1 safety tests (pure seams + MacroRecorder, which the
 * harness can compile with the JSON stubs). CommandPalette's SQLite
 * paths and the Activity dispatch are Android-bound and are covered
 * by CI/device verification, not here — the pure rules they rely on
 * (MacroName, SearchPattern, CommandGuide) are asserted here.
 */
class OmniSafetyTest {

    @Test
    fun macroNameTraversalRejected() {
        assertFalse(MacroName.isValid("../../x"))
        assertFalse(MacroName.isValid("a/b"))
        assertFalse(MacroName.isValid("a\\b"))
        assertFalse(MacroName.isValid(""))
        assertFalse(MacroName.isValid(".."))
        assertTrue(MacroName.isValid("morning"))
        assertTrue(MacroName.isValid("a..b"))
    }

    @Test
    fun macroFileStaysUnderMacrosDir() {
        val dir = createTempDir("macros")
        val file = MacroName.resolveFile(dir.absolutePath, "morning")
        assertEquals(File(dir, "morning.json").canonicalPath, file?.canonicalPath)
        assertNull(MacroName.resolveFile(dir.absolutePath, "../../x"))
    }

    @Test
    fun macroSelfPlayDoesNotRecurse() {
        val dir = createTempDir("macroself")
        val recorder = MacroRecorder()
        assertTrue(recorder.startRecording("self"))
        recorder.recordStep(":play self")
        val saved = recorder.stopRecording(dir.absolutePath)
        assertTrue(saved != null)
        assertTrue(File(dir, "self.json").exists())
        var stepsSeen = 0
        val ok = recorder.playMacro("self", dir.absolutePath) { step ->
            stepsSeen++
            // Simulate the Activity re-entering playMacro for a :play step:
            // the nested call must refuse, not recurse.
            if (step == ":play self") {
                assertFalse(recorder.playMacro("self", dir.absolutePath) {})
            }
        }
        assertTrue(ok)
        assertEquals(1, stepsSeen)
        // After playback finishes, the macro can play again.
        assertTrue(recorder.playMacro("self", dir.absolutePath) {})
    }

    @Test
    fun macroBadNameWritesNothing() {
        val dir = createTempDir("macrobad")
        val recorder = MacroRecorder()
        assertFalse(recorder.startRecording("../../x"))
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test
    fun macroListAndDelete() {
        val dir = createTempDir("macrolist")
        val recorder = MacroRecorder()
        assertTrue(recorder.startRecording("alpha"))
        recorder.recordStep(":power")
        recorder.stopRecording(dir.absolutePath)
        assertEquals(listOf("alpha"), recorder.listMacros(dir.absolutePath))
        assertFalse(recorder.deleteMacro("../../x", dir.absolutePath))
        assertTrue(recorder.deleteMacro("alpha", dir.absolutePath))
        assertTrue(recorder.listMacros(dir.absolutePath).isEmpty())
    }

    @Test
    fun macroStepCapAndPartialRecovery() {
        val dir = createTempDir("macrocap")
        val recorder = MacroRecorder()
        assertTrue(recorder.startRecording("big", dir.absolutePath))
        repeat(500) { recorder.recordStep(":power") }
        assertFalse(recorder.recordStep(":power"))
        // A partial file exists mid-record and is recoverable.
        assertEquals(500, recorder.loadPartial("big", dir.absolutePath).size)
        val saved = recorder.stopRecording(dir.absolutePath)
        assertTrue(saved != null)
        // A successful save removes the partial.
        assertTrue(recorder.loadPartial("big", dir.absolutePath).isEmpty())
    }

    @Test
    fun macroStepClassifier() {
        assertTrue(MacroStepClassifier.isShellStep(":run echo hi"))
        assertTrue(MacroStepClassifier.isShellStep(":sh ls"))
        assertTrue(MacroStepClassifier.isShellStep(":! pwd"))
        assertTrue(MacroStepClassifier.isShellStep(":term ls"))
        assertFalse(MacroStepClassifier.isShellStep(":power"))
        assertFalse(MacroStepClassifier.isShellStep(":unknowncmd"))
        assertFalse(MacroStepClassifier.isShellStep("chrome"))
    }

    @Test
    fun likePatternEscapesWildcards() {
        assertEquals("%100\\%%", SearchPattern.escapeLikePattern("100%"))
        assertEquals("%a\\_b%", SearchPattern.escapeLikePattern("a_b"))
        assertEquals("%plain%", SearchPattern.escapeLikePattern("plain"))
    }

    @Test
    fun commandGuideSuggestsByPrefix() {
        val suggestions = CommandGuide.suggest("the")
        assertTrue(suggestions.contains("theme"))
        assertTrue(suggestions.contains("themes"))
    }

    @Test
    fun migrationPolicyIsLossless() {
        assertTrue(CommandPaletteMigration.isLossless(CommandPaletteMigration.V1_TO_V2))
        assertTrue(CommandPaletteMigration.V1_TO_V2.any { it.contains("idx_snippet_alias") })
        assertFalse(CommandPaletteMigration.isLossless(listOf("DROP TABLE history")))
    }

    @Test
    fun commandGuideContainsDock17AndShellHatch() {
        val guide = CommandGuide.formatQuickGuide()
        assertTrue(guide.contains("[1]..[7]"))
        assertTrue(guide.contains(":!"))
        assertTrue(guide.contains(":void-js"))
    }
}
