package org.signal.donations

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.ByteString
import org.json.JSONObject
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import java.math.BigDecimal
import java.util.Locale

class StripeApi(
  private val configuration: Configuration,
  private val paymentIntentFetcher: PaymentIntentFetcher,
  private val setupIntentHelper: SetupIntentHelper,
  private val okHttpClient: OkHttpClient
) {

  companion object {
    private val TAG = Log.tag(StripeApi::class.java)
  }

  sealed class CreatePaymentIntentResult {
    data class AmountIsTooSmall(val amount: FiatMoney) : CreatePaymentIntentResult()
    data class AmountIsTooLarge(val amount: FiatMoney) : CreatePaymentIntentResult()
    data class CurrencyIsNotSupported(val currencyCode: String) : CreatePaymentIntentResult()
    data class Success(val paymentIntent: PaymentIntent) : CreatePaymentIntentResult()
  }

  data class CreateSetupIntentResult(val setupIntent: SetupIntent)

  fun createSetupIntent(): Single<CreateSetupIntentResult> {
    return setupIntentHelper
      .fetchSetupIntent()
      .map { CreateSetupIntentResult(it) }
      .subscribeOn(Schedulers.io())
  }

  fun confirmSetupIntent(paymentSource: PaymentSource, setupIntent: SetupIntent): Completable = Single.fromCallable {
    val paymentMethodId = createPaymentMethodAndParseId(paymentSource)

    val parameters = mapOf(
      "client_secret" to setupIntent.clientSecret,
      "payment_method" to paymentMethodId
    )

    postForm("setup_intents/${setupIntent.id}/confirm", parameters)
    paymentMethodId
  }.flatMapCompletable {
    setupIntentHelper.setDefaultPaymentMethod(it)
  }

  fun createPaymentIntent(price: FiatMoney, description: String? = null): Single<CreatePaymentIntentResult> {
    @Suppress("CascadeIf")
    return if (Validation.isAmountTooSmall(price)) {
      Single.just(CreatePaymentIntentResult.AmountIsTooSmall(price))
    } else if (Validation.isAmountTooLarge(price)) {
      Single.just(CreatePaymentIntentResult.AmountIsTooLarge(price))
    } else if (!Validation.supportedCurrencyCodes.contains(price.currency.currencyCode.toUpperCase(Locale.ROOT))) {
      Single.just<CreatePaymentIntentResult>(CreatePaymentIntentResult.CurrencyIsNotSupported(price.currency.currencyCode))
    } else {
      paymentIntentFetcher
        .fetchPaymentIntent(price, description)
        .map<CreatePaymentIntentResult> { CreatePaymentIntentResult.Success(it) }
    }.subscribeOn(Schedulers.io())
  }

  fun confirmPaymentIntent(paymentSource: PaymentSource, paymentIntent: PaymentIntent): Completable = Completable.fromAction {
    val paymentMethodId = createPaymentMethodAndParseId(paymentSource)

    val parameters = mutableMapOf(
      "client_secret" to paymentIntent.clientSecret,
      "payment_method" to paymentMethodId
    )

    postForm("payment_intents/${paymentIntent.id}/confirm", parameters)
  }.subscribeOn(Schedulers.io())

  private fun createPaymentMethodAndParseId(paymentSource: PaymentSource): String {
    return createPaymentMethod(paymentSource).use { response ->
      val body = response.body()
      if (body != null) {
        val paymentMethodObject = body.string().replace("\n", "").let { JSONObject(it) }
        paymentMethodObject.getString("id")
      } else {
        throw StripeError.FailedToParsePaymentMethodResponseError
      }
    }
  }

  private fun createPaymentMethod(paymentSource: PaymentSource): Response {
    val tokenizationData = paymentSource.parameterize()
    val parameters = mutableMapOf(
      "card[token]" to JSONObject((tokenizationData.get("token") as String).replace("\n", "")).getString("id"),
      "type" to "card",
    )

    return postForm("payment_methods", parameters)
  }

  private fun postForm(endpoint: String, parameters: Map<String, String>): Response {
    val formBodyBuilder = FormBody.Builder()
    parameters.forEach { (k, v) ->
      formBodyBuilder.add(k, v)
    }

    val request = Request.Builder()
      .url("${configuration.baseUrl}/$endpoint")
      .addHeader("Authorization", "Basic ${ByteString.encodeUtf8("${configuration.publishableKey}:").base64()}")
      .post(formBodyBuilder.build())
      .build()

    val response = okHttpClient.newCall(request).execute()

    if (response.isSuccessful) {
      return response
    } else {
      val body = response.body()?.toString()
      throw StripeError.PostError(
        response.code(),
        parseErrorCode(body),
        parseDeclineCode(body)
      )
    }
  }

  private fun parseErrorCode(body: String?): String? {
    if (body == null) {
      Log.d(TAG, "parseErrorCode: No body.", true)
      return null
    }

    return try {
      JSONObject(body).getJSONObject("error").getString("code")
    } catch (e: Exception) {
      Log.d(TAG, "parseErrorCode: Failed to parse error.", e, true)
      null
    }
  }

  private fun parseDeclineCode(body: String?): StripeDeclineCode? {
    if (body == null) {
      Log.d(TAG, "parseDeclineCode: No body.", true)
      return null
    }

    return try {
      val jsonBody = JSONObject(body)
      Log.d(TAG, "parseDeclineCode: Parsed body with keys: ${jsonBody.keys().asSequence().joinToString(", ")}")
      val jsonError = jsonBody.getJSONObject("error")
      Log.d(TAG, "parseDeclineCode: Parsed error with keys: ${jsonError.keys().asSequence().joinToString(", ")}")

      StripeDeclineCode.getFromCode(jsonError.getString("decline_code"))
    } catch (e: Exception) {
      Log.d(TAG, "parseDeclineCode: Failed to parse decline code.", e, true)
      null
    }
  }

  object Validation {
    private val MAX_AMOUNT = BigDecimal(99_999_999)

    fun isAmountTooLarge(fiatMoney: FiatMoney): Boolean {
      return fiatMoney.minimumUnitPrecisionString.toBigDecimal() > MAX_AMOUNT
    }

    fun isAmountTooSmall(fiatMoney: FiatMoney): Boolean {
      return fiatMoney.minimumUnitPrecisionString.toBigDecimal() < BigDecimal(minimumIntegralChargePerCurrencyCode[fiatMoney.currency.currencyCode] ?: 50)
    }

    private val minimumIntegralChargePerCurrencyCode: Map<String, Int> = mapOf(
      "USD" to 50,
      "AED" to 200,
      "AUD" to 50,
      "BGN" to 100,
      "BRL" to 50,
      "CAD" to 50,
      "CHF" to 50,
      "CZK" to 1500,
      "DKK" to 250,
      "EUR" to 50,
      "GBP" to 30,
      "HKD" to 400,
      "HUF" to 17500,
      "INR" to 50,
      "JPY" to 50,
      "MXN" to 10,
      "MYR" to 2,
      "NOK" to 300,
      "NZD" to 50,
      "PLN" to 200,
      "RON" to 200,
      "SEK" to 300,
      "SGD" to 50
    )

    val supportedCurrencyCodes: List<String> = listOf(
      "USD",
      "AED",
      "AFN",
      "ALL",
      "AMD",
      "ANG",
      "AOA",
      "ARS",
      "AUD",
      "AWG",
      "AZN",
      "BAM",
      "BBD",
      "BDT",
      "BGN",
      "BIF",
      "BMD",
      "BND",
      "BOB",
      "BRL",
      "BSD",
      "BWP",
      "BZD",
      "CAD",
      "CDF",
      "CHF",
      "CLP",
      "CNY",
      "COP",
      "CRC",
      "CVE",
      "CZK",
      "DJF",
      "DKK",
      "DOP",
      "DZD",
      "EGP",
      "ETB",
      "EUR",
      "FJD",
      "FKP",
      "GBP",
      "GEL",
      "GIP",
      "GMD",
      "GNF",
      "GTQ",
      "GYD",
      "HKD",
      "HNL",
      "HRK",
      "HTG",
      "HUF",
      "IDR",
      "ILS",
      "INR",
      "ISK",
      "JMD",
      "JPY",
      "KES",
      "KGS",
      "KHR",
      "KMF",
      "KRW",
      "KYD",
      "KZT",
      "LAK",
      "LBP",
      "LKR",
      "LRD",
      "LSL",
      "MAD",
      "MDL",
      "MGA",
      "MKD",
      "MMK",
      "MNT",
      "MOP",
      "MRO",
      "MUR",
      "MVR",
      "MWK",
      "MXN",
      "MYR",
      "MZN",
      "NAD",
      "NGN",
      "NIO",
      "NOK",
      "NPR",
      "NZD",
      "PAB",
      "PEN",
      "PGK",
      "PHP",
      "PKR",
      "PLN",
      "PYG",
      "QAR",
      "RON",
      "RSD",
      "RUB",
      "RWF",
      "SAR",
      "SBD",
      "SCR",
      "SEK",
      "SGD",
      "SHP",
      "SLL",
      "SOS",
      "SRD",
      "STD",
      "SZL",
      "THB",
      "TJS",
      "TOP",
      "TRY",
      "TTD",
      "TWD",
      "TZS",
      "UAH",
      "UGX",
      "UYU",
      "UZS",
      "VND",
      "VUV",
      "WST",
      "XAF",
      "XCD",
      "XOF",
      "XPF",
      "YER",
      "ZAR",
      "ZMW"
    )
  }

  class Gateway(private val configuration: Configuration) : GooglePayApi.Gateway {
    override fun getTokenizationSpecificationParameters(): Map<String, String> {
      return mapOf(
        "gateway" to "stripe",
        "stripe:version" to configuration.version,
        "stripe:publishableKey" to configuration.publishableKey
      )
    }

    override val allowedCardNetworks: List<String> = listOf(
      "AMEX",
      "DISCOVER",
      "JCB",
      "MASTERCARD",
      "VISA"
    )
  }

  data class Configuration(
    val publishableKey: String,
    val baseUrl: String = "https://api.stripe.com/v1",
    val version: String = "2018-10-31"
  )

  interface PaymentIntentFetcher {
    fun fetchPaymentIntent(
      price: FiatMoney,
      description: String? = null
    ): Single<PaymentIntent>
  }

  interface SetupIntentHelper {
    fun fetchSetupIntent(): Single<SetupIntent>
    fun setDefaultPaymentMethod(paymentMethodId: String): Completable
  }

  data class PaymentIntent(
    val id: String,
    val clientSecret: String
  )

  data class SetupIntent(
    val id: String,
    val clientSecret: String
  )

  interface PaymentSource {
    fun parameterize(): JSONObject
    fun email(): String?
  }
}