package org.thoughtcrime.securesms.contacts.paged

import android.app.Application
import android.database.MatrixCursor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.readToList

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WrapAroundCursorTest {

  private val values = listOf("Cereal", "Marshmallows", "Toast", "Meatballs", "Oatmeal")

  private val delegate = MatrixCursor(arrayOf("Breakfast")).apply {
    values.forEach {
      addRow(arrayOf(it))
    }
  }

  @Test
  fun `Given an offset of zero, then I expect the original order`() {
    val testSubject = WrapAroundCursor(delegate, offset = 0)
    val actual = testSubject.readToList { cursor -> cursor.getString(0) }
    assertEquals(values, actual)
  }

  @Test
  fun `Given an offset of two, then I expect wrap around`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    val expected = values.drop(2) + values.dropLast(3)
    val actual = testSubject.readToList { cursor -> cursor.getString(0) }
    assertEquals(expected, actual)
  }

  @Test
  fun `Given an offset of two and internal position of 1, when I getPosition, then I expect an offset position`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    delegate.moveToPosition(1)
    val actual = testSubject.position
    assertEquals(4, actual)
  }

  @Test
  fun `Given an offset of two and internal position of 4, when I getPosition, then I expect an offset position`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    delegate.moveToPosition(4)
    val actual = testSubject.position
    assertEquals(2, actual)
  }

  @Test
  fun `Given an offset of two and internal position of 2, when I getPosition, then I expect an offset position`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    delegate.moveToPosition(2)
    val actual = testSubject.position
    assertEquals(0, actual)
  }

  @Test
  fun `Given an offset of two and internal position of -1, when I getPosition, then I expect -1`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    delegate.moveToPosition(-1)
    val actual = testSubject.position
    assertEquals(-1, actual)
  }

  @Test
  fun `Given an offset of two and internal position of 5, when I getPosition, then I expect 5`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    delegate.moveToPosition(5)
    val actual = testSubject.position
    assertEquals(5, actual)
  }

  @Test
  fun `Given an offset of two, when I set internal cursor position to 2 and isFirst, then I expect true`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    delegate.moveToPosition(2)
    val actual = testSubject.isFirst
    assertTrue(actual)
  }

  @Test
  fun `Given an offset of two, when I set internal cursor position to 0 and isFirst, then I expect false`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    delegate.moveToPosition(0)
    val actual = testSubject.isFirst
    assertFalse(actual)
  }

  @Test
  fun `Given an offset of two, when I set internal cursor position to 1 and isLast, then I expect true`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    delegate.moveToPosition(1)
    val actual = testSubject.isLast
    assertTrue(actual)
  }

  @Test
  fun `Given an offset of two, when I set internal cursor position to 4 and isLast, then I expect false`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    delegate.moveToPosition(4)
    val actual = testSubject.isLast
    assertFalse(actual)
  }

  @Test
  fun `Given an offset of two, when I moveToPosition to 0, then I expect the internal cursor to be at position 2`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    testSubject.moveToPosition(0)
    val actual = delegate.position
    assertEquals(2, actual)
  }

  @Test
  fun `Given an offset of two, when I moveToPosition to 4, then I expect the internal cursor to be at position 1`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    testSubject.moveToPosition(4)
    val actual = delegate.position
    assertEquals(1, actual)
  }

  @Test
  fun `Given an offset of two, when I moveToPosition to -1, then I expect the internal cursor to be at position -1`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    testSubject.moveToPosition(-1)
    val actual = delegate.position
    assertEquals(-1, actual)
  }

  @Test
  fun `Given an offset of two, when I moveToPosition to 5, then I expect the internal cursor to be at position 5`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    testSubject.moveToPosition(5)
    val actual = delegate.position
    assertEquals(5, actual)
  }

  @Test
  fun `Given an offset of two, when I moveToFirst, then I expect the internal cursor to be at position 2`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    testSubject.moveToFirst()
    val actual = delegate.position
    assertEquals(2, actual)
  }

  @Test
  fun `Given an offset of two, when I moveToLast, then I expect the internal cursor to be at position 1`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    testSubject.moveToLast()
    val actual = delegate.position
    assertEquals(1, actual)
  }

  @Test
  fun `Given an offset of two and at first position, when I move 4, then I expect the internal cursor to be at position 1`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    testSubject.moveToFirst()
    testSubject.move(4)
    val actual = delegate.position
    assertEquals(1, actual)
  }

  @Test
  fun `Given an offset of two and at first position, when I move 6, then I expect the internal cursor to be at position 5`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    testSubject.moveToFirst()
    testSubject.move(6)
    val actual = delegate.position
    assertEquals(5, actual)
  }

  @Test
  fun `Given an offset of two and at first position, when I move -1, then I expect the internal cursor to be at position -1`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    testSubject.moveToFirst()
    testSubject.move(-1)
    val actual = delegate.position
    assertEquals(-1, actual)
  }

  @Test
  fun `Given an offset of two and at last position, when I move -1, then I expect the internal cursor to be at position 0`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    testSubject.moveToLast()
    testSubject.move(-1)
    val actual = delegate.position
    assertEquals(0, actual)
  }

  @Test
  fun `Given an offset of two and at last position, when I move 1, then I expect the internal cursor to be at position 5`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    testSubject.moveToLast()
    testSubject.move(1)
    val actual = delegate.position
    assertEquals(5, actual)
  }

  @Test
  fun `Given an offset of two and at last position, when I moveToNext, then I expect the internal cursor to be at position 5`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    testSubject.moveToLast()
    testSubject.moveToNext()
    val actual = delegate.position
    assertEquals(5, actual)
  }

  @Test
  fun `Given an offset of two and at first position, when I moveToPrevious, then I expect the internal cursor to be at position -1`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    testSubject.moveToFirst()
    testSubject.moveToPrevious()
    val actual = delegate.position
    assertEquals(-1, actual)
  }

  @Test
  fun `Given an offset of two and at internal position 4, when I moveToNext, then I expect the internal cursor to be at position 0`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    delegate.moveToPosition(4)
    testSubject.moveToNext()
    val actual = delegate.position
    assertEquals(0, actual)
  }

  @Test
  fun `Given an offset of two and at internal position 0, when I moveToPrevious, then I expect the internal cursor to be at position 4`() {
    val testSubject = WrapAroundCursor(delegate, offset = 2)
    delegate.moveToPosition(0)
    testSubject.moveToPrevious()
    val actual = delegate.position
    assertEquals(4, actual)
  }
}
