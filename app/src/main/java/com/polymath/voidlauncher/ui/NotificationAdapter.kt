package com.polymath.voidlauncher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.polymath.voidlauncher.core.VoidNotificationListenerService
import com.polymath.voidlauncher.databinding.ItemNotificationEntryBinding
import com.polymath.voidlauncher.model.NotificationItem
import java.util.Locale

class NotificationAdapter(
    private val onItemClick: (NotificationItem) -> Unit,
    private val onDismissClick: (NotificationItem) -> Unit
) : ListAdapter<NotificationItem, NotificationAdapter.NotificationViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NotificationViewHolder(
        private val binding: ItemNotificationEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NotificationItem) {
            binding.tvNotifAppName.text = item.appName.uppercase(Locale.ROOT)
            binding.tvNotifTitle.text = item.title.ifEmpty { item.appName }
            binding.tvNotifText.text = item.text

            if (item.appIcon != null) {
                binding.ivNotifIcon.setImageDrawable(item.appIcon)
                binding.ivNotifIcon.visibility = View.VISIBLE
            } else {
                binding.ivNotifIcon.visibility = View.GONE
            }

            binding.tvNotifTime.text = formatTimeAgo(item.postTimeMs)

            binding.btnNotifDismiss.visibility = if (item.isOngoing) View.GONE else View.VISIBLE
            binding.btnNotifDismiss.setOnClickListener {
                onDismissClick(item)
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }

        private fun formatTimeAgo(timestampMs: Long): String {
            val now = System.currentTimeMillis()
            val diffMs = now - timestampMs
            if (diffMs < 60_000L) return "now"
            val mins = diffMs / 60_000L
            if (mins < 60L) return "${mins}m ago"
            val hours = mins / 60L
            if (hours < 24L) return "${hours}h ago"
            val days = hours / 24L
            return "${days}d ago"
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<NotificationItem>() {
            override fun areItemsTheSame(oldItem: NotificationItem, newItem: NotificationItem): Boolean {
                return oldItem.key == newItem.key
            }

            override fun areContentsTheSame(oldItem: NotificationItem, newItem: NotificationItem): Boolean {
                return oldItem.title == newItem.title &&
                       oldItem.text == newItem.text &&
                       oldItem.postTimeMs == newItem.postTimeMs
            }
        }
    }
}
