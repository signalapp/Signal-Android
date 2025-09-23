/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.preferences

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.conversation.ConversationAdapter
import org.thoughtcrime.securesms.conversation.ConversationItem
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.MappingAdapter
import org.thoughtcrime.securesms.util.MappingViewHolder

/**
 * Renders a horizontal list of pinned messages in the conversation settings screen.
 */
object PinnedMessagesPreference {

  fun register(adapter: MappingAdapter) {
    adapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory(::ViewHolder, R.layout.conversation_settings_pinned_messages))
  }

  class Model(
    val pinnedMessages: List<MessageRecord>,
    val recipient: Recipient,
    val onPinnedMessageClick: (MessageRecord) -> Unit
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return true
    }
    
    override fun areContentsTheSame(newItem: Model): Boolean {
      return pinnedMessages == newItem.pinnedMessages
    }
  }

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val recyclerView: RecyclerView = findViewById(R.id.pinned_messages_recycler)
    private val adapter = PinnedMessagesAdapter()

    init {
      recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
      recyclerView.adapter = adapter
    }

    override fun bind(model: Model) {
      adapter.submitList(model.pinnedMessages.map { messageRecord ->
        PinnedMessageItem(messageRecord, model.recipient, model.onPinnedMessageClick)
      })
    }
  }

  private class PinnedMessagesAdapter : MappingAdapter() {
    init {
      PinnedMessageItem.register(this)
    }
  }

  private data class PinnedMessageItem(
    val messageRecord: MessageRecord,
    val recipient: Recipient,
    val onPinnedMessageClick: (MessageRecord) -> Unit
  ) {
    companion object {
      fun register(adapter: MappingAdapter) {
        adapter.registerFactory(PinnedMessageItem::class.java, MappingAdapter.LayoutFactory(::ItemViewHolder, R.layout.conversation_settings_pinned_message_item))
      }
    }

    private class ItemViewHolder(itemView: View) : MappingViewHolder<PinnedMessageItem>(itemView) {
      
      private val messagePreview: ConversationItem = findViewById(R.id.pinned_message_preview)

      override fun bind(model: PinnedMessageItem) {
        val conversationMessage = ConversationMessage.ConversationMessageFactory.createWithUnresolvedData(
          model.messageRecord,
          model.recipient
        )
        
        messagePreview.bind(
          conversationMessage,
          null,
          null,
          null,
          null,
          Recipient.UNKNOWN,
          null,
          false,
          false,
          false,
          false,
          false,
          -1,
          false
        )
        
        itemView.setOnClickListener {
          model.onPinnedMessageClick(model.messageRecord)
        }
      }
    }
  }
}