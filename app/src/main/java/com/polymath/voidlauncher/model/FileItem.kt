package com.polymath.voidlauncher.model

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val childCount: Int = 0,
    val extension: String = ""
)
