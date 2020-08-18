package org.whispersystems.util;

import java.io.IOException;

public final class Base64UrlSafe {

  private Base64UrlSafe() {
  }

  public static byte[] decode(String s) throws IOException {
    return Base64.decode(s, Base64.URL_SAFE);
  }

  public static byte[] decodePaddingAgnostic(String s) throws IOException {
    switch (s.length() % 4) {
      case 1:
      case 3: s = s + "="; break;
      case 2: s = s + "=="; break;
    }
    return decode(s);
  }

  public static String encodeBytes(byte[] source) {
    try {
      return Base64.encodeBytes(source, Base64.URL_SAFE);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  public static String encodeBytesWithoutPadding(byte[] source) {
    return encodeBytes(source).replace("=", "");
  }
}
