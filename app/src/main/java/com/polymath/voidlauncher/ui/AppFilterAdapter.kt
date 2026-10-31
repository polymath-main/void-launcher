package com.polymath.voidlauncher.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.polymath.voidlauncher.databinding.ItemAppEntryBinding
import com.polymath.voidlauncher.model.AppEntry

class AppFilterAdapter(
    private val onItemClick: (AppEntry) -> Unit
) : RecyclerView.Adapter<AppFilterAdapter.AppViewHolder>() {

    private val items = mutableListOf<AppEntry>()

    fun submitList(newItems: List<AppEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(items[position], position + 1)
    }

    override fun getItemCount(): Int = items.size

    inner class AppViewHolder(private val binding: ItemAppEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: AppEntry, index: Int) {
            binding.tvIndex.text = "[$index] "
            binding.tvAppName.text = entry.label
            binding.tvPackageName.text = entry.packageName

            binding.root.setOnClickListener {
                onItemClick(entry)
            }
        }
    }
}
