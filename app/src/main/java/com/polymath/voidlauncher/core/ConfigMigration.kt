package com.polymath.voidlauncher.core

import org.json.JSONObject

/**
 * Area 07 step 5 — schema migration hook (plan §4 step 5).
 *
 * Deliberately a no-op passthrough today: schema_version is
 * advisory (area 01 step 7) and every field has a default, so old
 * configs already load. This hook is where the NEXT schema change
 * lives — a future version maps (json, fromVersion) to the current
 * shape instead of every caller inventing its own migration. It is
 * NOT wired as a gate: loadConfig does not call it to reject
 * anything, and no version-numbering scheme beyond "the bundled
 * asset's schema_version is the current version" is invented here.
 */
object ConfigMigration {
    fun migrate(json: JSONObject, fromVersion: Int): JSONObject {
        // No-op today. fromVersion is recorded for the future
        // migration that will switch on it; unused otherwise.
        @Suppress("UNUSED_PARAMETER")
        val version = fromVersion
        return json
    }
}
