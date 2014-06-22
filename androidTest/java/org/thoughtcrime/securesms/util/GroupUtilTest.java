package org.thoughtcrime.securesms.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class GroupUtilTest {

  @Test
     public void testIsEncodedGroup() throws Exception {
    assertThat(GroupUtil.isEncodedGroup("__textsecure_group__!faceface")).isTrue();
    assertThat(GroupUtil.isEncodedGroup("some@email.com")).isFalse();
    assertThat(GroupUtil.isEncodedGroup("")).isFalse();
  }

  @Test
  public void testGetDecodedId() throws Exception {
    assertThat(GroupUtil.getDecodedId("__textsecure_group__!faceface"))
        .isEqualTo(new byte[]{(byte) 0xfa, (byte) 0xce, (byte) 0xfa, (byte) 0xce});
  }

  @Test
  public void testGetEncodedId() throws Exception {
    assertThat(GroupUtil.getEncodedId(new byte[]{(byte) 0xfa, (byte) 0xce, (byte) 0xfa, (byte) 0xce}))
        .isEqualTo("__textsecure_group__!faceface");
  }
}