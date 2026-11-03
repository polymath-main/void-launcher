package com.polymath.voidlauncher.core

import android.content.Context

/**
 * LiveScript Engine (F-01).
 *
 * Lightweight, zero-dependency mathematical and expression evaluator
 * executing directly from the omni-bar (>_ :eval <expression>).
 */
class LiveScriptEngine(private val context: Context) {

    data class EvalResult(
        val success: Boolean,
        val output: String,
        val executionTimeMs: Long
    )

    fun evaluate(expression: String): EvalResult {
        val startTime = System.currentTimeMillis()
        val expr = expression.trim()

        if (expr.isEmpty()) {
            return EvalResult(false, "Empty expression", 0)
        }

        return try {
            val result = when {
                expr.equals("time", ignoreCase = true) -> {
                    System.currentTimeMillis().toString()
                }
                expr.equals("nanotime", ignoreCase = true) -> {
                    System.nanoTime().toString()
                }
                expr.startsWith("0x", ignoreCase = true) -> {
                    java.lang.Long.decode(expr).toString()
                }
                expr.startsWith("0b", ignoreCase = true) -> {
                    java.lang.Long.parseLong(expr.substring(2), 2).toString()
                }
                else -> {
                    val evaluated = evaluateMath(expr)
                    if (evaluated == evaluated.toLong().toDouble()) {
                        evaluated.toLong().toString()
                    } else {
                        String.format(java.util.Locale.US, "%.6f", evaluated).trimEnd('0').trimEnd('.')
                    }
                }
            }
            val elapsed = System.currentTimeMillis() - startTime
            EvalResult(true, result, elapsed)
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            EvalResult(false, "Eval error: ${e.message}", elapsed)
        }
    }

    /**
     * Delegates to the pure [EvalMath] evaluator (impl-04 step 0).
     * The evaluator used to live here and the old test copied it, so a
     * production regression was invisible; the test now drives this
     * class (and EvalMath) directly. `strict` is off here in step 0
     * and is switched on in step 5, in this one place.
     */
    private fun evaluateMath(expr: String): Double =
        EvalMath.evaluateMath(expr, strict = false)
}
