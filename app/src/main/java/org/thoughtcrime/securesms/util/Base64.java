package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

public final class Base64 {

  private Base64() {
  }

  public static @NonNull byte[] decode(@NonNull String s) throws IOException {
    return org.whispersystems.util.Base64.decode(s);
  }

  public static @NonNull String encodeBytes(@NonNull byte[] source) {
    return org.whispersystems.util.Base64.encodeBytes(source);
  }

  public static @NonNull byte[] decodeOrThrow(@NonNull String s) {
    try {
      return org.whispersystems.util.Base64.decode(s);
    } catch (IOException e) {
      throw new AssertionError();
    }
  }

  public static @Nullable byte[] decodeNullableOrThrow(@Nullable String s) {
    if (s == null) {
      return null;
    }

    try {
      return org.whispersystems.util.Base64.decode(s);
    } catch (IOException e) {
      throw new AssertionError();
    }
  }
}
