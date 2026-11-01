package com.polymath.voidlauncher.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.polymath.voidlauncher.model.CommandEntry

/**
 * High-performance, zero-overhead SQLite-backed command history & snippet manager (F-09).
 */
class CommandPalette(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "void_command_history.db"
        private const val DB_VERSION = 1

        private const val TABLE_HISTORY = "history"
        private const val COL_ID = "id"
        private const val COL_COMMAND = "command_text"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_USE_COUNT = "use_count"
        private const val COL_IS_SNIPPET = "is_snippet"
        private const val COL_ALIAS = "snippet_alias"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_HISTORY (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_COMMAND TEXT NOT NULL UNIQUE,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_USE_COUNT INTEGER NOT NULL DEFAULT 1,
                $COL_IS_SNIPPET INTEGER NOT NULL DEFAULT 0,
                $COL_ALIAS TEXT DEFAULT ''
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_history_timestamp ON $TABLE_HISTORY ($COL_TIMESTAMP DESC);")
        db.execSQL("CREATE INDEX idx_history_cmd ON $TABLE_HISTORY ($COL_COMMAND);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    /**
     * Records a command invocation. Upserts frequency and updates timestamp.
     */
    fun recordCommand(commandText: String, isSnippet: Boolean = false, snippetAlias: String = "") {
        val trimmed = commandText.trim()
        if (trimmed.isEmpty()) return

        val db = writableDatabase
        val now = System.currentTimeMillis()

        val cursor = db.rawQuery(
            "SELECT $COL_ID, $COL_USE_COUNT FROM $TABLE_HISTORY WHERE $COL_COMMAND = ?",
            arrayOf(trimmed)
        )

        try {
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                val currentCount = cursor.getInt(1)
                val values = ContentValues().apply {
                    put(COL_TIMESTAMP, now)
                    put(COL_USE_COUNT, currentCount + 1)
                    if (isSnippet) {
                        put(COL_IS_SNIPPET, 1)
                        put(COL_ALIAS, snippetAlias)
                    }
                }
                db.update(TABLE_HISTORY, values, "$COL_ID = ?", arrayOf(id.toString()))
            } else {
                val values = ContentValues().apply {
                    put(COL_COMMAND, trimmed)
                    put(COL_TIMESTAMP, now)
                    put(COL_USE_COUNT, 1)
                    put(COL_IS_SNIPPET, if (isSnippet) 1 else 0)
                    put(COL_ALIAS, snippetAlias)
                }
                db.insertWithOnConflict(TABLE_HISTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
        } finally {
            cursor.close()
        }
    }

    /**
     * Returns recent commands ordered by last used timestamp.
     */
    fun getRecentHistory(limit: Int = 30): List<CommandEntry> {
        val result = mutableListOf<CommandEntry>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_HISTORY,
            null,
            "$COL_IS_SNIPPET = 0",
            null,
            null,
            null,
            "$COL_TIMESTAMP DESC",
            limit.toString()
        )

        try {
            while (cursor.moveToNext()) {
                result.add(cursorToEntry(cursor))
            }
        } finally {
            cursor.close()
        }
        return result
    }

    /**
     * Searches history and snippets matching query prefix or substring.
     */
    fun search(query: String, limit: Int = 15): List<CommandEntry> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return getRecentHistory(limit)

        val result = mutableListOf<CommandEntry>()
        val db = readableDatabase
        val pattern = "%$trimmed%"
        val cursor = db.query(
            TABLE_HISTORY,
            null,
            "$COL_COMMAND LIKE ? OR $COL_ALIAS LIKE ?",
            arrayOf(pattern, pattern),
            null,
            null,
            "$COL_USE_COUNT DESC, $COL_TIMESTAMP DESC",
            limit.toString()
        )

        try {
            while (cursor.moveToNext()) {
                result.add(cursorToEntry(cursor))
            }
        } finally {
            cursor.close()
        }
        return result
    }

    /**
     * Retrieves all saved snippets.
     */
    fun getSnippets(): List<CommandEntry> {
        val result = mutableListOf<CommandEntry>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_HISTORY,
            null,
            "$COL_IS_SNIPPET = 1",
            null,
            null,
            null,
            "$COL_ALIAS ASC",
            null
        )

        try {
            while (cursor.moveToNext()) {
                result.add(cursorToEntry(cursor))
            }
        } finally {
            cursor.close()
        }
        return result
    }

    /**
     * Saves a named snippet alias.
     */
    fun saveSnippet(alias: String, command: String) {
        recordCommand(commandText = command, isSnippet = true, snippetAlias = alias)
    }

    /**
     * Enforces the configured history ceiling (config: `command_history_limit`).
     * Snippets are never pruned; only plain command history is capped.
     */
    fun pruneToLimit(limit: Int) {
        if (limit <= 0) return
        try {
            writableDatabase.execSQL(
                "DELETE FROM $TABLE_HISTORY WHERE $COL_IS_SNIPPET = 0 AND $COL_ID NOT IN " +
                    "(SELECT $COL_ID FROM $TABLE_HISTORY WHERE $COL_IS_SNIPPET = 0 " +
                    "ORDER BY $COL_TIMESTAMP DESC LIMIT ?)",
                arrayOf<Any>(limit)
            )
        } catch (_: Exception) {
            // Pruning is best-effort; never let it break command dispatch.
        }
    }

    /**
     * Deletes a record by ID.
     */
    fun delete(id: Long): Boolean {
        val db = writableDatabase
        return db.delete(TABLE_HISTORY, "$COL_ID = ?", arrayOf(id.toString())) > 0
    }

    private fun cursorToEntry(cursor: android.database.Cursor): CommandEntry {
        return CommandEntry(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            commandText = cursor.getString(cursor.getColumnIndexOrThrow(COL_COMMAND)),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP)),
            useCount = cursor.getInt(cursor.getColumnIndexOrThrow(COL_USE_COUNT)),
            isSnippet = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_SNIPPET)) == 1,
            snippetAlias = cursor.getString(cursor.getColumnIndexOrThrow(COL_ALIAS)) ?: ""
        )
    }
}
