package org.thoughtcrime.securesms.contacts.paged

import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * A Contact Search Key that is backed by a recipient, along with information about whether it is a story.
 */
interface RecipientSearchKey {
  val recipientId: RecipientId
  val isStory: Boolean
}
