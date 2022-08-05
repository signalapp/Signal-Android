package org.thoughtcrime.securesms.conversation.drafts

import org.thoughtcrime.securesms.database.DraftDatabase
import org.thoughtcrime.securesms.database.DraftDatabase.Drafts
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
  val textDraft: DraftDatabase.Draft? = null,
  val mentionsDraft: DraftDatabase.Draft? = null,
  val quoteDraft: DraftDatabase.Draft? = null,
  val locationDraft: DraftDatabase.Draft? = null,
  val voiceNoteDraft: DraftDatabase.Draft? = null,
) {

  fun copyAndClearDrafts(threadId: Long = this.threadId): DraftState {
    return DraftState(recipientId = recipientId, threadId = threadId, distributionType = distributionType)
  }

  fun toDrafts(): Drafts {
    return Drafts().apply {
      addIfNotNull(textDraft)
      addIfNotNull(mentionsDraft)
      addIfNotNull(quoteDraft)
      addIfNotNull(locationDraft)
      addIfNotNull(voiceNoteDraft)
    }
  }

  fun copyAndSetDrafts(threadId: Long, drafts: Drafts): DraftState {
    return copy(
      threadId = threadId,
      textDraft = drafts.getDraftOfType(DraftDatabase.Draft.TEXT),
      mentionsDraft = drafts.getDraftOfType(DraftDatabase.Draft.MENTION),
      quoteDraft = drafts.getDraftOfType(DraftDatabase.Draft.QUOTE),
      locationDraft = drafts.getDraftOfType(DraftDatabase.Draft.LOCATION),
      voiceNoteDraft = drafts.getDraftOfType(DraftDatabase.Draft.VOICE_NOTE),
    )
  }
}
