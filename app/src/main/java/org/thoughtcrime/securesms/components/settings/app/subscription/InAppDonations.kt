package org.thoughtcrime.securesms.components.settings.app.subscription

import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonateToSignalType
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

  fun isPaymentSourceAvailable(paymentSourceType: PaymentSourceType, donateToSignalType: DonateToSignalType): Boolean {
    return when (paymentSourceType) {
      PaymentSourceType.PayPal -> isPayPalAvailableForDonateToSignalType(donateToSignalType)
      PaymentSourceType.Stripe.CreditCard -> isCreditCardAvailable()
      PaymentSourceType.Stripe.GooglePay -> isGooglePayAvailable()
      PaymentSourceType.Unknown -> false
    }
  }

  private fun isPayPalAvailableForDonateToSignalType(donateToSignalType: DonateToSignalType): Boolean {
    return when (donateToSignalType) {
      DonateToSignalType.ONE_TIME, DonateToSignalType.GIFT -> FeatureFlags.paypalOneTimeDonations()
      DonateToSignalType.MONTHLY -> FeatureFlags.paypalRecurringDonations()
    } && !LocaleFeatureFlags.isPayPalDisabled()
  }

  /**
   * Whether the user is in a region that supports credit cards, based off local phone number.
   */
  private fun isCreditCardAvailable(): Boolean {
    return FeatureFlags.creditCardPayments() && !LocaleFeatureFlags.isCreditCardDisabled()
  }

  /**
   * Whether the user is in a region that supports PayPal, based off local phone number.
   */
  private fun isPayPalAvailable(): Boolean {
    return (FeatureFlags.paypalOneTimeDonations() || FeatureFlags.paypalRecurringDonations()) && !LocaleFeatureFlags.isPayPalDisabled()
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
