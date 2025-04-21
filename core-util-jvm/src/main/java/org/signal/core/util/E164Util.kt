/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import com.google.i18n.phonenumbers.ShortNumberInfo
import org.signal.core.util.logging.Log
import java.util.Locale
import java.util.Optional
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Contains a bunch of utility functions to parse and format phone numbers.
 */
object E164Util {
  private val TAG = Log.tag(E164Util::class)

  private const val COUNTRY_CODE_BR = "55"
  private const val COUNTRY_CODE_US = "1"

  private val US_NO_AREACODE: Pattern = Pattern.compile("^(\\d{7})$")
  private val BR_NO_AREACODE: Pattern = Pattern.compile("^(9?\\d{8})$")

  private const val COUNTRY_CODE_US_INT = 1
  private const val COUNTRY_CODE_UK_INT = 44

  /** A set of country codes representing countries where we'd like to use the (555) 555-5555 number format for pretty printing. */
  private val NATIONAL_FORMAT_COUNTRY_CODES = setOf(COUNTRY_CODE_US_INT, COUNTRY_CODE_UK_INT)

  /**
   * Creates a formatter based on the provided local number. This is largely an improvement in performance/convenience
   * over parsing out the various number attributes themselves and caching them manually.
   *
   * It is assumed that this number is properly formatted. If it is not, this may throw a [NumberParseException].
   *
   * @throws NumberParseException
   */
  fun createFormatterForE164(localNumber: String): Formatter {
    val phoneNumber = PhoneNumberUtil.getInstance().parse(localNumber, null)
    val regionCode = PhoneNumberUtil.getInstance().getRegionCodeForNumber(phoneNumber) ?: PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(phoneNumber.countryCode)
    val areaCode = parseAreaCode(localNumber, phoneNumber.countryCode)

    return Formatter(localNumber = phoneNumber, localAreaCode = areaCode, localRegionCode = regionCode)
  }

  /**
   * Creates a formatter based on the provided region code. This is largely an improvement in performance/convenience
   * over parsing out the various number attributes themselves and caching them manually.
   */
  fun createFormatterForRegionCode(regionCode: String): Formatter {
    return Formatter(localNumber = null, localAreaCode = null, localRegionCode = regionCode)
  }

  /**
   * The same as [formatAsE164WithCountryCode], but if we determine the number to be invalid,
   * we will do some cleanup to *roughly* format it as E164.
   *
   * IMPORTANT: Do not use this for actual number storage! There is no guarantee that this
   * will be a properly-formatted E164 number. It should only be used in situations where a
   * value is needed for user display.
   */
  @JvmStatic
  fun formatAsE164WithCountryCodeForDisplay(countryCode: String, input: String?): String {
    val input = input ?: ""

    val result: String? = formatAsE164WithCountryCode(countryCode, input)
    if (result != null) {
      return result
    }

    val cleanCountryCode = countryCode
      .numbersOnly()
      .replace("^0*".toRegex(), "")
    val cleanNumber = input.numbersOnly()

    return "+$cleanCountryCode$cleanNumber"
  }

  /**
   * Returns whether or not an input number is valid for registration. Besides checking to ensure that libphonenumber thinks it's a possible number at all,
   * we also have a few country-specific checks, as well as some of our own length and formatting checks.
   */
  @JvmStatic
  fun isValidNumberForRegistration(countryCode: String, input: String): Boolean {
    if (!PhoneNumberUtil.getInstance().isPossibleNumber(input, countryCode)) {
      Log.w(TAG, "Failed isPossibleNumber()")
      return false
    }

    if (COUNTRY_CODE_US == countryCode && !Pattern.matches("^\\+1[0-9]{10}$", input)) {
      Log.w(TAG, "Failed US number format check")
      return false
    }

    if (COUNTRY_CODE_BR == countryCode && !Pattern.matches("^\\+55[0-9]{2}9?[0-9]{8}$", input)) {
      Log.w(TAG, "Failed Brazil number format check")
      return false
    }

    return input.matches("^\\+[1-9][0-9]{6,14}$".toRegex())
  }

  /**
   * Given a regionCode, this will attempt to provide the display name for that region.
   */
  @JvmStatic
  fun getRegionDisplayName(regionCode: String?): Optional<String> {
    if (regionCode == null || regionCode == "ZZ" || regionCode == PhoneNumberUtil.REGION_CODE_FOR_NON_GEO_ENTITY) {
      return Optional.empty()
    }

    val displayCountry: String? = Locale("", regionCode).getDisplayCountry(Locale.getDefault()).nullIfBlank()
    return Optional.ofNullable(displayCountry)
  }

  /**
   * Identical to [formatAsE164WithRegionCode], except rather than supply the region code, you supply the
   * country code (i.e. "1" for the US). This will convert the country code to a region code on your behalf.
   * See [formatAsE164WithRegionCode] for behavior.
   */
  private fun formatAsE164WithCountryCode(countryCode: String, input: String): String? {
    val regionCode = try {
      val countryCodeInt = countryCode.toInt()
      PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCodeInt)
    } catch (e: NumberFormatException) {
      return null
    }

    return formatAsE164WithRegionCode(
      localNumber = null,
      localAreaCode = null,
      regionCode = regionCode,
      input = input
    )
  }

  /**
   * Formats the number as an E164, or null if the number cannot be reasonably interpreted as a phone number.
   * This does not check if the number is *valid* for a given region. Instead, it's very lenient and just
   * does it's best to interpret the input string as a number that could be put into the E164 format.
   *
   * Note that shortcodes will not have leading '+' signs.
   *
   * In other words, if this method returns null, you likely do not have anything that could be considered
   * a phone number.
   */
  private fun formatAsE164WithRegionCode(localNumber: PhoneNumber?, localAreaCode: String?, regionCode: String, input: String): String? {
    try {
      val correctedInput = input.e164CharsOnly().stripLeadingZerosFromInput()
      if (correctedInput.trimStart('0').length < 3) {
        return null
      }

      val withAreaCodeRules: String = applyAreaCodeRules(localNumber, localAreaCode, correctedInput)
      val parsedNumber: PhoneNumber = PhoneNumberUtil.getInstance().parse(withAreaCodeRules, regionCode)

      val isShortCode = ShortNumberInfo.getInstance().isValidShortNumberForRegion(parsedNumber, regionCode) || withAreaCodeRules.length <= 5
      if (isShortCode) {
        return correctedInput.numbersOnly().stripLeadingZerosFromE164()
      }

      return PhoneNumberUtil.getInstance().format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.E164).stripLeadingZerosFromE164()
    } catch (e: NumberParseException) {
      return null
    }
  }

  /**
   * Attempts to parse the area code out of an e164-formatted number provided that it's in one of the supported countries.
   */
  private fun parseAreaCode(e164Number: String, countryCode: Int): String? {
    when (countryCode) {
      1 -> return e164Number.substring(2, 5)
      55 -> return e164Number.substring(3, 5)
    }
    return null
  }

  /**
   * Given an input number, this will attempt to add in an area code for certain locales if we have one in the local number.
   * For example, in the US, if your local number is (610) 555-5555, and we're given a `testNumber` of 123-4567, we could
   * assume that the full number would be (610) 123-4567.
   */
  private fun applyAreaCodeRules(localNumber: PhoneNumber?, localAreaCode: String?, testNumber: String): String {
    if (localNumber === null || localAreaCode == null) {
      return testNumber
    }

    val matcher: Matcher? = when (localNumber.countryCode) {
      1 -> US_NO_AREACODE.matcher(testNumber)
      55 -> BR_NO_AREACODE.matcher(testNumber)
      else -> null
    }

    if (matcher != null && matcher.matches()) {
      return localAreaCode + matcher.group()
    }

    return testNumber
  }

  private fun String.numbersOnly(): String {
    return this.filter { it.isDigit() }
  }

  private fun String.e164CharsOnly(): String {
    return this.filter { it.isDigit() || it == '+' }
  }

  /**
   * Strips out bad leading zeros from input strings that can confuse libphonenumber.
   */
  private fun String.stripLeadingZerosFromInput(): String {
    return if (this.startsWith("+0")) {
      "+" + this.substring(1).trimStart('0')
    } else {
      this
    }
  }

  /**
   * Strips out leading zeros from a string after it's been e164-formatted by libphonenumber.
   */
  private fun String.stripLeadingZerosFromE164(): String {
    return if (this.startsWith("0")) {
      this.trimStart('0')
    } else if (this.startsWith("+0")) {
      "+" + this.substring(1).trimStart('0')
    } else {
      this
    }
  }

  class Formatter(
    val localNumber: PhoneNumber?,
    val localAreaCode: String?,
    val localRegionCode: String
  ) {
    /**
     * Formats the number as an E164, or null if the number cannot be reasonably interpreted as a phone number.
     * This does not check if the number is *valid* for a given region. Instead, it's very lenient and just
     * does it's best to interpret the input string as a number that could be put into the E164 format.
     *
     * Note that shortcodes will not have leading '+' signs.
     *
     * In other words, if this method returns null, you likely do not have anything that could be considered
     * a phone number.
     */
    fun formatAsE164(input: String): String? {
      return formatAsE164WithRegionCode(
        localNumber = localNumber,
        localAreaCode = localAreaCode,
        regionCode = localRegionCode,
        input = input
      )
    }

    /**
     * Formats the number for human-readable display. e.g. "(555) 555-5555"
     */
    fun prettyPrint(input: String): String {
      val raw = try {
        val parsedNumber: PhoneNumber = PhoneNumberUtil.getInstance().parse(input, localRegionCode)

        return if (localNumber != null && localNumber.countryCode == parsedNumber.countryCode && NATIONAL_FORMAT_COUNTRY_CODES.contains(localNumber.countryCode)) {
          PhoneNumberUtil.getInstance().format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
        } else {
          PhoneNumberUtil.getInstance().format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
        }
      } catch (e: NumberParseException) {
        Log.w(TAG, "Failed to format number: $e")
        input
      }

      return BidiUtil.forceLtr(BidiUtil.isolateBidi(raw))
    }
  }
}
