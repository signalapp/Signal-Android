package org.thoughtcrime.securesms.contacts.paged

import android.database.Cursor
import org.signal.core.util.requireLong
import org.signal.paging.PagedDataSource
import org.thoughtcrime.securesms.contacts.ContactRepository
import org.thoughtcrime.securesms.contacts.paged.collections.ContactSearchCollection
import org.thoughtcrime.securesms.contacts.paged.collections.ContactSearchIterator
import org.thoughtcrime.securesms.contacts.paged.collections.CursorSearchIterator
import org.thoughtcrime.securesms.contacts.paged.collections.StoriesSearchCollection
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.keyvalue.StorySend
import org.thoughtcrime.securesms.phonenumbers.NumberUtil
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.search.MessageResult
import org.thoughtcrime.securesms.search.MessageSearchResult
import org.thoughtcrime.securesms.search.SearchRepository
import org.thoughtcrime.securesms.search.ThreadSearchResult
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.UsernameUtil
import java.util.concurrent.TimeUnit

/**
 * Manages the querying of contact information based off a configuration.
 */
class ContactSearchPagedDataSource(
  private val contactConfiguration: ContactSearchConfiguration,
  private val contactSearchPagedDataSourceRepository: ContactSearchPagedDataSourceRepository,
  private val arbitraryRepository: ArbitraryRepository? = null,
  private val searchRepository: SearchRepository? = null
) : PagedDataSource<ContactSearchKey, ContactSearchData> {

  companion object {
    private val ACTIVE_STORY_CUTOFF_DURATION = TimeUnit.DAYS.toMillis(1)
  }

  private val latestStorySends: List<StorySend> = contactSearchPagedDataSourceRepository.getLatestStorySends(ACTIVE_STORY_CUTOFF_DURATION)

  private val activeStoryCount = latestStorySends.size

  private var searchCache = SearchCache()
  private var searchSize = -1
  private var displayEmptyState: Boolean = false

  /**
   * When determining when the list is in an empty state, we ignore any arbitrary items, since in general
   * they are always present. If you'd like arbitrary items to appear even when the list is empty, ensure
   * they are added to the empty state configuration.
   */
  override fun size(): Int {
    val (arbitrarySections, nonArbitrarySections) = contactConfiguration.sections.partition {
      it is ContactSearchConfiguration.Section.Arbitrary
    }

    val sizeOfNonArbitrarySections = nonArbitrarySections.sumOf {
      getSectionSize(it, contactConfiguration.query)
    }

    displayEmptyState = sizeOfNonArbitrarySections == 0
    searchSize = if (displayEmptyState) {
      contactConfiguration.emptyStateSections.sumOf {
        getSectionSize(it, contactConfiguration.query)
      }
    } else {
      arbitrarySections.sumOf {
        getSectionSize(it, contactConfiguration.query)
      } + sizeOfNonArbitrarySections
    }

    return searchSize
  }

  override fun load(start: Int, length: Int, cancellationSignal: PagedDataSource.CancellationSignal): MutableList<ContactSearchData> {
    val sections: List<ContactSearchConfiguration.Section> = if (displayEmptyState) {
      contactConfiguration.emptyStateSections
    } else {
      contactConfiguration.sections
    }

    val sizeMap: Map<ContactSearchConfiguration.Section, Int> = sections.associateWith { getSectionSize(it, contactConfiguration.query) }
    val startIndex: Index = findIndex(sizeMap, start)
    val endIndex: Index = findIndex(sizeMap, start + length)

    val indexOfStartSection = sections.indexOf(startIndex.category)
    val indexOfEndSection = sections.indexOf(endIndex.category)

    val results: List<List<ContactSearchData>> = sections.mapIndexed { index, section ->
      if (index in indexOfStartSection..indexOfEndSection) {
        getSectionData(
          section = section,
          query = contactConfiguration.query,
          startIndex = if (index == indexOfStartSection) startIndex.offset else 0,
          endIndex = if (index == indexOfEndSection) endIndex.offset else sizeMap[section] ?: error("Unknown section")
        )
      } else {
        emptyList()
      }
    }

    return results.flatten().toMutableList()
  }

  private fun findIndex(sizeMap: Map<ContactSearchConfiguration.Section, Int>, target: Int): Index {
    var offset = 0
    sizeMap.forEach { (key, size) ->
      if (offset + size > target) {
        return Index(key, target - offset)
      }

      offset += size
    }

    return Index(sizeMap.keys.last(), sizeMap.values.last())
  }

  data class Index(val category: ContactSearchConfiguration.Section, val offset: Int)

  override fun load(key: ContactSearchKey?): ContactSearchData? {
    throw UnsupportedOperationException()
  }

  override fun getKey(data: ContactSearchData): ContactSearchKey {
    return data.contactSearchKey
  }

  private fun getSectionSize(section: ContactSearchConfiguration.Section, query: String?): Int {
    return when (section) {
      is ContactSearchConfiguration.Section.Individuals -> getNonGroupSearchIterator(section, query).getCollectionSize(section, query, null)
      is ContactSearchConfiguration.Section.Groups -> contactSearchPagedDataSourceRepository.getGroupSearchIterator(section, query).getCollectionSize(section, query, this::canSendToGroup)
      is ContactSearchConfiguration.Section.Recents -> getRecentsSearchIterator(section, query).getCollectionSize(section, query, null)
      is ContactSearchConfiguration.Section.Stories -> getStoriesSearchIterator(query).getCollectionSize(section, query, null)
      is ContactSearchConfiguration.Section.Arbitrary -> arbitraryRepository?.getSize(section, query) ?: error("Invalid arbitrary section.")
      is ContactSearchConfiguration.Section.GroupMembers -> getGroupMembersSearchIterator(query).getCollectionSize(section, query, null)
      is ContactSearchConfiguration.Section.Chats -> getThreadData(query, section.isUnreadOnly).getCollectionSize(section, query, null)
      is ContactSearchConfiguration.Section.Messages -> getMessageData(query).getCollectionSize(section, query, null)
      is ContactSearchConfiguration.Section.GroupsWithMembers -> getGroupsWithMembersIterator(query).getCollectionSize(section, query, null)
      is ContactSearchConfiguration.Section.ContactsWithoutThreads -> getContactsWithoutThreadsIterator(query).getCollectionSize(section, query, null)
      is ContactSearchConfiguration.Section.PhoneNumber -> if (isPossiblyPhoneNumber(query)) 1 else 0
      is ContactSearchConfiguration.Section.Username -> if (isPossiblyUsername(query)) 1 else 0
      is ContactSearchConfiguration.Section.Empty -> 1
    }
  }

  private fun <R> ContactSearchIterator<R>.getCollectionSize(section: ContactSearchConfiguration.Section, query: String?, recordsPredicate: ((R) -> Boolean)?): Int {
    val extras: List<ContactSearchData> = when (section) {
      is ContactSearchConfiguration.Section.Stories -> getFilteredGroupStories(section, query)
      else -> emptyList()
    }

    val collection = createResultsCollection(
      section = section,
      records = this,
      recordsPredicate = recordsPredicate,
      extraData = extras,
      recordMapper = { error("Unsupported") }
    )
    return collection.getSize()
  }

  private fun getFilteredGroupStories(section: ContactSearchConfiguration.Section.Stories, query: String?): List<ContactSearchData> {
    return (contactSearchPagedDataSourceRepository.getGroupStories() + section.groupStories)
      .filter { contactSearchPagedDataSourceRepository.recipientNameContainsQuery(it.recipient, query) }
  }

  private fun getSectionData(section: ContactSearchConfiguration.Section, query: String?, startIndex: Int, endIndex: Int): List<ContactSearchData> {
    return when (section) {
      is ContactSearchConfiguration.Section.Groups -> getGroupContactsData(section, query, startIndex, endIndex)
      is ContactSearchConfiguration.Section.Individuals -> getNonGroupContactsData(section, query, startIndex, endIndex)
      is ContactSearchConfiguration.Section.Recents -> getRecentsContactData(section, query, startIndex, endIndex)
      is ContactSearchConfiguration.Section.Stories -> getStoriesContactData(section, query, startIndex, endIndex)
      is ContactSearchConfiguration.Section.Arbitrary -> arbitraryRepository?.getData(section, query, startIndex, endIndex, searchSize) ?: error("Invalid arbitrary section.")
      is ContactSearchConfiguration.Section.GroupMembers -> getGroupMembersContactData(section, query, startIndex, endIndex)
      is ContactSearchConfiguration.Section.Chats -> getThreadContactData(section, query, startIndex, endIndex)
      is ContactSearchConfiguration.Section.Messages -> getMessageContactData(section, query, startIndex, endIndex)
      is ContactSearchConfiguration.Section.GroupsWithMembers -> getGroupsWithMembersContactData(section, query, startIndex, endIndex)
      is ContactSearchConfiguration.Section.ContactsWithoutThreads -> getContactsWithoutThreadsContactData(section, query, startIndex, endIndex)
      is ContactSearchConfiguration.Section.PhoneNumber -> getPossiblePhoneNumber(section, query)
      is ContactSearchConfiguration.Section.Username -> getPossibleUsername(section, query)
      is ContactSearchConfiguration.Section.Empty -> listOf(ContactSearchData.Empty(query))
    }
  }

  private fun isPossiblyPhoneNumber(query: String?): Boolean {
    if (query == null) {
      return false
    }

    return if (FeatureFlags.usernames()) {
      NumberUtil.isVisuallyValidNumberOrEmail(query)
    } else {
      NumberUtil.isValidSmsOrEmail(query)
    }
  }
  private fun isPossiblyUsername(query: String?): Boolean {
    return query != null && FeatureFlags.usernames() && UsernameUtil.isValidUsernameForSearch(query)
  }
  private fun getPossiblePhoneNumber(section: ContactSearchConfiguration.Section.PhoneNumber, query: String?): List<ContactSearchData> {
    return if (isPossiblyPhoneNumber(query)) {
      listOf(ContactSearchData.UnknownRecipient(section.sectionKey, section.newRowMode, query!!))
    } else {
      emptyList()
    }
  }
  private fun getPossibleUsername(section: ContactSearchConfiguration.Section.Username, query: String?): List<ContactSearchData> {
    return if (isPossiblyUsername(query)) {
      listOf(ContactSearchData.UnknownRecipient(section.sectionKey, section.newRowMode, query!!))
    } else {
      emptyList()
    }
  }

  private fun getNonGroupSearchIterator(section: ContactSearchConfiguration.Section.Individuals, query: String?): ContactSearchIterator<Cursor> {
    return when (section.transportType) {
      ContactSearchConfiguration.TransportType.PUSH -> CursorSearchIterator(wrapRecipientCursor(contactSearchPagedDataSourceRepository.querySignalContacts(query, section.includeSelf)))
      ContactSearchConfiguration.TransportType.SMS -> CursorSearchIterator(wrapRecipientCursor(contactSearchPagedDataSourceRepository.queryNonSignalContacts(query)))
      ContactSearchConfiguration.TransportType.ALL -> CursorSearchIterator(wrapRecipientCursor(contactSearchPagedDataSourceRepository.queryNonGroupContacts(query, section.includeSelf)))
    }
  }

  private fun wrapRecipientCursor(cursor: Cursor?): Cursor? {
    return if (cursor == null || cursor.count == 0) {
      null
    } else {
      WrapAroundCursor(cursor, offset = getFirstAlphaRecipientPosition(cursor))
    }
  }

  private fun getFirstAlphaRecipientPosition(cursor: Cursor): Int {
    cursor.moveToPosition(-1)
    while (cursor.moveToNext()) {
      val sortName = cursor.getString(cursor.getColumnIndexOrThrow(ContactRepository.NAME_COLUMN))
      if (sortName.isNotEmpty() && !sortName.first().isDigit()) {
        return cursor.position
      }
    }

    return 0
  }

  private fun getNonGroupHeaderLetterMap(section: ContactSearchConfiguration.Section.Individuals, query: String?): Map<RecipientId, String> {
    return contactSearchPagedDataSourceRepository.querySignalContactLetterHeaders(
      query = query,
      includeSelf = section.includeSelf,
      includePush = when (section.transportType) {
        ContactSearchConfiguration.TransportType.PUSH, ContactSearchConfiguration.TransportType.ALL -> true
        else -> false
      },
      includeSms = when (section.transportType) {
        ContactSearchConfiguration.TransportType.SMS, ContactSearchConfiguration.TransportType.ALL -> true
        else -> false
      }
    )
  }

  private fun getStoriesSearchIterator(query: String?): ContactSearchIterator<Cursor> {
    return CursorSearchIterator(contactSearchPagedDataSourceRepository.getStories(query))
  }

  private fun getGroupsWithMembersIterator(query: String?): ContactSearchIterator<Cursor> {
    return if (query.isNullOrEmpty()) {
      CursorSearchIterator(null)
    } else {
      CursorSearchIterator(contactSearchPagedDataSourceRepository.getGroupsWithMembers(query))
    }
  }

  private fun getContactsWithoutThreadsIterator(query: String?): ContactSearchIterator<Cursor> {
    return if (query.isNullOrEmpty()) {
      CursorSearchIterator(null)
    } else {
      CursorSearchIterator(contactSearchPagedDataSourceRepository.getContactsWithoutThreads(query))
    }
  }

  private fun getRecentsSearchIterator(section: ContactSearchConfiguration.Section.Recents, query: String?): ContactSearchIterator<Cursor> {
    if (!query.isNullOrEmpty()) {
      throw IllegalArgumentException("Searching Recents is not supported")
    }

    return CursorSearchIterator(contactSearchPagedDataSourceRepository.getRecents(section))
  }

  private fun getGroupMembersSearchIterator(query: String?): ContactSearchIterator<Cursor> {
    return CursorSearchIterator(contactSearchPagedDataSourceRepository.queryGroupMemberContacts(query))
  }

  private fun <R> readContactData(
    records: ContactSearchIterator<R>,
    recordsPredicate: ((R) -> Boolean)?,
    section: ContactSearchConfiguration.Section,
    startIndex: Int,
    endIndex: Int,
    recordMapper: (R) -> ContactSearchData,
    extraData: List<ContactSearchData> = emptyList()
  ): List<ContactSearchData> {
    val results = mutableListOf<ContactSearchData>()

    val collection = createResultsCollection(section, records, recordsPredicate, extraData, recordMapper)
    results.addAll(collection.getSublist(startIndex, endIndex))

    return results
  }

  private fun getStoriesContactData(section: ContactSearchConfiguration.Section.Stories, query: String?, startIndex: Int, endIndex: Int): List<ContactSearchData> {
    return getStoriesSearchIterator(query).use { records ->
      readContactData(
        records = records,
        null,
        section = section,
        startIndex = startIndex,
        endIndex = endIndex,
        recordMapper = {
          val recipient = contactSearchPagedDataSourceRepository.getRecipientFromDistributionListCursor(it)
          val count = contactSearchPagedDataSourceRepository.getDistributionListMembershipCount(recipient)
          val privacyMode = contactSearchPagedDataSourceRepository.getPrivacyModeFromDistributionListCursor(it)
          ContactSearchData.Story(recipient, count, privacyMode)
        },
        extraData = getFilteredGroupStories(section, query)
      )
    }
  }

  private fun getGroupsWithMembersContactData(section: ContactSearchConfiguration.Section.GroupsWithMembers, query: String?, startIndex: Int, endIndex: Int): List<ContactSearchData> {
    return getGroupsWithMembersIterator(query).use { records ->
      readContactData(
        records = records,
        recordsPredicate = null,
        section = section,
        startIndex = startIndex,
        endIndex = endIndex,
        recordMapper = { cursor ->
          val record = GroupTable.Reader(cursor).getCurrent()
          ContactSearchData.GroupWithMembers(query!!, record!!, cursor.requireLong(GroupTable.THREAD_DATE))
        }
      )
    }
  }

  private fun getRecentsContactData(section: ContactSearchConfiguration.Section.Recents, query: String?, startIndex: Int, endIndex: Int): List<ContactSearchData> {
    return getRecentsSearchIterator(section, query).use { records ->
      readContactData(
        records = records,
        recordsPredicate = null,
        section = section,
        startIndex = startIndex,
        endIndex = endIndex,
        recordMapper = {
          ContactSearchData.KnownRecipient(section.sectionKey, contactSearchPagedDataSourceRepository.getRecipientFromThreadCursor(it))
        }
      )
    }
  }

  private fun getContactsWithoutThreadsContactData(section: ContactSearchConfiguration.Section.ContactsWithoutThreads, query: String?, startIndex: Int, endIndex: Int): List<ContactSearchData> {
    return getContactsWithoutThreadsIterator(query).use { records ->
      readContactData(
        records = records,
        recordsPredicate = null,
        section = section,
        startIndex = startIndex,
        endIndex = endIndex,
        recordMapper = {
          ContactSearchData.KnownRecipient(section.sectionKey, contactSearchPagedDataSourceRepository.getRecipientFromRecipientCursor(it))
        }
      )
    }
  }

  private fun getNonGroupContactsData(section: ContactSearchConfiguration.Section.Individuals, query: String?, startIndex: Int, endIndex: Int): List<ContactSearchData> {
    val headerMap: Map<RecipientId, String> = if (section.includeLetterHeaders) {
      getNonGroupHeaderLetterMap(section, query)
    } else {
      emptyMap()
    }

    return getNonGroupSearchIterator(section, query).use { records ->
      readContactData(
        records = records,
        recordsPredicate = null,
        section = section,
        startIndex = startIndex,
        endIndex = endIndex,
        recordMapper = {
          val recipient = contactSearchPagedDataSourceRepository.getRecipientFromSearchCursor(it)
          ContactSearchData.KnownRecipient(section.sectionKey, recipient, headerLetter = headerMap[recipient.id])
        }
      )
    }
  }

  private fun getGroupContactsData(section: ContactSearchConfiguration.Section.Groups, query: String?, startIndex: Int, endIndex: Int): List<ContactSearchData> {
    return contactSearchPagedDataSourceRepository.getGroupSearchIterator(section, query).use { records ->
      readContactData(
        records = records,
        recordsPredicate = this::canSendToGroup,
        section = section,
        startIndex = startIndex,
        endIndex = endIndex,
        recordMapper = {
          if (section.returnAsGroupStories) {
            ContactSearchData.Story(contactSearchPagedDataSourceRepository.getRecipientFromGroupRecord(it), 0, DistributionListPrivacyMode.ALL)
          } else {
            ContactSearchData.KnownRecipient(section.sectionKey, contactSearchPagedDataSourceRepository.getRecipientFromGroupRecord(it), shortSummary = section.shortSummary)
          }
        }
      )
    }
  }

  private fun canSendToGroup(groupRecord: GroupRecord?): Boolean {
    if (groupRecord == null) return false

    return if (groupRecord.isAnnouncementGroup) {
      groupRecord.isAdmin(Recipient.self())
    } else {
      groupRecord.isActive
    }
  }

  private fun getGroupMembersContactData(section: ContactSearchConfiguration.Section.GroupMembers, query: String?, startIndex: Int, endIndex: Int): List<ContactSearchData> {
    return getGroupMembersSearchIterator(query).use { records ->
      readContactData(
        records = records,
        recordsPredicate = null,
        section = section,
        startIndex = startIndex,
        endIndex = endIndex,
        recordMapper = {
          val recipient = contactSearchPagedDataSourceRepository.getRecipientFromSearchCursor(it)
          val groupsInCommon = contactSearchPagedDataSourceRepository.getGroupsInCommon(recipient)
          ContactSearchData.KnownRecipient(section.sectionKey, recipient, groupsInCommon = groupsInCommon)
        }
      )
    }
  }

  private fun getMessageData(query: String?): ContactSearchIterator<MessageResult> {
    check(searchRepository != null)

    if (searchCache.messageSearchResult == null && query != null) {
      searchCache = searchCache.copy(messageSearchResult = searchRepository.queryMessagesSync(query))
    }

    return if (query != null) {
      ListSearchIterator(searchCache.messageSearchResult!!.results)
    } else {
      ListSearchIterator(emptyList())
    }
  }

  private fun getMessageContactData(section: ContactSearchConfiguration.Section.Messages, query: String?, startIndex: Int, endIndex: Int): List<ContactSearchData> {
    return getMessageData(query).use { records ->
      readContactData(
        records = records,
        recordsPredicate = null,
        section = section,
        startIndex = startIndex,
        endIndex = endIndex,
        recordMapper = {
          ContactSearchData.Message(query ?: "", it)
        }
      )
    }
  }

  private fun getThreadData(query: String?, unreadOnly: Boolean): ContactSearchIterator<ThreadRecord> {
    check(searchRepository != null)
    if (searchCache.threadSearchResult == null && query != null) {
      searchCache = searchCache.copy(threadSearchResult = searchRepository.queryThreadsSync(query, unreadOnly))
    }

    return if (query != null) {
      ListSearchIterator(searchCache.threadSearchResult!!.results)
    } else {
      ListSearchIterator(emptyList())
    }
  }

  private fun getThreadContactData(section: ContactSearchConfiguration.Section.Chats, query: String?, startIndex: Int, endIndex: Int): List<ContactSearchData> {
    return getThreadData(query, section.isUnreadOnly).use { records ->
      readContactData(
        records = records,
        recordsPredicate = null,
        section = section,
        startIndex = startIndex,
        endIndex = endIndex,
        recordMapper = {
          ContactSearchData.Thread(query ?: "", it)
        }
      )
    }
  }

  private fun <R> createResultsCollection(
    section: ContactSearchConfiguration.Section,
    records: ContactSearchIterator<R>,
    recordsPredicate: ((R) -> Boolean)?,
    extraData: List<ContactSearchData>,
    recordMapper: (R) -> ContactSearchData
  ): ContactSearchCollection<R> {
    return when (section) {
      is ContactSearchConfiguration.Section.Stories -> StoriesSearchCollection(section, records, extraData, recordMapper, activeStoryCount, StoryComparator(latestStorySends))
      else -> ContactSearchCollection(section, records, recordsPredicate, recordMapper, 0)
    }
  }

  /**
   * Caches search results of particularly intensive queries.
   */
  private data class SearchCache(
    val messageSearchResult: MessageSearchResult? = null,
    val threadSearchResult: ThreadSearchResult? = null
  )

  /**
   * StoryComparator
   */
  private class StoryComparator(private val latestStorySends: List<StorySend>) : Comparator<ContactSearchData.Story> {
    override fun compare(lhs: ContactSearchData.Story, rhs: ContactSearchData.Story): Int {
      val lhsActiveRank = latestStorySends.indexOfFirst { it.identifier.matches(lhs.recipient) }.let { if (it == -1) Int.MAX_VALUE else it }
      val rhsActiveRank = latestStorySends.indexOfFirst { it.identifier.matches(rhs.recipient) }.let { if (it == -1) Int.MAX_VALUE else it }

      return when {
        lhs.recipient.isMyStory && rhs.recipient.isMyStory -> 0
        lhs.recipient.isMyStory -> -1
        rhs.recipient.isMyStory -> 1
        lhsActiveRank < rhsActiveRank -> -1
        lhsActiveRank > rhsActiveRank -> 1
        else -> 0
      }
    }
  }

  private class ListSearchIterator<T>(val list: List<T>) : ContactSearchIterator<T> {

    private var position = -1

    override fun moveToPosition(n: Int) {
      position = n
    }

    override fun getCount(): Int = list.size

    override fun hasNext(): Boolean = position < list.lastIndex

    override fun next(): T = list[++position]

    override fun close() = Unit
  }
}
