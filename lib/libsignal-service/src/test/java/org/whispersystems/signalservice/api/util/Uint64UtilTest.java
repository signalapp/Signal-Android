package org.whispersystems.signalservice.api.util;

import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.whispersystems.signalservice.api.util.Uint64Util.bigIntegerToUInt64;
import static org.whispersystems.signalservice.api.util.Uint64Util.uint64ToBigInteger;

public final class Uint64UtilTest {

  @Test
  public void long_zero_to_bigInteger() {
    BigInteger bigInteger = uint64ToBigInteger(0);

    assertEquals("0", bigInteger.toString());
  }

  @Test
  public void long_to_bigInteger() {
    BigInteger bigInteger = uint64ToBigInteger(12345L);

    assertEquals("12345", bigInteger.toString());
  }

  @Test
  public void bigInteger_zero_to_long() throws Uint64RangeException {
    long uint64 = bigIntegerToUInt64(BigInteger.ZERO);

    assertEquals(0, uint64);
  }

  @Test
  public void first_uint64_value_to_bigInteger() {
    BigInteger bigInteger = uint64ToBigInteger(0x8000000000000000L);

    assertEquals("9223372036854775808", bigInteger.toString());
  }

  @Test
  public void bigInteger_to_first_uint64_value() throws Uint64RangeException {
    long uint64 = bigIntegerToUInt64(new BigInteger("9223372036854775808"));

    assertEquals(0x8000000000000000L, uint64);
  }

  @Test
  public void large_uint64_value_to_bigInteger() {
    BigInteger bigInteger = uint64ToBigInteger(0xa523f21e412c14d2L);

    assertEquals("11899620852199331026", bigInteger.toString());
  }

  @Test
  public void bigInteger_to_large_uint64_value() throws Uint64RangeException {
    long uint64 = bigIntegerToUInt64(new BigInteger("11899620852199331026"));

    assertEquals(0xa523f21e412c14d2L, uint64);
  }

  @Test
  public void largest_uint64_value_to_bigInteger() {
    BigInteger bigInteger = uint64ToBigInteger(0xffffffffffffffffL);

    assertEquals("18446744073709551615", bigInteger.toString());
  }

  @Test
  public void bigInteger_to_largest_uint64_value() throws Uint64RangeException {
    long uint64 = bigIntegerToUInt64(new BigInteger("18446744073709551615"));

    assertEquals(0xffffffffffffffffL, uint64);
  }

  @Test(expected = Uint64RangeException.class)
  public void too_big_by_one() throws Uint64RangeException {
    bigIntegerToUInt64(new BigInteger("18446744073709551616"));
  }

  @Test(expected = Uint64RangeException.class)
  public void too_small_by_one() throws Uint64RangeException {
    bigIntegerToUInt64(new BigInteger("-1"));
  }

  @Test(expected = Uint64RangeException.class)
  public void too_big_by_a_lot() throws Uint64RangeException {
    bigIntegerToUInt64(new BigInteger("1844674407370955161623"));
  }

  @Test(expected = Uint64RangeException.class)
  public void too_small_by_a_lot() throws Uint64RangeException {
    bigIntegerToUInt64(new BigInteger("-1844674407370955161623"));
  }
}
