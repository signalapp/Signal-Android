package org.thoughtcrime.securesms.conversationlist.chatfilter

/**
 * Describes where the chat filter was applied from.
 */
enum class ConversationFilterSource {
  /**
   * User pulled and released the pull view.
   */
  DRAG,

  /**
   * User utilized the menu item in the overflow.
   */
  OVERFLOW
}
