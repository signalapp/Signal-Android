package org.whispersystems.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FlagUtilTest {
  @Test
  fun given1_whenIConvertToBinaryFlag_thenIExpect1() {
    assertEquals(1, FlagUtil.toBinaryFlag(1))
  }

  @Test
  fun given2_whenIConvertToBinaryFlag_thenIExpect2() {
    assertEquals(2, FlagUtil.toBinaryFlag(2))
  }

  @Test
  fun given3_whenIConvertToBinaryFlag_thenIExpect4() {
    assertEquals(4, FlagUtil.toBinaryFlag(3))
  }
}
