package com.polymath.voidlauncher.model

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.util.Base64
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.roundToInt

/** Surface overlay shader drawn by `LiquidTouchSurfaceView` (Blueprint 07 §1). */
enum class OverlayShaderType { NONE, DOT_MATRIX, CRT_SCANLINE, PAPER_GRAIN, NEON_HUD, CAD_BLUEPRINT_GRID }

/** OLED readability scrim painted over system / custom wallpapers. */
enum class ScrimLevel(val fraction: Float, val label: String) {
    SCRIM_25(0.25f, "Light (25%)"),
    SCRIM_45(0.45f, "Balanced (45%)"),
    SCRIM_70(0.70f, "Deep OLED (70%)");

    /** Scrim alpha on the 0..255 channel scale. */
    val alpha255: Int get() = (fraction * 255f).roundToInt().coerceIn(0, 255)

    val percent: Int get() = (fraction * 100f).roundToInt()
}

/** Nothing Glyph behaviour hook for a theme. */
enum class GlyphMode { PULSE_SYNC, SILENT_FLIP, ACTIVE_HUD, OFF }

/** Dock slot + long-press menu rendering style. */
enum class DockStyle { NUMERIC_BRACKET, ASCII_TTY, EDITORIAL_SERIF, CHAMFERED_HUD, CAD_WIREFRAME }

/** Default workspace persona bound by a theme. [label] is the serialized schema value. */
enum class ThemePersona(val label: String, val personaId: String) {
    VOID("[VOID]", "void"),
    STILL("[STILL]", "still"),
    MAIN("[MAIN]", "main"),
    AGENT("[AGENT]", "agent"),
    CLIENT("[CLIENT]", "client");

    companion object {
        /** Accepts `[VOID]`, `VOID` or `void`. */
        fun parse(raw: String?, fallback: ThemePersona = VOID): ThemePersona {
            val key = raw?.trim()?.removePrefix("[")?.removeSuffix("]")?.uppercase(Locale.US) ?: return fallback
            return values().firstOrNull { it.name == key } ?: fallback
        }
    }
}

/**
 * Universal theme contract — `VoidThemeSchema` v2.0 (`*.voidtheme` files and
 * `void://theme/import?payload=<base64url>` share URIs).
 *
 * Every built-in preset, user customization and imported theme is one of these.
 * Pure Kotlin + org.json only, so it is unit-testable on the JVM.
 */
data class VoidThemeManifest(
    // ── Metadata ─────────────────────────────────────────────
    val schemaVersion: String = SCHEMA_VERSION,
    val id: String,
    val name: String,
    val author: String = "VoidLauncher",
    val isBuiltIn: Boolean = false,

    // ── Color tokens ─────────────────────────────────────────
    val surfacePrimaryHex: String,
    val surfaceElevatedHex: String,
    val textPrimaryHex: String,
    val textSecondaryHex: String,
    val accentPrimaryHex: String,
    val accentWarnHex: String,
    val borderHairlineHex: String,

    // ── Typography & geometry ────────────────────────────────
    val headerFont: String = "monospace",
    val monoFont: String = "monospace",
    val cornerRadiusDp: Int = 8,
    val borderWidthDp: Int = 1,
    val touchTargetMinDp: Int = MIN_TOUCH_TARGET_DP,
    val overlayShader: OverlayShaderType = OverlayShaderType.NONE,

    // ── Surface & wallpaper hooks ────────────────────────────
    val showSystemWallpaper: Boolean = false,
    val scrimLevel: ScrimLevel = ScrimLevel.SCRIM_45,
    val customWallpaperPath: String = "",

    // ── Micro-service hooks ──────────────────────────────────
    val defaultPersona: ThemePersona = ThemePersona.VOID,
    val glyphMode: GlyphMode = GlyphMode.PULSE_SYNC,
    val dockStyle: DockStyle = DockStyle.NUMERIC_BRACKET
) {

    /** True when the primary surface is light (paper themes): drives contrast decisions. */
    val isLightSurface: Boolean get() = luminance(surfacePrimaryHex) > 0.5

    /** Returns a copy with every field clamped/validated to the v2.0 schema. */
    fun normalized(): VoidThemeManifest = copy(
        schemaVersion = SCHEMA_VERSION,
        id = sanitizeId(id),
        name = name.trim().take(MAX_NAME_LENGTH).ifBlank { sanitizeId(id) },
        author = author.trim().take(MAX_NAME_LENGTH).ifBlank { "Unknown" },
        surfacePrimaryHex = normalizeHex(surfacePrimaryHex, "#000000"),
        surfaceElevatedHex = normalizeHex(surfaceElevatedHex, "#0D0D0D"),
        textPrimaryHex = normalizeHex(textPrimaryHex, "#FFFFFF"),
        textSecondaryHex = normalizeHex(textSecondaryHex, "#A3A3A3"),
        accentPrimaryHex = normalizeHex(accentPrimaryHex, "#FF1E1E"),
        accentWarnHex = normalizeHex(accentWarnHex, "#FFB020"),
        borderHairlineHex = normalizeHex(borderHairlineHex, "#262626"),
        headerFont = sanitizeFont(headerFont),
        monoFont = sanitizeFont(monoFont),
        cornerRadiusDp = cornerRadiusDp.coerceIn(0, MAX_CORNER_RADIUS_DP),
        borderWidthDp = borderWidthDp.coerceIn(0, MAX_BORDER_WIDTH_DP),
        touchTargetMinDp = touchTargetMinDp.coerceIn(MIN_TOUCH_TARGET_DP, MAX_TOUCH_TARGET_DP),
        customWallpaperPath = customWallpaperPath.trim().take(MAX_PATH_LENGTH)
    )

    /** Serializes to the flat v2.0 JSON object. */
    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_SCHEMA_VERSION, schemaVersion)
        put(KEY_ID, id)
        put(KEY_NAME, name)
        put(KEY_AUTHOR, author)
        put(KEY_IS_BUILT_IN, isBuiltIn)
        put(KEY_SURFACE_PRIMARY, surfacePrimaryHex)
        put(KEY_SURFACE_ELEVATED, surfaceElevatedHex)
        put(KEY_TEXT_PRIMARY, textPrimaryHex)
        put(KEY_TEXT_SECONDARY, textSecondaryHex)
        put(KEY_ACCENT_PRIMARY, accentPrimaryHex)
        put(KEY_ACCENT_WARN, accentWarnHex)
        put(KEY_BORDER_HAIRLINE, borderHairlineHex)
        put(KEY_HEADER_FONT, headerFont)
        put(KEY_MONO_FONT, monoFont)
        put(KEY_CORNER_RADIUS, cornerRadiusDp)
        put(KEY_BORDER_WIDTH, borderWidthDp)
        put(KEY_TOUCH_TARGET, touchTargetMinDp)
        put(KEY_OVERLAY_SHADER, overlayShader.name)
        put(KEY_SHOW_WALLPAPER, showSystemWallpaper)
        put(KEY_SCRIM_LEVEL, scrimLevel.name)
        put(KEY_CUSTOM_WALLPAPER, customWallpaperPath)
        put(KEY_DEFAULT_PERSONA, defaultPersona.label)
        put(KEY_GLYPH_MODE, glyphMode.name)
        put(KEY_DOCK_STYLE, dockStyle.name)
    }

    /** Pretty-printed `*.voidtheme` file body. */
    fun toJsonString(indent: Int = 2): String = toJson().toString(indent)

    /**
     * Encodes as a compact `void://theme/import?payload=<base64url>` URI.
     * The payload is GZIP-compressed JSON, base64url without padding.
     * The local wallpaper path is stripped: it is meaningless on another device.
     */
    fun encodeShareUri(): String {
        val json = copy(customWallpaperPath = "", isBuiltIn = false).toJson().toString()
        return URI_PREFIX + encodePayload(json)
    }

    /** Expands the 7 schema tokens into the launcher's full 15-token [ColorPalette]. */
    fun toColorPalette(): ColorPalette {
        val light = isLightSurface
        return ColorPalette(
            background = surfacePrimaryHex,
            surface = surfaceElevatedHex,
            surfacePressed = blendHex(surfaceElevatedHex, textPrimaryHex, 0.10f),
            border = borderHairlineHex,
            borderActive = blendHex(borderHairlineHex, textPrimaryHex, 0.25f),
            textPrimary = textPrimaryHex,
            textSecondary = textSecondaryHex,
            textMuted = blendHex(textSecondaryHex, surfacePrimaryHex, 0.40f),
            accent = accentPrimaryHex,
            accentSurface = blendHex(surfacePrimaryHex, accentPrimaryHex, 0.18f),
            secondary = textSecondaryHex,
            secondarySurface = blendHex(surfacePrimaryHex, textSecondaryHex, 0.15f),
            safe = if (light) "#2F6B3A" else "#30D158",
            warning = accentWarnHex,
            critical = accentWarnHex
        )
    }

    /** ARGB int for a token without android.graphics.Color (JVM-testable). */
    fun argb(hex: String): Int = parseHexArgb(hex)

    companion object {
        const val SCHEMA_VERSION = "2.0"
        const val FILE_EXTENSION = ".voidtheme"
        const val URI_PREFIX = "void://theme/import?payload="
        const val MIN_TOUCH_TARGET_DP = 48
        const val MAX_TOUCH_TARGET_DP = 96
        const val MAX_CORNER_RADIUS_DP = 28
        const val MAX_BORDER_WIDTH_DP = 4
        const val MAX_PAYLOAD_CHARS = 64 * 1024
        private const val MAX_NAME_LENGTH = 64
        private const val MAX_ID_LENGTH = 48
        private const val MAX_PATH_LENGTH = 1024

        const val KEY_SCHEMA_VERSION = "schema_version"
        const val KEY_ID = "id"
        const val KEY_NAME = "name"
        const val KEY_AUTHOR = "author"
        const val KEY_IS_BUILT_IN = "isBuiltIn"
        const val KEY_SURFACE_PRIMARY = "surfacePrimaryHex"
        const val KEY_SURFACE_ELEVATED = "surfaceElevatedHex"
        const val KEY_TEXT_PRIMARY = "textPrimaryHex"
        const val KEY_TEXT_SECONDARY = "textSecondaryHex"
        const val KEY_ACCENT_PRIMARY = "accentPrimaryHex"
        const val KEY_ACCENT_WARN = "accentWarnHex"
        const val KEY_BORDER_HAIRLINE = "borderHairlineHex"
        const val KEY_HEADER_FONT = "headerFont"
        const val KEY_MONO_FONT = "monoFont"
        const val KEY_CORNER_RADIUS = "cornerRadiusDp"
        const val KEY_BORDER_WIDTH = "borderWidthDp"
        const val KEY_TOUCH_TARGET = "touchTargetMinDp"
        const val KEY_OVERLAY_SHADER = "overlayShader"
        const val KEY_SHOW_WALLPAPER = "showSystemWallpaper"
        const val KEY_SCRIM_LEVEL = "scrimLevel"
        const val KEY_CUSTOM_WALLPAPER = "customWallpaperPath"
        const val KEY_DEFAULT_PERSONA = "defaultPersona"
        const val KEY_GLYPH_MODE = "glyphMode"
        const val KEY_DOCK_STYLE = "dockStyle"

        private val HEX_REGEX = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
        private val ALLOWED_FONTS = setOf(
            "monospace", "sans-serif", "sans-serif-condensed", "sans-serif-medium",
            "sans-serif-light", "serif", "serif-monospace", "casual", "cursive"
        )

        /**
         * Parses a v2.0 manifest. Throws [IllegalArgumentException] for wrong
         * schema major versions or missing identity.
         *
         * @param trusted false for imports from outside the app: the theme is
         * forced to non-built-in and the local wallpaper path is dropped.
         */
        fun fromJson(json: JSONObject, trusted: Boolean = false): VoidThemeManifest {
            val version = json.optString(KEY_SCHEMA_VERSION, "")
            require(version.substringBefore('.') == SCHEMA_VERSION.substringBefore('.')) {
                "Unsupported theme schema_version '$version' (expected $SCHEMA_VERSION)"
            }
            val rawId = json.optString(KEY_ID, "").trim()
            val rawName = json.optString(KEY_NAME, "").trim()
            require(rawId.isNotEmpty() || rawName.isNotEmpty()) { "Theme has neither id nor name" }

            return VoidThemeManifest(
                schemaVersion = SCHEMA_VERSION,
                id = rawId.ifEmpty { rawName },
                name = rawName.ifEmpty { rawId },
                author = json.optString(KEY_AUTHOR, "Unknown"),
                isBuiltIn = trusted && json.optBoolean(KEY_IS_BUILT_IN, false),
                surfacePrimaryHex = json.optString(KEY_SURFACE_PRIMARY, ""),
                surfaceElevatedHex = json.optString(KEY_SURFACE_ELEVATED, ""),
                textPrimaryHex = json.optString(KEY_TEXT_PRIMARY, ""),
                textSecondaryHex = json.optString(KEY_TEXT_SECONDARY, ""),
                accentPrimaryHex = json.optString(KEY_ACCENT_PRIMARY, ""),
                accentWarnHex = json.optString(KEY_ACCENT_WARN, ""),
                borderHairlineHex = json.optString(KEY_BORDER_HAIRLINE, ""),
                headerFont = json.optString(KEY_HEADER_FONT, "monospace"),
                monoFont = json.optString(KEY_MONO_FONT, "monospace"),
                cornerRadiusDp = json.optInt(KEY_CORNER_RADIUS, 8),
                borderWidthDp = json.optInt(KEY_BORDER_WIDTH, 1),
                touchTargetMinDp = json.optInt(KEY_TOUCH_TARGET, MIN_TOUCH_TARGET_DP),
                overlayShader = enumOr(json.optString(KEY_OVERLAY_SHADER, ""), OverlayShaderType.NONE),
                showSystemWallpaper = json.optBoolean(KEY_SHOW_WALLPAPER, false),
                scrimLevel = enumOr(json.optString(KEY_SCRIM_LEVEL, ""), ScrimLevel.SCRIM_45),
                customWallpaperPath = if (trusted) json.optString(KEY_CUSTOM_WALLPAPER, "") else "",
                defaultPersona = ThemePersona.parse(json.optString(KEY_DEFAULT_PERSONA, "")),
                glyphMode = enumOr(json.optString(KEY_GLYPH_MODE, ""), GlyphMode.PULSE_SYNC),
                dockStyle = enumOr(json.optString(KEY_DOCK_STYLE, ""), DockStyle.NUMERIC_BRACKET)
            ).normalized()
        }

        fun fromJsonString(text: String, trusted: Boolean = false): VoidThemeManifest {
            require(text.length <= MAX_PAYLOAD_CHARS) { "Theme payload too large (${text.length} chars)" }
            return fromJson(JSONObject(text.trim()), trusted)
        }

        /** True for `void://theme/import?...` URIs. */
        fun isShareUri(text: String): Boolean =
            text.trim().startsWith("void://theme/import", ignoreCase = true)

        /** Decodes a `void://theme/import?payload=<base64url>` URI. */
        fun decodeShareUri(uri: String): VoidThemeManifest {
            val trimmed = uri.trim()
            require(isShareUri(trimmed)) { "Not a void://theme/import URI" }
            val query = trimmed.substringAfter('?', "")
            val payload = query.split('&')
                .map { it.split('=', limit = 2) }
                .firstOrNull { it.size == 2 && it[0] == "payload" }
                ?.get(1)
                ?.let { URLDecoder.decode(it, "UTF-8") }
            require(!payload.isNullOrBlank()) { "Theme URI has no payload" }
            return fromJsonString(decodePayload(payload), trusted = false)
        }

        /** GZIP + base64url (no padding). */
        fun encodePayload(json: String): String {
            val bytes = ByteArrayOutputStream().use { bos ->
                GZIPOutputStream(bos).use { it.write(json.toByteArray(Charsets.UTF_8)) }
                bos.toByteArray()
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        /** Inverse of [encodePayload]; also accepts uncompressed and standard-alphabet base64. */
        fun decodePayload(payload: String): String {
            require(payload.length <= MAX_PAYLOAD_CHARS) { "Theme payload too large" }
            val cleaned = payload.trim().replace('+', '-').replace('/', '_').trimEnd('=')
            val raw = Base64.getUrlDecoder().decode(cleaned)
            val isGzip = raw.size >= 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()
            val bytes = if (isGzip) {
                GZIPInputStream(ByteArrayInputStream(raw)).use { gz ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(4096)
                    var total = 0
                    while (true) {
                        val n = gz.read(buf)
                        if (n < 0) break
                        total += n
                        require(total <= MAX_PAYLOAD_CHARS) { "Decompressed theme payload too large" }
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
            } else raw
            return String(bytes, Charsets.UTF_8)
        }

        /** Lower-case slug `[a-z0-9_-]`, max 48 chars, never empty. */
        fun sanitizeId(raw: String): String {
            val slug = raw.trim().lowercase(Locale.US)
                .replace(Regex("[^a-z0-9_-]+"), "_")
                .trim('_', '-')
                .take(MAX_ID_LENGTH)
            return slug.ifEmpty { "theme_${System.currentTimeMillis()}" }
        }

        fun isValidHex(hex: String): Boolean = HEX_REGEX.matches(hex.trim())

        /** Upper-cases valid hex (adding '#' if missing) or returns [fallback]. */
        fun normalizeHex(hex: String, fallback: String): String {
            val t = hex.trim().let { if (it.startsWith("#")) it else "#$it" }
            return if (isValidHex(t)) t.uppercase(Locale.US) else fallback
        }

        private fun sanitizeFont(font: String): String {
            val f = font.trim().lowercase(Locale.US)
            return if (f in ALLOWED_FONTS) f else "monospace"
        }

        private inline fun <reified T : Enum<T>> enumOr(raw: String, fallback: T): T =
            enumValues<T>().firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: fallback

        /** Parses `#RRGGBB` / `#AARRGGBB` to an ARGB int; opaque black on failure. */
        fun parseHexArgb(hex: String): Int {
            val t = hex.trim().removePrefix("#")
            return try {
                when (t.length) {
                    6 -> (0xFF000000L or t.toLong(16)).toInt()
                    8 -> t.toLong(16).toInt()
                    else -> 0xFF000000.toInt()
                }
            } catch (_: NumberFormatException) {
                0xFF000000.toInt()
            }
        }

        /** Linear RGB blend of two hex colors (alpha dropped); t=0 → a, t=1 → b. */
        fun blendHex(a: String, b: String, t: Float): String {
            val ca = parseHexArgb(a)
            val cb = parseHexArgb(b)
            val f = t.coerceIn(0f, 1f)
            fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
            fun mix(shift: Int) = (ch(ca, shift) + (ch(cb, shift) - ch(ca, shift)) * f).roundToInt().coerceIn(0, 255)
            return String.format(Locale.US, "#%02X%02X%02X", mix(16), mix(8), mix(0))
        }

        /** Relative luminance 0..1 (sRGB approximation). */
        fun luminance(hex: String): Double {
            val c = parseHexArgb(hex)
            val r = ((c shr 16) and 0xFF) / 255.0
            val g = ((c shr 8) and 0xFF) / 255.0
            val b = (c and 0xFF) / 255.0
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }

        /**
         * Builds a manifest from a legacy (pre-v2) [ColorPalette] so existing user
         * colors survive the migration; non-color hooks come from [template].
         */
        fun fromLegacyPalette(
            id: String,
            name: String,
            palette: ColorPalette,
            template: VoidThemeManifest
        ): VoidThemeManifest = template.copy(
            id = id,
            name = name,
            author = "Local",
            isBuiltIn = false,
            surfacePrimaryHex = palette.background,
            surfaceElevatedHex = palette.surface,
            textPrimaryHex = palette.textPrimary,
            textSecondaryHex = palette.textSecondary,
            accentPrimaryHex = palette.accent,
            accentWarnHex = palette.critical,
            borderHairlineHex = palette.border
        ).normalized()
    }
}
