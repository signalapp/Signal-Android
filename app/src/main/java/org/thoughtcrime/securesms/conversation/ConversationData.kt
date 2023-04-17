package org.thoughtcrime.securesms.conversation

/**
 * Represents metadata about a conversation.
 */
data class ConversationData(
  val threadId: Long,
  val lastSeen: Long,
  val lastSeenPosition: Int,
  val lastScrolledPosition: Int,
  val jumpToPosition: Int,
  val threadSize: Int,
  val messageRequestData: MessageRequestData,
  @get:JvmName("showUniversalExpireTimerMessage") val showUniversalExpireTimerMessage: Boolean
) {

  fun shouldJumpToMessage(): Boolean {
    return jumpToPosition >= 0
  }

  fun shouldScrollToLastSeen(): Boolean {
    return lastSeenPosition > 0
  }

  data class MessageRequestData @JvmOverloads constructor(
    val isMessageRequestAccepted: Boolean,
    val isHidden: Boolean,
    private val groupsInCommon: Boolean = false,
    val isGroup: Boolean = false
  ) {

    fun includeWarningUpdateMessage(): Boolean {
      return !isMessageRequestAccepted && !groupsInCommon && !isHidden
    }
  }
}
