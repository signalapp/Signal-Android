package org.whispersystems.signalservice.api.util

import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.whispersystems.signalservice.api.util.Uint64Util.bigIntegerToUInt64
import org.whispersystems.signalservice.api.util.Uint64Util.uint64ToBigInteger
import java.math.BigInteger

class Uint64UtilTest {

  @Test
  fun long_zero_to_bigInteger() {
    val bigInteger = uint64ToBigInteger(0)
    assertEquals("0", bigInteger.toString())
  }

  @Test
  fun long_to_bigInteger() {
    val bigInteger = uint64ToBigInteger(12345L)
    assertEquals("12345", bigInteger.toString())
  }

  @Test
  fun bigInteger_zero_to_long() {
    val uint64 = bigIntegerToUInt64(BigInteger.ZERO)
    assertEquals(0L, uint64)
  }

  @Test
  fun first_uint64_value_to_bigInteger() {
    val bigInteger = uint64ToBigInteger(0x8000000000000000UL.toLong())
    assertEquals("9223372036854775808", bigInteger.toString())
  }

  @Test
  fun bigInteger_to_first_uint64_value() {
    val uint64 = bigIntegerToUInt64(BigInteger("9223372036854775808"))
    assertEquals(0x8000000000000000UL.toLong(), uint64)
  }

  @Test
  fun large_uint64_value_to_bigInteger() {
    val bigInteger = uint64ToBigInteger(0xa523f21e412c14d2UL.toLong())
    assertEquals("11899620852199331026", bigInteger.toString())
  }

  @Test
  fun bigInteger_to_large_uint64_value() {
    val uint64 = bigIntegerToUInt64(BigInteger("11899620852199331026"))
    assertEquals(0xa523f21e412c14d2UL.toLong(), uint64)
  }

  @Test
  fun largest_uint64_value_to_bigInteger() {
    val bigInteger = uint64ToBigInteger(0xffffffffffffffffUL.toLong())
    assertEquals("18446744073709551615", bigInteger.toString())
  }

  @Test
  fun bigInteger_to_largest_uint64_value() {
    val uint64 = bigIntegerToUInt64(BigInteger("18446744073709551615"))
    assertEquals(0xffffffffffffffffUL.toLong(), uint64)
  }

  @Test(expected = Uint64RangeException::class)
  fun too_big_by_one() {
    bigIntegerToUInt64(BigInteger("18446744073709551616"))
  }

  @Test(expected = Uint64RangeException::class)
  fun too_small_by_one() {
    bigIntegerToUInt64(BigInteger("-1"))
  }

  @Test(expected = Uint64RangeException::class)
  fun too_big_by_a_lot() {
    bigIntegerToUInt64(BigInteger("1844674407370955161623"))
  }

  @Test(expected = Uint64RangeException::class)
  fun too_small_by_a_lot() {
    bigIntegerToUInt64(BigInteger("-1844674407370955161623"))
  }
}
