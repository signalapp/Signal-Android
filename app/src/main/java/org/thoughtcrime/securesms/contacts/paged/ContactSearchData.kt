package org.thoughtcrime.securesms.contacts.paged

import androidx.annotation.VisibleForTesting
import org.thoughtcrime.securesms.contacts.HeaderAction
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
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
  data class Story(
    val recipient: Recipient,
    val count: Int,
    val privacyMode: DistributionListPrivacyMode
  ) : ContactSearchData(ContactSearchKey.RecipientSearchKey.Story(recipient.id))

  /**
   * A row displaying a known recipient.
   */
  data class KnownRecipient(
    val recipient: Recipient,
    val shortSummary: Boolean = false,
    val headerLetter: String? = null
  ) : ContactSearchData(ContactSearchKey.RecipientSearchKey.KnownRecipient(recipient.id))

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

  /**
   * A row which contains an integer, for testing.
   */
  @VisibleForTesting
  class TestRow(val value: Int) : ContactSearchData(ContactSearchKey.Expand(ContactSearchConfiguration.SectionKey.RECENTS))
}
