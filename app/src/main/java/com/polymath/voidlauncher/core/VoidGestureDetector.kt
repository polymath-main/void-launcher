package com.polymath.voidlauncher.core

import android.content.Context
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs

class VoidGestureDetector(
    private val context: Context,
    private val callbacks: GestureCallbacks
) {

    interface GestureCallbacks {
        fun onSwipeUp()
        fun onSwipeDown()
        fun onSwipeLeft()
        fun onSwipeRight()
        fun onDoubleTap()
        fun onLongPress()
    }

    private val minDistancePx: Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        45f,
        context.resources.displayMetrics
    )

    private val minVelocityPx: Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        120f,
        context.resources.displayMetrics
    )

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            callbacks.onDoubleTap()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            callbacks.onLongPress()
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false

            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            if (abs(diffX) > abs(diffY)) {
                // Horizontal fling
                if (abs(diffX) > minDistancePx && abs(velocityX) > minVelocityPx) {
                    if (diffX > 0) {
                        callbacks.onSwipeRight()
                    } else {
                        callbacks.onSwipeLeft()
                    }
                    return true
                }
            } else {
                // Vertical fling
                if (abs(diffY) > minDistancePx && abs(velocityY) > minVelocityPx) {
                    if (diffY > 0) {
                        callbacks.onSwipeDown()
                    } else {
                        callbacks.onSwipeUp()
                    }
                    return true
                }
            }
            return false
        }
    })

    fun onTouchEvent(event: MotionEvent): Boolean {
        return detector.onTouchEvent(event)
    }
}
