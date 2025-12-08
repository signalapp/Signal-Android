package org.thoughtcrime.securesms.events

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Allow system to identify a call participant by their device demux id and their
 * recipient id.
 */
@Parcelize
data class CallParticipantId(@JvmField val demuxId: Long, val recipientId: RecipientId) : Parcelable {
  constructor(recipient: Recipient) : this(DEFAULT_ID, recipient.id)

  companion object {
    const val DEFAULT_ID: Long = -1
  }
}
