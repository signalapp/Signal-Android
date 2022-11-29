package org.thoughtcrime.securesms.contacts.paged

import android.content.Context
import android.database.Cursor
import org.signal.core.util.CursorUtil
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.ContactRepository
import org.thoughtcrime.securesms.contacts.paged.collections.ContactSearchIterator
import org.thoughtcrime.securesms.database.DistributionListTables
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.GroupTable.GroupRecord
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.StorySend
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Database boundary interface which allows us to safely unit test the data source without
 * having to deal with database access.
 */
open class ContactSearchPagedDataSourceRepository(
  private val context: Context
) {

  private val contactRepository = ContactRepository(context, context.getString(R.string.note_to_self))

  open fun getLatestStorySends(activeStoryCutoffDuration: Long): List<StorySend> {
    return SignalStore.storyValues()
      .getLatestActiveStorySendTimestamps(System.currentTimeMillis() - activeStoryCutoffDuration)
  }

  open fun querySignalContacts(query: String?, includeSelf: Boolean): Cursor? {
    return contactRepository.querySignalContacts(query ?: "", includeSelf)
  }

  open fun querySignalContactLetterHeaders(query: String?, includeSelf: Boolean): Map<RecipientId, String> {
    return SignalDatabase.recipients.querySignalContactLetterHeaders(query ?: "", includeSelf)
  }

  open fun queryNonSignalContacts(query: String?): Cursor? {
    return contactRepository.queryNonSignalContacts(query ?: "")
  }

  open fun queryNonGroupContacts(query: String?, includeSelf: Boolean): Cursor? {
    return contactRepository.queryNonGroupContacts(query ?: "", includeSelf)
  }

  open fun getGroupSearchIterator(
    section: ContactSearchConfiguration.Section.Groups,
    query: String?
  ): ContactSearchIterator<GroupRecord> {
    return SignalDatabase.groups.queryGroups(
      GroupTable.GroupQuery.Builder()
        .withSearchQuery(query)
        .withInactiveGroups(section.includeInactive)
        .withMmsGroups(section.includeMms)
        .withV1Groups(section.includeV1)
        .withSortOrder(section.sortOrder)
        .build()
    )
  }

  open fun getRecents(section: ContactSearchConfiguration.Section.Recents): Cursor? {
    return SignalDatabase.threads.getRecentConversationList(
      section.limit,
      section.includeInactiveGroups,
      section.mode == ContactSearchConfiguration.Section.Recents.Mode.INDIVIDUALS,
      section.mode == ContactSearchConfiguration.Section.Recents.Mode.GROUPS,
      !section.includeGroupsV1,
      !section.includeSms,
      !section.includeSelf
    )
  }

  open fun getStories(query: String?): Cursor? {
    return SignalDatabase.distributionLists.getAllListsForContactSelectionUiCursor(query, myStoryContainsQuery(query ?: ""))
  }

  open fun getRecipientFromDistributionListCursor(cursor: Cursor): Recipient {
    return Recipient.resolved(RecipientId.from(CursorUtil.requireLong(cursor, DistributionListTables.RECIPIENT_ID)))
  }

  open fun getPrivacyModeFromDistributionListCursor(cursor: Cursor): DistributionListPrivacyMode {
    return DistributionListPrivacyMode.deserialize(CursorUtil.requireLong(cursor, DistributionListTables.PRIVACY_MODE))
  }

  open fun getRecipientFromThreadCursor(cursor: Cursor): Recipient {
    return Recipient.resolved(RecipientId.from(CursorUtil.requireLong(cursor, ThreadTable.RECIPIENT_ID)))
  }

  open fun getRecipientFromRecipientCursor(cursor: Cursor): Recipient {
    return Recipient.resolved(RecipientId.from(CursorUtil.requireLong(cursor, ContactRepository.ID_COLUMN)))
  }

  open fun getRecipientFromGroupRecord(groupRecord: GroupRecord): Recipient {
    return Recipient.resolved(groupRecord.recipientId)
  }

  open fun getDistributionListMembershipCount(recipient: Recipient): Int {
    return SignalDatabase.distributionLists.getMemberCount(recipient.requireDistributionListId())
  }

  open fun getGroupStories(): Set<ContactSearchData.Story> {
    return SignalDatabase.groups.groupsToDisplayAsStories.map {
      val recipient = Recipient.resolved(SignalDatabase.recipients.getOrInsertFromGroupId(it))
      ContactSearchData.Story(recipient, recipient.participantIds.size, DistributionListPrivacyMode.ALL)
    }.toSet()
  }

  open fun recipientNameContainsQuery(recipient: Recipient, query: String?): Boolean {
    return query.isNullOrBlank() || recipient.getDisplayName(context).contains(query, ignoreCase = true)
  }

  open fun myStoryContainsQuery(query: String): Boolean {
    if (query.isEmpty()) {
      return true
    }

    val myStory = context.getString(R.string.Recipient_my_story)
    return myStory.contains(query, ignoreCase = true)
  }
}
