package org.thoughtcrime.securesms.conversation.drafts

import org.thoughtcrime.securesms.database.DraftTable
import org.thoughtcrime.securesms.database.DraftTable.Drafts
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * State object responsible for holding Voice Note draft state. The intention is to allow
 * other pieces of draft state to be held here as well in the future, and to serve as a
 * management pattern going forward for drafts.
 */
data class DraftState(
  val recipientId: RecipientId = RecipientId.UNKNOWN,
  val threadId: Long = -1,
  val distributionType: Int = 0,
  val textDraft: DraftTable.Draft? = null,
  val bodyRangesDraft: DraftTable.Draft? = null,
  val quoteDraft: DraftTable.Draft? = null,
  val locationDraft: DraftTable.Draft? = null,
  val voiceNoteDraft: DraftTable.Draft? = null,
) {

  fun copyAndClearDrafts(threadId: Long = this.threadId): DraftState {
    return DraftState(recipientId = recipientId, threadId = threadId, distributionType = distributionType)
  }

  fun toDrafts(): Drafts {
    return Drafts().apply {
      addIfNotNull(textDraft)
      addIfNotNull(bodyRangesDraft)
      addIfNotNull(quoteDraft)
      addIfNotNull(locationDraft)
      addIfNotNull(voiceNoteDraft)
    }
  }

  fun copyAndSetDrafts(threadId: Long, drafts: Drafts): DraftState {
    return copy(
      threadId = threadId,
      textDraft = drafts.getDraftOfType(DraftTable.Draft.TEXT),
      bodyRangesDraft = drafts.getDraftOfType(DraftTable.Draft.BODY_RANGES),
      quoteDraft = drafts.getDraftOfType(DraftTable.Draft.QUOTE),
      locationDraft = drafts.getDraftOfType(DraftTable.Draft.LOCATION),
      voiceNoteDraft = drafts.getDraftOfType(DraftTable.Draft.VOICE_NOTE),
    )
  }
}
