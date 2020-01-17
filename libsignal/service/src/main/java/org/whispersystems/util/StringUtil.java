package org.whispersystems.util;

import java.nio.charset.StandardCharsets;

public final class StringUtil {

  private StringUtil() {
  }

  public static byte[] utf8(String string) {
    return string.getBytes(StandardCharsets.UTF_8);
  }
}
