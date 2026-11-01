package com.polymath.voidlauncher.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.polymath.voidlauncher.databinding.ItemDrawerAppBinding
import com.polymath.voidlauncher.model.AppEntry

class DrawerAppAdapter(
    private val onItemClick: (AppEntry) -> Unit,
    private val onItemLongClick: (AppEntry) -> Unit
) : RecyclerView.Adapter<DrawerAppAdapter.DrawerViewHolder>() {

    private val items = mutableListOf<AppEntry>()
    var showIcons: Boolean = true
    var iconStyle: String = "original"
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

    /** Re-binds visible rows after appearance settings change (icons / style / tint). */
    fun refreshAppearance() = notifyDataSetChanged()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrawerViewHolder {
        val binding = ItemDrawerAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DrawerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DrawerViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class DrawerViewHolder(private val binding: ItemDrawerAppBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: AppEntry) {
            binding.tvDrawerAppName.text = entry.label
            binding.tvDrawerAppPkg.text = entry.packageName

            if (showIcons && entry.icon != null) {
                binding.ivDrawerAppIcon.visibility = View.VISIBLE
                binding.ivDrawerAppIcon.setImageDrawable(entry.icon)

                when (iconStyle) {
                    "monochrome" -> binding.ivDrawerAppIcon.colorFilter = grayscaleFilter
                    "tinted" -> if (tintColor != 0) {
                        binding.ivDrawerAppIcon.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
                    } else {
                        binding.ivDrawerAppIcon.clearColorFilter()
                    }
                    else -> binding.ivDrawerAppIcon.clearColorFilter()
                }
            } else {
                binding.ivDrawerAppIcon.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onItemClick(entry)
            }
            binding.root.setOnLongClickListener {
                onItemLongClick(entry)
                true
            }
        }
    }
}
