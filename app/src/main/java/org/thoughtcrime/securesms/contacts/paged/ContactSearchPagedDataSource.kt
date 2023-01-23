package org.thoughtcrime.securesms.contacts.paged

import android.database.Cursor
import org.signal.paging.PagedDataSource
import org.thoughtcrime.securesms.contacts.paged.collections.ContactSearchCollection
import org.thoughtcrime.securesms.contacts.paged.collections.ContactSearchIterator
import org.thoughtcrime.securesms.contacts.paged.collections.CursorSearchIterator
import org.thoughtcrime.securesms.contacts.paged.collections.StoriesSearchCollection
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.StorySend
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.search.MessageResult
import org.thoughtcrime.securesms.search.MessageSearchResult
import org.thoughtcrime.securesms.search.SearchRepository
import org.thoughtcrime.securesms.search.ThreadSearchResult
import java.util.concurrent.TimeUnit

/**
 * Manages the querying of contact information based off a configuration.
 */
class ContactSearchPagedDataSource(
  private val contactConfiguration: ContactSearchConfiguration,
  private val contactSearchPagedDataSourceRepository: ContactSearchPagedDataSourceRepository = ContactSearchPagedDataSourceRepository(ApplicationDependencies.getApplication()),
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

  override fun size(): Int {
    searchSize = contactConfiguration.sections.sumOf {
      getSectionSize(it, contactConfiguration.query)
    }

    return if (searchSize == 0 && contactConfiguration.hasEmptyState) {
      1
    } else {
      searchSize
    }
  }

  override fun load(start: Int, length: Int, cancellationSignal: PagedDataSource.CancellationSignal): MutableList<ContactSearchData> {
    if (searchSize == 0 && contactConfiguration.hasEmptyState) {
      return mutableListOf(ContactSearchData.Empty(contactConfiguration.query))
    }

    val sizeMap: Map<ContactSearchConfiguration.Section, Int> = contactConfiguration.sections.associateWith { getSectionSize(it, contactConfiguration.query) }
    val startIndex: Index = findIndex(sizeMap, start)
    val endIndex: Index = findIndex(sizeMap, start + length)

    val indexOfStartSection = contactConfiguration.sections.indexOf(startIndex.category)
    val indexOfEndSection = contactConfiguration.sections.indexOf(endIndex.category)

    val results: List<List<ContactSearchData>> = contactConfiguration.sections.mapIndexed { index, section ->
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
    }
  }

  private fun getNonGroupSearchIterator(section: ContactSearchConfiguration.Section.Individuals, query: String?): ContactSearchIterator<Cursor> {
    return when (section.transportType) {
      ContactSearchConfiguration.TransportType.PUSH -> CursorSearchIterator(contactSearchPagedDataSourceRepository.querySignalContacts(query, section.includeSelf))
      ContactSearchConfiguration.TransportType.SMS -> CursorSearchIterator(contactSearchPagedDataSourceRepository.queryNonSignalContacts(query))
      ContactSearchConfiguration.TransportType.ALL -> CursorSearchIterator(contactSearchPagedDataSourceRepository.queryNonGroupContacts(query, section.includeSelf))
    }
  }

  private fun getNonGroupHeaderLetterMap(section: ContactSearchConfiguration.Section.Individuals, query: String?): Map<RecipientId, String> {
    return when (section.transportType) {
      ContactSearchConfiguration.TransportType.PUSH -> contactSearchPagedDataSourceRepository.querySignalContactLetterHeaders(query, section.includeSelf)
      else -> error("This has only been implemented for push recipients.")
    }
  }

  private fun getStoriesSearchIterator(query: String?): ContactSearchIterator<Cursor> {
    return CursorSearchIterator(contactSearchPagedDataSourceRepository.getStories(query))
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
          val recipient = contactSearchPagedDataSourceRepository.getRecipientFromRecipientCursor(it)
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
          val recipient = contactSearchPagedDataSourceRepository.getRecipientFromRecipientCursor(it)
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
