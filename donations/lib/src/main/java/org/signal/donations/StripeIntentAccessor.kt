package org.signal.donations

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * An object which wraps the necessary information to access a SetupIntent or PaymentIntent
 * from the Stripe API
 */
@Parcelize
data class StripeIntentAccessor(
  val objectType: ObjectType,
  val intentId: String,
  val intentClientSecret: String
) : Parcelable {

  enum class ObjectType {
    NONE,
    PAYMENT_INTENT,
    SETUP_INTENT
  }

  companion object {

    /**
     * noActionRequired is a safe default for when there was no 3DS required,
     * in order to continue a reactive payment chain.
     */
    val NO_ACTION_REQUIRED = StripeIntentAccessor(ObjectType.NONE,"", "")

    private const val KEY_PAYMENT_INTENT = "payment_intent"
    private const val KEY_PAYMENT_INTENT_CLIENT_SECRET = "payment_intent_client_secret"
    private const val KEY_SETUP_INTENT = "setup_intent"
    private const val KEY_SETUP_INTENT_CLIENT_SECRET = "setup_intent_client_secret"

    fun fromUri(uri: String): StripeIntentAccessor {
      val parsedUri = Uri.parse(uri)
      return if (parsedUri.queryParameterNames.contains(KEY_PAYMENT_INTENT)) {
        StripeIntentAccessor(
          ObjectType.PAYMENT_INTENT,
          parsedUri.getQueryParameter(KEY_PAYMENT_INTENT)!!,
          parsedUri.getQueryParameter(KEY_PAYMENT_INTENT_CLIENT_SECRET)!!
        )
      } else {
        StripeIntentAccessor(
          ObjectType.SETUP_INTENT,
          parsedUri.getQueryParameter(KEY_SETUP_INTENT)!!,
          parsedUri.getQueryParameter(KEY_SETUP_INTENT_CLIENT_SECRET)!!
        )
      }
    }
  }
}