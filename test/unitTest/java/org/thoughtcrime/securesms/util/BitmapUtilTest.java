package org.thoughtcrime.securesms.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BitmapUtilTest {
  @Test public void testScaleFactorNormal() {
    assertEquals(1, BitmapUtil.getScaleFactor(1000, 1000, 9000, 9000, false));

    assertEquals(1, BitmapUtil.getScaleFactor(1000, 1000, 750, 750, false));
    assertEquals(2, BitmapUtil.getScaleFactor(1000, 1000, 500, 500, false));
    assertEquals(2, BitmapUtil.getScaleFactor(1000, 1000, 499, 499, false));
    assertEquals(4, BitmapUtil.getScaleFactor(1000, 1000, 250, 250, false));
    assertEquals(4, BitmapUtil.getScaleFactor(1000, 1000, 249, 249, false));

    assertEquals(1, BitmapUtil.getScaleFactor(1000, 500, 750, 750, false));
    assertEquals(1, BitmapUtil.getScaleFactor(2000, 1000, 501, 501, false));
    assertEquals(2, BitmapUtil.getScaleFactor(2000, 1000, 500, 500, false));
    assertEquals(2, BitmapUtil.getScaleFactor(1000, 2000, 499, 499, false));
  }

  @Test public void testScaleFactorConstrained() {
    assertEquals(1, BitmapUtil.getScaleFactor(1000, 1000, 9000, 9000, true));

    assertEquals(2, BitmapUtil.getScaleFactor(1000, 1000, 750, 750, true));
    assertEquals(2, BitmapUtil.getScaleFactor(1000, 1000, 500, 500, true));
    assertEquals(4, BitmapUtil.getScaleFactor(1000, 1000, 499, 499, true));
    assertEquals(4, BitmapUtil.getScaleFactor(1000, 1000, 250, 250, true));
    assertEquals(8, BitmapUtil.getScaleFactor(1000, 1000, 249, 249, true));

    assertEquals(2, BitmapUtil.getScaleFactor(1000, 500, 750, 750, true));
    assertEquals(4, BitmapUtil.getScaleFactor(2000, 1000, 500, 500, true));
    assertEquals(8, BitmapUtil.getScaleFactor(1000, 2000, 499, 499, true));
  }
}
