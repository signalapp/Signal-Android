package org.thoughtcrime.securesms.conversation

sealed class ConversationItemDisplayMode(val messageMode: MessageMode = MessageMode.STANDARD) {
  /** Normal rendering, used for normal bubbles in the conversation view */
  object Standard : ConversationItemDisplayMode()

  /** Smaller bubbles, often trimming text and shrinking images. Used for quote threads. */
  class Condensed(messageMode: MessageMode) : ConversationItemDisplayMode(messageMode)

  /** Smaller bubbles, always singular bubbles, with a footer. Used for edit message history. */
  object EditHistory : ConversationItemDisplayMode()

  /** Less length restrictions. Used to show more info in message details. */
  object Detailed : ConversationItemDisplayMode()

  fun displayWallpaper(): Boolean {
    return this == Standard || this == Detailed
  }

  enum class MessageMode {
    SCHEDULED,
    PINNED,
    STANDARD
  }
}
