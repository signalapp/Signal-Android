/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.registration

import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.account.AccountAttributes
import org.whispersystems.signalservice.api.account.PreKeyCollection
import org.whispersystems.signalservice.internal.push.BackupAuthCheckResponse
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import org.whispersystems.signalservice.internal.push.RegistrationSessionMetadataResponse
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse
import java.util.Locale

/**
 * Class to interact with various registration-related endpoints.
 */
class RegistrationApi(
  private val pushServiceSocket: PushServiceSocket
) {

  /**
   * Request that the service initialize a new registration session.
   */
  fun createRegistrationSession(fcmToken: String?, mcc: String?, mnc: String?): NetworkResult<RegistrationSessionMetadataResponse> {
    return NetworkResult.fromFetch {
      pushServiceSocket.createVerificationSession(fcmToken, mcc, mnc)
    }
  }

  /**
   * Submit an FCM token to the service as proof that this is an honest user attempting to register.
   */
  fun submitPushChallengeToken(sessionId: String?, pushChallengeToken: String?): NetworkResult<RegistrationSessionMetadataResponse> {
    return NetworkResult.fromFetch {
      pushServiceSocket.patchVerificationSession(sessionId, null, null, null, null, pushChallengeToken)
    }
  }

  /**
   * Request an SMS verification code.  On success, the server will send
   * an SMS verification code to this Signal user.
   *
   * @param androidSmsRetrieverSupported whether the system framework will automatically parse the incoming verification message.
   */
  fun requestSmsVerificationCode(sessionId: String?, locale: Locale?, androidSmsRetrieverSupported: Boolean): NetworkResult<RegistrationSessionMetadataResponse> {
    return NetworkResult.fromFetch {
      pushServiceSocket.requestVerificationCode(sessionId, locale, androidSmsRetrieverSupported, PushServiceSocket.VerificationCodeTransport.SMS)
    }
  }

  /**
   * Submit a verification code sent by the service via one of the supported channels (SMS, phone call) to prove the registrant's control of the phone number.
   */
  fun verifyAccount(verificationCode: String, sessionId: String): NetworkResult<RegistrationSessionMetadataResponse> {
    return NetworkResult.fromFetch {
      pushServiceSocket.submitVerificationCode(sessionId, verificationCode)
    }
  }

  /**
   * Submit the cryptographic assets required for an account to use the service.
   */
  fun registerAccount(sessionId: String?, recoveryPassword: String?, attributes: AccountAttributes?, aciPreKeys: PreKeyCollection?, pniPreKeys: PreKeyCollection?, fcmToken: String?, skipDeviceTransfer: Boolean): NetworkResult<VerifyAccountResponse> {
    return NetworkResult.fromFetch {
      pushServiceSocket.submitRegistrationRequest(sessionId, recoveryPassword, attributes, aciPreKeys, pniPreKeys, fcmToken, skipDeviceTransfer)
    }
  }

  fun getSvrAuthCredential(e164: String, usernamePasswords: List<String>): NetworkResult<BackupAuthCheckResponse> {
    return NetworkResult.fromFetch {
      pushServiceSocket.checkBackupAuthCredentials(e164, usernamePasswords)
    }
  }
}
