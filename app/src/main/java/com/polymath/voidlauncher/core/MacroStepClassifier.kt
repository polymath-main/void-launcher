package com.polymath.voidlauncher.core

/**
 * Classifies recorded macro steps (impl-04 step 6).
 *
 * A step "would run shell" if, dispatched through the current omni
 * pipeline, it reaches VoidShell: explicit `:run`, `:sh`, `:!`, or
 * `:term` (panel shell). Unknown colon commands do NOT run shell
 * since step 1, and snippet aliases are resolved at play time, so
 * a step that is only an alias is not classified as shell here —
 * its target is classified when it is dispatched, not when the
 * macro is scanned. Pure Kotlin, JVM-testable.
 */
object MacroStepClassifier {
    fun isShellStep(step: String): Boolean {
        val s = step.trim()
        return s.startsWith(":run ") || s == ":run" ||
            s.startsWith(":sh ") || s == ":sh" ||
            s.startsWith(":! ") || s == ":!" ||
            s.startsWith(":term ") || s == ":term"
    }

    fun shellSteps(steps: List<String>): List<String> = steps.filter { isShellStep(it) }
}
