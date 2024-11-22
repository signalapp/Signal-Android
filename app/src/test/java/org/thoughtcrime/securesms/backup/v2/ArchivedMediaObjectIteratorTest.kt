package org.thoughtcrime.securesms.backup.v2

import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.thoughtcrime.securesms.MockCursor
import org.thoughtcrime.securesms.assertIsSize

class ArchivedMediaObjectIteratorTest {

  private val cursor: MockCursor = mock()

  @Before
  fun setUp() {
    whenever(cursor.getString(any())).thenReturn("A")
    whenever(cursor.moveToPosition(any())).thenCallRealMethod()
    whenever(cursor.moveToNext()).thenCallRealMethod()
    whenever(cursor.position).thenCallRealMethod()
    whenever(cursor.isLast).thenCallRealMethod()
    whenever(cursor.isAfterLast).thenCallRealMethod()
  }

  @Test
  fun `Given a cursor with 0 items, when I convert to a list, then I expect a size of 0`() {
    runTest(0)
  }

  @Test
  fun `Given a cursor with 100 items, when I convert to a list, then I expect a size of 100`() {
    runTest(100)
  }

  private fun runTest(size: Int) {
    whenever(cursor.count).thenReturn(size)
    val iterator = ArchivedMediaObjectIterator(cursor)

    val list = iterator.asSequence().toList()

    list.assertIsSize(size)
  }
}
