package org.thoughtcrime.securesms.components.settings.app.subscription

import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonateToSignalType
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.LocaleFeatureFlags

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
  fun isCreditCardAvailable(): Boolean {
    return FeatureFlags.creditCardPayments() && !LocaleFeatureFlags.isCreditCardDisabled()
  }

  /**
   * Whether the user is in a region that supports PayPal, based off local phone number.
   */
  fun isPayPalAvailable(): Boolean {
    return (FeatureFlags.paypalOneTimeDonations() || FeatureFlags.paypalRecurringDonations()) && !LocaleFeatureFlags.isPayPalDisabled()
  }

  /**
   * Whether the user is using a device that supports GooglePay, based off Wallet API and phone number.
   */
  fun isGooglePayAvailable(): Boolean {
    return SignalStore.donationsValues().isGooglePayReady && !LocaleFeatureFlags.isGooglePayDisabled()
  }
}
