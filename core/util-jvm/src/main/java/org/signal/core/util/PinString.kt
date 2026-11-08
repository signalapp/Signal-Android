/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

object PinString {

  @JvmStatic
  fun allNumeric(pin: CharSequence): Boolean {
    return pin.all { Character.isDigit(it) }
  }

  /**
   * Converts a string of not necessarily Arabic numerals to Arabic 0..9 characters.
   */
  @JvmStatic
  fun toArabic(numerals: CharSequence): String {
    return String(CharArray(numerals.length) { i -> '0' + Character.digit(numerals[i], 10) })
  }
}
