package org.thoughtcrime.securesms.conversation

/**
 * Represents how conversation bubbles should animate at any given time.
 */
data class ThreadAnimationState constructor(
  val threadId: Long,
  val threadMetadata: ConversationData?,
  val hasCommittedNonEmptyMessageList: Boolean
) {
  fun shouldPlayMessageAnimations(): Boolean {
    return when {
      threadId == -1L || threadMetadata == null -> false
      threadMetadata.threadSize == 0 -> true
      threadMetadata.threadSize > 0 && hasCommittedNonEmptyMessageList -> true
      else -> false
    }
  }
}
