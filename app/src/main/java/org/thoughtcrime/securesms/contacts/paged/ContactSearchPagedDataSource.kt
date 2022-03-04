package org.thoughtcrime.securesms.contacts.paged

import android.database.Cursor
import org.signal.paging.PagedDataSource
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import kotlin.math.min

/**
 * Manages the querying of contact information based off a configuration.
 */
class ContactSearchPagedDataSource(
  private val contactConfiguration: ContactSearchConfiguration,
  private val contactSearchPagedDataSourceRepository: ContactSearchPagedDataSourceRepository = ContactSearchPagedDataSourceRepository(ApplicationDependencies.getApplication())
) : PagedDataSource<ContactSearchKey, ContactSearchData> {

  override fun size(): Int {
    return contactConfiguration.sections.sumBy {
      getSectionSize(it, contactConfiguration.query)
    }
  }

  override fun load(start: Int, length: Int, cancellationSignal: PagedDataSource.CancellationSignal): MutableList<ContactSearchData> {
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
    val cursor: Cursor = when (section) {
      is ContactSearchConfiguration.Section.Individuals -> getNonGroupContactsCursor(section, query)
      is ContactSearchConfiguration.Section.Groups -> contactSearchPagedDataSourceRepository.getGroupContacts(section, query)
      is ContactSearchConfiguration.Section.Recents -> getRecentsCursor(section, query)
      is ContactSearchConfiguration.Section.Stories -> getStoriesCursor(query)
    }!!

    val extras: List<ContactSearchData> = when (section) {
      is ContactSearchConfiguration.Section.Stories -> getFilteredGroupStories(section, query)
      else -> emptyList()
    }

    val collection = ResultsCollection(
      section = section,
      cursor = cursor,
      extraData = extras,
      cursorMapper = { error("Unsupported") }
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
    }
  }

  private fun getNonGroupContactsCursor(section: ContactSearchConfiguration.Section.Individuals, query: String?): Cursor? {
    return when (section.transportType) {
      ContactSearchConfiguration.TransportType.PUSH -> contactSearchPagedDataSourceRepository.querySignalContacts(query, section.includeSelf)
      ContactSearchConfiguration.TransportType.SMS -> contactSearchPagedDataSourceRepository.queryNonSignalContacts(query)
      ContactSearchConfiguration.TransportType.ALL -> contactSearchPagedDataSourceRepository.queryNonGroupContacts(query, section.includeSelf)
    }
  }

  private fun getStoriesCursor(query: String?): Cursor? {
    return contactSearchPagedDataSourceRepository.getStories(query)
  }

  private fun getRecentsCursor(section: ContactSearchConfiguration.Section.Recents, query: String?): Cursor? {
    if (!query.isNullOrEmpty()) {
      throw IllegalArgumentException("Searching Recents is not supported")
    }

    return contactSearchPagedDataSourceRepository.getRecents(section)
  }

  private fun readContactDataFromCursor(
    cursor: Cursor,
    section: ContactSearchConfiguration.Section,
    startIndex: Int,
    endIndex: Int,
    cursorRowToData: (Cursor) -> ContactSearchData,
    extraData: List<ContactSearchData> = emptyList()
  ): List<ContactSearchData> {
    val results = mutableListOf<ContactSearchData>()

    val collection = ResultsCollection(section, cursor, extraData, cursorRowToData)
    results.addAll(collection.getSublist(startIndex, endIndex))

    return results
  }

  private fun getStoriesContactData(section: ContactSearchConfiguration.Section.Stories, query: String?, startIndex: Int, endIndex: Int): List<ContactSearchData> {
    return getStoriesCursor(query)?.use { cursor ->
      readContactDataFromCursor(
        cursor = cursor,
        section = section,
        startIndex = startIndex,
        endIndex = endIndex,
        cursorRowToData = {
          val recipient = contactSearchPagedDataSourceRepository.getRecipientFromDistributionListCursor(it)
          ContactSearchData.Story(recipient, contactSearchPagedDataSourceRepository.getDistributionListMembershipCount(recipient))
        },
        extraData = getFilteredGroupStories(section, query)
      )
    } ?: emptyList()
  }

  private fun getRecentsContactData(section: ContactSearchConfiguration.Section.Recents, query: String?, startIndex: Int, endIndex: Int): List<ContactSearchData> {
    return getRecentsCursor(section, query)?.use { cursor ->
      readContactDataFromCursor(
        cursor = cursor,
        section = section,
        startIndex = startIndex,
        endIndex = endIndex,
        cursorRowToData = {
          ContactSearchData.KnownRecipient(contactSearchPagedDataSourceRepository.getRecipientFromThreadCursor(cursor))
        }
      )
    } ?: emptyList()
  }

  private fun getNonGroupContactsData(section: ContactSearchConfiguration.Section.Individuals, query: String?, startIndex: Int, endIndex: Int): List<ContactSearchData> {
    return getNonGroupContactsCursor(section, query)?.use { cursor ->
      readContactDataFromCursor(
        cursor = cursor,
        section = section,
        startIndex = startIndex,
        endIndex = endIndex,
        cursorRowToData = {
          ContactSearchData.KnownRecipient(contactSearchPagedDataSourceRepository.getRecipientFromRecipientCursor(cursor))
        }
      )
    } ?: emptyList()
  }

  private fun getGroupContactsData(section: ContactSearchConfiguration.Section.Groups, query: String?, startIndex: Int, endIndex: Int): List<ContactSearchData> {
    return contactSearchPagedDataSourceRepository.getGroupContacts(section, query)?.use { cursor ->
      readContactDataFromCursor(
        cursor = cursor,
        section = section,
        startIndex = startIndex,
        endIndex = endIndex,
        cursorRowToData = {
          if (section.returnAsGroupStories) {
            ContactSearchData.Story(contactSearchPagedDataSourceRepository.getRecipientFromGroupCursor(cursor), 0)
          } else {
            ContactSearchData.KnownRecipient(contactSearchPagedDataSourceRepository.getRecipientFromGroupCursor(cursor))
          }
        }
      )
    } ?: emptyList()
  }

  /**
   * We assume that the collection is [cursor contents] + [extraData contents]
   */
  private data class ResultsCollection(
    val section: ContactSearchConfiguration.Section,
    val cursor: Cursor,
    val extraData: List<ContactSearchData>,
    val cursorMapper: (Cursor) -> ContactSearchData
  ) {

    private val contentSize = cursor.count + extraData.count()

    fun getSize(): Int {
      val contentsAndExpand = min(
        section.expandConfig?.let {
          if (it.isExpanded) Int.MAX_VALUE else (it.maxCountWhenNotExpanded + 1)
        } ?: Int.MAX_VALUE,
        contentSize
      )

      return contentsAndExpand + (if (contentsAndExpand > 0 && section.includeHeader) 1 else 0)
    }

    fun getSublist(start: Int, end: Int): List<ContactSearchData> {
      val results = mutableListOf<ContactSearchData>()
      for (i in start until end) {
        results.add(getItemAt(i))
      }

      return results
    }

    private fun getItemAt(index: Int): ContactSearchData {
      return when {
        index == 0 && section.includeHeader -> ContactSearchData.Header(section.sectionKey, section.headerAction)
        index == getSize() - 1 && shouldDisplayExpandRow() -> ContactSearchData.Expand(section.sectionKey)
        else -> {
          val correctedIndex = if (section.includeHeader) index - 1 else index
          if (correctedIndex < cursor.count) {
            cursor.moveToPosition(correctedIndex)
            cursorMapper.invoke(cursor)
          } else {
            val extraIndex = correctedIndex - cursor.count
            extraData[extraIndex]
          }
        }
      }
    }

    private fun shouldDisplayExpandRow(): Boolean {
      val expandConfig = section.expandConfig
      return when {
        expandConfig == null || expandConfig.isExpanded -> false
        else -> contentSize > expandConfig.maxCountWhenNotExpanded + 1
      }
    }
  }
}
