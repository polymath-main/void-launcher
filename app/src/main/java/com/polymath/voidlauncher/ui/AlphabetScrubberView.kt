package com.polymath.voidlauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

class AlphabetScrubberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val letters = listOf(
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '#'
    )

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF71717A.toInt() // text_secondary
        textSize = 22f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE11D48.toInt() // accent
        textSize = 26f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private var selectedIndex = -1
    private var onLetterSelectedListener: ((Char) -> Unit)? = null

    // impl-02 step 5: letters with no installed app section, as reported
    // by AppIndexer.getSectionPositions(). Null = unknown (before the
    // first index load): draw everything normally, never dim on a guess.
    private var availableLetters: Set<Char>? = null

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF71717A.toInt()
        alpha = 77
        textSize = 22f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }

    fun setAvailableLetters(letters: Set<Char>) {
        availableLetters = letters
        invalidate()
    }

    fun setOnLetterSelectedListener(listener: (Char) -> Unit) {
        onLetterSelectedListener = listener
    }

    fun setAccentColor(colorInt: Int) {
        activePaint.color = colorInt
        invalidate()
    }

    fun setTextColor(colorInt: Int) {
        textPaint.color = colorInt
        dimPaint.color = colorInt
        dimPaint.alpha = 77
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (letters.isEmpty()) return

        val totalHeight = height.toFloat()
        val letterHeight = totalHeight / letters.size
        val centerX = width / 2f

        for (i in letters.indices) {
            val y = i * letterHeight + letterHeight * 0.75f
            val available = availableLetters
            val paint = when {
                i == selectedIndex -> activePaint
                available != null && letters[i] !in available -> dimPaint
                else -> textPaint
            }
            canvas.drawText(letters[i].toString(), centerX, y, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val totalHeight = height.toFloat()
                val letterHeight = totalHeight / letters.size
                val index = (event.y / letterHeight).toInt().coerceIn(0, letters.size - 1)

                if (index != selectedIndex) {
                    selectedIndex = index
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onLetterSelectedListener?.invoke(letters[index])
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                selectedIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
