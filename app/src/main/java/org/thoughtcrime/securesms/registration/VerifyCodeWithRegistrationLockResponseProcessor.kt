package org.thoughtcrime.securesms.registration

import org.thoughtcrime.securesms.pin.KeyBackupSystemWrongPinException
import org.thoughtcrime.securesms.pin.TokenData
import org.thoughtcrime.securesms.registration.VerifyAccountRepository.VerifyAccountWithRegistrationLockResponse
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.ServiceResponseProcessor
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse

/**
 * Process responses from attempting to verify an account with registration lock for use in
 * account registration.
 */
class VerifyCodeWithRegistrationLockResponseProcessor(
  response: ServiceResponse<VerifyAccountWithRegistrationLockResponse>,
  val token: TokenData
) : ServiceResponseProcessor<VerifyAccountWithRegistrationLockResponse>(response), VerifyProcessor {

  public override fun rateLimit(): Boolean {
    return super.rateLimit()
  }

  public override fun getError(): Throwable? {
    return super.getError()
  }

  public override fun registrationLock(): Boolean {
    return super.registrationLock()
  }

  fun wrongPin(): Boolean {
    return error is KeyBackupSystemWrongPinException
  }

  fun getTokenResponse(): TokenResponse {
    return (error as KeyBackupSystemWrongPinException).tokenResponse
  }

  fun isKbsLocked(): Boolean {
    return error is KeyBackupSystemNoDataException
  }

  fun updatedIfRegistrationFailed(response: ServiceResponse<VerifyAccountResponse>): VerifyCodeWithRegistrationLockResponseProcessor {
    if (response.result.isPresent) {
      return this
    }

    return VerifyCodeWithRegistrationLockResponseProcessor(ServiceResponse.coerceError(response), token)
  }

  override fun isServerSentError(): Boolean {
    return error is NonSuccessfulResponseCodeException ||
      error is KeyBackupSystemWrongPinException ||
      error is KeyBackupSystemNoDataException
  }
}
