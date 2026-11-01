package com.polymath.voidlauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * LiquidTouchSurfaceView
 *
 * Implements fluid liquid wave and organic ripple touch dynamics
 * for OLED black surfaces and wallpaper underlays.
 */
class LiquidTouchSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val interpolator = DecelerateInterpolator(1.6f)
    private val ripples = mutableListOf<LiquidRipple>()

    var rippleColor: Int = Color.parseColor("#007AFF")
        set(value) {
            field = value
            paintStroke.color = value
            paintFill.color = value
        }

    var isLiquidEffectEnabled: Boolean = true

    private val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = rippleColor
    }

    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = rippleColor
    }

    data class LiquidRipple(
        val x: Float,
        val y: Float,
        val startTime: Long,
        val durationMs: Long = 450L,
        val maxRadius: Float = 220f
    )

    fun spawnLiquidWave(x: Float, y: Float) {
        if (!isLiquidEffectEnabled) return
        ripples.add(LiquidRipple(x, y, System.currentTimeMillis()))
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (ripples.isEmpty()) return

        val now = System.currentTimeMillis()
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
            val currentAlpha = ((1f - progress) * 90).toInt().coerceIn(0, 255)
            val fillAlpha = ((1f - progress) * 25).toInt().coerceIn(0, 255)

            // Inner translucent wave glow
            paintFill.alpha = fillAlpha
            canvas.drawCircle(r.x, r.y, currentRadius, paintFill)

            // Outer crisp boundary wave
            paintStroke.alpha = currentAlpha
            canvas.drawCircle(r.x, r.y, currentRadius, paintStroke)

            // Secondary concentric micro-wave
            if (currentRadius > 30f) {
                paintStroke.alpha = (currentAlpha * 0.45f).toInt()
                canvas.drawCircle(r.x, r.y, currentRadius * 0.65f, paintStroke)
            }
        }

        if (ripples.isNotEmpty()) {
            postInvalidateOnAnimation()
        }
    }
}
