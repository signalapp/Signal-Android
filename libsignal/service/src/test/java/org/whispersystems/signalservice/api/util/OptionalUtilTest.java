package org.whispersystems.signalservice.api.util;

import org.junit.Test;
import org.whispersystems.libsignal.util.guava.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class OptionalUtilTest {

  @Test
  public void absent_are_equal() {
    assertTrue(OptionalUtil.byteArrayEquals(Optional.<byte[]>absent(), Optional.<byte[]>absent()));
  }

  @Test
  public void first_non_absent_not_equal() {
    assertFalse(OptionalUtil.byteArrayEquals(Optional.of(new byte[1]), Optional.<byte[]>absent()));
  }

  @Test
  public void second_non_absent_not_equal() {
    assertFalse(OptionalUtil.byteArrayEquals(Optional.<byte[]>absent(), Optional.of(new byte[1])));
  }

  @Test
  public void equal_contents() {
    byte[]           contentsA = new byte[]{1, 2, 3};
    byte[]           contentsB = contentsA.clone();
    Optional<byte[]> a         = Optional.of(contentsA);
    Optional<byte[]> b         = Optional.of(contentsB);
    assertTrue(OptionalUtil.byteArrayEquals(a, b));
    assertEquals(OptionalUtil.byteArrayHashCode(a), OptionalUtil.byteArrayHashCode(b));
  }

  @Test
  public void in_equal_contents() {
    byte[]           contentsA = new byte[]{1, 2, 3};
    byte[]           contentsB = new byte[]{4, 5, 6};
    Optional<byte[]> a         = Optional.of(contentsA);
    Optional<byte[]> b         = Optional.of(contentsB);
    assertFalse(OptionalUtil.byteArrayEquals(a, b));
    assertNotEquals(OptionalUtil.byteArrayHashCode(a), OptionalUtil.byteArrayHashCode(b));
  }

  @Test
  public void hash_code_absent() {
    assertEquals(0, OptionalUtil.byteArrayHashCode(Optional.<byte[]>absent()));
  }

}