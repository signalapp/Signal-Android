/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import org.signal.core.util.E164Util
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * A wrapper around [E164Util] that automatically handles fetching our own number and caching formatters.
 */
object SignalE164Util {

  private val cachedFormatters: MutableMap<String, E164Util.Formatter> = LRUCache(2)
  private val defaultFormatter: E164Util.Formatter by lazy {
    E164Util.Formatter(
      localNumber = null,
      localAreaCode = null,
      localRegionCode = Util.getSimCountryIso(AppDependencies.application).orElse("US")
    )
  }

  /**
   * Formats the number for human-readable display. e.g. "(555) 555-5555"
   */
  @JvmStatic
  fun prettyPrint(input: String): String {
    return getFormatter().prettyPrint(input)
  }

  /**
   * Returns the country code for the local number, if present. Otherwise, it returns 0.
   */
  fun getLocalCountryCode(): Int {
    return getFormatter().localNumber?.countryCode ?: 0
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
  @JvmStatic
  fun formatAsE164(input: String): String? {
    return getFormatter().formatAsE164(input)
  }

  private fun getFormatter(): E164Util.Formatter {
    val localNumber = SignalStore.account.e164 ?: return defaultFormatter
    val formatter = cachedFormatters[localNumber]
    if (formatter != null) {
      return formatter
    }

    synchronized(cachedFormatters) {
      val formatter = cachedFormatters[localNumber]
      if (formatter != null) {
        return formatter
      }

      val newFormatter = E164Util.createFormatterForE164(localNumber)
      cachedFormatters[localNumber] = newFormatter
      return newFormatter
    }
  }
}
