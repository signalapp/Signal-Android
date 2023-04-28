package org.whispersystems.signalservice.api.util;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class OptionalUtilTest {

  @Test
  public void absent_are_equal() {
    assertTrue(OptionalUtil.byteArrayEquals(Optional.empty(), Optional.empty()));
  }

  @Test
  public void first_non_absent_not_equal() {
    assertFalse(OptionalUtil.byteArrayEquals(Optional.of(new byte[1]), Optional.empty()));
  }

  @Test
  public void second_non_absent_not_equal() {
    assertFalse(OptionalUtil.byteArrayEquals(Optional.empty(), Optional.of(new byte[1])));
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
    assertEquals(0, OptionalUtil.byteArrayHashCode(Optional.empty()));
  }

  @Test
  public void or_singleAbsent() {
    assertFalse(OptionalUtil.or(Optional.empty()).isPresent());
  }

  @Test
  public void or_multipleAbsent() {
    assertFalse(OptionalUtil.or(Optional.empty(), Optional.empty()).isPresent());
  }

  @Test
  public void or_firstHasValue() {
    assertEquals(5, OptionalUtil.or(Optional.of(5), Optional.empty()).get().longValue());
  }

  @Test
  public void or_secondHasValue() {
    assertEquals(5, OptionalUtil.or(Optional.empty(), Optional.of(5)).get().longValue());
  }
}
