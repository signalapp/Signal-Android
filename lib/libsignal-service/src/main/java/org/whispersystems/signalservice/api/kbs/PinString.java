/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.kbs;

final class PinString {

  static boolean allNumeric(CharSequence pin) {
    for (int i = 0; i < pin.length(); i++) {
      if (!Character.isDigit(pin.charAt(i))) return false;
    }
    return true;
  }

  /**
   * Converts a string of not necessarily Arabic numerals to Arabic 0..9 characters.
   */
  static String toArabic(CharSequence numerals) {
    int length = numerals.length();
    char[] arabic = new char[length];

    for (int i = 0; i < length; i++) {
      int digit = Character.digit(numerals.charAt(i), 10);

      arabic[i] = (char) ('0' + digit);
    }

    return new String(arabic);
  }
}
