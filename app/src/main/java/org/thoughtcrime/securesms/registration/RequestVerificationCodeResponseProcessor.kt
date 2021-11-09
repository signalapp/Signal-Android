package org.thoughtcrime.securesms.registration

import org.whispersystems.signalservice.api.push.exceptions.ImpossiblePhoneNumberException
import org.whispersystems.signalservice.api.push.exceptions.LocalRateLimitException
import org.whispersystems.signalservice.api.push.exceptions.NonNormalizedPhoneNumberException
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.ServiceResponseProcessor
import org.whispersystems.signalservice.internal.push.RequestVerificationCodeResponse
import java.lang.IllegalStateException

/**
 * Process responses from requesting an SMS or Phone code from the server.
 */
class RequestVerificationCodeResponseProcessor(response: ServiceResponse<RequestVerificationCodeResponse>) : ServiceResponseProcessor<RequestVerificationCodeResponse>(response) {
  public override fun captchaRequired(): Boolean {
    return super.captchaRequired()
  }

  public override fun rateLimit(): Boolean {
    return super.rateLimit()
  }

  public override fun getError(): Throwable? {
    return super.getError()
  }

  fun localRateLimit(): Boolean {
    return error is LocalRateLimitException
  }

  fun isImpossibleNumber(): Boolean {
    return error is ImpossiblePhoneNumberException
  }

  fun isNonNormalizedNumber(): Boolean {
    return error is NonNormalizedPhoneNumberException
  }

  /** Should only be called if [isNonNormalizedNumber] */
  fun getOriginalNumber(): String {
    if (error !is NonNormalizedPhoneNumberException) {
      throw IllegalStateException("This can only be called when isNonNormalizedNumber()")
    }

    return (error as NonNormalizedPhoneNumberException).originalNumber
  }

  /** Should only be called if [isNonNormalizedNumber] */
  fun getNormalizedNumber(): String {
    if (error !is NonNormalizedPhoneNumberException) {
      throw IllegalStateException("This can only be called when isNonNormalizedNumber()")
    }

    return (error as NonNormalizedPhoneNumberException).normalizedNumber
  }

  companion object {
    @JvmStatic
    fun forLocalRateLimit(): RequestVerificationCodeResponseProcessor {
      val response: ServiceResponse<RequestVerificationCodeResponse> = ServiceResponse.forExecutionError(LocalRateLimitException())
      return RequestVerificationCodeResponseProcessor(response)
    }
  }
}
