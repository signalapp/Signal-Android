package org.thoughtcrime.securesms.stories.viewer.reply.group

import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.recipients.Recipient

data class StoryGroupReplyItemData(
  val key: Key,
  val sender: Recipient,
  val sentAtMillis: Long,
  val replyBody: ReplyBody
) {
  sealed class ReplyBody {
    data class Text(val message: ConversationMessage) : ReplyBody()
    data class Reaction(val emoji: CharSequence) : ReplyBody()
  }

  sealed class Key {
    data class Text(val messageId: Long) : Key()
    data class Reaction(val reactionId: Long) : Key()
  }
}
