package org.thoughtcrime.securesms.conversation

enum class ConversationItemDisplayMode {
  /** Normal rendering, used for normal bubbles in the conversation view */
  STANDARD,

  /** Smaller bubbles, often trimming text and shrinking images. Used for quote threads. */
  CONDENSED,

  /** Less length restrictions. Used to show more info in message details. */
  DETAILED
}
