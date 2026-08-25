/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.paged

import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration.SectionKey
import org.thoughtcrime.securesms.database.model.ThreadWithRecipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.search.MessageResult

/**
 * The results of the search sections that [ContactSearchViewModel] queries in parallel, folded in
 * as each one lands so that finished sections can render while the rest are still running.
 *
 * A section listed in [pending] has not finished yet, and renders as a loading placeholder rather
 * than as an empty section.
 */
data class ContactSearchSectionResults(
  val pending: Set<SectionKey> = emptySet(),
  val threads: List<ThreadWithRecipient>? = null,
  val messages: List<MessageResult>? = null,
  val groupsWithMembers: List<GroupWithMembersRecord>? = null,
  val contactsWithoutThreads: List<RecipientId>? = null
) {
  fun withSection(result: ContactSearchSectionResult): ContactSearchSectionResults {
    val remaining = pending - result.sectionKey

    return when (result) {
      is ContactSearchSectionResult.Chats -> copy(pending = remaining, threads = result.threads)
      is ContactSearchSectionResult.Messages -> copy(pending = remaining, messages = result.messages)
      is ContactSearchSectionResult.GroupsWithMembers -> copy(pending = remaining, groupsWithMembers = result.groups)
      is ContactSearchSectionResult.ContactsWithoutThreads -> copy(pending = remaining, contactsWithoutThreads = result.recipientIds)
    }
  }
}

/**
 * The result of querying a single section. Rows are held in whatever form is cheapest to produce in
 * bulk -- ids where resolving the full model costs a query per row -- and are turned into
 * [ContactSearchData] lazily, as they scroll into view.
 */
sealed interface ContactSearchSectionResult {
  val sectionKey: SectionKey

  data class Chats(val threads: List<ThreadWithRecipient>) : ContactSearchSectionResult {
    override val sectionKey: SectionKey = SectionKey.CHATS
  }

  data class Messages(val messages: List<MessageResult>) : ContactSearchSectionResult {
    override val sectionKey: SectionKey = SectionKey.MESSAGES
  }

  data class GroupsWithMembers(val groups: List<GroupWithMembersRecord>) : ContactSearchSectionResult {
    override val sectionKey: SectionKey = SectionKey.GROUPS_WITH_MEMBERS
  }

  data class ContactsWithoutThreads(val recipientIds: List<RecipientId>) : ContactSearchSectionResult {
    override val sectionKey: SectionKey = SectionKey.CONTACTS_WITHOUT_THREADS
  }
}
