package org.signal.donations

sealed class StripeError(message: String) : Exception(message) {
  object FailedToParsePaymentMethodResponseError : StripeError("Failed to parse payment method response")
  class PostError(val statusCode: Int, val errorCode: String?, val declineCode: StripeDeclineCode?) : StripeError("postForm failed with code: $statusCode. errorCode: $errorCode. declineCode: $declineCode")
}
