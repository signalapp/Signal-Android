package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.database.InAppPaymentTable

/**
 * Encapsulates data returned from the credit card form that can be used
 * for a credit card based donation payment.
 */
@Parcelize
data class CreditCardResult(
  val inAppPayment: InAppPaymentTable.InAppPayment,
  val creditCardData: StripeApi.CardData
) : Parcelable
