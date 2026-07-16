package org.signal.registration.screens.countrycode

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

/**
 * Utility functions used when working with countries
 */
object CountryUtils {

  /**
   * Representative country for the most popular languages, used to guess a region when a locale carries no explicit
   * country (e.g. a bare "en" or "de"). Intentionally not exhaustive; unmapped languages fall through to the caller's
   * default.
   */
  private val LANGUAGE_TO_REGION: Map<String, String> = mapOf(
    "ar" to "SA",
    "bn" to "BD",
    "cs" to "CZ",
    "da" to "DK",
    "de" to "DE",
    "el" to "GR",
    "en" to "US",
    "es" to "ES",
    "fa" to "IR",
    "fi" to "FI",
    "fr" to "FR",
    "he" to "IL",
    "hi" to "IN",
    "hu" to "HU",
    "id" to "ID",
    "it" to "IT",
    "ja" to "JP",
    "ko" to "KR",
    "nb" to "NO",
    "nl" to "NL",
    "nn" to "NO",
    "no" to "NO",
    "pl" to "PL",
    "pt" to "BR",
    "ro" to "RO",
    "ru" to "RU",
    "sv" to "SE",
    "th" to "TH",
    "tr" to "TR",
    "uk" to "UA",
    "vi" to "VN",
    "zh" to "CN"
  )

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

  /**
   * Derives a best-guess dialing region code from a [locale]. Prefers the locale's explicit country when it maps to a
   * real dialing region, then falls back to a representative country for the locale's language. Returns null if neither
   * yields a usable region.
   */
  fun localeToRegionCode(locale: Locale): String? {
    val country = locale.country.uppercase(Locale.US)
    if (country.isNotEmpty() && PhoneNumberUtil.getInstance().getCountryCodeForRegion(country) != 0) {
      return country
    }

    return LANGUAGE_TO_REGION[locale.language.lowercase(Locale.US)]
  }
}
