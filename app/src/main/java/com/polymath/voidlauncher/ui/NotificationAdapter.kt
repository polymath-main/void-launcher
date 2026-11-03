package com.polymath.voidlauncher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.polymath.voidlauncher.core.NotifIntelligence
import com.polymath.voidlauncher.databinding.ItemNotificationEntryBinding
import com.polymath.voidlauncher.model.NotificationItem
import java.util.Locale

/**
 * Notification list adapter.
 *
 * impl-06 step 10: items are now ScoredNotifications, so the row can
 * show its score reason — but NOT by default. The "why" line and the
 * notification's own action taps live in a detail area that is GONE
 * until the user long-presses the row, so rows stay uncluttered.
 * Actions are user taps only, never fired automatically; dismiss
 * stays per-tap (no bulk/auto dismiss anywhere in this area).
 */
class NotificationAdapter(
    private val onItemClick: (NotificationItem) -> Unit,
    private val onDismissClick: (NotificationItem) -> Unit
) : ListAdapter<NotifIntelligence.ScoredNotification, NotificationAdapter.NotificationViewHolder>(DiffCallback) {

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

        fun bind(scored: NotifIntelligence.ScoredNotification) {
            val item = scored.item
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
            binding.tvNotifReason.text = "why: ${scored.reason} (score ${scored.score})"
            binding.tvNotifReason.visibility = View.GONE
            binding.layoutNotifActions.removeAllViews()
            binding.layoutNotifActions.visibility = View.GONE

            binding.btnNotifDismiss.visibility = if (item.isOngoing) View.GONE else View.VISIBLE
            binding.btnNotifDismiss.setOnClickListener {
                onDismissClick(item)
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
            binding.root.setOnLongClickListener {
                val expanded = binding.tvNotifReason.visibility != View.VISIBLE
                binding.tvNotifReason.visibility = if (expanded) View.VISIBLE else View.GONE
                if (expanded) {
                    // Rebuild action taps only when expanded, so the
                    // row carries no action views while collapsed.
                    for (action in item.actions) {
                        val btn = TextView(binding.root.context).apply {
                            text = "▸ ${action.title}"
                            setPadding(8, 6, 8, 6)
                            setOnClickListener {
                                try {
                                    action.intent?.send()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot run action: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        binding.layoutNotifActions.addView(btn)
                    }
                    binding.layoutNotifActions.visibility =
                        if (item.actions.isEmpty()) View.GONE else View.VISIBLE
                } else {
                    binding.layoutNotifActions.removeAllViews()
                    binding.layoutNotifActions.visibility = View.GONE
                }
                true
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
        private val DiffCallback = object : DiffUtil.ItemCallback<NotifIntelligence.ScoredNotification>() {
            override fun areItemsTheSame(
                oldItem: NotifIntelligence.ScoredNotification,
                newItem: NotifIntelligence.ScoredNotification
            ): Boolean {
                return oldItem.item.key == newItem.item.key
            }

            override fun areContentsTheSame(
                oldItem: NotifIntelligence.ScoredNotification,
                newItem: NotifIntelligence.ScoredNotification
            ): Boolean {
                return oldItem.item.title == newItem.item.title &&
                       oldItem.item.text == newItem.item.text &&
                       oldItem.item.postTimeMs == newItem.item.postTimeMs &&
                       oldItem.score == newItem.score &&
                       oldItem.reason == newItem.reason
            }
        }
    }
}
