package org.signal.donations

import com.fasterxml.jackson.databind.exc.InvalidDefinitionException

sealed class StripeError(message: String) : Exception(message) {
  class FailedToParsePaymentIntentResponseError(invalidDefCause: InvalidDefinitionException?) : StripeError("Failed to parse payment intent response: ${invalidDefCause?.type} ${invalidDefCause?.property} ${invalidDefCause?.beanDescription}")
  class FailedToParseSetupIntentResponseError(invalidDefCause: InvalidDefinitionException?) : StripeError("Failed to parse setup intent response: ${invalidDefCause?.type} ${invalidDefCause?.property} ${invalidDefCause?.beanDescription}")
  object FailedToParsePaymentMethodResponseError : StripeError("Failed to parse payment method response")
  object FailedToCreatePaymentSourceFromCardData : StripeError("Failed to create payment source from card data")
  sealed class PostError(
    override val message: String
  ) : StripeError(message) {
    class Generic(statusCode: Int, val errorCode: String?) : PostError("postForm failed with code: $statusCode errorCode: $errorCode")
    class Declined(statusCode: Int, val declineCode: StripeDeclineCode) : PostError("postForm failed with code: $statusCode declineCode: $declineCode")
    class Failed(statusCode: Int, val failureCode: StripeFailureCode) : PostError("postForm failed with code: $statusCode failureCode: $failureCode")
  }
}
