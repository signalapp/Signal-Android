package org.thoughtcrime.securesms.loki

import org.thoughtcrime.securesms.database.model.MessageRecord

interface ConversationUpdateItemViewDelegate {
  fun updateItemButtonPressed(message: MessageRecord)
}