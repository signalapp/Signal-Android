package org.thoughtcrime.securesms.conversation

import org.thoughtcrime.securesms.database.model.MessageRecord

/**
 * Callback interface for bottom sheets that show conversation data in a conversation and
 * want to manipulate the conversation view.
 */
interface ConversationBottomSheetCallback {
  fun getConversationAdapterListener(): ConversationAdapter.ItemClickListener
  fun jumpToMessage(messageRecord: MessageRecord)
}
