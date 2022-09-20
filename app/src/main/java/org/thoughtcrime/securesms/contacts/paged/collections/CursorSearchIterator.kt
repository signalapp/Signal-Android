package org.thoughtcrime.securesms.contacts.paged.collections

import android.database.Cursor

class CursorSearchIterator(private val cursor: Cursor?) : ContactSearchIterator<Cursor> {
  override fun hasNext(): Boolean = cursor?.let { !it.isLast && !it.isAfterLast } ?: false

  override fun next(): Cursor {
    cursor?.moveToNext()
    return cursor!!
  }

  override fun close() {
    cursor?.close()
  }

  override fun moveToPosition(n: Int) {
    cursor?.moveToPosition(n)
  }

  override fun getCount(): Int = cursor?.count ?: 0
}
