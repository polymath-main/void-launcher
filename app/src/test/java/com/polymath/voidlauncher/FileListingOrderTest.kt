package com.polymath.voidlauncher

import com.polymath.voidlauncher.core.FileListingOrder
import com.polymath.voidlauncher.core.FileSortMode
import com.polymath.voidlauncher.model.FileItem
import org.junit.Assert.assertEquals
import org.junit.Test

/** impl-02 Step 4 — real unit tests for the pure search/sort seam. */
class FileListingOrderTest {

    private fun file(name: String, dir: Boolean = false, size: Long = 0, modified: Long = 0) =
        FileItem(name = name, path = "/tmp/$name", isDirectory = dir, sizeBytes = size, lastModifiedMs = modified)

    private val fixture = listOf(
        file("zebra.txt", size = 10, modified = 300),
        file("Alpha", dir = true, modified = 100),
        file("apple.txt", size = 50, modified = 200),
        file("beta", dir = true, modified = 400),
        file("Mango.txt", size = 20, modified = 100)
    )

    @Test
    fun dirsAlwaysSortFirstInEveryMode() {
        for (mode in FileSortMode.values()) {
            val sorted = FileListingOrder.sort(fixture, mode)
            val firstFileIdx = sorted.indexOfFirst { !it.isDirectory }
            assertEquals("dirs must precede files in $mode", 2, firstFileIdx)
            assertEquals("all dirs must be first in $mode", 2, sorted.take(2).count { it.isDirectory })
        }
    }

    @Test
    fun nameSortIsCaseInsensitiveAscAndDesc() {
        assertEquals(
            listOf("Alpha", "beta", "apple.txt", "Mango.txt", "zebra.txt"),
            FileListingOrder.sort(fixture, FileSortMode.NAME_ASC).map { it.name }
        )
        assertEquals(
            listOf("beta", "Alpha", "zebra.txt", "Mango.txt", "apple.txt"),
            FileListingOrder.sort(fixture, FileSortMode.NAME_DESC).map { it.name }
        )
    }

    @Test
    fun dateAndSizeSortsOrderWithinGroups() {
        assertEquals(
            listOf("beta", "Alpha", "zebra.txt", "apple.txt", "Mango.txt"),
            FileListingOrder.sort(fixture, FileSortMode.DATE_NEWEST).map { it.name }
        )
        assertEquals(
            listOf("Alpha", "beta", "apple.txt", "Mango.txt", "zebra.txt"),
            FileListingOrder.sort(fixture, FileSortMode.SIZE_LARGEST).map { it.name }
        )
        // Dirs both report size 0, so SIZE_SMALLEST keeps them in
        // fixture order (stable sort) — Alpha before beta.
        assertEquals(
            listOf("Alpha", "beta", "zebra.txt", "Mango.txt", "apple.txt"),
            FileListingOrder.sort(fixture, FileSortMode.SIZE_SMALLEST).map { it.name }
        )
    }

    @Test
    fun searchFiltersByNameCaseInsensitiveAndComposesWithSort() {
        assertEquals(
            listOf("apple.txt"),
            FileListingOrder.filterByName(fixture, "pp").map { it.name }
        )
        assertEquals(
            listOf("Alpha", "Mango.txt", "apple.txt", "beta", "zebra.txt").sorted(),
            FileListingOrder.filterByName(fixture, "A").map { it.name }.sorted()
        )
        // Search first, then sort: "ma" matches only Mango.txt among files.
        assertEquals(
            listOf("Mango.txt"),
            FileListingOrder.apply(fixture, "ma", FileSortMode.NAME_ASC).map { it.name }
        )
        // Blank query restores the full listing.
        assertEquals(fixture.size, FileListingOrder.apply(fixture, "  ", FileSortMode.NAME_ASC).size)
        // Clearing a search never launches anything — the seam has no
        // launch path at all (compile-time: no Activity/Intent imports).
    }

    @Test
    fun activityWiresSearchSortAndCountLine() {
        // Source-text check for the Android-bound wiring (same pattern
        // as the UiV2* tests): the pure seam above is tested for real.
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
        var root: java.io.File? = null
        repeat(6) {
            if (root == null && dir != null && java.io.File(dir, "app/src/main/java/com/polymath/voidlauncher/core/BareIntegerRouter.kt").exists()) root = dir
            dir = dir?.parentFile
        }
        org.junit.Assume.assumeTrue(root != null)
        val activity = java.io.File(root, "app/src/main/java/com/polymath/voidlauncher/VoidLauncherActivity.kt").readText()
        org.junit.Assert.assertTrue(activity.contains("binding.etFilesSearch.addTextChangedListener"))
        org.junit.Assert.assertTrue(activity.contains("binding.btnFilesSort.setOnClickListener"))
        org.junit.Assert.assertTrue(activity.contains("fun applyFilesSearchAndSort()"))
        org.junit.Assert.assertTrue(activity.contains("FileListingOrder.apply(lastFilesItems, filesSearchQuery, filesSortMode)"))
        org.junit.Assert.assertTrue(activity.contains("binding.tvFilesCount.text"))
        val layout = java.io.File(root, "app/src/main/res/layout/activity_void_launcher.xml").readText()
        org.junit.Assert.assertTrue(layout.contains("android:id=\"@+id/etFilesSearch\""))
        org.junit.Assert.assertTrue(layout.contains("android:id=\"@+id/btnFilesSort\""))
        org.junit.Assert.assertTrue(layout.contains("android:id=\"@+id/tvFilesCount\""))
    }

    @Test
    fun sortModeCyclesThroughAllModesAndWraps() {
        var mode = FileSortMode.NAME_ASC
        val seen = mutableSetOf<FileSortMode>()
        repeat(FileSortMode.values().size) { seen.add(mode); mode = mode.next() }
        assertEquals(FileSortMode.values().toSet(), seen)
        assertEquals(FileSortMode.NAME_ASC, mode)
    }
}
