package org.thoughtcrime.securesms.conversationlist.model

/**
 * Describes what conversations should display in the
 * conversation list.
 */
enum class ConversationFilter {
  /**
   * No filtering is applied to the conversation list
   */
  OFF,

  /**
   * Only unread chats will be displayed in the conversation list
   */
  UNREAD,

  /**
   * Only muted chats will be displayed in the conversation list
   */
  MUTED,

  /**
   * Only group chats will be displayed in the conversation list
   */
  GROUPS
}
