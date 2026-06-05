package org.signal.registration.screens.countrycode

import java.util.Locale

/**
 * Utility functions used when working with countries
 */
object CountryUtils {

  @JvmStatic
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
}
