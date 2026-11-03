package com.polymath.voidlauncher.core


/**
 * LiveScript Engine (F-01).
 *
 * Lightweight, zero-dependency mathematical and expression evaluator
 * executing directly from the omni-bar (>_ :eval <expression>).
 */
class LiveScriptEngine() {

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
     * Step 5 switches it to strict: unknown characters are errors
     * (no more silent letter-dropping, so `2+abc` no longer
     * evaluates as 2), unary minus works, `^` is right-associative,
     * and unbalanced parentheses are errors.
     */
    private fun evaluateMath(expr: String): Double =
        EvalMath.evaluateMath(expr, strict = true)
}
