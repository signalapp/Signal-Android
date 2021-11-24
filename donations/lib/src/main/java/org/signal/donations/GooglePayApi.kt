package org.signal.donations

import android.app.Activity
import android.content.Intent
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.gms.wallet.AutoResolveHelper
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import java.util.Locale

/**
 * Entrypoint for Google Pay APIs
 *
 * @param activity The activity the Pay Client will attach itself to
 * @param gateway The payment gateway (Such as Stripe)
 */
class GooglePayApi(
  private val activity: Activity,
  private val gateway: Gateway,
  configuration: Configuration
) {

  private val paymentsClient: PaymentsClient

  init {
    val walletOptions = Wallet.WalletOptions.Builder()
      .setEnvironment(configuration.walletEnvironment)
      .build()

    paymentsClient = Wallet.getPaymentsClient(activity, walletOptions)
  }

  /**
   * Query the Google Pay API to determine whether or not the device has Google Pay available and ready.
   *
   * @return A completable which, when it completes, indicates that Google Pay is available, or when it errors, indicates it is not.
   */
  fun queryIsReadyToPay(): Completable = Completable.create { emitter ->
    try {
      val request: IsReadyToPayRequest = buildIsReadyToPayRequest()
      val task: Task<Boolean> = paymentsClient.isReadyToPay(request)
      task.addOnCompleteListener { completedTask ->
        if (!emitter.isDisposed) {
          try {
            val result: Boolean = completedTask.getResult(ApiException::class.java) ?: false
            if (result) {
              emitter.onComplete()
            } else {
              emitter.onError(Exception("Google Pay is not available."))
            }
          } catch (e: ApiException) {
            emitter.onError(e)
          }
        }
      }
    } catch (e: JSONException) {
      emitter.onError(e)
    }
  }.subscribeOn(Schedulers.io())

  /**
   * Launches the Google Pay sheet via an Activity intent.  It is up to the caller to pass
   * through the activity result to onActivityResult.
   */
  fun requestPayment(price: FiatMoney, label: String, requestCode: Int) {
    val paymentDataRequest = getPaymentDataRequest(price, label)
    val request = PaymentDataRequest.fromJson(paymentDataRequest.toString())
    AutoResolveHelper.resolveTask(paymentsClient.loadPaymentData(request), activity, requestCode)
  }

  /**
   * Checks the activity result for payment data and fires off the corresponding callback. Does nothing if
   * the request code is not the expected request code.
   */
  fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?,
    expectedRequestCode: Int,
    paymentRequestCallback: PaymentRequestCallback
  ) {
    if (requestCode != expectedRequestCode) {
      return
    }

    when (resultCode) {
      Activity.RESULT_OK -> {
        data?.let { intent ->
          PaymentData.getFromIntent(intent)?.let { paymentRequestCallback.onSuccess(it) }
        } ?: paymentRequestCallback.onError(GooglePayException(-1, "No data returned from Google Pay"))
      }
      Activity.RESULT_CANCELED -> paymentRequestCallback.onCancelled()
      AutoResolveHelper.RESULT_ERROR -> {
        AutoResolveHelper.getStatusFromIntent(data)?.let {
          Log.w(TAG, "loadPaymentData failed with error code ${it.statusCode}: ${it.statusMessage}")
          paymentRequestCallback.onError(GooglePayException(it.statusCode, it.statusMessage))
        }
      }
    }
  }

  private fun getPaymentDataRequest(price: FiatMoney, label: String): JSONObject {
    return baseRequest.apply {
      put("merchantInfo", merchantInfo)
      put("allowedPaymentMethods", JSONArray().put(cardPaymentMethod()))
      put("transactionInfo", getTransactionInfo(price, label))
      // TODO Donation receipts
      put("emailRequired", false)
      put("shippingAddressRequired", false)
    }
  }

  private fun getTransactionInfo(price: FiatMoney, label: String): JSONObject {
    return JSONObject().apply {
      put("currencyCode", price.currency.currencyCode)
      put("countryCode", "US")
      put("totalPriceStatus", "FINAL")
      put("totalPrice", price.getDefaultPrecisionString(Locale.US))
      put("totalPriceLabel", label)
      put("checkoutOption", "COMPLETE_IMMEDIATE_PURCHASE")
    }
  }

  private fun buildIsReadyToPayRequest(): IsReadyToPayRequest {
    val isReadyToPayJson: JSONObject = baseRequest.apply {
      put("allowedPaymentMethods", JSONArray().put(baseCardPaymentMethod()))
    }

    return IsReadyToPayRequest.fromJson(isReadyToPayJson.toString())
  }

  private fun gatewayTokenizationSpecification(): JSONObject {
    return JSONObject().apply {
      put("type", "PAYMENT_GATEWAY")
      put("parameters", JSONObject(gateway.getTokenizationSpecificationParameters()))
    }
  }

  private fun baseCardPaymentMethod(): JSONObject {
    return JSONObject().apply {
      val parameters = JSONObject().apply {
        put("allowedAuthMethods", allowedCardAuthMethods)
        put("allowedCardNetworks", JSONArray(gateway.allowedCardNetworks))
        put("billingAddressRequired", false)
      }

      put("type", "CARD")
      put("parameters", parameters)
    }
  }

  private fun cardPaymentMethod(): JSONObject {
    val cardPaymentMethod = baseCardPaymentMethod()
    cardPaymentMethod.put("tokenizationSpecification", gatewayTokenizationSpecification())

    return cardPaymentMethod
  }

  companion object {
    private val TAG = Log.tag(GooglePayApi::class.java)

    private const val MERCHANT_NAME = "Signal"

    private val merchantInfo: JSONObject =
      JSONObject().put("merchantName", MERCHANT_NAME)

    private val allowedCardAuthMethods = JSONArray(listOf("PAN_ONLY", "CRYPTOGRAM_3DS"))

    private val baseRequest = JSONObject().apply {
      put("apiVersion", 2)
      put("apiVersionMinor", 0)
    }
  }

  /**
   * @param walletEnvironment From WalletConstants
   */
  data class Configuration(
    val walletEnvironment: Int
  )

  interface Gateway {
    fun getTokenizationSpecificationParameters(): Map<String, String>
    val allowedCardNetworks: List<String>
  }

  interface PaymentRequestCallback {
    fun onSuccess(paymentData: PaymentData)
    fun onError(googlePayException: GooglePayException)
    fun onCancelled()
  }

  class GooglePayException(code: Int, message: String?) : Exception(message)
}