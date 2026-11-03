package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.BuiltInThemes
import com.polymath.voidlauncher.model.DockStyle
import com.polymath.voidlauncher.model.GlyphMode
import com.polymath.voidlauncher.model.OverlayShaderType
import com.polymath.voidlauncher.model.ScrimLevel
import com.polymath.voidlauncher.model.ThemePersona
import com.polymath.voidlauncher.model.VoidThemeManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests verifying Scheme-Based Theme Library (`STILL & COCKPIT v2`).
 * Blueprint 07 implementation verification.
 */
class ThemeLibraryTest {

    private fun findRepoRoot(): File? {
        var dir: File? = File(".").canonicalFile
        while (dir != null) {
            if (File(dir, "settings.gradle").exists() && File(dir, "app").isDirectory) {
                return dir
            }
            dir = dir.parentFile
        }
        return null
    }

    private fun read(rel: String): String {
        val root = findRepoRoot()
        assumeTrue("repo root not found", root != null)
        val file = File(root, rel)
        assumeTrue("$rel missing", file.exists())
        return file.readText()
    }

    @Test
    fun testBuiltInThemesIntegrity() {
        assertEquals("Must provide exactly 5 reference theme presets", 5, BuiltInThemes.ALL.size)

        for (theme in BuiltInThemes.ALL) {
            assertTrue("Touch target must be >= 48dp", theme.touchTargetMinDp >= 48)
            assertTrue("Schema version must be 2.0", theme.schemaVersion == "2.0")
            assertTrue("Built-in flag must be true", theme.isBuiltIn)
            assertTrue("Primary surface hex valid", VoidThemeManifest.isValidHex(theme.surfacePrimaryHex))
            assertTrue("Elevated surface hex valid", VoidThemeManifest.isValidHex(theme.surfaceElevatedHex))
            assertTrue("Text primary hex valid", VoidThemeManifest.isValidHex(theme.textPrimaryHex))
            assertTrue("Accent primary hex valid", VoidThemeManifest.isValidHex(theme.accentPrimaryHex))
            assertTrue("Accent warn hex valid", VoidThemeManifest.isValidHex(theme.accentWarnHex))
            assertTrue("Border hairline hex valid", VoidThemeManifest.isValidHex(theme.borderHairlineHex))
        }
    }

    @Test
    fun testObsidianVoidPreset() {
        val theme = BuiltInThemes.OBSIDIAN_VOID
        assertEquals("obsidian_void", theme.id)
        assertEquals("#000000", theme.surfacePrimaryHex)
        assertEquals("#0D0D0D", theme.surfaceElevatedHex)
        assertEquals("#FFFFFF", theme.textPrimaryHex)
        assertEquals("#FF1E1E", theme.accentPrimaryHex)
        assertEquals("#262626", theme.borderHairlineHex)
        assertEquals(OverlayShaderType.DOT_MATRIX, theme.overlayShader)
        assertEquals(ScrimLevel.SCRIM_70, theme.scrimLevel)
        assertEquals(ThemePersona.VOID, theme.defaultPersona)
        assertEquals(DockStyle.NUMERIC_BRACKET, theme.dockStyle)
        assertEquals(GlyphMode.PULSE_SYNC, theme.glyphMode)
    }

    @Test
    fun testPhosphorTerminalPreset() {
        val theme = BuiltInThemes.PHOSPHOR_TERMINAL
        assertEquals("phosphor_terminal", theme.id)
        assertEquals("#050806", theme.surfacePrimaryHex)
        assertEquals("#00FF66", theme.textPrimaryHex)
        assertEquals("#FFB000", theme.accentWarnHex)
        assertEquals(OverlayShaderType.CRT_SCANLINE, theme.overlayShader)
        assertEquals(ScrimLevel.SCRIM_70, theme.scrimLevel)
        assertEquals(ThemePersona.AGENT, theme.defaultPersona)
        assertEquals(DockStyle.ASCII_TTY, theme.dockStyle)
    }

    @Test
    fun testStillnessEinkPreset() {
        val theme = BuiltInThemes.STILLNESS_EINK
        assertEquals("stillness_eink", theme.id)
        assertEquals("#F4F1EA", theme.surfacePrimaryHex)
        assertEquals("#1A1A1A", theme.textPrimaryHex)
        assertEquals("#525252", theme.accentPrimaryHex)
        assertEquals("#D6D1C4", theme.borderHairlineHex)
        assertEquals(OverlayShaderType.PAPER_GRAIN, theme.overlayShader)
        assertEquals(ScrimLevel.SCRIM_25, theme.scrimLevel)
        assertEquals(ThemePersona.STILL, theme.defaultPersona)
        assertEquals(DockStyle.EDITORIAL_SERIF, theme.dockStyle)
        assertEquals(GlyphMode.SILENT_FLIP, theme.glyphMode)
        assertTrue("Stillness E-Ink should have a light surface", theme.isLightSurface)
    }

    @Test
    fun testCyberTelemetryPreset() {
        val theme = BuiltInThemes.CYBER_TELEMETRY
        assertEquals("cyber_telemetry", theme.id)
        assertEquals("#0A0F1A", theme.surfacePrimaryHex)
        assertEquals("#00F0FF", theme.accentPrimaryHex)
        assertEquals("#FF0055", theme.accentWarnHex)
        assertEquals(OverlayShaderType.NEON_HUD, theme.overlayShader)
        assertEquals(ScrimLevel.SCRIM_45, theme.scrimLevel)
        assertTrue("Cyber Telemetry enables wallpaper", theme.showSystemWallpaper)
        assertEquals(DockStyle.CHAMFERED_HUD, theme.dockStyle)
    }

    @Test
    fun testTacticalBlueprintPreset() {
        val theme = BuiltInThemes.TACTICAL_BLUEPRINT
        assertEquals("tactical_blueprint", theme.id)
        assertEquals("#0A0E17", theme.surfacePrimaryHex)
        assertEquals("#FF5500", theme.accentPrimaryHex)
        assertEquals("#1E2D47", theme.borderHairlineHex)
        assertEquals(OverlayShaderType.CAD_BLUEPRINT_GRID, theme.overlayShader)
        assertEquals(ScrimLevel.SCRIM_45, theme.scrimLevel)
        assertEquals(ThemePersona.MAIN, theme.defaultPersona)
        assertEquals(DockStyle.CAD_WIREFRAME, theme.dockStyle)
    }

    @Test
    fun testManifestSerializationAndDeserialization() {
        for (original in BuiltInThemes.ALL) {
            val jsonString = original.toJsonString()
            val parsed = VoidThemeManifest.fromJsonString(jsonString, trusted = true)
            assertEquals(original.id, parsed.id)
            assertEquals(original.name, parsed.name)
            assertEquals(original.surfacePrimaryHex, parsed.surfacePrimaryHex)
            assertEquals(original.surfaceElevatedHex, parsed.surfaceElevatedHex)
            assertEquals(original.textPrimaryHex, parsed.textPrimaryHex)
            assertEquals(original.accentPrimaryHex, parsed.accentPrimaryHex)
            assertEquals(original.overlayShader, parsed.overlayShader)
            assertEquals(original.scrimLevel, parsed.scrimLevel)
            assertEquals(original.dockStyle, parsed.dockStyle)
            assertEquals(original.defaultPersona, parsed.defaultPersona)
        }
    }

    @Test
    fun testShareUriEncodingAndDecoding() {
        val original = BuiltInThemes.CYBER_TELEMETRY
        val uri = original.encodeShareUri()
        assertTrue("Must start with void://theme/import?payload=", uri.startsWith("void://theme/import?payload="))
        assertTrue("isShareUri must be true", VoidThemeManifest.isShareUri(uri))

        val decoded = VoidThemeManifest.decodeShareUri(uri)
        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(original.accentPrimaryHex, decoded.accentPrimaryHex)
        assertEquals(original.overlayShader, decoded.overlayShader)
        assertEquals(original.scrimLevel, decoded.scrimLevel)
        assertEquals(original.dockStyle, decoded.dockStyle)
        assertFalse("Imported theme must not be marked built-in", decoded.isBuiltIn)
    }

    @Test
    fun testBuiltInThemesAliasResolution() {
        assertEquals(BuiltInThemes.OBSIDIAN_VOID, BuiltInThemes.byId("void"))
        assertEquals(BuiltInThemes.OBSIDIAN_VOID, BuiltInThemes.byId("obsidian"))
        assertEquals(BuiltInThemes.PHOSPHOR_TERMINAL, BuiltInThemes.byId("matrix"))
        assertEquals(BuiltInThemes.PHOSPHOR_TERMINAL, BuiltInThemes.byId("amber"))
        assertEquals(BuiltInThemes.STILLNESS_EINK, BuiltInThemes.byId("still"))
        assertEquals(BuiltInThemes.STILLNESS_EINK, BuiltInThemes.byId("eink"))
        assertEquals(BuiltInThemes.CYBER_TELEMETRY, BuiltInThemes.byId("cyber"))
        assertEquals(BuiltInThemes.CYBER_TELEMETRY, BuiltInThemes.byId("midnight"))
        assertEquals(BuiltInThemes.TACTICAL_BLUEPRINT, BuiltInThemes.byId("blueprint"))
        assertEquals(BuiltInThemes.TACTICAL_BLUEPRINT, BuiltInThemes.byId("graphite"))
    }

    @Test
    fun testColorPaletteExpansion() {
        val theme = BuiltInThemes.TACTICAL_BLUEPRINT
        val palette = theme.toColorPalette()
        assertEquals(theme.surfacePrimaryHex, palette.background)
        assertEquals(theme.surfaceElevatedHex, palette.surface)
        assertEquals(theme.textPrimaryHex, palette.textPrimary)
        assertEquals(theme.textSecondaryHex, palette.textSecondary)
        assertEquals(theme.accentPrimaryHex, palette.accent)
        assertEquals(theme.borderHairlineHex, palette.border)
    }

    @Test
    fun testAndroidManifestIntegrity() {
        val manifestXml = read("app/src/main/AndroidManifest.xml")
        assertTrue("FileProvider must be configured in AndroidManifest.xml", manifestXml.contains("androidx.core.content.FileProvider"))
        assertTrue("fileprovider authority must be configured", manifestXml.contains("\${applicationId}.fileprovider"))
        assertTrue("file_paths resource must be referenced", manifestXml.contains("@xml/file_paths"))
        assertTrue("void:// scheme filter must be registered", manifestXml.contains("android:scheme=\"void\""))
        assertTrue("host=theme filter must be registered", manifestXml.contains("android:host=\"theme\""))

        val filePathsXml = read("app/src/main/res/xml/file_paths.xml")
        assertTrue("file_paths must declare cache-path", filePathsXml.contains("<cache-path"))
        assertTrue("file_paths must declare external-files-path", filePathsXml.contains("<external-files-path"))
    }
}
