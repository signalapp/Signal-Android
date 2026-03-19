package org.thoughtcrime.securesms.search

import org.thoughtcrime.securesms.recipients.RecipientId

data class SearchFilter(
  val startDate: Long? = null,
  val endDate: Long? = null,
  val author: RecipientId? = null
) {
  val isEmpty: Boolean
    get() = startDate == null && endDate == null && author == null

  companion object {
    @JvmField
    val EMPTY = SearchFilter()
  }
}
