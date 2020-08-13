package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;

import org.whispersystems.util.Base64;

import java.io.IOException;

public final class Base64UrlSafe {

  private Base64UrlSafe() {
  }

  public static @NonNull byte[] decode(@NonNull String s) throws IOException {
    return Base64.decode(s, Base64.URL_SAFE);
  }

  public static @NonNull byte[] decodePaddingAgnostic(@NonNull String s) throws IOException {
    switch (s.length() % 4) {
      case 1:
      case 3: s = s + "="; break;
      case 2: s = s + "=="; break;
    }
    return decode(s);
  }

  public static @NonNull String encodeBytes(@NonNull byte[] source) {
    try {
      return Base64.encodeBytes(source, Base64.URL_SAFE);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  public static @NonNull String encodeBytesWithoutPadding(@NonNull byte[] source) {
    return encodeBytes(source).replace("=", "");
  }
}
