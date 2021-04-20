package org.thoughtcrime.securesms.giph.mp4

import org.junit.Assert
import org.junit.Test

class GiphyMp4PlaybackControllerRangeComparatorTest {
  @Test
  fun `Given a range of numbers, when I sort with comparator, then I expect an array sorted from the center out`() {
    val testSubject = createComparator(0, 10)

    val sorted = (0..10).sortedWith(testSubject).toIntArray()
    val expected = intArrayOf(5, 4, 6, 3, 7, 2, 8, 1, 9, 0, 10)

    Assert.assertArrayEquals(expected, sorted)
  }

  private fun createComparator(start: Int, end: Int): GiphyMp4PlaybackController.RangeComparator =
    GiphyMp4PlaybackController.RangeComparator(start, end)
}
