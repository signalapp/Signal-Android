package org.thoughtcrime.securesms.conversation.v2.messages

import org.thoughtcrime.securesms.database.model.MessageId

interface VisibleMessageViewDelegate {

    fun playVoiceMessageAtIndexIfPossible(indexInAdapter: Int)

    fun scrollToMessageIfPossible(timestamp: Long)

    fun onReactionClicked(emoji: String, messageId: MessageId, userWasSender: Boolean)

    fun onReactionLongClicked(messageId: MessageId)

}