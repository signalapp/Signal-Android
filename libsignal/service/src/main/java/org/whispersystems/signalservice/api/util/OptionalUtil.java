package org.whispersystems.signalservice.api.util;

import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Arrays;

public final class OptionalUtil {

  private OptionalUtil() {
  }

  public static boolean byteArrayEquals(Optional<byte[]> a, Optional<byte[]> b) {
    if (a.isPresent() != b.isPresent()) {
      return false;
    }

    if (a.isPresent()) {
      return Arrays.equals(a.get(), b.get());
    }

    return true;
  }

  public static int byteArrayHashCode(Optional<byte[]> bytes) {
    return bytes.isPresent() ? Arrays.hashCode(bytes.get()) : 0;
  }
}
