package org.thoughtcrime.securesms.stories.viewer.info

import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.messagedetails.MessageDetails

/**
 * Contains the needed information to render the story info sheet.
 */
data class StoryInfoState(
  val messageDetails: MessageDetails? = null
) {
  private val mediaMessage = messageDetails?.conversationMessage?.messageRecord as? MediaMmsMessageRecord

  val sentMillis: Long = mediaMessage?.dateSent ?: -1L
  val receivedMillis: Long = mediaMessage?.dateReceived ?: -1L
  val size: Long = mediaMessage?.slideDeck?.thumbnailSlide?.fileSize ?: 0
  val isOutgoing: Boolean = mediaMessage?.isOutgoing ?: false
  val isLoaded: Boolean = mediaMessage != null
}
