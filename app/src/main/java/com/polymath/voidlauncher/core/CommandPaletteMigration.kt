package com.polymath.voidlauncher.core

/**
 * CommandPalette migration policy (impl-04 step 4).
 *
 * The statements for each version step live here, in pure Kotlin,
 * so the JVM harness can assert the policy: every migration must
 * preserve rows, and no migration may DROP a table. CommandPalette
 * (Android-bound, SQLite) executes these statements verbatim; it
 * must not invent its own upgrade SQL. A future version adds a new
 * list (e.g. V2_TO_V3) and a branch in onUpgrade — never a DROP.
 */
object CommandPaletteMigration {

    /** v1 -> v2 (impl-04 step 1): dedupe snippet aliases, add index. */
    val V1_TO_V2: List<String> = listOf(
        "DELETE FROM history WHERE is_snippet = 1 AND snippet_alias <> '' AND " +
            "id NOT IN (SELECT MAX(id) FROM history " +
            "WHERE is_snippet = 1 AND snippet_alias <> '' GROUP BY UPPER(snippet_alias))",
        "CREATE INDEX IF NOT EXISTS idx_snippet_alias ON history (snippet_alias);"
    )

    /** Policy check used by tests: no statement may drop data. */
    fun isLossless(statements: List<String>): Boolean =
        statements.none { it.trim().uppercase().startsWith("DROP TABLE") }
}
