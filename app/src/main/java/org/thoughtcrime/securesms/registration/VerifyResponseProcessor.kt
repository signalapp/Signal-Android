package org.thoughtcrime.securesms.registration

import org.thoughtcrime.securesms.pin.SvrWrongPinException
import org.thoughtcrime.securesms.registration.viewmodel.SvrAuthCredentialSet
import org.whispersystems.signalservice.api.SvrNoDataException
import org.whispersystems.signalservice.api.push.exceptions.IncorrectRegistrationRecoveryPasswordException
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.ServiceResponseProcessor
import org.whispersystems.signalservice.internal.push.LockedException

/**
 * Process responses from attempting to verify an account for use in account registration.
 */
sealed class VerifyResponseProcessor(response: ServiceResponse<VerifyResponse>) : ServiceResponseProcessor<VerifyResponse>(response) {

  open val svrTriesRemaining: Int?
    get() = (error as? SvrWrongPinException)?.triesRemaining

  open val svrAuthCredentials: SvrAuthCredentialSet?
    get() {
      return error?.let {
        if (it is LockedException) {
          SvrAuthCredentialSet(svr2Credentials = it.svr2Credentials, svr3Credentials = it.svr3Credentials)
        } else {
          null
        }
      }
    }

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

  open fun isServerSentError(): Boolean {
    return error is NonSuccessfulResponseCodeException
  }

  fun isIncorrectRegistrationRecoveryPassword(): Boolean {
    return error is IncorrectRegistrationRecoveryPasswordException
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

/**
 * Verify processor indicating we cannot register until registration lock has been resolved.
 */
class VerifyResponseHitRegistrationLock(response: ServiceResponse<VerifyResponse>) : VerifyResponseProcessor(response) {
  override fun isRegistrationLockPresentAndSvrExhausted(): Boolean {
    return false
  }
}

/**
 * Process responses from attempting to verify an account with registration lock for use in
 * account registration.
 */
class VerifyResponseWithRegistrationLockProcessor(response: ServiceResponse<VerifyResponse>, override val svrAuthCredentials: SvrAuthCredentialSet?) : VerifyResponseProcessor(response) {

  fun wrongPin(): Boolean {
    return error is SvrWrongPinException
  }

  override fun isRegistrationLockPresentAndSvrExhausted(): Boolean {
    return error is SvrNoDataException
  }

  fun updatedIfRegistrationFailed(response: ServiceResponse<VerifyResponse>): VerifyResponseWithRegistrationLockProcessor {
    if (response.result.isPresent) {
      return this
    }

    return VerifyResponseWithRegistrationLockProcessor(ServiceResponse.coerceError(response), svrAuthCredentials)
  }

  override fun isServerSentError(): Boolean {
    return super.isServerSentError() ||
      error is SvrWrongPinException ||
      error is SvrNoDataException
  }
}
