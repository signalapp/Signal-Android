package org.whispersystems.util;

import org.junit.Assert;
import org.junit.Test;

public class FlagUtilTest {

  @Test
  public void given1_whenIConvertToBinaryFlag_thenIExpect1() {
    int expected = 1;

    int actual = FlagUtil.toBinaryFlag(1);

    Assert.assertEquals(expected, actual);
  }


  @Test
  public void given2_whenIConvertToBinaryFlag_thenIExpect2() {
    int expected = 2;

    int actual = FlagUtil.toBinaryFlag(2);

    Assert.assertEquals(expected, actual);
  }

  @Test
  public void given3_whenIConvertToBinaryFlag_thenIExpect4() {
    int expected = 4;

    int actual = FlagUtil.toBinaryFlag(3);

    Assert.assertEquals(expected, actual);
  }
}
