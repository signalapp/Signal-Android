/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.registration

import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.registration.proto.RegistrationProvisionMessage
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.account.AccountAttributes
import org.whispersystems.signalservice.api.account.PreKeyCollection
import org.whispersystems.signalservice.internal.crypto.PrimaryProvisioningCipher
import org.whispersystems.signalservice.internal.push.BackupV2AuthCheckResponse
import org.whispersystems.signalservice.internal.push.BackupV3AuthCheckResponse
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
   *
   * `POST /v1/verification/session`
   */
  fun createRegistrationSession(fcmToken: String?, mcc: String?, mnc: String?): NetworkResult<RegistrationSessionMetadataResponse> {
    return NetworkResult.fromFetch {
      pushServiceSocket.createVerificationSession(fcmToken, mcc, mnc)
    }
  }

  /**
   * Retrieve current status of a registration session.
   *
   * `GET /v1/verification/session/{session-id}`
   */
  fun getRegistrationSessionStatus(sessionId: String): NetworkResult<RegistrationSessionMetadataResponse> {
    return NetworkResult.fromFetch {
      pushServiceSocket.getSessionStatus(sessionId)
    }
  }

  /**
   * Submit an FCM token to the service as proof that this is an honest user attempting to register.
   *
   * `PATCH /v1/verification/session/{session-id}`
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
   * `POST /v1/verification/session/{session-id}/code`
   *
   * @param androidSmsRetrieverSupported whether the system framework will automatically parse the incoming verification message.
   */
  fun requestSmsVerificationCode(sessionId: String?, locale: Locale?, androidSmsRetrieverSupported: Boolean, transport: PushServiceSocket.VerificationCodeTransport): NetworkResult<RegistrationSessionMetadataResponse> {
    return NetworkResult.fromFetch {
      pushServiceSocket.requestVerificationCode(sessionId, locale, androidSmsRetrieverSupported, transport)
    }
  }

  /**
   * Submit a verification code sent by the service via one of the supported channels (SMS, phone call) to prove the registrant's control of the phone number.
   *
   * `PUT /v1/verification/session/{session-id}/code`
   */
  fun verifyAccount(sessionId: String, verificationCode: String): NetworkResult<RegistrationSessionMetadataResponse> {
    return NetworkResult.fromFetch {
      pushServiceSocket.submitVerificationCode(sessionId, verificationCode)
    }
  }

  /**
   * Submits the solved captcha token to the service.
   *
   * `PATCH /v1/verification/session/{session-id}`
   */
  fun submitCaptchaToken(sessionId: String, captchaToken: String): NetworkResult<RegistrationSessionMetadataResponse> {
    return NetworkResult.fromFetch {
      pushServiceSocket.patchVerificationSession(sessionId, null, null, null, captchaToken, null)
    }
  }

  /**
   * Submit the cryptographic assets required for an account to use the service.
   *
   * `POST /v1/registration`
   */
  fun registerAccount(sessionId: String?, recoveryPassword: String?, attributes: AccountAttributes?, aciPreKeys: PreKeyCollection?, pniPreKeys: PreKeyCollection?, fcmToken: String?, skipDeviceTransfer: Boolean): NetworkResult<VerifyAccountResponse> {
    return NetworkResult.fromFetch {
      pushServiceSocket.submitRegistrationRequest(sessionId, recoveryPassword, attributes, aciPreKeys, pniPreKeys, fcmToken, skipDeviceTransfer)
    }
  }

  /**
   * Validates the provided SVR2 auth credentials, returning information on their usability.
   *
   * `POST /v2/backup/auth/check`
   */
  fun validateSvr2AuthCredential(e164: String, usernamePasswords: List<String>): NetworkResult<BackupV2AuthCheckResponse> {
    return NetworkResult.fromFetch {
      pushServiceSocket.checkSvr2AuthCredentials(e164, usernamePasswords)
    }
  }

  /**
   * Validates the provided SVR3 auth credentials, returning information on their usability.
   *
   * `POST /v3/backup/auth/check`
   */
  fun validateSvr3AuthCredential(e164: String, usernamePasswords: List<String>): NetworkResult<BackupV3AuthCheckResponse> {
    return NetworkResult.fromFetch {
      pushServiceSocket.checkSvr3AuthCredentials(e164, usernamePasswords)
    }
  }

  /**
   * Encrypts and sends the [RegistrationProvisionMessage] from the current primary (old device) to the new device over
   * the provisioning web socket identified by [deviceIdentifier].
   */
  fun sendReRegisterDeviceProvisioningMessage(
    deviceIdentifier: String,
    deviceKey: ECPublicKey,
    registrationProvisionMessage: RegistrationProvisionMessage
  ): NetworkResult<Unit> {
    val cipherText = PrimaryProvisioningCipher(deviceKey).encrypt(registrationProvisionMessage)

    return NetworkResult.fromFetch {
      pushServiceSocket.sendProvisioningMessage(deviceIdentifier, cipherText)
    }
  }

  /**
   * Set [RestoreMethod] enum on the server for use by the old device to update UX.
   */
  fun setRestoreMethod(token: String, method: RestoreMethod): NetworkResult<Unit> {
    return NetworkResult.fromFetch {
      pushServiceSocket.setRestoreMethodChosen(token, RestoreMethodBody(method = method))
    }
  }

  /**
   * Wait for the [RestoreMethod] to be set on the server by the new device. This is a long polling operation.
   */
  fun waitForRestoreMethod(token: String, timeout: Int = 30): NetworkResult<RestoreMethod> {
    return NetworkResult.fromFetch {
      pushServiceSocket.waitForRestoreMethodChosen(token, timeout).method ?: RestoreMethod.DECLINE
    }
  }
}
