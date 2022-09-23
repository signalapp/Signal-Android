package org.thoughtcrime.securesms.contacts.paged.collections

import org.junit.Assert.assertEquals
import org.junit.Test
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData

class ContactSearchCollectionTest {

  @Test
  fun `When I get size, then I expect size of filtered content`() {
    // GIVEN
    val testSubject = createTestSubject()

    // WHEN
    val size = testSubject.getSize()

    // THEN
    assertEquals(5, size)
  }

  @Test
  fun `When I get size with header, then I expect size of filtered content plus header`() {
    // GIVEN
    val testSubject = createTestSubject(includeHeader = true)

    // WHEN
    val size = testSubject.getSize()

    // THEN
    assertEquals(6, size)
  }

  @Test
  fun `When I getSublist without 5, then I expect the corresponding values without 5`() {
    // GIVEN
    val testSubject = createTestSubject(
      recordPredicate = { it != 5 }
    )

    // WHEN
    val result = testSubject.getSublist(0, 9)

    // THEN
    assertEquals(listOf(0, 1, 2, 3, 4, 6, 7, 8, 9), result.filterIsInstance(ContactSearchData.TestRow::class.java).map { it.value })
  }

  @Test
  fun `Given I got first page when I getSublist without 5, then I expect the corresponding values without 5`() {
    // GIVEN
    val testSubject = createTestSubject(
      recordPredicate = { it != 5 }
    )
    testSubject.getSublist(0, 5)

    // WHEN
    val result = testSubject.getSublist(5, 9)

    // THEN
    assertEquals(listOf(6, 7, 8, 9), result.filterIsInstance(ContactSearchData.TestRow::class.java).map { it.value })
  }

  @Test
  fun `Given I get second page with header, then I expect the corresponding values without 5`() {
    // GIVEN
    val testSubject = createTestSubject(
      recordPredicate = { it != 5 },
      includeHeader = true
    )

    // WHEN
    val result = testSubject.getSublist(2, testSubject.getSize())

    // THEN
    assertEquals(listOf(1, 2, 3, 4, 6, 7, 8, 9), result.filterIsInstance(ContactSearchData.TestRow::class.java).map { it.value })
  }

  @Test
  fun `Given I get entire page with header, then I expect the corresponding values without 5`() {
    // GIVEN
    val testSubject = createTestSubject(
      recordPredicate = { it != 5 },
      includeHeader = true
    )

    // WHEN
    val result = testSubject.getSublist(0, testSubject.getSize())

    // THEN
    assertEquals(listOf(0, 1, 2, 3, 4, 6, 7, 8, 9), result.filterIsInstance(ContactSearchData.TestRow::class.java).map { it.value })
  }

  private fun createTestSubject(
    size: Int = 10,
    includeHeader: Boolean = false,
    section: ContactSearchConfiguration.Section = ContactSearchConfiguration.Section.Groups(includeHeader = includeHeader),
    records: ContactSearchIterator<Int> = FakeContactSearchIterator((0 until size).toList()),
    recordPredicate: (Int) -> Boolean = { i -> i % 2 == 0 },
    recordMapper: (Int) -> ContactSearchData = { i -> ContactSearchData.TestRow(i) },
    activeContactCount: Int = 0
  ): ContactSearchCollection<Int> {
    return ContactSearchCollection(section, records, recordPredicate, recordMapper, activeContactCount)
  }

  private class FakeContactSearchIterator(private val numbers: List<Int>) : ContactSearchIterator<Int> {

    private var position = -1

    override fun hasNext(): Boolean = position < numbers.lastIndex

    override fun next(): Int = numbers[++position]

    override fun moveToPosition(n: Int) {
      position = n
    }

    override fun getCount(): Int = numbers.size

    override fun close() = Unit
  }
}
