package org.signal.donations

import com.google.android.gms.wallet.PaymentData
import org.json.JSONObject

class GooglePayPaymentSource(private val paymentData: PaymentData) : StripeApi.PaymentSource {
  override fun parameterize(): JSONObject {
    val jsonData = JSONObject(paymentData.toJson())
    val paymentMethodJsonData = jsonData.getJSONObject("paymentMethodData")
    return paymentMethodJsonData.getJSONObject("tokenizationData")
  }
}