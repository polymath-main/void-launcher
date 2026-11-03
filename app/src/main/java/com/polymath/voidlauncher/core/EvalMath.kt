package com.polymath.voidlauncher.core

import java.util.Stack
import kotlin.math.pow

/**
 * Pure, Context-free arithmetic evaluator behind `:eval`.
 *
 * Extracted from LiveScriptEngine in impl-04 step 0 so the evaluator can
 * be tested directly: the old LiveScriptEngineTest tested a copy of this
 * code pasted into the test class, so a regression in the production
 * file was invisible. LiveScriptEngine now delegates here.
 *
 * Step 0 semantics are the historical ones (unknown characters dropped,
 * `^` left-associative, unbalanced parentheses tolerated); step 5
 * tightens them (strict characters, unary minus, right-assoc `^`,
 * unbalanced-paren errors) in this same file.
 */
object EvalMath {

    /**
     * [strict] = false is the step-0 (historical) behaviour; true is the
     * step-5 behaviour (strict characters, unary minus, right-assoc `^`,
     * unbalanced-paren errors). Passed as a parameter, never global
     * state, so tests cannot leak a mode into production dispatch.
     */
    fun evaluateMath(expr: String, strict: Boolean = false): Double {
        val sanitized = expr.replace(" ", "")
        val tokens = tokenize(sanitized, strict)
        val rpn = toRpn(tokens, strict)
        return evalRpn(rpn)
    }

    private fun tokenize(s: String, strict: Boolean): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        var previous: String? = null // last emitted token, for unary minus
        while (i < s.length) {
            val c = s[i]
            when {
                c.isDigit() || c == '.' -> {
                    val sb = StringBuilder()
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) {
                        sb.append(s[i])
                        i++
                    }
                    tokens.add(sb.toString())
                    previous = tokens.last()
                    continue
                }
                c == '-' && strict &&
                    (previous == null || previous == "(" ||
                        previous in setOf("+", "-", "*", "/", "%", "^")) -> {
                    // Unary minus: fold into the following number, or
                    // rewrite "-(...)" as "0 - (...)".
                    if (i + 1 < s.length && (s[i + 1].isDigit() || s[i + 1] == '.')) {
                        val sb = StringBuilder("-")
                        i++
                        while (i < s.length && (s[i].isDigit() || s[i] == '.')) {
                            sb.append(s[i])
                            i++
                        }
                        tokens.add(sb.toString())
                        previous = tokens.last()
                        continue
                    }
                    tokens.add("0")
                    tokens.add("-")
                    previous = "-"
                    i++
                }
                c == '+' || c == '-' || c == '*' || c == '/' || c == '%' || c == '^' ||
                    c == '(' || c == ')' -> {
                    tokens.add(c.toString())
                    previous = c.toString()
                    i++
                }
                else -> {
                    if (strict) {
                        throw IllegalArgumentException("unsupported character '$c'")
                    }
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

    private fun isRightAssociative(op: String, strict: Boolean): Boolean =
        strict && op == "^"

    private fun toRpn(tokens: List<String>, strict: Boolean): List<String> {
        val output = mutableListOf<String>()
        val ops = Stack<String>()
        var openParens = 0
        for (token in tokens) {
            when {
                token.toDoubleOrNull() != null -> output.add(token)
                token == "(" -> {
                    ops.push(token)
                    openParens++
                }
                token == ")" -> {
                    if (openParens == 0 && strict) {
                        throw IllegalArgumentException("unbalanced parentheses")
                    }
                    while (ops.isNotEmpty() && ops.peek() != "(") {
                        output.add(ops.pop())
                    }
                    if (ops.isNotEmpty()) {
                        ops.pop()
                        openParens--
                    }
                }
                else -> {
                    while (ops.isNotEmpty() && ops.peek() != "(" &&
                        (precedence(ops.peek()) > precedence(token) ||
                            (precedence(ops.peek()) == precedence(token) &&
                                !isRightAssociative(token, strict)))
                    ) {
                        output.add(ops.pop())
                    }
                    ops.push(token)
                }
            }
        }
        while (ops.isNotEmpty()) {
            val op = ops.pop()
            if (op == "(") {
                if (strict) {
                    throw IllegalArgumentException("unbalanced parentheses")
                }
                // LEGACY: a "(" left on the stack is dropped, as before.
            } else {
                output.add(op)
            }
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
