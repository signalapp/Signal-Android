package org.whispersystems.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.whispersystems.signalservice.internal.util.Hex;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public final class Base64UrlSafeTest {

  private final byte[] data;
  private final String encoded;
  private final String encodedWithoutPadding;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      { "", "", "" },
      { "01", "AQ==", "AQ" },
      { "0102", "AQI=", "AQI" },
      { "010203", "AQID", "AQID" },
      { "030405", "AwQF", "AwQF" },
      { "03040506", "AwQFBg==", "AwQFBg" },
      { "0304050708", "AwQFBwg=", "AwQFBwg" },
      { "af4d6cff", "r01s_w==", "r01s_w" },
      { "ffefde", "_-_e", "_-_e" },
    });
  }

  public Base64UrlSafeTest(String hexData, String encoded, String encodedWithoutPadding) throws IOException {
    this.data                  = Hex.fromStringCondensed(hexData);
    this.encoded               = encoded;
    this.encodedWithoutPadding = encodedWithoutPadding;
  }

  @Test
  public void encodes_as_expected() {
    assertEquals(encoded, Base64UrlSafe.encodeBytes(data));
  }

  @Test
  public void encodes_as_expected_without_padding() {
    assertEquals(encodedWithoutPadding, Base64UrlSafe.encodeBytesWithoutPadding(data));
  }

  @Test
  public void decodes_as_expected() throws IOException {
    assertArrayEquals(data, Base64UrlSafe.decode(encoded));
  }

  @Test
  public void decodes_padding_agnostic_as_expected() throws IOException {
    assertArrayEquals(data, Base64UrlSafe.decodePaddingAgnostic(encoded));
  }

  @Test
  public void decodes_as_expected_without_padding() throws IOException {
    assertArrayEquals(data, Base64UrlSafe.decodePaddingAgnostic(encodedWithoutPadding));
  }
}