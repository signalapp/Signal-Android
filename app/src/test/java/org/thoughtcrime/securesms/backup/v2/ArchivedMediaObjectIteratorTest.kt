package org.thoughtcrime.securesms.backup.v2

import assertk.assertThat
import assertk.assertions.hasSize
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.thoughtcrime.securesms.MockCursor

class ArchivedMediaObjectIteratorTest {
  private val cursor = mockk<MockCursor>(relaxed = true) {
    every { getString(any()) } returns "A"
    every { moveToPosition(any()) } answers { callOriginal() }
    every { moveToNext() } answers { callOriginal() }
    every { position } answers { callOriginal() }
    every { isLast } answers { callOriginal() }
    every { isAfterLast } answers { callOriginal() }
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
    every { cursor.count } returns size
    val iterator = ArchivedMediaObjectIterator(cursor)

    val list = iterator.asSequence().toList()

    assertThat(list).hasSize(size)
  }
}
