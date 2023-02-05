package org.signal.donations

import com.google.android.gms.wallet.PaymentData
import org.json.JSONObject

class GooglePayPaymentSource(private val paymentData: PaymentData) : StripeApi.PaymentSource {
  override val type = PaymentSourceType.Stripe.GooglePay

  override fun parameterize(): JSONObject {
    val jsonData = JSONObject(paymentData.toJson())
    val paymentMethodJsonData = jsonData.getJSONObject("paymentMethodData")
    return paymentMethodJsonData.getJSONObject("tokenizationData")
  }

  override fun getTokenId(): String {
    val serializedToken = parameterize().getString("token").replace("\n", "")
    return JSONObject(serializedToken).getString("id")
  }

  override fun email(): String? {
    val jsonData = JSONObject(paymentData.toJson())
    return if (jsonData.has("email")) {
      jsonData.getString("email")
    } else {
      null
    }
  }
}