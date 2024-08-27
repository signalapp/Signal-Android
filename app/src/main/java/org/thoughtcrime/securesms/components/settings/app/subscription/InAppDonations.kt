package org.thoughtcrime.securesms.components.settings.app.subscription

import org.signal.donations.InAppPaymentType
import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.Environment
import org.thoughtcrime.securesms.util.LocaleRemoteConfig
import org.thoughtcrime.securesms.util.RemoteConfig

/**
 * Helper object to determine in-app donations availability.
 */
object InAppDonations {

  /**
   * The user is:
   *
   * - Able to use Credit Cards and is in a region where they are able to be accepted.
   * - Able to access Google Play services (and thus possibly able to use Google Pay).
   * - Able to use SEPA Debit and is in a region where they are able to be accepted.
   * - Able to use PayPal and is in a region where it is able to be accepted.
   */
  fun hasAtLeastOnePaymentMethodAvailable(): Boolean {
    return isCreditCardAvailable() || isPayPalAvailable() || isGooglePayAvailable() || isSEPADebitAvailable() || isIDEALAvailable()
  }

  fun isPaymentSourceAvailable(paymentSourceType: PaymentSourceType, inAppPaymentType: InAppPaymentType): Boolean {
    return when (paymentSourceType) {
      PaymentSourceType.PayPal -> isPayPalAvailableForDonateToSignalType(inAppPaymentType)
      PaymentSourceType.Stripe.CreditCard -> isCreditCardAvailable()
      PaymentSourceType.Stripe.GooglePay -> isGooglePayAvailable()
      PaymentSourceType.Stripe.SEPADebit -> isSEPADebitAvailableForDonateToSignalType(inAppPaymentType)
      PaymentSourceType.Stripe.IDEAL -> isIDEALAvailbleForDonateToSignalType(inAppPaymentType)
      PaymentSourceType.Unknown -> false
    }
  }

  private fun isPayPalAvailableForDonateToSignalType(inAppPaymentType: InAppPaymentType): Boolean {
    return when (inAppPaymentType) {
      InAppPaymentType.UNKNOWN -> error("Unsupported type UNKNOWN")
      InAppPaymentType.ONE_TIME_DONATION, InAppPaymentType.ONE_TIME_GIFT -> RemoteConfig.paypalOneTimeDonations
      InAppPaymentType.RECURRING_DONATION -> RemoteConfig.paypalRecurringDonations
      InAppPaymentType.RECURRING_BACKUP -> RemoteConfig.messageBackups && RemoteConfig.paypalRecurringDonations
    } && !LocaleRemoteConfig.isPayPalDisabled()
  }

  /**
   * Whether the user is in a region that supports credit cards, based off local phone number.
   */
  fun isCreditCardAvailable(): Boolean {
    return !LocaleRemoteConfig.isCreditCardDisabled()
  }

  /**
   * Whether the user is in a region that supports PayPal, based off local phone number.
   */
  fun isPayPalAvailable(): Boolean {
    return (RemoteConfig.paypalOneTimeDonations || RemoteConfig.paypalRecurringDonations) && !LocaleRemoteConfig.isPayPalDisabled()
  }

  /**
   * Whether the user is using a device that supports GooglePay, based off Wallet API and phone number.
   */
  fun isGooglePayAvailable(): Boolean {
    return SignalStore.inAppPayments.isGooglePayReady && !LocaleRemoteConfig.isGooglePayDisabled()
  }

  /**
   * Whether the user is in a region which supports SEPA Debit transfers, based off local phone number.
   */
  fun isSEPADebitAvailable(): Boolean {
    return Environment.IS_STAGING || (RemoteConfig.sepaDebitDonations && LocaleRemoteConfig.isSepaEnabled())
  }

  /**
   * Whether the user is in a region which supports IDEAL transfers, based off local phone number.
   */
  fun isIDEALAvailable(): Boolean {
    return Environment.IS_STAGING || (RemoteConfig.idealDonations && LocaleRemoteConfig.isIdealEnabled())
  }

  /**
   * Whether the user is in a region which supports SEPA Debit transfers, based off local phone number
   * and donation type.
   */
  fun isSEPADebitAvailableForDonateToSignalType(inAppPaymentType: InAppPaymentType): Boolean {
    return inAppPaymentType != InAppPaymentType.ONE_TIME_GIFT && isSEPADebitAvailable()
  }

  /**
   * Whether the user is in a region which suports IDEAL transfers, based off local phone number and
   * donation type
   */
  fun isIDEALAvailbleForDonateToSignalType(inAppPaymentType: InAppPaymentType): Boolean {
    return inAppPaymentType != InAppPaymentType.ONE_TIME_GIFT && isIDEALAvailable()
  }
}
