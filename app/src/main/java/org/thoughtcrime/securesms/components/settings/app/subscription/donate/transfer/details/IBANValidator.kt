/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.transfer.details

import java.math.BigInteger

object IBANValidator {

  private val countryCodeToLength: Map<String, Int> by lazy {
    mapOf(
      "AL" to 28,
      "AD" to 24,
      "AT" to 20,
      "AZ" to 28,
      "BH" to 22,
      "BY" to 28,
      "BE" to 16,
      "BA" to 20,
      "BR" to 29,
      "BG" to 22,
      "CR" to 22,
      "HR" to 21,
      "CY" to 28,
      "CZ" to 24,
      "DK" to 18,
      "DO" to 28,
      "TL" to 23,
      "EG" to 29,
      "SV" to 28,
      "EE" to 20,
      "FO" to 18,
      "FI" to 18,
      "FR" to 27,
      "GE" to 22,
      "DE" to 22,
      "GI" to 23,
      "GR" to 27,
      "GL" to 18,
      "GT" to 28,
      "HU" to 28,
      "IS" to 26,
      "IQ" to 23,
      "IE" to 22,
      "IL" to 23,
      "IT" to 27,
      "JO" to 30,
      "KZ" to 20,
      "XK" to 20,
      "KW" to 30,
      "LV" to 21,
      "LB" to 28,
      "LY" to 25,
      "LI" to 21,
      "LT" to 20,
      "LU" to 20,
      "MT" to 31,
      "MR" to 27,
      "MU" to 30,
      "MC" to 27,
      "MD" to 24,
      "ME" to 22,
      "NL" to 18,
      "MK" to 19,
      "NO" to 15,
      "PK" to 24,
      "PS" to 29,
      "PL" to 28,
      "PT" to 25,
      "QA" to 29,
      "RO" to 24,
      "RU" to 33,
      "LC" to 32,
      "SM" to 27,
      "ST" to 25,
      "SA" to 24,
      "RS" to 22,
      "SC" to 31,
      "SK" to 24,
      "SI" to 19,
      "ES" to 24,
      "SD" to 18,
      "SE" to 24,
      "CH" to 21,
      "TN" to 24,
      "TR" to 26,
      "UA" to 29,
      "AE" to 23,
      "GB" to 22,
      "VA" to 22,
      "VG" to 24
    )
  }

  fun validate(iban: String, isIBANFieldFocused: Boolean): Validity {
    val trimmedIban = iban.trim()

    if (trimmedIban.isEmpty()) {
      return Validity.POTENTIALLY_VALID
    }

    val lengthValidity = validateLength(trimmedIban, isIBANFieldFocused)
    if (lengthValidity != Validity.COMPLETELY_VALID) {
      return lengthValidity
    }

    val countryAndCheck = trimmedIban.take(4)
    val rearranged = trimmedIban.drop(4) + countryAndCheck
    val expanded = rearranged.map {
      if (it.isLetter()) {
        (it - 'A') + 10
      } else if (it.isDigit()) {
        it.digitToInt()
      } else {
        return Validity.INVALID_CHARACTERS
      }
    }.joinToString("")
    val bigInteger = BigInteger(expanded)
    if (bigInteger.mod(BigInteger.valueOf(97L)) == BigInteger.ONE) {
      return Validity.COMPLETELY_VALID
    }

    return Validity.INVALID_MOD_97
  }

  private fun validateLength(iban: String, isIBANFieldFocused: Boolean): Validity {
    if (iban.length < 2) {
      return if (isIBANFieldFocused) {
        Validity.POTENTIALLY_VALID
      } else {
        Validity.TOO_SHORT
      }
    }

    val countryCode = iban.take(2)
    val requiredLength = countryCodeToLength[countryCode] ?: -1
    if (requiredLength == -1) {
      return Validity.INVALID_COUNTRY
    }

    if (requiredLength > iban.length) {
      return if (isIBANFieldFocused) Validity.POTENTIALLY_VALID else Validity.TOO_SHORT
    }

    if (requiredLength < iban.length) {
      return Validity.TOO_LONG
    }

    return Validity.COMPLETELY_VALID
  }

  enum class Validity(val isError: Boolean) {
    TOO_SHORT(true),
    TOO_LONG(true),
    INVALID_COUNTRY(true),
    INVALID_CHARACTERS(true),
    INVALID_MOD_97(true),
    POTENTIALLY_VALID(false),
    COMPLETELY_VALID(false)
  }
}
