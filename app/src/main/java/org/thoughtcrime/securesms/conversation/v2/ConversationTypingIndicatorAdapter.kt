/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.signal.core.util.toInt
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ConversationTypingView
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.recipients.Recipient

class ConversationTypingIndicatorAdapter(
  private val glideRequests: GlideRequests
) : RecyclerView.Adapter<ConversationTypingIndicatorAdapter.ViewHolder>() {

  private var state: State = State()

  fun setState(state: State) {
    val isInsert = this.state.typists.isEmpty() && state.typists.isNotEmpty()
    val isRemoval = state.typists.isEmpty() && this.state.typists.isNotEmpty()
    val isChange = state.typists.isNotEmpty() && this.state.typists.isNotEmpty()

    this.state = state

    when {
      isInsert -> notifyItemInserted(0)
      isRemoval -> notifyItemRemoved(0)
      isChange -> notifyItemChanged(0)
      else -> Unit
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.conversation_typing_view, parent, false) as ConversationTypingView)
  }

  override fun getItemCount(): Int = state.typists.isNotEmpty().toInt()

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bind(glideRequests, state)
  }

  class ViewHolder(private val conversationTypingView: ConversationTypingView) : RecyclerView.ViewHolder(conversationTypingView) {
    fun bind(
      glideRequests: GlideRequests,
      state: State
    ) {
      conversationTypingView.setTypists(
        glideRequests,
        state.typists,
        state.isGroupThread,
        state.hasWallpaper
      )
    }
  }

  data class State(
    val typists: List<Recipient> = emptyList(),
    val hasWallpaper: Boolean = false,
    val isGroupThread: Boolean = false,
    val isReplacedByIncomingMessage: Boolean = false // TODO
  )
}
