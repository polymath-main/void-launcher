package com.polymath.voidlauncher.core

import com.polymath.voidlauncher.model.FileItem

/**
 * impl-02 step 4: pure-Kotlin files search + sort, deliberately kept out
 * of the Activity so the JVM harness can test it for real (the Activity
 * is Android-bound). Directories always sort first, in every mode —
 * the sort key only orders within the directory group and within the
 * file group. Search is typing-only, name-contains, case-insensitive,
 * uncapped, and never launches anything; search applies first, then
 * sort, so the two compose. No recursive search in this step.
 */
enum class FileSortMode(val label: String) {
    NAME_ASC("⇅ NAME ↑"),
    NAME_DESC("⇅ NAME ↓"),
    DATE_NEWEST("⇅ DATE ↓"),
    DATE_OLDEST("⇅ DATE ↑"),
    SIZE_LARGEST("⇅ SIZE ↓"),
    SIZE_SMALLEST("⇅ SIZE ↑");

    fun next(): FileSortMode = values()[(ordinal + 1) % values().size]
}

object FileListingOrder {

    fun filterByName(items: List<FileItem>, query: String): List<FileItem> {
        val q = query.trim()
        if (q.isBlank()) return items
        return items.filter { it.name.contains(q, ignoreCase = true) }
    }

    fun sort(items: List<FileItem>, mode: FileSortMode): List<FileItem> {
        val dirsFirst = compareBy<FileItem> { !it.isDirectory }
        val byMode: Comparator<FileItem> = when (mode) {
            FileSortMode.NAME_ASC -> compareBy { it.name.lowercase() }
            FileSortMode.NAME_DESC -> compareByDescending { it.name.lowercase() }
            FileSortMode.DATE_NEWEST -> compareByDescending { it.lastModifiedMs }
            FileSortMode.DATE_OLDEST -> compareBy { it.lastModifiedMs }
            FileSortMode.SIZE_LARGEST -> compareByDescending { it.sizeBytes }
            FileSortMode.SIZE_SMALLEST -> compareBy { it.sizeBytes }
        }
        return items.sortedWith(dirsFirst.then(byMode))
    }

    fun apply(items: List<FileItem>, query: String, mode: FileSortMode): List<FileItem> =
        sort(filterByName(items, query), mode)
}
