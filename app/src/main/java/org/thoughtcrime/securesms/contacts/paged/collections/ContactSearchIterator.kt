package org.thoughtcrime.securesms.contacts.paged.collections

import java.io.Closeable

/**
 * Describes the required interface for the ContactSearchPagedDataSource to pull
 * and filter the information it needs from the database.
 */
interface ContactSearchIterator<ContactRecord> : Iterator<ContactRecord>, Closeable {
  fun moveToPosition(n: Int)
  fun getCount(): Int
}
