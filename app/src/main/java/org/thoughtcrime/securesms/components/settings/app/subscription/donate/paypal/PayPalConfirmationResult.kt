package org.thoughtcrime.securesms.components.settings.app.subscription.donate.paypal

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PayPalConfirmationResult(
  val payerId: String,
  val paymentId: String,
  val paymentToken: String
) : Parcelable {
  companion object {
    private const val KEY_PAYER_ID = "PayerID"
    private const val KEY_PAYMENT_ID = "paymentId"
    private const val KEY_PAYMENT_TOKEN = "token"

    fun fromUrl(url: String): PayPalConfirmationResult? {
      val uri = Uri.parse(url)
      return PayPalConfirmationResult(
        payerId = uri.getQueryParameter(KEY_PAYER_ID) ?: return null,
        paymentId = uri.getQueryParameter(KEY_PAYMENT_ID) ?: return null,
        paymentToken = uri.getQueryParameter(KEY_PAYMENT_TOKEN) ?: return null
      )
    }
  }
}
