package org.signal.core.util.money

import java.util.Currency

/**
 * Utility methods for java.util.Currency
 *
 * This is prefixed with "Platform" as there are several different Currency classes
 * available in the app, and this utility class is specifically for dealing with
 * java.util.Currency
 */
object PlatformCurrencyUtil {

  val USD: Currency = Currency.getInstance("USD")

  /**
   * Note: Adding this as an extension method of Currency causes some confusion in
   *       AndroidStudio due to a separate Currency class from the AndroidSDK having
   *       an extension method of the same signature.
   */
  fun getAvailableCurrencyCodes(): Set<String> {
    return Currency.getAvailableCurrencies().map { it.currencyCode }.toSet()
  }
}
