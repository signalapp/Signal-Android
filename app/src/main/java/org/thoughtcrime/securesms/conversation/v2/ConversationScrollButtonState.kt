package org.thoughtcrime.securesms.conversation.v2

data class ConversationScrollButtonState(
  val showScrollButtons: Boolean = false,
  val unreadCount: Int = 0,
  val hasMentions: Boolean = false
)
