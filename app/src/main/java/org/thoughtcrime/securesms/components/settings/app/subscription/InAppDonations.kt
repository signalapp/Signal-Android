package org.thoughtcrime.securesms.components.settings.app.subscription

import org.signal.donations.PaymentSourceType
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.Environment
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
   * - Able to use SEPA Debit and is in a region where they are able to be accepted.
   * - Able to use PayPal and is in a region where it is able to be accepted.
   */
  fun hasAtLeastOnePaymentMethodAvailable(): Boolean {
    return isCreditCardAvailable() || isPayPalAvailable() || isGooglePayAvailable() || isSEPADebitAvailable() || isIDEALAvailable()
  }

  fun isPaymentSourceAvailable(paymentSourceType: PaymentSourceType, inAppPaymentType: InAppPaymentTable.Type): Boolean {
    return when (paymentSourceType) {
      PaymentSourceType.PayPal -> isPayPalAvailableForDonateToSignalType(inAppPaymentType)
      PaymentSourceType.Stripe.CreditCard -> isCreditCardAvailable()
      PaymentSourceType.Stripe.GooglePay -> isGooglePayAvailable()
      PaymentSourceType.Stripe.SEPADebit -> isSEPADebitAvailableForDonateToSignalType(inAppPaymentType)
      PaymentSourceType.Stripe.IDEAL -> isIDEALAvailbleForDonateToSignalType(inAppPaymentType)
      PaymentSourceType.Unknown -> false
    }
  }

  private fun isPayPalAvailableForDonateToSignalType(inAppPaymentType: InAppPaymentTable.Type): Boolean {
    return when (inAppPaymentType) {
      InAppPaymentTable.Type.UNKNOWN -> error("Unsupported type UNKNOWN")
      InAppPaymentTable.Type.ONE_TIME_DONATION, InAppPaymentTable.Type.ONE_TIME_GIFT -> FeatureFlags.paypalOneTimeDonations()
      InAppPaymentTable.Type.RECURRING_DONATION -> FeatureFlags.paypalRecurringDonations()
      InAppPaymentTable.Type.RECURRING_BACKUP -> FeatureFlags.messageBackups() && FeatureFlags.paypalRecurringDonations()
    } && !LocaleFeatureFlags.isPayPalDisabled()
  }

  /**
   * Whether the user is in a region that supports credit cards, based off local phone number.
   */
  fun isCreditCardAvailable(): Boolean {
    return !LocaleFeatureFlags.isCreditCardDisabled()
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

  /**
   * Whether the user is in a region which supports SEPA Debit transfers, based off local phone number.
   */
  fun isSEPADebitAvailable(): Boolean {
    return Environment.IS_STAGING || (FeatureFlags.sepaDebitDonations() && LocaleFeatureFlags.isSepaEnabled())
  }

  /**
   * Whether the user is in a region which supports IDEAL transfers, based off local phone number.
   */
  fun isIDEALAvailable(): Boolean {
    return Environment.IS_STAGING || (FeatureFlags.idealDonations() && LocaleFeatureFlags.isIdealEnabled())
  }

  /**
   * Whether the user is in a region which supports SEPA Debit transfers, based off local phone number
   * and donation type.
   */
  fun isSEPADebitAvailableForDonateToSignalType(inAppPaymentType: InAppPaymentTable.Type): Boolean {
    return inAppPaymentType != InAppPaymentTable.Type.ONE_TIME_GIFT && isSEPADebitAvailable()
  }

  /**
   * Whether the user is in a region which suports IDEAL transfers, based off local phone number and
   * donation type
   */
  fun isIDEALAvailbleForDonateToSignalType(inAppPaymentType: InAppPaymentTable.Type): Boolean {
    return inAppPaymentType != InAppPaymentTable.Type.ONE_TIME_GIFT && isIDEALAvailable()
  }
}
