package org.thoughtcrime.securesms.contacts.paged

import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel

interface ArbitraryRepository {
  /**
   * Get the count of arbitrary rows to include for the given query from the given section.
   */
  fun getSize(section: ContactSearchConfiguration.Section.Arbitrary, query: String?): Int

  /**
   * Get the data for the given arbitrary rows within the start and end index.
   */
  fun getData(
    section: ContactSearchConfiguration.Section.Arbitrary,
    query: String?,
    startIndex: Int,
    endIndex: Int,
    totalSearchSize: Int
  ): List<ContactSearchData.Arbitrary>

  /**
   * Map an arbitrary object to a mapping model
   */
  fun getMappingModel(arbitrary: ContactSearchData.Arbitrary): MappingModel<*>
}
