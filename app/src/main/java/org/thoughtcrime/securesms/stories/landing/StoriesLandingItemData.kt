package org.thoughtcrime.securesms.stories.landing

import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.database.model.StoryViewState
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Data required by each row of the Stories Landing Page for proper rendering.
 */
data class StoriesLandingItemData(
  val storyViewState: StoryViewState,
  val hasReplies: Boolean,
  val hasRepliesFromSelf: Boolean,
  val isHidden: Boolean,
  val primaryStory: ConversationMessage,
  val secondaryStory: ConversationMessage?,
  val storyRecipient: Recipient,
  val individualRecipient: Recipient = primaryStory.messageRecord.individualRecipient,
  val dateInMilliseconds: Long = primaryStory.messageRecord.dateSent
) : Comparable<StoriesLandingItemData> {
  override fun compareTo(other: StoriesLandingItemData): Int {
    return if (storyRecipient.isMyStory && !other.storyRecipient.isMyStory) {
      -1
    } else if (!storyRecipient.isMyStory && other.storyRecipient.isMyStory) {
      1
    } else {
      -dateInMilliseconds.compareTo(other.dateInMilliseconds)
    }
  }
}
