package org.signal.donations

import com.fasterxml.jackson.databind.exc.InvalidDefinitionException

sealed class StripeError(message: String) : Exception(message) {
  class FailedToParsePaymentIntentResponseError(invalidDefCause: InvalidDefinitionException?) : StripeError("Failed to parse payment intent response: ${invalidDefCause?.type} ${invalidDefCause?.property} ${invalidDefCause?.beanDescription}")
  class FailedToParseSetupIntentResponseError(invalidDefCause: InvalidDefinitionException?) : StripeError("Failed to parse setup intent response: ${invalidDefCause?.type} ${invalidDefCause?.property} ${invalidDefCause?.beanDescription}")
  object FailedToParsePaymentMethodResponseError : StripeError("Failed to parse payment method response")
  object FailedToCreatePaymentSourceFromCardData : StripeError("Failed to create payment source from card data")
  class PostError(val statusCode: Int, val errorCode: String?, val declineCode: StripeDeclineCode?) : StripeError("postForm failed with code: $statusCode. errorCode: $errorCode. declineCode: $declineCode")
}
