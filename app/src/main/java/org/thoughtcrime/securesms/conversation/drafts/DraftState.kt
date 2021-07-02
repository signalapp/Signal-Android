package org.thoughtcrime.securesms.conversation.drafts

import org.thoughtcrime.securesms.database.DraftDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * State object responsible for holding Voice Note draft state. The intention is to allow
 * other pieces of draft state to be held here as well in the future, and to serve as a
 * management pattern going forward for drafts.
 */
data class DraftState(
  val recipientId: RecipientId = Recipient.UNKNOWN.id,
  val voiceNoteDraft: DraftDatabase.Draft? = null
)
