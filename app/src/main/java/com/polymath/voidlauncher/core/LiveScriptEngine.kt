package com.polymath.voidlauncher.core

import android.content.Context
import java.util.Stack
import kotlin.math.pow

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
     * Standard Dijkstra Shunting-yard + RPN evaluator for arithmetic expressions.
     */
    private fun evaluateMath(expr: String): Double {
        val sanitized = expr.replace(" ", "")
        val tokens = tokenize(sanitized)
        val rpn = toRpn(tokens)
        return evalRpn(rpn)
    }

    private fun tokenize(s: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isDigit() || c == '.' -> {
                    val sb = java.lang.StringBuilder()
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) {
                        sb.append(s[i])
                        i++
                    }
                    tokens.add(sb.toString())
                    continue
                }
                c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == '^' || c == '(' || c == ')' -> {
                    tokens.add(c.toString())
                    i++
                }
                else -> {
                    i++
                }
            }
        }
        return tokens
    }

    private fun precedence(op: String): Int = when (op) {
        "+", "-" -> 1
        "*", "/", "%" -> 2
        "^" -> 3
        else -> 0
    }

    private fun toRpn(tokens: List<String>): List<String> {
        val output = mutableListOf<String>()
        val ops = Stack<String>()

        for (token in tokens) {
            when {
                token.toDoubleOrNull() != null -> output.add(token)
                token == "(" -> ops.push(token)
                token == ")" -> {
                    while (ops.isNotEmpty() && ops.peek() != "(") {
                        output.add(ops.pop())
                    }
                    if (ops.isNotEmpty()) ops.pop()
                }
                else -> {
                    while (ops.isNotEmpty() && precedence(ops.peek()) >= precedence(token)) {
                        output.add(ops.pop())
                    }
                    ops.push(token)
                }
            }
        }
        while (ops.isNotEmpty()) {
            output.add(ops.pop())
        }
        return output
    }

    private fun evalRpn(rpn: List<String>): Double {
        val stack = Stack<Double>()
        for (token in rpn) {
            val num = token.toDoubleOrNull()
            if (num != null) {
                stack.push(num)
            } else {
                if (stack.size < 2) throw IllegalArgumentException("Malformed expression")
                val b = stack.pop()
                val a = stack.pop()
                val res = when (token) {
                    "+" -> a + b
                    "-" -> a - b
                    "*" -> a * b
                    "/" -> if (b != 0.0) a / b else throw ArithmeticException("Division by zero")
                    "%" -> a % b
                    "^" -> a.pow(b)
                    else -> throw UnsupportedOperationException("Unknown operator: $token")
                }
                stack.push(res)
            }
        }
        if (stack.size != 1) throw IllegalArgumentException("Malformed expression")
        return stack.pop()
    }
}
