package org.thoughtcrime.securesms.conversation

enum class ConversationItemDisplayMode {
  /** Normal rendering, used for normal bubbles in the conversation view */
  STANDARD,

  /** Smaller bubbles, often trimming text and shrinking images. Used for quote threads. */
  CONDENSED,

  /** Smaller bubbles, always singular bubbles, with a footer. Used for edit message history. */
  EDIT_HISTORY,

  /** Less length restrictions. Used to show more info in message details. */
  DETAILED;

  fun displayWallpaper(): Boolean {
    return this == STANDARD || this == DETAILED
  }
}
