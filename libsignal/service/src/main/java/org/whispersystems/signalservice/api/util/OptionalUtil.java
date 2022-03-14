package org.whispersystems.signalservice.api.util;

import com.google.protobuf.ByteString;

import java.util.Arrays;
import java.util.Optional;

public final class OptionalUtil {

  private OptionalUtil() { }

  @SafeVarargs
  public static <E> Optional<E> or(Optional<E>... optionals) {
    return Arrays.stream(optionals)
                 .filter(Optional::isPresent)
                 .findFirst()
                 .orElse(Optional.empty());
  }

  public static boolean byteArrayEquals(Optional<byte[]> a, Optional<byte[]> b) {
    if (a.isPresent() != b.isPresent()) {
      return false;
    } else if (a.isPresent()) {
      return Arrays.equals(a.get(), b.get());
    } else {
      return true;
    }
  }

  public static int byteArrayHashCode(Optional<byte[]> bytes) {
    if (bytes.isPresent()) {
      return Arrays.hashCode(bytes.get());
    } else {
      return 0;
    }
  }

  public static Optional<String> absentIfEmpty(String value) {
    if (value == null || value.length() == 0) {
      return Optional.empty();
    } else {
      return Optional.of(value);
    }
  }

  public static Optional<byte[]> absentIfEmpty(ByteString value) {
    if (value == null || value.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(value.toByteArray());
    }
  }
}
