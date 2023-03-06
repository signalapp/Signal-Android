package org.thoughtcrime.securesms.registration

import org.thoughtcrime.securesms.pin.KeyBackupSystemWrongPinException
import org.thoughtcrime.securesms.pin.TokenData
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException
import org.whispersystems.signalservice.api.push.exceptions.IncorrectRegistrationRecoveryPasswordException
import org.whispersystems.signalservice.api.push.exceptions.NoSuchSessionException
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.ServiceResponseProcessor
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse
import org.whispersystems.signalservice.internal.push.LockedException

/**
 * Process responses from attempting to verify an account for use in account registration.
 */
sealed class VerifyResponseProcessor(response: ServiceResponse<VerifyResponse>) : ServiceResponseProcessor<VerifyResponse>(response) {

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

  fun invalidSession(): Boolean {
    return error is NoSuchSessionException
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

  abstract fun isKbsLocked(): Boolean
}

/**
 * Verify processor specific to verifying without needing to handle registration lock.
 */
class VerifyResponseWithoutKbs(response: ServiceResponse<VerifyResponse>) : VerifyResponseProcessor(response) {
  override fun isKbsLocked(): Boolean {
    return registrationLock() && getLockedException().basicStorageCredentials == null
  }
}

/**
 * Verify processor specific to verifying and successfully retrieving KBS information to
 * later attempt to verif with registration lock data (pin).
 */
class VerifyResponseWithSuccessfulKbs(response: ServiceResponse<VerifyResponse>, override val tokenData: TokenData) : VerifyResponseProcessor(response) {
  override fun isKbsLocked(): Boolean {
    return registrationLock() && tokenData.triesRemaining == 0
  }
}

/**
 * Verify processor specific to verifying and unsuccessfully retrieving KBS information that
 * is required for attempting to verify a registration locked account.
 */
class VerifyResponseWithFailedKbs(response: ServiceResponse<TokenData>) : VerifyResponseProcessor(ServiceResponse.coerceError(response)) {
  override fun isKbsLocked(): Boolean {
    return false
  }
}

/**
 * Process responses from attempting to verify an account with registration lock for use in
 * account registration.
 */
class VerifyResponseWithRegistrationLockProcessor(response: ServiceResponse<VerifyResponse>, override val tokenData: TokenData?) : VerifyResponseProcessor(response) {

  fun wrongPin(): Boolean {
    return error is KeyBackupSystemWrongPinException
  }

  fun getTokenResponse(): TokenResponse {
    return (error as KeyBackupSystemWrongPinException).tokenResponse
  }

  override fun isKbsLocked(): Boolean {
    return error is KeyBackupSystemNoDataException
  }

  fun updatedIfRegistrationFailed(response: ServiceResponse<VerifyResponse>): VerifyResponseWithRegistrationLockProcessor {
    if (response.result.isPresent) {
      return this
    }

    return VerifyResponseWithRegistrationLockProcessor(ServiceResponse.coerceError(response), tokenData)
  }

  override fun isServerSentError(): Boolean {
    return super.isServerSentError() ||
      error is KeyBackupSystemWrongPinException ||
      error is KeyBackupSystemNoDataException
  }
}
