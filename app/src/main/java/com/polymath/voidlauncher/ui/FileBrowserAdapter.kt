package com.polymath.voidlauncher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.polymath.voidlauncher.core.FileManager
import com.polymath.voidlauncher.databinding.ItemFileEntryBinding
import com.polymath.voidlauncher.model.FileItem

class FileBrowserAdapter(
    private val fileManager: FileManager,
    private val onItemClick: (FileItem) -> Unit,
    private val onItemMoreClick: (FileItem, View) -> Unit
) : RecyclerView.Adapter<FileBrowserAdapter.FileViewHolder>() {

    private val items = mutableListOf<FileItem>()

    fun submitList(newItems: List<FileItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class FileViewHolder(private val binding: ItemFileEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FileItem) {
            binding.tvFileName.text = item.name

            if (item.isDirectory) {
                binding.tvFileIcon.text = "📁"
                binding.tvFileDetails.text = "${item.childCount} items · ${fileManager.formatDate(item.lastModifiedMs)}"
            } else {
                binding.tvFileIcon.text = resolveFileIcon(item.extension)
                val sizeStr = fileManager.formatFileSize(item.sizeBytes)
                binding.tvFileDetails.text = "$sizeStr · ${fileManager.formatDate(item.lastModifiedMs)}"
            }

            binding.root.setOnClickListener { onItemClick(item) }
            binding.tvFileActionMore.setOnClickListener { view -> onItemMoreClick(item, view) }
            binding.root.setOnLongClickListener { view ->
                onItemMoreClick(item, view)
                true
            }
        }

        private fun resolveFileIcon(ext: String): String {
            return when (ext) {
                "kt", "java", "py", "js", "ts", "c", "cpp", "sh", "rs" -> "⚡"
                "json", "yaml", "yml", "toml", "xml", "properties" -> "⚙"
                "md", "txt", "log", "doc" -> "📄"
                "apk", "zip", "tar", "gz", "7z" -> "📦"
                "png", "jpg", "jpeg", "webp", "gif", "svg" -> "🖼"
                "mp3", "wav", "ogg", "flac" -> "🎵"
                "mp4", "mkv", "webm" -> "🎬"
                "db", "sqlite" -> "🗄"
                else -> "📄"
            }
        }
    }
}
