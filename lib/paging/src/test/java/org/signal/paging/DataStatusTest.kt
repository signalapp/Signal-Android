package org.signal.paging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStatusTest {
  @Test
  fun insertState_initiallyEmpty_InsertAtZero() {
    val subject = DataStatus.obtain(0)
    subject.insertState(0, true)

    assertEquals(1, subject.size())
    assertTrue(subject[0])
  }

  @Test
  fun insertState_someData_InsertAtZero() {
    val subject = DataStatus.obtain(2)
    subject.mark(1)

    subject.insertState(0, true)

    assertEquals(3, subject.size())
    assertTrue(subject[0])
    assertFalse(subject[1])
    assertTrue(subject[2])
  }

  @Test
  fun insertState_someData_InsertAtOne() {
    val subject = DataStatus.obtain(3)
    subject.mark(1)

    subject.insertState(1, true)

    assertEquals(4, subject.size())
    assertFalse(subject[0])
    assertTrue(subject[1])
    assertTrue(subject[2])
    assertFalse(subject[3])
  }

  @Test(expected = IndexOutOfBoundsException::class)
  fun insertState_negativeThrows() {
    val subject = DataStatus.obtain(0)
    subject.insertState(-1, true)
  }

  @Test(expected = IndexOutOfBoundsException::class)
  fun insertState_largerThanSizePlusOneThrows() {
    val subject = DataStatus.obtain(0)
    subject.insertState(2, true)
  }
}
