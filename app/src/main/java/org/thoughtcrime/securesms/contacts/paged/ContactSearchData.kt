package org.thoughtcrime.securesms.contacts.paged

import org.thoughtcrime.securesms.contacts.HeaderAction
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Represents the data backed by a ContactSearchKey
 */
sealed class ContactSearchData(val contactSearchKey: ContactSearchKey) {
  /**
   * A row displaying a story.
   *
   * Note that if the recipient is a group, it's participant list size is used instead of viewerCount.
   */
  data class Story(val recipient: Recipient, val viewerCount: Int) : ContactSearchData(ContactSearchKey.Story(recipient.id))

  /**
   * A row displaying a known recipient.
   */
  data class KnownRecipient(val recipient: Recipient) : ContactSearchData(ContactSearchKey.KnownRecipient(recipient.id))

  /**
   * A row containing a title for a given section
   */
  class Header(
    val sectionKey: ContactSearchConfiguration.SectionKey,
    val action: HeaderAction?
  ) : ContactSearchData(ContactSearchKey.Header(sectionKey))

  /**
   * A row which the user can click to view all entries for a given section.
   */
  class Expand(val sectionKey: ContactSearchConfiguration.SectionKey) : ContactSearchData(ContactSearchKey.Expand(sectionKey))
}
