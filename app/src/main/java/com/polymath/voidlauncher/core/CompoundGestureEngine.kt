package com.polymath.voidlauncher.core

import android.content.Context
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.abs

/**
 * Compound Gesture Engine (F-07).
 *
 * Combines single-touch directional flings, screen-edge swipe zones,
 * two-finger swipes, and scale pinch-in / pinch-out gestures.
 */
class CompoundGestureEngine(
    private val context: Context,
    private val callbacks: CompoundGestureCallbacks
) {

    interface CompoundGestureCallbacks {
        fun onSwipeUp()
        fun onSwipeDown()
        fun onSwipeLeft()
        fun onSwipeRight()
        fun onDoubleTap()
        fun onLongPress()
        fun onPinchIn()
        fun onPinchOut()
        fun onTwoFingerSwipeUp()
        fun onLeftEdgeSwipe()
        fun onRightEdgeSwipe()
        fun onBottomEdgeSwipe()
    }

    private val minDistancePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 45f, context.resources.displayMetrics)
    private val minVelocityPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 120f, context.resources.displayMetrics)
    private val edgeZonePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, context.resources.displayMetrics)

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var screenWidthPx = context.resources.displayMetrics.widthPixels.toFloat()
    private var screenHeightPx = context.resources.displayMetrics.heightPixels.toFloat()

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            val factor = detector.scaleFactor
            if (factor < 0.85f) {
                callbacks.onPinchIn()
            } else if (factor > 1.15f) {
                callbacks.onPinchOut()
            }
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            initialTouchX = e.x
            initialTouchY = e.y
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

            // Multi-touch 2-finger swipe check
            if (e2.pointerCount >= 2) {
                if (velocityY < -minVelocityPx) {
                    callbacks.onTwoFingerSwipeUp()
                    return true
                }
            }

            // Edge zone checks
            if (e1.x <= edgeZonePx && (e2.x - e1.x) > minDistancePx) {
                callbacks.onLeftEdgeSwipe()
                return true
            }
            if (e1.x >= (screenWidthPx - edgeZonePx) && (e1.x - e2.x) > minDistancePx) {
                callbacks.onRightEdgeSwipe()
                return true
            }
            if (e1.y >= (screenHeightPx - edgeZonePx * 2) && (e1.y - e2.y) > minDistancePx) {
                callbacks.onBottomEdgeSwipe()
                return true
            }

            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            if (abs(diffX) > abs(diffY)) {
                if (abs(diffX) > minDistancePx && abs(velocityX) > minVelocityPx) {
                    if (diffX > 0) callbacks.onSwipeRight() else callbacks.onSwipeLeft()
                    return true
                }
            } else {
                if (abs(diffY) > minDistancePx && abs(velocityY) > minVelocityPx) {
                    if (diffY > 0) callbacks.onSwipeDown() else callbacks.onSwipeUp()
                    return true
                }
            }
            return false
        }
    })

    fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        return gestureDetector.onTouchEvent(event)
    }
}
