/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.countrycode

import java.util.Locale

/**
 * Utility functions used when working with countries
 */
object CountryUtils {

  fun countryToEmoji(countryCode: String): String {
    return if (countryCode.isNotEmpty()) {
      countryCode
        .uppercase(Locale.US)
        .map { char -> Character.codePointAt("$char", 0) - 0x41 + 0x1F1E6 }
        .map { codePoint -> Character.toChars(codePoint) }
        .joinToString(separator = "") { charArray -> String(charArray) }
    } else {
      ""
    }
  }

  /** A hardcoded list of countries to suggest during registration. Can change at any time. */
  val COMMON_COUNTRIES = listOf("US", "DE", "IN", "NL", "UA")
}
