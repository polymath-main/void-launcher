package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.EvalMath
import com.polymath.voidlauncher.core.LiveScriptEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * impl-04 step 0: this test used to copy-paste the tokenizer/RPN code
 * into the test class and test its own copy, so a production regression
 * was invisible. It now drives the real LiveScriptEngine and the pure
 * EvalMath directly.
 * Legacy semantics are asserted here; step 5 flips the engine to
 * strict and updates the strict cases in this file.
 */
class LiveScriptEngineTest {

    private val engine = LiveScriptEngine()

    @Test
    fun testBasicArithmeticViaRealEngine() {
        assertEquals("1048576", engine.evaluate("1024 * 1024").output)
        assertEquals("42", engine.evaluate("6 * 7").output)
        assertEquals("14", engine.evaluate("2 + 3 * 4").output)
        assertEquals("20", engine.evaluate("(2 + 3) * 4").output)
    }

    @Test
    fun testPowerAndModuloViaRealEngine() {
        assertEquals("8", engine.evaluate("2 ^ 3").output)
        assertEquals("1", engine.evaluate("10 % 3").output)
    }

    @Test
    fun testEvalMathDirectly() {
        assertEquals(14.0, EvalMath.evaluateMath("2 + 3 * 4"), 0.001)
        assertEquals(8.0, EvalMath.evaluateMath("2 ^ 3"), 0.001)
    }

    @Test
    fun testDivisionByZeroIsAnErrorNotInfinity() {
        val result = engine.evaluate("1 / 0")
        assertFalse(result.success)
        assertTrue(result.output.contains("Division by zero"))
    }

    @Test
    fun testStrictRightAssocPower() {
        // Step 5: ^ is right-associative, so 2^3^2 = 512.
        assertEquals("512", engine.evaluate("2^3^2").output)
    }

    @Test
    fun testStrictUnaryMinus() {
        assertEquals("-5", engine.evaluate("-5").output)
        assertEquals("-6", engine.evaluate("2*-3").output)
        assertEquals("-5", engine.evaluate("-(2+3)").output)
    }

    @Test
    fun testStrictRejectsLettersAndUnbalancedParens() {
        val letters = engine.evaluate("2+abc")
        assertFalse(letters.success)
        assertTrue(letters.output.contains("unsupported character"))
        assertFalse(engine.evaluate("(2+3").success)
        assertFalse(engine.evaluate("2+3)").success)
    }

    @Test
    fun testEmptyExpression() {
        assertFalse(engine.evaluate("   ").success)
    }
}
