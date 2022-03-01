package org.thoughtcrime.securesms.stories.viewer.page

import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Each story is made up of a collection of posts
 */
class StoryPost(
  val id: Long,
  val sender: Recipient,
  val group: Recipient?,
  val distributionList: Recipient?,
  val viewCount: Int,
  val replyCount: Int,
  val dateInMilliseconds: Long,
  val attachment: Attachment,
  val conversationMessage: ConversationMessage,
  val allowsReplies: Boolean
)
