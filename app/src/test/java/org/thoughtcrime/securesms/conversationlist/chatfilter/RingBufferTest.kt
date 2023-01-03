package org.thoughtcrime.securesms.conversationlist.chatfilter

import org.junit.Assert.assertEquals
import org.junit.Test

class RingBufferTest {

  @Test(expected = IllegalArgumentException::class)
  fun `Given a negative capacity, when I call the constructor, then I expect an IllegalArgumentException`() {
    RingBuffer<Int>(-1)
  }

  @Test
  fun `Given I enqueue more items than my capacity, when I getSize, then I expect my initial capacity`() {
    val capacity = 10
    val testSubject = RingBuffer<Int>(capacity)

    (1..(capacity * 2)).forEach {
      testSubject.add(it)
    }

    assertEquals("Capacity should never exceed $capacity items.", capacity, testSubject.size())
    assertEquals("First item should be 10", 11, testSubject[0])
    assertEquals("Last item should be 20", 20, testSubject[testSubject.size() - 1])
  }

  @Test(expected = ArrayIndexOutOfBoundsException::class)
  fun `when I get, then I expect an ArrayIndexOutOfBoundsException`() {
    val testSubject = RingBuffer<Int>(10)
    testSubject[0]
  }

  @Test
  fun `Given some added elements, when I get, then I expect the element`() {
    val testSubject = RingBuffer<Int>(10)
    val expected = 1
    testSubject.add(expected)
    val actual = testSubject[0]
    assertEquals("Expected get to return $expected", expected, actual)
  }
}
