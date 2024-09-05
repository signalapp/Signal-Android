package org.thoughtcrime.securesms.registration

import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.ServiceResponseProcessor
import org.whispersystems.signalservice.internal.push.LockedException

/**
 * Process responses from attempting to verify an account for use in account registration.
 */
sealed class VerifyResponseProcessor(response: ServiceResponse<VerifyResponse>) : ServiceResponseProcessor<VerifyResponse>(response) {

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

  /** True if the account has reglock enabled but all guesses have been exhausted, otherwise false. */
  abstract fun isRegistrationLockPresentAndSvrExhausted(): Boolean
}

/**
 * Verify processor specific to verifying without needing to handle registration lock.
 */
class VerifyResponseWithoutKbs(response: ServiceResponse<VerifyResponse>) : VerifyResponseProcessor(response) {
  override fun isRegistrationLockPresentAndSvrExhausted(): Boolean {
    return registrationLock() && getLockedException().svr2Credentials == null
  }
}
