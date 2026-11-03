package com.polymath.voidlauncher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.polymath.voidlauncher.core.RippleCoalescer
import com.polymath.voidlauncher.model.OverlayShaderType
import kotlin.math.cos
import kotlin.math.sin

/**
 * LiquidTouchSurfaceView
 *
 * Implements hardware-accelerated Canvas shader overlays across the 5 theme modes:
 * - [OverlayShaderType.DOT_MATRIX]: 16dp dot grid with 96dp touch ripple
 * - [OverlayShaderType.CRT_SCANLINE]: 1px scanline every 3px + CRT vignette
 * - [OverlayShaderType.PAPER_GRAIN]: Procedural cached paper texture, 0% idle GPU draw
 * - [OverlayShaderType.NEON_HUD]: Hexagonal cyber mesh with neon bloom ripples
 * - [OverlayShaderType.CAD_BLUEPRINT_GRID]: 8dp minor / 64dp major grid with orange crosshairs
 *
 * Features automatic frame-rate throttling when [isThermalThrottled] is active.
 */
class LiquidTouchSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val interpolator = DecelerateInterpolator(1.6f)
    private val ripples = mutableListOf<ShaderRipple>()

    var shaderType: OverlayShaderType = OverlayShaderType.NONE
        set(value) {
            if (field != value) {
                field = value
                cachedOverlayBitmap?.recycle()
                cachedOverlayBitmap = null
                invalidate()
            }
        }

    var rippleColor: Int = Color.parseColor("#007AFF")
        set(value) {
            field = value
            paintStroke.color = value
            paintFill.color = value
        }

    var accentColor: Int = Color.parseColor("#FF1E1E")
    var secondaryColor: Int = Color.parseColor("#00F0FF")
    var isLightSurface: Boolean = false
    var isLiquidEffectEnabled: Boolean = true

    /** When true (ThermalGuardian >= 39.0°C), throttles animation frame cadence to ~20 FPS. */
    var isThermalThrottled: Boolean = false

    private var lastFrameTimeMs: Long = 0L
    private val throttledFrameDelayMs: Long = 50L // ~20 FPS cap under thermal saturation

    // Paint primitives (hardware-accelerated)
    private val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = rippleColor
    }

    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = rippleColor
    }

    private val paintGridMinor = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = false // Crisp 1px pixel-aligned grid lines
    }

    private val paintGridMajor = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = false
    }

    private val paintDots = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val paintCrosshair = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val paintVignette = Paint(Paint.ANTI_ALIAS_FLAG)

    // Cached bitmap for static overlays (e.g. Paper Grain & CRT Scanline) to guarantee 0% idle GPU
    private var cachedOverlayBitmap: Bitmap? = null
    private var cachedWidth: Int = 0
    private var cachedHeight: Int = 0

    data class ShaderRipple(
        val x: Float,
        val y: Float,
        val startTime: Long,
        val durationMs: Long,
        val maxRadius: Float,
        val type: OverlayShaderType
    )

    private var lastRippleTimeMs: Long? = null
    private var lastRippleX: Float? = null
    private var lastRippleY: Float? = null

    fun spawnLiquidWave(x: Float, y: Float) {
        if (!isLiquidEffectEnabled) return

        // impl-03: coalesce rapid near-identical ACTION_DOWN events so
        // fast tapping cannot queue unbounded ripples (80 ms / 24 px).
        if (!RippleCoalescer.shouldSpawn(System.currentTimeMillis(), x, y, lastRippleTimeMs, lastRippleX, lastRippleY)) return
        lastRippleTimeMs = System.currentTimeMillis()
        lastRippleX = x
        lastRippleY = y

        // Under thermal saturation, limit ripple stack depth
        if (isThermalThrottled && ripples.size >= 2) {
            ripples.removeAt(0)
        }

        val duration = when (shaderType) {
            OverlayShaderType.PAPER_GRAIN -> 180L // Crisp instant ink feedback
            OverlayShaderType.CRT_SCANLINE -> 320L
            OverlayShaderType.CAD_BLUEPRINT_GRID -> 360L
            OverlayShaderType.DOT_MATRIX -> 420L
            else -> 450L
        }

        val radius = when (shaderType) {
            OverlayShaderType.DOT_MATRIX -> dpToPx(96f)
            OverlayShaderType.CAD_BLUEPRINT_GRID -> dpToPx(140f)
            OverlayShaderType.NEON_HUD -> dpToPx(240f)
            else -> dpToPx(180f)
        }

        ripples.add(ShaderRipple(x, y, System.currentTimeMillis(), duration, radius, shaderType))
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && (w != cachedWidth || h != cachedHeight)) {
            cachedWidth = w
            cachedHeight = h
            cachedOverlayBitmap?.recycle()
            cachedOverlayBitmap = null
            generateStaticOverlayCache(w, h)
        }
    }

    private fun generateStaticOverlayCache(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        when (shaderType) {
            OverlayShaderType.PAPER_GRAIN -> {
                // Procedural micro-grain texture rendered once into a hardware bitmap
                try {
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val cv = Canvas(bmp)
                    val rnd = java.util.Random(42L)
                    val dotPaint = Paint().apply {
                        color = if (isLightSurface) Color.argb(12, 40, 30, 20) else Color.argb(18, 255, 255, 255)
                    }
                    val step = dpToPx(4f).toInt().coerceAtLeast(3)
                    for (x in 0 until w step step) {
                        for (y in 0 until h step step) {
                            if (rnd.nextFloat() > 0.45f) {
                                val jitterX = x + rnd.nextInt(step)
                                val jitterY = y + rnd.nextInt(step)
                                cv.drawPoint(jitterX.toFloat(), jitterY.toFloat(), dotPaint)
                            }
                        }
                    }
                    cachedOverlayBitmap = bmp
                } catch (_: OutOfMemoryError) {}
            }
            OverlayShaderType.CRT_SCANLINE -> {
                try {
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val cv = Canvas(bmp)
                    val scanPaint = Paint().apply {
                        color = Color.argb(35, 0, 0, 0)
                        strokeWidth = 1f
                    }
                    // 1px scanline every 3px
                    var y = 0f
                    while (y < h) {
                        cv.drawLine(0f, y, w.toFloat(), y, scanPaint)
                        y += 3f
                    }
                    // Edge vignette
                    val vignette = RadialGradient(
                        w / 2f, h / 2f,
                        kotlin.math.max(w, h) * 0.75f,
                        intArrayOf(Color.TRANSPARENT, Color.argb(140, 0, 0, 0)),
                        floatArrayOf(0.55f, 1.0f),
                        Shader.TileMode.CLAMP
                    )
                    paintVignette.shader = vignette
                    cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paintVignette)
                    cachedOverlayBitmap = bmp
                } catch (_: OutOfMemoryError) {}
            }
            else -> {
                cachedOverlayBitmap = null
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw Static / Base Overlay Shader
        drawOverlayShader(canvas)

        // 2. Draw Interactive Touch Waves / Ripples
        drawRipples(canvas)
    }

    private fun drawOverlayShader(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        when (shaderType) {
            OverlayShaderType.NONE -> { /* Clean surface */ }

            OverlayShaderType.DOT_MATRIX -> {
                // 16dp dot grid
                val spacing = dpToPx(16f)
                val dotRadius = dpToPx(1.2f)
                paintDots.color = if (isLightSurface) Color.argb(28, 0, 0, 0) else Color.argb(45, 255, 255, 255)
                var x = spacing / 2
                while (x < w) {
                    var y = spacing / 2
                    while (y < h) {
                        canvas.drawCircle(x, y, dotRadius, paintDots)
                        y += spacing
                    }
                    x += spacing
                }
            }

            OverlayShaderType.CRT_SCANLINE -> {
                if (cachedOverlayBitmap != null && !cachedOverlayBitmap!!.isRecycled) {
                    canvas.drawBitmap(cachedOverlayBitmap!!, 0f, 0f, null)
                } else {
                    generateStaticOverlayCache(width, height)
                    cachedOverlayBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
                }
            }

            OverlayShaderType.PAPER_GRAIN -> {
                if (cachedOverlayBitmap != null && !cachedOverlayBitmap!!.isRecycled) {
                    canvas.drawBitmap(cachedOverlayBitmap!!, 0f, 0f, null)
                } else {
                    generateStaticOverlayCache(width, height)
                    cachedOverlayBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
                }
            }

            OverlayShaderType.NEON_HUD -> {
                // Subtle hexagonal cyber grid mesh
                val hexSize = dpToPx(42f)
                paintGridMinor.color = Color.argb(18, 0, 240, 255)
                val hexPath = Path()
                val dy = hexSize * 1.5f
                val dx = (hexSize * kotlin.math.sqrt(3.0)).toFloat()
                var row = 0
                var y = 0f
                while (y < h + hexSize) {
                    val xOffset = if (row % 2 == 1) dx / 2f else 0f
                    var x = -hexSize + xOffset
                    while (x < w + hexSize) {
                        buildHexagon(hexPath, x, y, hexSize * 0.5f)
                        canvas.drawPath(hexPath, paintGridMinor)
                        x += dx
                    }
                    y += dy
                    row++
                }
            }

            OverlayShaderType.CAD_BLUEPRINT_GRID -> {
                // 8dp minor / 64dp major grid with bezel tick marks
                val minor = dpToPx(8f)
                val major = dpToPx(64f)
                paintGridMinor.color = Color.argb(16, 255, 255, 255)
                paintGridMajor.color = Color.argb(45, 30, 144, 255)

                var x = 0f
                while (x <= w) {
                    val isMajor = (x % major) < 1f || ((x + 1f) % major) < 2f
                    canvas.drawLine(x, 0f, x, h, if (isMajor) paintGridMajor else paintGridMinor)
                    x += minor
                }
                var y = 0f
                while (y <= h) {
                    val isMajor = (y % major) < 1f || ((y + 1f) % major) < 2f
                    canvas.drawLine(0f, y, w, y, if (isMajor) paintGridMajor else paintGridMinor)
                    y += minor
                }
            }
        }
    }

    private fun drawRipples(canvas: Canvas) {
        if (ripples.isEmpty()) return

        val now = System.currentTimeMillis()

        // Frame rate throttling for Thermal Guardian envelope
        if (isThermalThrottled && (now - lastFrameTimeMs) < throttledFrameDelayMs) {
            // Re-schedule next frame tick without processing duplicate canvas draw
            postInvalidateOnAnimation()
            return
        }
        lastFrameTimeMs = now

        val iterator = ripples.iterator()

        while (iterator.hasNext()) {
            val r = iterator.next()
            val elapsed = now - r.startTime
            if (elapsed >= r.durationMs) {
                iterator.remove()
                continue
            }

            val progress = elapsed.toFloat() / r.durationMs
            val ease = interpolator.getInterpolation(progress)
            val currentRadius = r.maxRadius * ease
            val currentAlpha = ((1f - progress) * 120).toInt().coerceIn(0, 255)
            val fillAlpha = ((1f - progress) * 35).toInt().coerceIn(0, 255)

            when (r.type) {
                OverlayShaderType.CAD_BLUEPRINT_GRID -> {
                    // Precision orange touch crosshairs
                    paintCrosshair.color = accentColor
                    paintCrosshair.alpha = currentAlpha
                    val crosshairSpan = currentRadius * 1.2f
                    canvas.drawLine(r.x - crosshairSpan, r.y, r.x + crosshairSpan, r.y, paintCrosshair)
                    canvas.drawLine(r.x, r.y - crosshairSpan, r.x, r.y + crosshairSpan, paintCrosshair)
                    // Box bounds
                    paintCrosshair.alpha = (currentAlpha * 0.7f).toInt()
                    val boxHalf = currentRadius * 0.4f
                    canvas.drawRect(r.x - boxHalf, r.y - boxHalf, r.x + boxHalf, r.y + boxHalf, paintCrosshair)
                }

                OverlayShaderType.PAPER_GRAIN -> {
                    // Instant crisp color-invert pulse
                    paintFill.color = if (isLightSurface) Color.BLACK else Color.WHITE
                    paintFill.alpha = ((1f - progress) * 50).toInt()
                    canvas.drawCircle(r.x, r.y, currentRadius, paintFill)
                }

                OverlayShaderType.NEON_HUD -> {
                    // Hexagonal neon wave
                    paintStroke.color = secondaryColor
                    paintStroke.alpha = currentAlpha
                    val hexPath = Path()
                    buildHexagon(hexPath, r.x, r.y, currentRadius)
                    canvas.drawPath(hexPath, paintStroke)

                    // Secondary inner bloom
                    paintFill.color = accentColor
                    paintFill.alpha = fillAlpha
                    buildHexagon(hexPath, r.x, r.y, currentRadius * 0.6f)
                    canvas.drawPath(hexPath, paintFill)
                }

                else -> {
                    // Concentric radial ripple (Obsidian Dot-Matrix & CRT)
                    paintFill.color = rippleColor
                    paintFill.alpha = fillAlpha
                    canvas.drawCircle(r.x, r.y, currentRadius, paintFill)

                    paintStroke.color = rippleColor
                    paintStroke.alpha = currentAlpha
                    canvas.drawCircle(r.x, r.y, currentRadius, paintStroke)

                    if (currentRadius > 30f) {
                        paintStroke.alpha = (currentAlpha * 0.45f).toInt()
                        canvas.drawCircle(r.x, r.y, currentRadius * 0.65f, paintStroke)
                    }
                }
            }
        }

        if (ripples.isNotEmpty()) {
            postInvalidateOnAnimation()
        }
    }

    private fun buildHexagon(path: Path, cx: Float, cy: Float, radius: Float) {
        path.reset()
        for (i in 0 until 6) {
            val angle = Math.toRadians((60 * i + 30).toDouble())
            val px = cx + (radius * cos(angle)).toFloat()
            val py = cy + (radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }
}
