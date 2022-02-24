package org.thoughtcrime.securesms.contacts.paged

/**
 * Simple search state for contacts.
 */
data class ContactSearchState(
  val query: String? = null,
  val expandedSections: Set<ContactSearchConfiguration.SectionKey> = emptySet(),
  val groupStories: Set<ContactSearchData.Story> = emptySet()
)
