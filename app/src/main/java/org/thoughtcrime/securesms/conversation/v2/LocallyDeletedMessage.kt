/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.database.model.MessageRecord

/**
 * The minimal data needed to send a remote delete for an outgoing message that has already been deleted locally.
 * Contains no message content.
 */
data class LocallyDeletedMessage(
  val messageId: Long,
  val threadRecipientId: RecipientId,
  val dateSent: Long,
  val dateReceived: Long,
  val type: Long,
  val expiresIn: Long,
  val expireStarted: Long,
  val expireTimerVersion: Int
) {
  companion object {
    fun from(record: MessageRecord): LocallyDeletedMessage {
      return LocallyDeletedMessage(
        messageId = record.id,
        threadRecipientId = record.toRecipient.id,
        dateSent = record.dateSent,
        dateReceived = record.dateReceived,
        type = record.type,
        expiresIn = record.expiresIn,
        expireStarted = record.expireStarted,
        expireTimerVersion = record.expireTimerVersion
      )
    }
  }
}
