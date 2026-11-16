/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import android.content.res.Resources
import androidx.annotation.VisibleForTesting
import androidx.core.os.ConfigurationCompat
import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.signal.core.util.logging.Log
import java.util.Currency
import java.util.Locale

/**
 * Sometimes we want to gate something by region, and historically we've used country code for that. Can't do
 * that with numberless accounts. For these accounts, we use a combination of the user's locale and any other
 * relevant info (like a selected currency) to determine if they match a list of country codes.
 */
object NumberlessRegionMatcher {

  private val TAG = Log.tag(NumberlessRegionMatcher::class)

  /**
   * Whether the current device's system locales and the [currency] the user selected match the regions
   * covered by [countryAndAreaCodes]. Pass a null [currency] when the user has not selected one yet, in which case
   * only the locales are checked.
   */
  @JvmStatic
  fun matches(countryAndAreaCodes: Collection<String>, currency: Currency?): Boolean {
    val matches = matches(
      countryAndAreaCodes = countryAndAreaCodes,
      locales = getSystemLocales(),
      currency = currency
    )

    Log.d(TAG, "Numberless region fallback match: $matches")
    return matches
  }

  /**
   * A user matches when both:
   *  - One of their [locales] has its country set to an enabled region. Locales without a country never match,
   *    since a language alone (e.g. "en") is spoken across too many regions to say anything useful.
   *  - Their selected [currency], if any, is used in an enabled region.
   */
  @VisibleForTesting
  fun matches(countryAndAreaCodes: Collection<String>, locales: List<Locale>, currency: Currency?): Boolean {
    val regions = getRegionsForCallingCodes(countryAndAreaCodes)
    if (regions.isEmpty()) {
      return false
    }

    if (currency != null && currency !in getCurrenciesForRegions(regions)) {
      return false
    }

    return locales.any { locale ->
      locale.country.isNotEmpty() && locale.country.uppercase(Locale.ROOT) in regions
    }
  }

  /**
   * Maps each calling code in [countryAndAreaCodes] to the ISO 3166 region codes that share it. Entries narrowed
   * by an area code (e.g. "1555") cannot be represented by a whole region, so they are skipped.
   */
  @VisibleForTesting
  fun getRegionsForCallingCodes(countryAndAreaCodes: Collection<String>): Set<String> {
    val phoneNumberUtil = PhoneNumberUtil.getInstance()

    return countryAndAreaCodes
      .map { it.filter(Char::isDigit) }
      .filter { it.isNotEmpty() && it.length <= 3 }
      .flatMap { phoneNumberUtil.getRegionCodesForCountryCode(it.toInt()) }
      .filter { it != PhoneNumberUtil.REGION_CODE_FOR_NON_GEO_ENTITY && it != "ZZ" }
      .toSet()
  }

  @VisibleForTesting
  fun getCurrenciesForRegions(regions: Set<String>): Set<Currency> {
    return regions.mapNotNull { region ->
      try {
        Currency.getInstance(Locale("", region))
      } catch (e: IllegalArgumentException) {
        null
      }
    }.toSet()
  }

  /**
   * System locales are used rather than [Locale.getDefault], since the latter reflects the in-app language
   * override, which says nothing about where the user is.
   */
  private fun getSystemLocales(): List<Locale> {
    val localeList = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
    return (0 until localeList.size()).mapNotNull { localeList[it] }
  }
}
