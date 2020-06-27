package org.thoughtcrime.securesms.util;

import org.junit.Test;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class ShortCodeUtilTest {

  @Test
  public void testShortCodes() throws Exception {
    assertTrue(ShortCodeUtil.isShortCode("+14152222222", "40404"));
    assertTrue(ShortCodeUtil.isShortCode("+14152222222", "431"));
    assertFalse(ShortCodeUtil.isShortCode("+14152222222", "4157778888"));
    assertFalse(ShortCodeUtil.isShortCode("+14152222222", "+14157778888"));
    assertFalse(ShortCodeUtil.isShortCode("+14152222222", "415-777-8888"));
    assertFalse(ShortCodeUtil.isShortCode("+14152222222", "(415) 777-8888"));
    assertFalse(ShortCodeUtil.isShortCode("+14152222222", "8882222"));
    assertFalse(ShortCodeUtil.isShortCode("+14152222222", "888-2222"));

    assertTrue(ShortCodeUtil.isShortCode("+491723742522", "670"));
    assertTrue(ShortCodeUtil.isShortCode("+491723742522", "115"));
    assertFalse(ShortCodeUtil.isShortCode("+491723742522", "089-12345678"));
    assertFalse(ShortCodeUtil.isShortCode("+491723742522", "089/12345678"));
    assertFalse(ShortCodeUtil.isShortCode("+491723742522", "12345678"));

    assertTrue(ShortCodeUtil.isShortCode("+298123456", "4040"));
    assertTrue(ShortCodeUtil.isShortCode("+298123456", "6701"));
    assertTrue(ShortCodeUtil.isShortCode("+298123456", "433"));
    assertFalse(ShortCodeUtil.isShortCode("+298123456", "123456"));

    assertTrue(ShortCodeUtil.isShortCode("+61414915066", "19808948"));
    assertFalse(ShortCodeUtil.isShortCode("+61414915066", "119808948"));

    assertTrue(ShortCodeUtil.isShortCode("+79166503388", "8080"));
    assertTrue(ShortCodeUtil.isShortCode("+79166503388", "6701"));
    assertTrue(ShortCodeUtil.isShortCode("+79166503388", "431"));
    assertFalse(ShortCodeUtil.isShortCode("+79166503388", "111-22-33"));
  }

}
