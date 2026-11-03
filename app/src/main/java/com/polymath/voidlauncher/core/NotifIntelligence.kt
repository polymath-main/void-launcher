package com.polymath.voidlauncher.core

import com.polymath.voidlauncher.model.NotificationItem
import java.util.Locale

/**
 * Notification Intelligence Layer (F-11).
 *
 * Implements deterministic priority scoring and filtering to surface
 * critical engineering and system alerts over low-value notifications.
 */
class NotifIntelligence {

    data class ScoredNotification(
        val item: NotificationItem,
        val score: Float,
        // impl-06 step 10: the "why" line, e.g. "whitelist +5 ·
        // keyword 'failed' +3 · recent +1". Scoring numbers are
        // UNCHANGED (blacklist drop, whitelist +5, keyword +3,
        // recency < 5 min +1, base 1.0, max 5) — only the reason,
        // previously discarded, is now carried through.
        val reason: String = ""
    )

    fun scoreAndFilter(
        items: List<NotificationItem>,
        whitelist: List<String>,
        blacklist: List<String>,
        priorityKeywords: List<String>,
        filterOngoing: Boolean = true,
        maxCount: Int = 5
    ): List<NotificationItem> {
        return scoreWithReasons(items, whitelist, blacklist, priorityKeywords, filterOngoing, maxCount)
            .map { it.item }
    }

    fun scoreWithReasons(
        items: List<NotificationItem>,
        whitelist: List<String>,
        blacklist: List<String>,
        priorityKeywords: List<String>,
        filterOngoing: Boolean = true,
        maxCount: Int = 5
    ): List<ScoredNotification> {
        val scoredList = mutableListOf<ScoredNotification>()

        for (item in items) {
            // 1. Blacklist check
            if (blacklist.any { it.equals(item.packageName, ignoreCase = true) }) {
                continue
            }

            // 2. Ongoing check
            if (filterOngoing && item.isOngoing) {
                continue
            }

            // 3. Base score
            var score = 1.0f
            val reasons = mutableListOf("base 1.0")

            // Whitelist multiplier
            if (whitelist.any { it.equals(item.packageName, ignoreCase = true) }) {
                score += 5.0f
                reasons.add("whitelist +5")
            }

            // Keyword boosts (substring, case-insensitive, as before —
            // word-boundary matching is a product decision, not a
            // silent change, per plan risk R8)
            val combinedText = "${item.title} ${item.text}".lowercase(Locale.ROOT)
            for (kw in priorityKeywords) {
                if (combinedText.contains(kw.lowercase(Locale.ROOT))) {
                    score += 3.0f
                    reasons.add("keyword '$kw' +3")
                }
            }

            // Recency boost (events less than 5 min old get +1.0)
            val ageMs = System.currentTimeMillis() - item.postTimeMs
            if (ageMs in 0..300_000) {
                score += 1.0f
                reasons.add("recent +1")
            }

            scoredList.add(ScoredNotification(item, score, reasons.joinToString(" · ")))
        }

        // Sort descending by score, then by post time
        return scoredList
            .sortedWith(compareByDescending<ScoredNotification> { it.score }.thenByDescending { it.item.postTimeMs })
            .take(maxCount)
    }
}
