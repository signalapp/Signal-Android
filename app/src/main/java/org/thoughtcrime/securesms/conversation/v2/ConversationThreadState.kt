package org.thoughtcrime.securesms.conversation.v2

import org.signal.paging.ObservablePagedData
import org.thoughtcrime.securesms.conversation.ConversationData
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.database.model.MessageId

/**
 * Represents the content that will be displayed in the conversation
 * thread (recycler).
 */
class ConversationThreadState(
  val items: ObservablePagedData<MessageId, ConversationMessage>,
  val meta: ConversationData
)
