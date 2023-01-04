package org.thoughtcrime.securesms.conversationlist.chatfilter

import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter

data class ConversationFilterRequest(
  val filter: ConversationFilter,
  val source: ConversationFilterSource
)
