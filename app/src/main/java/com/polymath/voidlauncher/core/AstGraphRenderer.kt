package com.polymath.voidlauncher.core

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

/**
 * AstGraphRenderer (F-05).
 *
 * Directly queries agy_nodeos.db via read-only SQLite to extract
 * real AST node counts, edge distributions, and topology metrics.
 */
class AstGraphRenderer {

    companion object {
        private const val TAG = "AstGraphRenderer"
    }

    data class TopologySnapshot(
        val totalNodes: Int,
        val totalEdges: Int,
        val typeBreakdown: List<Pair<String, Int>>,
        val isAlive: Boolean
    )

    fun queryTopology(dbPath: String): TopologySnapshot {
        val file = File(dbPath)
        if (!file.exists() || !file.canRead()) {
            return TopologySnapshot(0, 0, emptyList(), false)
        }

        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)

            var nodeCount = 0
            val nodeCursor = db.rawQuery("SELECT COUNT(*) FROM nodes", null)
            if (nodeCursor.moveToFirst()) {
                nodeCount = nodeCursor.getInt(0)
            }
            nodeCursor.close()

            var edgeCount = 0
            val edgeCursor = db.rawQuery("SELECT COUNT(*) FROM edges", null)
            if (edgeCursor.moveToFirst()) {
                edgeCount = edgeCursor.getInt(0)
            }
            edgeCursor.close()

            val breakdown = mutableListOf<Pair<String, Int>>()
            val typeCursor = db.rawQuery(
                "SELECT node_type, COUNT(*) AS c FROM nodes GROUP BY node_type ORDER BY c DESC LIMIT 8",
                null
            )
            while (typeCursor.moveToNext()) {
                val typeName = typeCursor.getString(0) ?: "unknown"
                val count = typeCursor.getInt(1)
                breakdown.add(Pair(typeName, count))
            }
            typeCursor.close()

            return TopologySnapshot(
                totalNodes = nodeCount,
                totalEdges = edgeCount,
                typeBreakdown = breakdown,
                isAlive = true
            )
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read nodeos AST db ($dbPath): ${e.message}")
            return TopologySnapshot(0, 0, emptyList(), false)
        } finally {
            try { db?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Renders a textual topology summary.
     *
     * @param nodeCap maximum number of nodes to visualise (config: `ast_graph_node_cap`).
     *        A value <= 0 renders the full breakdown.
     */
    fun formatSummaryText(snapshot: TopologySnapshot, nodeCap: Int = 0): String {
        if (!snapshot.isAlive) {
            return "[NodeOS Graph: Offline / agy_nodeos.db not accessible]"
        }

        val cap = if (nodeCap > 0) nodeCap else Int.MAX_VALUE
        val sb = StringBuilder()
        sb.appendLine("=== NodeOS Spatial AST Graph ===")
        sb.appendLine("Total Nodes: ${snapshot.totalNodes} | Total Edges: ${snapshot.totalEdges}")
        if (cap != Int.MAX_VALUE) {
            sb.appendLine("Render cap: $cap nodes")
        }
        sb.appendLine("--------------------------------")

        var rendered = 0
        for ((type, count) in snapshot.typeBreakdown) {
            if (rendered >= cap) {
                sb.appendLine("… breakdown truncated at cap")
                break
            }
            rendered += count
            val barLen = (count * 15 / (snapshot.totalNodes.coerceAtLeast(1))).coerceIn(1, 15)
            val bar = "■".repeat(barLen)
            sb.appendLine(String.format(java.util.Locale.US, "%-12s [%4d] %s", type, count, bar))
        }
        return sb.toString().trimEnd()
    }
}
