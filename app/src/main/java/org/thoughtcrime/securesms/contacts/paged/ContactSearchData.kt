package org.thoughtcrime.securesms.contacts.paged

import android.os.Bundle
import androidx.annotation.VisibleForTesting
import org.thoughtcrime.securesms.contacts.HeaderAction
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.search.MessageResult

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
  ) : ContactSearchData(ContactSearchKey.RecipientSearchKey(recipient.id, true))

  /**
   * A row displaying a known recipient.
   */
  data class KnownRecipient(
    val sectionKey: ContactSearchConfiguration.SectionKey,
    val recipient: Recipient,
    val shortSummary: Boolean = false,
    val headerLetter: String? = null,
    val groupsInCommon: GroupsInCommon = GroupsInCommon(0, listOf())
  ) : ContactSearchData(ContactSearchKey.RecipientSearchKey(recipient.id, false))

  /**
   * A row displaying a message
   */
  data class Message(
    val query: String,
    val messageResult: MessageResult
  ) : ContactSearchData(ContactSearchKey.Message(messageResult.messageId))

  /**
   * A row displaying a thread
   */
  data class Thread(
    val query: String,
    val threadRecord: ThreadRecord
  ) : ContactSearchData(ContactSearchKey.Thread(threadRecord.threadId))

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
   * A row representing arbitrary data tied to a specific section.
   */
  class Arbitrary(val type: String, val data: Bundle? = null) : ContactSearchData(ContactSearchKey.Arbitrary(type))

  /**
   * Empty state, only included if no other rows exist.
   */
  data class Empty(val query: String?) : ContactSearchData(ContactSearchKey.Empty)

  /**
   * A row which contains an integer, for testing.
   */
  @VisibleForTesting
  class TestRow(val value: Int) : ContactSearchData(ContactSearchKey.Expand(ContactSearchConfiguration.SectionKey.RECENTS))
}
