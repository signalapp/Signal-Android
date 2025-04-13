package org.whispersystems.signalservice.api.util

import junit.framework.TestCase.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.whispersystems.signalservice.api.util.OptionalUtil.byteArrayEquals
import org.whispersystems.signalservice.api.util.OptionalUtil.byteArrayHashCode
import org.whispersystems.signalservice.api.util.OptionalUtil.or
import java.util.Optional

class OptionalUtilTest {

  @Test
  fun absent_are_equal() {
    assertTrue(byteArrayEquals(Optional.empty(), Optional.empty()))
  }

  @Test
  fun first_non_absent_not_equal() {
    assertFalse(byteArrayEquals(Optional.of(ByteArray(1)), Optional.empty()))
  }

  @Test
  fun second_non_absent_not_equal() {
    assertFalse(byteArrayEquals(Optional.empty(), Optional.of(ByteArray(1))))
  }

  @Test
  fun equal_contents() {
    val contentsA = byteArrayOf(1, 2, 3)
    val contentsB = contentsA.copyOf()
    val a: Optional<ByteArray> = Optional.of(contentsA)
    val b: Optional<ByteArray> = Optional.of(contentsB)
    assertTrue(byteArrayEquals(a, b))
    assertEquals(byteArrayHashCode(a), byteArrayHashCode(b))
  }

  @Test
  fun in_equal_contents() {
    val contentsA = byteArrayOf(1, 2, 3)
    val contentsB = byteArrayOf(4, 5, 6)
    val a: Optional<ByteArray> = Optional.of(contentsA)
    val b: Optional<ByteArray> = Optional.of(contentsB)
    assertFalse(byteArrayEquals(a, b))
    assertNotEquals(byteArrayHashCode(a), byteArrayHashCode(b))
  }

  @Test
  fun hash_code_absent() {
    assertEquals(0, byteArrayHashCode(Optional.empty()))
  }

  @Test
  fun or_singleAbsent() {
    assertFalse(or(Optional.empty()).isPresent)
  }

  @Test
  fun or_multipleAbsent() {
    assertFalse(or(Optional.empty(), Optional.empty()).isPresent)
  }

  @Test
  fun or_firstHasValue() {
    assertEquals(5, or(Optional.of(5), Optional.empty()).get())
  }

  @Test
  fun or_secondHasValue() {
    assertEquals(5, or(Optional.empty(), Optional.of(5)).get())
  }
}
