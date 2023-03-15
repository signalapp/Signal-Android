package org.thoughtcrime.securesms.registration

import org.whispersystems.signalservice.api.push.exceptions.AlreadyVerifiedException
import org.whispersystems.signalservice.api.push.exceptions.ExternalServiceFailureException
import org.whispersystems.signalservice.api.push.exceptions.ImpossiblePhoneNumberException
import org.whispersystems.signalservice.api.push.exceptions.InvalidTransportModeException
import org.whispersystems.signalservice.api.push.exceptions.MustRequestNewCodeException
import org.whispersystems.signalservice.api.push.exceptions.NoSuchSessionException
import org.whispersystems.signalservice.api.push.exceptions.NonNormalizedPhoneNumberException
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException
import org.whispersystems.signalservice.api.push.exceptions.TokenNotAcceptedException
import org.whispersystems.signalservice.api.util.Preconditions
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.ServiceResponseProcessor
import org.whispersystems.signalservice.internal.push.RegistrationSessionMetadataResponse
import org.whispersystems.signalservice.internal.push.RegistrationSessionState
import kotlin.time.Duration.Companion.seconds

/**
 * Makes the server's response describing the state of the registration session as digestible as possible.
 */
sealed class RegistrationSessionProcessor(response: ServiceResponse<RegistrationSessionMetadataResponse>) : ServiceResponseProcessor<RegistrationSessionMetadataResponse>(response) {

  companion object {
    const val CAPTCHA_KEY = "captcha"
    const val PUSH_CHALLENGE_KEY = "pushChallenge"
    val REQUESTABLE_INFORMATION = listOf(PUSH_CHALLENGE_KEY, CAPTCHA_KEY)
  }

  public override fun rateLimit(): Boolean {
    return error is RateLimitException
  }

  public override fun getError(): Throwable? {
    return super.getError()
  }

  fun captchaRequired(excludedChallenges: List<String>): Boolean {
    return response.status == 402 || (hasResult() && CAPTCHA_KEY == getChallenge(excludedChallenges))
  }

  fun pushChallengeTimedOut(): Boolean {
    if (response.result.isEmpty) {
      return false
    } else {
      val state: RegistrationSessionState = response.result.get().state ?: return false
      return state.pushChallengeTimedOut
    }
  }

  fun isTokenRejected(): Boolean {
    return error is TokenNotAcceptedException
  }

  fun isImpossibleNumber(): Boolean {
    return error is ImpossiblePhoneNumberException
  }

  fun isNonNormalizedNumber(): Boolean {
    return error is NonNormalizedPhoneNumberException
  }

  fun getRateLimit(): Long {
    Preconditions.checkState(error is RateLimitException, "This can only be called when isRateLimited()")
    return (error as RateLimitException).retryAfterMilliseconds.orElse(-1L)
  }

  /**
   * The soonest time at which the server will accept a request to send a new code via SMS.
   * @return a unix timestamp in milliseconds, or 0 to represent null
   */
  fun getNextCodeViaSmsAttempt(): Long {
    return deriveTimestamp(result.body.nextSms)
  }

  /**
   * The soonest time at which the server will accept a request to send a new code via a voice call.
   * @return a unix timestamp in milliseconds, or 0 to represent null
   */
  fun getNextCodeViaCallAttempt(): Long {
    return deriveTimestamp(result.body.nextCall)
  }

  fun canSubmitProofImmediately(): Boolean {
    Preconditions.checkState(hasResult(), "This can only be called when result is present!")
    return 0 == result.body.nextVerificationAttempt
  }

  /**
   * The soonest time at which the server will accept a submission of proof of ownership.
   * @return a unix timestamp in milliseconds, or 0 to represent null
   */
  fun getNextProofSubmissionAttempt(): Long {
    Preconditions.checkState(hasResult(), "This can only be called when result is present!")
    return deriveTimestamp(result.body.nextVerificationAttempt)
  }

  fun exhaustedVerificationCodeAttempts(): Boolean {
    return rateLimit() && getRateLimit() == -1L
  }

  fun isInvalidSession(): Boolean {
    return error is NoSuchSessionException
  }

  fun getSessionId(): String {
    Preconditions.checkState(hasResult(), "This can only be called when result is present!")
    return result.body.id
  }

  fun isAllowedToRequestCode(): Boolean {
    Preconditions.checkState(hasResult(), "This can only be called when result is present!")
    return result.body.allowedToRequestCode
  }

  /**
   * Parse the response body for the server requested challenges that the client may submit.
   *
   * @param exclusions a collection of keys to ignore, used when they've already tried and failed
   * @return the next challenge
   */
  fun getChallenge(exclusions: Collection<String>): String? {
    Preconditions.checkState(hasResult(), "This can only be called when result is present!")
    return result.body.requestedInformation.filterNot { exclusions.contains(it) }.firstOrNull { REQUESTABLE_INFORMATION.contains(it) }
  }

  fun isVerified(): Boolean {
    return hasResult() && result.body.verified
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

  fun cannotSubmitVerificationAttempt(): Boolean {
    return !hasResult() || result.body.nextVerificationAttempt == null
  }

  /**
   * @param deltaSeconds the number of whole seconds to be added to the server timestamp
   * @return a unix timestamp in milliseconds, or 0 to represent null
   */
  private fun deriveTimestamp(deltaSeconds: Int?): Long {
    Preconditions.checkState(hasResult(), "This can only be called when result is present!")

    if (deltaSeconds == null) {
      return 0L
    }

    val timestamp: Long = result.headers.timestamp
    return timestamp + deltaSeconds.seconds.inWholeMilliseconds
  }

  abstract fun verificationCodeRequestSuccess(): Boolean

  class RegistrationSessionProcessorForSession(response: ServiceResponse<RegistrationSessionMetadataResponse>) : RegistrationSessionProcessor(response) {

    override fun verificationCodeRequestSuccess(): Boolean = false
  }

  class RegistrationSessionProcessorForVerification(response: ServiceResponse<RegistrationSessionMetadataResponse>) : RegistrationSessionProcessor(response) {
    override fun verificationCodeRequestSuccess(): Boolean = hasResult()

    fun isAlreadyVerified(): Boolean {
      return error is AlreadyVerifiedException
    }

    fun mustRequestNewCode(): Boolean {
      return error is MustRequestNewCodeException
    }

    fun externalServiceFailure(): Boolean {
      return error is ExternalServiceFailureException
    }

    fun invalidTransportModeFailure(): Boolean {
      return error is InvalidTransportModeException
    }
  }
}
