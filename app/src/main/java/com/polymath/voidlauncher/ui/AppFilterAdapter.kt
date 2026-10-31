package com.polymath.voidlauncher.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.polymath.voidlauncher.databinding.ItemAppEntryBinding
import com.polymath.voidlauncher.model.AppEntry

class AppFilterAdapter(
    private val onItemClick: (AppEntry) -> Unit
) : RecyclerView.Adapter<AppFilterAdapter.AppViewHolder>() {

    private val items = mutableListOf<AppEntry>()
    var showIcons: Boolean = true
    var iconStyle: String = "original" // original | monochrome | tinted
    var tintColor: Int = 0

    private val grayscaleFilter by lazy {
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        ColorMatrixColorFilter(matrix)
    }

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

            if (showIcons && entry.icon != null) {
                binding.ivAppIcon.visibility = View.VISIBLE
                binding.ivAppIcon.setImageDrawable(entry.icon)

                when (iconStyle) {
                    "monochrome" -> binding.ivAppIcon.colorFilter = grayscaleFilter
                    "tinted" -> if (tintColor != 0) {
                        binding.ivAppIcon.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
                    } else {
                        binding.ivAppIcon.clearColorFilter()
                    }
                    else -> binding.ivAppIcon.clearColorFilter()
                }
            } else {
                binding.ivAppIcon.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onItemClick(entry)
            }
        }
    }
}
