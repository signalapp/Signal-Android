/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiImageView
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.events.GroupCallReactionEvent
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * RecyclerView adapter for the reactions feed. This takes in a list of [GroupCallReactionEvent] and renders them onto the screen.
 * This adapter also encapsulates logic for whether the reaction should be displayed, such as expiration and maximum visible count.
 */
class WebRtcReactionsRecyclerAdapter : ListAdapter<GroupCallReactionEvent, WebRtcReactionsRecyclerAdapter.ViewHolder>(DiffCallback()) {
  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.webrtc_call_reaction_recycler_item, parent, false))
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val item = getItem(position)
    holder.bind(item)
  }

  override fun submitList(list: MutableList<GroupCallReactionEvent>?) {
    if (list == null) {
      super.submitList(null)
    } else {
      super.submitList(
        list.filter { it.getExpirationTimestamp() > System.currentTimeMillis() }
          .sortedBy { it.timestamp }
          .takeLast(MAX_REACTION_NUMBER)
          .reversed()
      )
    }
  }

  class ViewHolder(val itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val emojiView: EmojiImageView = itemView.findViewById(R.id.webrtc_call_reaction_emoji_view)
    private val textView: EmojiTextView = itemView.findViewById(R.id.webrtc_call_reaction_name_textview)
    fun bind(item: GroupCallReactionEvent) {
      emojiView.setImageEmoji(item.reaction)
      textView.text = getName(item.sender)
      itemView.isClickable = false
      textView.isClickable = false
      emojiView.isClickable = false
    }

    private fun getName(recipient: Recipient): String {
      return if (recipient.isSelf) {
        itemView.context.getString(R.string.CallParticipant__you)
      } else {
        recipient.getDisplayName(itemView.context)
      }
    }
  }

  private class DiffCallback : DiffUtil.ItemCallback<GroupCallReactionEvent>() {
    override fun areItemsTheSame(oldItem: GroupCallReactionEvent, newItem: GroupCallReactionEvent): Boolean {
      return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: GroupCallReactionEvent, newItem: GroupCallReactionEvent): Boolean {
      return oldItem == newItem
    }
  }

  companion object {
    const val MAX_REACTION_NUMBER = 5
  }
}
