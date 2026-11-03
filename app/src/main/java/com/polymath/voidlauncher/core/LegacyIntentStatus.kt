package com.polymath.voidlauncher.core

/**
 * Pure legacy-intent resolution (impl-06 step 1, honesty fixes).
 *
 * The old code presented three misleading things as facts on the HUD:
 *  - `git: main` in legacy mode — the branch was never read from any
 *    repo; it was a hardcoded string. The HUD now shows `git: —`.
 *  - `git: running|idle` in Void mode — that was task state, not a
 *    branch. The HUD now shows `task: running|idle` ([voidTaskLabel]).
 *  - An AST number that was either a real `COUNT(*) FROM nodes` or a
 *    file-size/3 estimate, with nothing saying which. [resolveLegacyStatus]
 *    now returns [astProvenance] explicitly: "exact", "estimate", or "none".
 *  - The `Sovereign Cockpit Ready` fallback shown even when no
 *    workflow.json existed at all, as if it were a real external task.
 *    A missing file now yields "No workflow file"; the fallback is kept
 *    ONLY when the file exists but lacks `active_task`.
 *
 * Pure Kotlin (no Android, no org.json): [NodeOsBridge] feeds it the
 * already-parsed workflow fields and DB facts, and renders its result.
 * The bridge's async AST cache is the only source of "exact" counts:
 * cachedAstNodes is only ever set by a successful queryTopology(),
 * so cachedNodes > 0 implies an exact count from a real query.
 */
enum class AstProvenance { NONE, EXACT, ESTIMATE }

data class LegacyStatusInput(
    val workflowExists: Boolean,
    /** Null/blank = file exists but has no active_task field. */
    val activeTask: String?,
    val taskStatus: String?,
    val dbExists: Boolean,
    /** Nodes from a successful queryTopology(), or 0 if never queried. */
    val cachedNodes: Int,
    val dbSizeKb: Long
)

data class LegacyStatusResult(
    val title: String,
    val taskStatus: String,
    val astCount: Int,
    val provenance: AstProvenance,
    val workflowPresent: Boolean
)

fun resolveLegacyStatus(input: LegacyStatusInput): LegacyStatusResult {
    val title: String
    val status: String
    if (!input.workflowExists) {
        title = "No workflow file"
        status = "idle"
    } else if (input.activeTask.isNullOrBlank()) {
        title = "Sovereign Cockpit Ready"
        status = input.taskStatus ?: "idle"
    } else {
        title = input.activeTask
        status = input.taskStatus ?: "idle"
    }
    val (astCount, provenance) = when {
        input.cachedNodes > 0 -> input.cachedNodes to AstProvenance.EXACT
        input.dbExists && input.dbSizeKb > 0 -> (input.dbSizeKb / 3).toInt().coerceAtLeast(1) to AstProvenance.ESTIMATE
        else -> 0 to AstProvenance.NONE
    }
    return LegacyStatusResult(title, status, astCount, provenance, input.workflowExists)
}

/** Void-mode HUD line: task state, never labelled as a git branch. */
fun voidTaskLabel(hasRunningTask: Boolean): String =
    "task: ${if (hasRunningTask) "running" else "idle"}"

/** Legacy-mode AST line, with provenance visible on the face of it. */
fun formatAstLine(count: Int, provenance: AstProvenance): String = when (provenance) {
    AstProvenance.EXACT -> "AST: $count (exact)"
    AstProvenance.ESTIMATE -> "AST: ~$count (est.)"
    AstProvenance.NONE -> "AST: —"
}
