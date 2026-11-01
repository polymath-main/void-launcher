package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.NotifIntelligence
import com.polymath.voidlauncher.model.NotificationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifIntelligenceTest {

    private val notifIntelligence = NotifIntelligence()

    @Test
    fun testPriorityScoringAndBlacklist() {
        val items = listOf(
            NotificationItem(
                key = "1",
                packageName = "com.spam.ads",
                appName = "Spam",
                title = "Flash Sale",
                text = "50% off today!"
            ),
            NotificationItem(
                key = "2",
                packageName = "com.termux",
                appName = "Termux",
                title = "Agent Loop Finished",
                text = "task build completed successfully"
            ),
            NotificationItem(
                key = "3",
                packageName = "com.social.chat",
                appName = "Social",
                title = "Friend message",
                text = "Hey there"
            )
        )

        val whitelist = listOf("com.termux")
        val blacklist = listOf("com.spam.ads")
        val priorityKeywords = listOf("completed", "error", "failed", "agent")

        val result = notifIntelligence.scoreAndFilter(
            items = items,
            whitelist = whitelist,
            blacklist = blacklist,
            priorityKeywords = priorityKeywords,
            filterOngoing = false,
            maxCount = 5
        )

        // Spam is blacklisted
        assertEquals(2, result.size)
        // Termux with keywords has highest score and must be first
        assertEquals("com.termux", result[0].packageName)
        assertTrue(result[0].title.contains("Agent"))
    }
}
