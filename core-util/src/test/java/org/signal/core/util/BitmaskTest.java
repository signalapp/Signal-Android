package org.signal.core.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BitmaskTest {

  @Test
  public void read_singleBit() {
    assertFalse(Bitmask.read(0b00000000, 0));
    assertFalse(Bitmask.read(0b11111101, 1));
    assertFalse(Bitmask.read(0b11111011, 2));
    assertFalse(Bitmask.read(0b01111111_11111111_11111111_11111111_11111111_11111111_11111111_11111111L, 63));

    assertTrue(Bitmask.read(0b00000001, 0));
    assertTrue(Bitmask.read(0b00000010, 1));
    assertTrue(Bitmask.read(0b00000100, 2));
    assertTrue(Bitmask.read(0b10000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L, 63));
  }

  @Test
  public void read_twoBits() {
    assertEquals(0, Bitmask.read(0b11111100, 0, 2));
    assertEquals(1, Bitmask.read(0b11111101, 0, 2));
    assertEquals(2, Bitmask.read(0b11111110, 0, 2));
    assertEquals(3, Bitmask.read(0b11111111, 0, 2));

    assertEquals(0, Bitmask.read(0b11110011, 1, 2));
    assertEquals(1, Bitmask.read(0b11110111, 1, 2));
    assertEquals(2, Bitmask.read(0b11111011, 1, 2));
    assertEquals(3, Bitmask.read(0b11111111, 1, 2));

    assertEquals(0, Bitmask.read(0b00000000, 2, 2));
    assertEquals(1, Bitmask.read(0b00010000, 2, 2));
    assertEquals(2, Bitmask.read(0b00100000, 2, 2));
    assertEquals(3, Bitmask.read(0b00110000, 2, 2));

    assertEquals(0, Bitmask.read(0b00000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L, 31, 2));
    assertEquals(1, Bitmask.read(0b01000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L, 31, 2));
    assertEquals(2, Bitmask.read(0b10000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L, 31, 2));
    assertEquals(3, Bitmask.read(0b11000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L, 31, 2));
  }

  @Test
  public void read_fourBits() {
    assertEquals(0,  Bitmask.read(0b00000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L, 15, 4));
    assertEquals(4,  Bitmask.read(0b01000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L, 15, 4));
    assertEquals(8,  Bitmask.read(0b10000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L, 15, 4));
    assertEquals(15, Bitmask.read(0b11110000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L, 15, 4));
  }

  @Test(expected = IllegalArgumentException.class)
  public void read_error_negativeIndex() {
    Bitmask.read(0b0000000, -1);
  }

  @Test(expected = IllegalArgumentException.class)
  public void read_error_indexTooLarge_singleBit() {
    Bitmask.read(0b0000000, 64);
  }

  @Test(expected = IllegalArgumentException.class)
  public void read_error_indexTooLarge_twoBits() {
    Bitmask.read(0b0000000, 32, 2);
  }

  @Test
  public void update_singleBit() {
    assertEquals(0b00000001, Bitmask.update(0b00000000, 0, true));
    assertEquals(0b00000010, Bitmask.update(0b00000000, 1, true));
    assertEquals(0b00000100, Bitmask.update(0b00000000, 2, true));
    assertEquals(0b10000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L,
                 Bitmask.update(0b00000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L, 63, true));

    assertEquals(0b11111110, Bitmask.update(0b11111111, 0, false));
    assertEquals(0b11111101, Bitmask.update(0b11111111, 1, false));
    assertEquals(0b11111011, Bitmask.update(0b11111111, 2, false));
    assertEquals(0b01111111_11111111_11111111_11111111_11111111_11111111_11111111_11111111L,
                 Bitmask.update(0b11111111_11111111_11111111_11111111_11111111_11111111_11111111_11111111L, 63, false));

    assertEquals(0b11111111, Bitmask.update(0b11111111, 0, true));
    assertEquals(0b11111111, Bitmask.update(0b11111111, 1, true));
    assertEquals(0b11111111, Bitmask.update(0b11111111, 2, true));
    assertEquals(0b11111111_11111111_11111111_11111111_11111111_11111111_11111111_11111111L,
                 Bitmask.update(0b11111111_11111111_11111111_11111111_11111111_11111111_11111111_11111111L, 63, true));

    assertEquals(0b00000000, Bitmask.update(0b00000000, 0, false));
    assertEquals(0b00000000, Bitmask.update(0b00000000, 1, false));
    assertEquals(0b00000000, Bitmask.update(0b00000000, 2, false));
    assertEquals(0b00000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L,
                 Bitmask.update(0b00000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L, 63, false));
  }

  @Test
  public void update_twoBits() {
    assertEquals(0b00000000, Bitmask.update(0b00000000, 0, 2, 0));
    assertEquals(0b00000001, Bitmask.update(0b00000000, 0, 2, 1));
    assertEquals(0b00000010, Bitmask.update(0b00000000, 0, 2, 2));
    assertEquals(0b00000011, Bitmask.update(0b00000000, 0, 2, 3));

    assertEquals(0b00000000, Bitmask.update(0b00000000, 1, 2, 0));
    assertEquals(0b00000100, Bitmask.update(0b00000000, 1, 2, 1));
    assertEquals(0b00001000, Bitmask.update(0b00000000, 1, 2, 2));
    assertEquals(0b00001100, Bitmask.update(0b00000000, 1, 2, 3));

    assertEquals(0b11111100, Bitmask.update(0b11111111, 0, 2, 0));
    assertEquals(0b11111101, Bitmask.update(0b11111111, 0, 2, 1));
    assertEquals(0b11111110, Bitmask.update(0b11111111, 0, 2, 2));
    assertEquals(0b11111111, Bitmask.update(0b11111111, 0, 2, 3));

    assertEquals(0b00000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L,
                 Bitmask.update(0b00000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L, 31, 2, 0));
    assertEquals(0b01000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L,
                 Bitmask.update(0b00000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L, 31, 2, 1));
    assertEquals(0b10000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L,
                 Bitmask.update(0b00000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L, 31, 2, 2));
    assertEquals(0b11000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L,
                 Bitmask.update(0b00000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L, 31, 2, 3));
  }

  @Test(expected = IllegalArgumentException.class)
  public void update_error_negativeIndex() {
    Bitmask.update(0b0000000, -1, true);
  }

  @Test(expected = IllegalArgumentException.class)
  public void update_error_indexTooLarge_singleBit() {
    Bitmask.update(0b0000000, 64, true);
  }

  @Test(expected = IllegalArgumentException.class)
  public void update_error_indexTooLarge_twoBits() {
    Bitmask.update(0b0000000, 32, 2, 0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void update_error_negativeValue() {
    Bitmask.update(0b0000000, 0, 2, -1);
  }

  @Test(expected = IllegalArgumentException.class)
  public void update_error_valueTooLarge() {
    Bitmask.update(0b0000000, 0, 2, 4);
  }
}
