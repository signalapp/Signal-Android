package org.thoughtcrime.securesms.contacts.paged.collections

import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData

/**
 * Search collection specifically for stories.
 */
class StoriesSearchCollection<ContactRecord>(
  section: ContactSearchConfiguration.Section,
  records: ContactSearchIterator<ContactRecord>,
  extraData: List<ContactSearchData>,
  recordMapper: (ContactRecord) -> ContactSearchData,
  activeContactCount: Int,
  private val storyComparator: Comparator<ContactSearchData.Story>
) : ContactSearchCollection<ContactRecord>(section, records, null, recordMapper, activeContactCount) {

  private val aggregateStoryData: List<ContactSearchData.Story> by lazy {
    if (section !is ContactSearchConfiguration.Section.Stories) {
      error("Aggregate data creation is only necessary for stories.")
    }

    val cursorContacts = records.asSequence().map(recordMapper).toList()

    (cursorContacts + extraData).filterIsInstance(ContactSearchData.Story::class.java).sortedWith(storyComparator)
  }

  override val contentSize: Int = records.getCount() + extraData.size

  override fun getItemAtCorrectedIndex(correctedIndex: Int): ContactSearchData {
    return aggregateStoryData[correctedIndex]
  }

  override fun fillDataWindow(offset: Int, limit: Int) = Unit
}
