package org.thoughtcrime.securesms.conversation.mutiselect

import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.database.model.MessageRecord

/**
 * Represents a part of a message that can be selected and sent as its own distinct entity.
 */
sealed class MultiselectPart(open val conversationMessage: ConversationMessage) {

  fun getMessageRecord(): MessageRecord = conversationMessage.messageRecord

  fun isExpired(): Boolean {
    val expiresAt = conversationMessage.messageRecord.expireStarted + conversationMessage.messageRecord.expiresIn

    return expiresAt > 0 && expiresAt < System.currentTimeMillis()
  }

  /**
   * Represents the body of the message
   */
  data class Text(override val conversationMessage: ConversationMessage) : MultiselectPart(conversationMessage)

  /**
   * Represents an attachment on the message, such as a file or image
   */
  data class Attachments(override val conversationMessage: ConversationMessage) : MultiselectPart(conversationMessage)

  /**
   * Represents an update, which is not forwardable
   */
  data class Update(override val conversationMessage: ConversationMessage) : MultiselectPart(conversationMessage)

  /**
   * Represents the entire message, for use when we've not yet enabled multiforward.
   */
  data class Message(override val conversationMessage: ConversationMessage) : MultiselectPart(conversationMessage)
}
