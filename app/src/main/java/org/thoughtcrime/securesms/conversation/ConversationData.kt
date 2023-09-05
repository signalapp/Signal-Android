package org.thoughtcrime.securesms.conversation

import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.push.ServiceId

/**
 * Represents metadata about a conversation.
 */
data class ConversationData(
  val threadRecipient: Recipient,
  val threadId: Long,
  val lastSeen: Long,
  val lastSeenPosition: Int,
  val lastScrolledPosition: Int,
  val jumpToPosition: Int,
  val threadSize: Int,
  val messageRequestData: MessageRequestData,
  @get:JvmName("showUniversalExpireTimerMessage") val showUniversalExpireTimerMessage: Boolean,
  val unreadCount: Int,
  val groupMemberAcis: List<ServiceId>
) {

  fun shouldJumpToMessage(): Boolean {
    return jumpToPosition >= 0
  }

  fun shouldScrollToLastSeen(): Boolean {
    return lastSeenPosition > 0
  }

  fun getStartPosition(): Int {
    return when {
      shouldJumpToMessage() -> jumpToPosition
      messageRequestData.isMessageRequestAccepted && shouldScrollToLastSeen() -> lastSeenPosition
      messageRequestData.isMessageRequestAccepted -> lastScrolledPosition
      else -> threadSize
    }
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
