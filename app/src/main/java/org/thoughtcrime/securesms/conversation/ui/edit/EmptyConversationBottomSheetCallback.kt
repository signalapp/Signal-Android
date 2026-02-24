/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.ui.edit

import org.thoughtcrime.securesms.conversation.ConversationAdapter
import org.thoughtcrime.securesms.conversation.ConversationBottomSheetCallback
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.database.model.MessageRecord

object EmptyConversationBottomSheetCallback : ConversationBottomSheetCallback {
  override fun getConversationAdapterListener(): ConversationAdapter.ItemClickListener = EmptyConversationAdapterListener
  override fun jumpToMessage(messageRecord: MessageRecord) = Unit
  override fun unpin(conversationMessage: ConversationMessage) = Unit
  override fun copy(conversationMessage: ConversationMessage) = Unit
  override fun delete(conversationMessage: ConversationMessage) = Unit
  override fun save(conversationMessage: ConversationMessage) = Unit
}
