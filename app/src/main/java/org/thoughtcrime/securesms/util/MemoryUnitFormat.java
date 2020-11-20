package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;

import java.text.DecimalFormat;

/**
 * Used for the pretty formatting of bytes for user display.
 */
public enum MemoryUnitFormat {
  BYTES(" B"),
  KILO_BYTES(" kB"),
  MEGA_BYTES(" MB"),
  GIGA_BYTES(" GB"),
  TERA_BYTES(" TB");

  private static final DecimalFormat ONE_DP          = new DecimalFormat("#,##0.0");
  private static final DecimalFormat OPTIONAL_ONE_DP = new DecimalFormat("#,##0.#");

  private final String unitString;

  MemoryUnitFormat(String unitString) {
    this.unitString = unitString;
  }

  public double fromBytes(long bytes) {
    return bytes / Math.pow(1000, ordinal());
  }

  /**
   * Creates a string suitable to present to the user from the specified {@param bytes}.
   * It will pick a suitable unit of measure to display depending on the size of the bytes.
   * It will not select a unit of measure lower than the specified {@param minimumUnit}.
   *
   * @param forceOneDp If true, will include 1 decimal place, even if 0. If false, will only show 1 dp when it's non-zero.
   */
  public static String formatBytes(long bytes, @NonNull MemoryUnitFormat minimumUnit, boolean forceOneDp) {
    if (bytes <= 0) bytes = 0;

    int ordinal = bytes != 0 ? (int) (Math.log10(bytes) / 3) : 0;

    if (ordinal >= MemoryUnitFormat.values().length) {
      ordinal = MemoryUnitFormat.values().length - 1;
    }

    MemoryUnitFormat unit = MemoryUnitFormat.values()[ordinal];

    if (unit.ordinal() < minimumUnit.ordinal()) {
      unit = minimumUnit;
    }

    return (forceOneDp ? ONE_DP : OPTIONAL_ONE_DP).format(unit.fromBytes(bytes)) + unit.unitString;
  }

  public static String formatBytes(long bytes) {
    return formatBytes(bytes, BYTES, false);
  }
}
