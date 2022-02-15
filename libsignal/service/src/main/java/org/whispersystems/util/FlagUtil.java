package org.whispersystems.util;

public final class FlagUtil {

  private FlagUtil() {}

  /**
   * Left shift 1 by 'flag' - 1 spaces.
   *
   * Examples:
   * 1 -> 0001
   * 2 -> 0010
   * 3 -> 0100
   * 4 -> 1000
   */
  public static int toBinaryFlag(int flag) {
    return 1 << (flag - 1);
  }
}
