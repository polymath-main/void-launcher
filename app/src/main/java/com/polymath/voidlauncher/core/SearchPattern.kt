package com.polymath.voidlauncher.core

/**
 * LIKE-pattern escaping (impl-04 step 1, plan gap G-S12).
 *
 * CommandPalette.search built `"%$trimmed%"` by interpolation, so `%`
 * and `_` in a query acted as wildcards. Escape them (and the escape
 * character itself) and use `ESCAPE '\'` at the call site. Pure
 * Kotlin, JVM-testable; CommandPalette is Android-bound and cannot
 * run in the JVM harness, so this rule lives here, not in it.
 */
object SearchPattern {
    fun escapeLikePattern(raw: String): String {
        val sb = StringBuilder("%")
        for (c in raw) {
            when (c) {
                '\\', '%', '_' -> sb.append('\\').append(c)
                else -> sb.append(c)
            }
        }
        sb.append('%')
        return sb.toString()
    }
}
