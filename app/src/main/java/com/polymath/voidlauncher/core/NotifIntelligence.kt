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
        val score: Float
    )

    fun scoreAndFilter(
        items: List<NotificationItem>,
        whitelist: List<String>,
        blacklist: List<String>,
        priorityKeywords: List<String>,
        filterOngoing: Boolean = true,
        maxCount: Int = 5
    ): List<NotificationItem> {
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

            // Whitelist multiplier
            if (whitelist.any { it.equals(item.packageName, ignoreCase = true) }) {
                score += 5.0f
            }

            // Keyword boosts
            val combinedText = "${item.title} ${item.text}".lowercase(Locale.ROOT)
            for (kw in priorityKeywords) {
                if (combinedText.contains(kw.lowercase(Locale.ROOT))) {
                    score += 3.0f
                }
            }

            // Recency boost (events less than 5 min old get +1.0)
            val ageMs = System.currentTimeMillis() - item.postTimeMs
            if (ageMs in 0..300_000) {
                score += 1.0f
            }

            scoredList.add(ScoredNotification(item, score))
        }

        // Sort descending by score, then by post time
        return scoredList
            .sortedWith(compareByDescending<ScoredNotification> { it.score }.thenByDescending { it.item.postTimeMs })
            .map { it.item }
            .take(maxCount)
    }
}
