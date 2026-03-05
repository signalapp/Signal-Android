/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util;

import java.util.Locale;

/**
 * A set of utilities to make working with Bitmasks easier.
 */
public final class Bitmask {

  /**
   * Reads a bitmasked boolean from a long at the requested position.
   */
  public static boolean read(long value, int position) {
    return read(value, position, 1) > 0;
  }

  /**
   * Reads a bitmasked value from a long at the requested position.
   *
   * @param value       The value your are reading state from
   * @param position    The position you'd like to read from
   * @param flagBitSize How many bits are in each flag
   * @return The value at the requested position
   */
  public static long read(long value, int position, int flagBitSize) {
    checkArgument(flagBitSize >= 0, "Must have a positive bit size! size: " + flagBitSize);

    int bitsToShift = position * flagBitSize;
    checkArgument(bitsToShift + flagBitSize <= 64 && position >= 0, String.format(Locale.US, "Your position is out of bounds! position: %d, flagBitSize: %d", position, flagBitSize));

    long shifted = value >>> bitsToShift;
    long mask    = twoToThe(flagBitSize) - 1;

    return shifted & mask;
  }

  /**
   * Sets the value at the specified position in a single-bit bitmasked long.
   */
  public static long update(long existing, int position, boolean value) {
    return update(existing, position, 1, value ? 1 : 0);
  }

  /**
   * Updates the value in a bitmasked long.
   *
   * @param existing    The existing state of the bitmask
   * @param position    The position you'd like to update
   * @param flagBitSize How many bits are in each flag
   * @param value       The value you'd like to set at the specified position
   * @return The updated bitmask
   */
  public static long update(long existing, int position, int flagBitSize, long value) {
    checkArgument(flagBitSize >= 0, "Must have a positive bit size! size: " + flagBitSize);
    checkArgument(value >= 0, "Value must be positive! value: " + value);
    checkArgument(value < twoToThe(flagBitSize), String.format(Locale.US, "Value is larger than you can hold for the given bitsize! value: %d, flagBitSize: %d", value, flagBitSize));

    int bitsToShift = position * flagBitSize;
    checkArgument(bitsToShift + flagBitSize <= 64 && position >= 0, String.format(Locale.US, "Your position is out of bounds! position: %d, flagBitSize: %d", position, flagBitSize));

    long clearMask    = ~((twoToThe(flagBitSize) - 1) << bitsToShift);
    long cleared      = existing & clearMask;
    long shiftedValue = value << bitsToShift;

    return cleared | shiftedValue;
  }

  /** Simple method to do 2^n. Giving it a name just so it's clear what's happening. */
  private static long twoToThe(long n) {
    return 1 << n;
  }

  private static void checkArgument(boolean state, String message) {
    if (!state) {
      throw new IllegalArgumentException(message);
    }
  }
}
