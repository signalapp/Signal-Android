package org.thoughtcrime.securesms.registration

import org.thoughtcrime.securesms.pin.TokenData
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.ServiceResponseProcessor
import org.whispersystems.signalservice.internal.push.LockedException
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse

/**
 * Process responses from attempting to verify an account for use in account registration.
 */
sealed class VerifyAccountResponseProcessor(
  response: ServiceResponse<VerifyAccountResponse>
) : ServiceResponseProcessor<VerifyAccountResponse>(response), VerifyProcessor {

  open val tokenData: TokenData? = null

  public override fun authorizationFailed(): Boolean {
    return super.authorizationFailed()
  }

  public override fun registrationLock(): Boolean {
    return super.registrationLock()
  }

  public override fun rateLimit(): Boolean {
    return super.rateLimit()
  }

  public override fun getError(): Throwable? {
    return super.getError()
  }

  fun getLockedException(): LockedException {
    return error as LockedException
  }

  override fun isServerSentError(): Boolean {
    return error is NonSuccessfulResponseCodeException
  }

  abstract fun isKbsLocked(): Boolean
}

/**
 * Verify processor specific to verifying without needing to handle registration lock.
 */
class VerifyAccountResponseWithoutKbs(response: ServiceResponse<VerifyAccountResponse>) : VerifyAccountResponseProcessor(response) {
  override fun isKbsLocked(): Boolean {
    return registrationLock() && getLockedException().basicStorageCredentials == null
  }
}

/**
 * Verify processor specific to verifying and successfully retrieving KBS information to
 * later attempt to verif with registration lock data (pin).
 */
class VerifyAccountResponseWithSuccessfulKbs(
  response: ServiceResponse<VerifyAccountResponse>,
  override val tokenData: TokenData
) : VerifyAccountResponseProcessor(response) {

  override fun isKbsLocked(): Boolean {
    return registrationLock() && tokenData.triesRemaining == 0
  }
}

/**
 * Verify processor specific to verifying and unsuccessfully retrieving KBS information that
 * is required for attempting to verify a registration locked account.
 */
class VerifyAccountResponseWithFailedKbs(response: ServiceResponse<TokenData>) : VerifyAccountResponseProcessor(ServiceResponse.coerceError(response)) {
  override fun isKbsLocked(): Boolean {
    return false
  }
}
