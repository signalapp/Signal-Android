package org.signal.donations

import android.net.Uri
import android.os.Parcelable
import androidx.annotation.WorkerThread
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.parcelize.Parcelize
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.ByteString.Companion.encodeUtf8
import org.json.JSONObject
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.donations.json.StripePaymentIntent
import org.signal.donations.json.StripeSetupIntent
import java.math.BigDecimal
import java.util.Locale

class StripeApi(
  private val configuration: Configuration,
  private val paymentIntentFetcher: PaymentIntentFetcher,
  private val setupIntentHelper: SetupIntentHelper,
  private val okHttpClient: OkHttpClient
) {

  private val objectMapper = jsonMapper {
    addModule(kotlinModule())
  }

  companion object {
    private val TAG = Log.tag(StripeApi::class.java)

    private val CARD_NUMBER_KEY = "card[number]"
    private val CARD_MONTH_KEY = "card[exp_month]"
    private val CARD_YEAR_KEY = "card[exp_year]"
    private val CARD_CVC_KEY = "card[cvc]"

    const val RETURN_URL_SCHEME = "sgnlpay"
    private const val RETURN_URL_3DS = "$RETURN_URL_SCHEME://3DS"

    const val RETURN_URL_IDEAL = "https://signaldonations.org/stripe/return/ideal"
  }

  sealed class CreatePaymentIntentResult {
    data class AmountIsTooSmall(val amount: FiatMoney) : CreatePaymentIntentResult()
    data class AmountIsTooLarge(val amount: FiatMoney) : CreatePaymentIntentResult()
    data class CurrencyIsNotSupported(val currencyCode: String) : CreatePaymentIntentResult()
    data class Success(val paymentIntent: StripeIntentAccessor) : CreatePaymentIntentResult()
  }

  data class CreateSetupIntentResult(val setupIntent: StripeIntentAccessor)

  sealed class CreatePaymentSourceFromCardDataResult {
    data class Success(val paymentSource: PaymentSource) : CreatePaymentSourceFromCardDataResult()
    data class Failure(val reason: Throwable) : CreatePaymentSourceFromCardDataResult()
  }

  fun createSetupIntent(inAppPaymentType: InAppPaymentType, sourceType: PaymentSourceType.Stripe): Single<CreateSetupIntentResult> {
    return setupIntentHelper
      .fetchSetupIntent(inAppPaymentType, sourceType)
      .map { CreateSetupIntentResult(it) }
      .subscribeOn(Schedulers.io())
  }

  fun confirmSetupIntent(paymentSource: PaymentSource, setupIntent: StripeIntentAccessor): Single<Secure3DSAction> {
    return Single.fromCallable {
      val paymentMethodId = createPaymentMethodAndParseId(paymentSource)

      val parameters = mutableMapOf(
        "client_secret" to setupIntent.intentClientSecret,
        "payment_method" to paymentMethodId,
        "return_url" to if (paymentSource is IDEALPaymentSource) RETURN_URL_IDEAL else RETURN_URL_3DS
      )

      if (paymentSource.type.isBankTransfer) {
        parameters["mandate_data[customer_acceptance][type]"] = "online"
        parameters["mandate_data[customer_acceptance][online][infer_from_client]"] = "true"
      }

      val (nextActionUri, returnUri) = postForm("setup_intents/${setupIntent.intentId}/confirm", parameters).use { response ->
        getNextAction(response)
      }

      Secure3DSAction.from(nextActionUri, returnUri, setupIntent, paymentMethodId)
    }
  }

  fun createPaymentIntent(price: FiatMoney, level: Long, sourceType: PaymentSourceType.Stripe): Single<CreatePaymentIntentResult> {
    @Suppress("CascadeIf")
    return if (Validation.isAmountTooSmall(price)) {
      Single.just(CreatePaymentIntentResult.AmountIsTooSmall(price))
    } else if (Validation.isAmountTooLarge(price)) {
      Single.just(CreatePaymentIntentResult.AmountIsTooLarge(price))
    } else {
      if (!Validation.supportedCurrencyCodes.contains(price.currency.currencyCode.uppercase(Locale.ROOT))) {
        Single.just<CreatePaymentIntentResult>(CreatePaymentIntentResult.CurrencyIsNotSupported(price.currency.currencyCode))
      } else {
        paymentIntentFetcher
          .fetchPaymentIntent(price, level, sourceType)
          .map<CreatePaymentIntentResult> { CreatePaymentIntentResult.Success(it) }
      }.subscribeOn(Schedulers.io())
    }
  }

  /**
   * Confirm a PaymentIntent
   *
   * This method will create a PaymentMethod with the given PaymentSource and then confirm the
   * PaymentIntent.
   *
   * @return A Secure3DSAction
   */
  fun confirmPaymentIntent(paymentSource: PaymentSource, paymentIntent: StripeIntentAccessor): Single<Secure3DSAction> {
    return Single.fromCallable {
      val paymentMethodId = createPaymentMethodAndParseId(paymentSource)

      val parameters = mutableMapOf(
        "client_secret" to paymentIntent.intentClientSecret,
        "payment_method" to paymentMethodId,
        "return_url" to if (paymentSource is IDEALPaymentSource) RETURN_URL_IDEAL else RETURN_URL_3DS
      )

      if (paymentSource.type.isBankTransfer) {
        parameters["mandate_data[customer_acceptance][type]"] = "online"
        parameters["mandate_data[customer_acceptance][online][infer_from_client]"] = "true"
      }

      val (nextActionUri, returnUri) = postForm("payment_intents/${paymentIntent.intentId}/confirm", parameters).use { response ->
        getNextAction(response)
      }

      Secure3DSAction.from(nextActionUri, returnUri, paymentIntent)
    }.subscribeOn(Schedulers.io())
  }

  /**
   * Retrieve the setup intent pointed to by the given accessor.
   */
  fun getSetupIntent(stripeIntentAccessor: StripeIntentAccessor): StripeSetupIntent {
    return when (stripeIntentAccessor.objectType) {
      StripeIntentAccessor.ObjectType.SETUP_INTENT -> get("setup_intents/${stripeIntentAccessor.intentId}?client_secret=${stripeIntentAccessor.intentClientSecret}&expand[0]=latest_attempt").use {
        val body = it.body?.string()
        try {
          objectMapper.readValue(body!!)
        } catch (e: InvalidDefinitionException) {
          Log.w(TAG, "Failed to parse JSON for StripeSetupIntent.")
          ResponseFieldLogger.logFields(objectMapper, body)
          throw StripeError.FailedToParseSetupIntentResponseError(e)
        } catch (e: Exception) {
          Log.w(TAG, "Failed to read value from JSON.", e, true)
          throw StripeError.FailedToParseSetupIntentResponseError(null)
        }
      }
      else -> error("Unsupported type")
    }
  }

  /**
   * Retrieve the payment intent pointed to by the given accessor.
   */
  fun getPaymentIntent(stripeIntentAccessor: StripeIntentAccessor): StripePaymentIntent {
    return when (stripeIntentAccessor.objectType) {
      StripeIntentAccessor.ObjectType.PAYMENT_INTENT -> get("payment_intents/${stripeIntentAccessor.intentId}?client_secret=${stripeIntentAccessor.intentClientSecret}").use {
        val body = it.body?.string()
        try {
          Log.d(TAG, "Reading StripePaymentIntent from JSON")
          objectMapper.readValue(body!!)
        } catch (e: InvalidDefinitionException) {
          Log.w(TAG, "Failed to parse JSON for StripePaymentIntent.")
          ResponseFieldLogger.logFields(objectMapper, body)
          throw StripeError.FailedToParsePaymentIntentResponseError(e)
        } catch (e: Exception) {
          Log.w(TAG, "Failed to read value from JSON.", e, true)
          throw StripeError.FailedToParsePaymentIntentResponseError(null)
        }
      }
      else -> error("Unsupported type")
    }
  }

  private fun getNextAction(response: Response): Pair<Uri, Uri> {
    val responseBody = response.body?.string()
    val bodyJson = responseBody?.let { JSONObject(it) }
    return if (bodyJson?.has("next_action") == true && !bodyJson.isNull("next_action")) {
      val nextAction = bodyJson.getJSONObject("next_action")
      if (BuildConfig.DEBUG) {
        Log.d(TAG, "[getNextAction] Next Action found:\n$nextAction")
      }

      val redirectToUrl = nextAction.getJSONObject("redirect_to_url")
      val nextActionUri = redirectToUrl.getString("url")
      val returnUri = redirectToUrl.getString("return_url")

      Uri.parse(nextActionUri) to Uri.parse(returnUri)
    } else {
      Uri.EMPTY to Uri.EMPTY
    }
  }

  fun createPaymentSourceFromCardData(cardData: CardData): Single<CreatePaymentSourceFromCardDataResult> {
    return Single.fromCallable<CreatePaymentSourceFromCardDataResult> {
      CreatePaymentSourceFromCardDataResult.Success(createPaymentSourceFromCardDataSync(cardData))
    }.onErrorReturn {
      CreatePaymentSourceFromCardDataResult.Failure(it)
    }.subscribeOn(Schedulers.io())
  }

  fun createPaymentSourceFromSEPADebitData(sepaDebitData: SEPADebitData): Single<PaymentSource> {
    return Single.just(SEPADebitPaymentSource(sepaDebitData))
  }

  fun createPaymentSourceFromIDEALData(idealData: IDEALData): Single<PaymentSource> {
    return Single.just(IDEALPaymentSource(idealData))
  }

  @WorkerThread
  private fun createPaymentSourceFromCardDataSync(cardData: CardData): PaymentSource {
    val parameters: Map<String, String> = mutableMapOf(
      CARD_NUMBER_KEY to cardData.number,
      CARD_MONTH_KEY to cardData.month.toString(),
      CARD_YEAR_KEY to cardData.year.toString(),
      CARD_CVC_KEY to cardData.cvc
    )

    postForm("tokens", parameters).use { response ->
      val body = response.body ?: throw StripeError.FailedToCreatePaymentSourceFromCardData
      return CreditCardPaymentSource(JSONObject(body.string()))
    }
  }

  private fun createPaymentMethodAndParseId(paymentSource: PaymentSource): String {
    val paymentMethodResponse = when (paymentSource) {
      is SEPADebitPaymentSource -> createPaymentMethodForSEPADebit(paymentSource)
      is IDEALPaymentSource -> createPaymentMethodForIDEAL(paymentSource)
      else -> createPaymentMethodForToken(paymentSource)
    }

    return paymentMethodResponse.use { response ->
      val body = response.body ?: throw StripeError.FailedToParsePaymentMethodResponseError
      val paymentMethodObject = body.string().replace("\n", "").let { JSONObject(it) }
      paymentMethodObject.getString("id")
    }
  }

  private fun createPaymentMethodForSEPADebit(paymentSource: SEPADebitPaymentSource): Response {
    val parameters = mutableMapOf(
      "type" to "sepa_debit",
      "sepa_debit[iban]" to paymentSource.sepaDebitData.iban,
      "billing_details[email]" to paymentSource.sepaDebitData.email,
      "billing_details[name]" to paymentSource.sepaDebitData.name
    )

    return postForm("payment_methods", parameters)
  }

  private fun createPaymentMethodForIDEAL(paymentSource: IDEALPaymentSource): Response {
    val parameters = mutableMapOf(
      "type" to "ideal",
      "ideal[bank]" to paymentSource.idealData.bank,
      "billing_details[email]" to paymentSource.idealData.email,
      "billing_details[name]" to paymentSource.idealData.name
    )

    return postForm("payment_methods", parameters)
  }

  private fun createPaymentMethodForToken(paymentSource: PaymentSource): Response {
    val tokenId = paymentSource.getTokenId()
    val parameters = mutableMapOf(
      "card[token]" to tokenId,
      "type" to "card"
    )

    return postForm("payment_methods", parameters)
  }

  private fun get(endpoint: String): Response {
    val request = getRequestBuilder(endpoint).get().build()
    val response = okHttpClient.newCall(request).execute()
    return checkResponseForErrors(response)
  }

  private fun postForm(endpoint: String, parameters: Map<String, String>): Response {
    val formBodyBuilder = FormBody.Builder()
    parameters.forEach { (k, v) ->
      formBodyBuilder.add(k, v)
    }

    val request = getRequestBuilder(endpoint)
      .post(formBodyBuilder.build())
      .build()

    val response = okHttpClient.newCall(request).execute()

    return checkResponseForErrors(response)
  }

  private fun getRequestBuilder(endpoint: String): Request.Builder {
    return Request.Builder()
      .url("${configuration.baseUrl}/$endpoint")
      .addHeader("Authorization", "Basic ${"${configuration.publishableKey}:".encodeUtf8().base64()}")
  }

  private fun checkResponseForErrors(response: Response): Response {
    if (response.isSuccessful) {
      return response
    } else {
      val body = response.body?.string()

      val errorCode = parseErrorCode(body)
      val declineCode = parseDeclineCode(body) ?: StripeDeclineCode.getFromCode(errorCode)
      val failureCode = parseFailureCode(body) ?: StripeFailureCode.getFromCode(errorCode)

      if (failureCode is StripeFailureCode.Known) {
        throw StripeError.PostError.Failed(response.code, failureCode)
      } else if (declineCode is StripeDeclineCode.Known) {
        throw StripeError.PostError.Declined(response.code, declineCode)
      } else {
        throw StripeError.PostError.Generic(response.code, errorCode)
      }
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
      StripeDeclineCode.getFromCode(JSONObject(body).getJSONObject("error").getString("decline_code"))
    } catch (e: Exception) {
      Log.d(TAG, "parseDeclineCode: Failed to parse decline code.", null, true)
      null
    }
  }

  private fun parseFailureCode(body: String?): StripeFailureCode? {
    if (body == null) {
      Log.d(TAG, "parseFailureCode: No body.", true)
      return null
    }

    return try {
      StripeFailureCode.getFromCode(JSONObject(body).getJSONObject("error").getString("failure_code"))
    } catch (e: Exception) {
      Log.d(TAG, "parseFailureCode: Failed to parse failure code.", null, true)
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
      level: Long,
      sourceType: PaymentSourceType.Stripe
    ): Single<StripeIntentAccessor>
  }

  interface SetupIntentHelper {
    fun fetchSetupIntent(
      inAppPaymentType: InAppPaymentType,
      sourceType: PaymentSourceType.Stripe
    ): Single<StripeIntentAccessor>
  }

  @Parcelize
  data class CardData(
    val number: String,
    val month: Int,
    val year: Int,
    val cvc: String
  ) : Parcelable

  @Parcelize
  data class SEPADebitData(
    val iban: String,
    val name: String,
    val email: String
  ) : Parcelable

  @Parcelize
  data class IDEALData(
    val bank: String,
    val name: String,
    val email: String
  ) : Parcelable

  interface PaymentSource {
    val type: PaymentSourceType
    fun parameterize(): JSONObject
    fun getTokenId(): String
    fun email(): String?
  }

  sealed interface Secure3DSAction {
    data class ConfirmRequired(val uri: Uri, val returnUri: Uri, override val stripeIntentAccessor: StripeIntentAccessor, override val paymentMethodId: String?) : Secure3DSAction
    data class NotNeeded(override val paymentMethodId: String?, override val stripeIntentAccessor: StripeIntentAccessor) : Secure3DSAction

    val paymentMethodId: String?
    val stripeIntentAccessor: StripeIntentAccessor

    companion object {
      fun from(
        uri: Uri,
        returnUri: Uri,
        stripeIntentAccessor: StripeIntentAccessor,
        paymentMethodId: String? = null
      ): Secure3DSAction {
        return if (uri == Uri.EMPTY) {
          NotNeeded(paymentMethodId, stripeIntentAccessor)
        } else {
          ConfirmRequired(uri, returnUri, stripeIntentAccessor, paymentMethodId)
        }
      }
    }
  }
}
