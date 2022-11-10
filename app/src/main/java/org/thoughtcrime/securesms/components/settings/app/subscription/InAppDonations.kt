package org.thoughtcrime.securesms.components.settings.app.subscription

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.LocaleFeatureFlags
import org.thoughtcrime.securesms.util.PlayServicesUtil

/**
 * Helper object to determine in-app donations availability.
 */
object InAppDonations {

  /**
   * The user is:
   *
   * - Able to use Credit Cards and is in a region where they are able to be accepted.
   * - Able to access Google Play services (and thus possibly able to use Google Pay).
   * - Able to use PayPal and is in a region where it is able to be accepted.
   */
  fun hasAtLeastOnePaymentMethodAvailable(): Boolean {
    return isCreditCardAvailable() || isPayPalAvailable() || isGooglePayAvailable()
  }

  /**
   * Whether the user is in a region that supports credit cards, based off local phone number.
   */
  fun isCreditCardAvailable(): Boolean {
    return FeatureFlags.creditCardPayments() && !LocaleFeatureFlags.isCreditCardDisabled()
  }

  /**
   * Whether the user is in a region that supports PayPal, based off local phone number.
   */
  fun isPayPalAvailable(): Boolean {
    return false
  }

  /**
   * Whether the user is in a region that supports GooglePay, based off local phone number.
   */
  private fun isGooglePayAvailable(): Boolean {
    return isPlayServicesAvailable() && !LocaleFeatureFlags.isGooglePayDisabled()
  }

  /**
   * Whether Play Services is available. This will *not* tell you whether a user has Google Pay set up, but is
   * enough information to determine whether we can display Google Pay as an option.
   */
  private fun isPlayServicesAvailable(): Boolean {
    return PlayServicesUtil.getPlayServicesStatus(ApplicationDependencies.getApplication()) == PlayServicesUtil.PlayServicesStatus.SUCCESS
  }
}
