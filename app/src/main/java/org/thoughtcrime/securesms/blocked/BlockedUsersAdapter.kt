/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.blocked

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.util.Consumer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.recipients.Recipient
import java.util.Objects
import kotlin.jvm.optionals.getOrNull

class BlockedUsersAdapter(private val recipientClickedListener:RecipientClickedListener) :
  ListAdapter<Recipient, BlockedUsersAdapter.ViewHolder>(RecipientDiffCallback()) {
  
  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.blocked_users_adapter_item, parent, false)) {
      position: Int ->
      recipientClickedListener.onRecipientClicked(getItem(position))
    }
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bind(Objects.requireNonNull(getItem(position)))
  }

  
  class ViewHolder(itemView: View, clickConsumer: Consumer<Int>) : RecyclerView.ViewHolder(itemView) {
    private val avatar: AvatarImageView = itemView.findViewById(R.id.avatar)
    private val displayName: TextView = itemView.findViewById(R.id.display_name)
    private val username: TextView = itemView.findViewById(R.id.username)

    init {
      itemView.setOnClickListener { _ ->
        if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
          clickConsumer.accept(bindingAdapterPosition)
        }
      }
    }

    fun bind(recipient: Recipient) {
      avatar.setAvatar(recipient)
      displayName.text = recipient.getDisplayName(itemView.context)

      val identifier = recipient.username.getOrNull()
      username.apply{
        text = identifier ?: ""
        visibility = identifier?.let { View.VISIBLE } ?: View.GONE
      }
    }
  }

  class RecipientDiffCallback : DiffUtil.ItemCallback<Recipient>() {
    override fun areItemsTheSame(oldItem: Recipient, newItem: Recipient): Boolean {
      return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: Recipient, newItem: Recipient): Boolean {
      return oldItem == newItem
    }
  }

  fun interface RecipientClickedListener {
    fun onRecipientClicked(recipient: Recipient)
  }
}



