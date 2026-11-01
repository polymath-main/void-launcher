package com.polymath.voidlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Stack
import kotlin.math.pow

class LiveScriptEngineTest {

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
                else -> i++
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
                val b = stack.pop()
                val a = stack.pop()
                val res = when (token) {
                    "+" -> a + b
                    "-" -> a - b
                    "*" -> a * b
                    "/" -> a / b
                    "%" -> a % b
                    "^" -> a.pow(b)
                    else -> 0.0
                }
                stack.push(res)
            }
        }
        return stack.pop()
    }

    @Test
    fun testBasicArithmetic() {
        assertEquals(1048576.0, evaluateMath("1024 * 1024"), 0.001)
        assertEquals(42.0, evaluateMath("6 * 7"), 0.001)
        assertEquals(14.0, evaluateMath("2 + 3 * 4"), 0.001)
        assertEquals(20.0, evaluateMath("(2 + 3) * 4"), 0.001)
    }

    @Test
    fun testPowerAndModulo() {
        assertEquals(8.0, evaluateMath("2 ^ 3"), 0.001)
        assertEquals(1.0, evaluateMath("10 % 3"), 0.001)
    }
}
