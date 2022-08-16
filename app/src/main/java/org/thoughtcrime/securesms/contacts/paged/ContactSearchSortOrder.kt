package org.thoughtcrime.securesms.contacts.paged

/**
 * Different options for sort order of contact search items.
 */
enum class ContactSearchSortOrder {
  /**
   * The "natural" expected order. This is considered the default ordering.
   * For example, Groups would normally be ordered by title from A-Z.
   */
  NATURAL,

  /**
   * The requested ordering is by recency. This can mean different things for
   * different contact types. For example, for Groups, this entry means that
   * the results are ordered by latest message date in descending order.
   */
  RECENCY
}
