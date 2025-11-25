/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.signal.core.util.serialization.ByteArrayToBase64Serializer
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.io.IOException
import java.util.Locale
import kotlin.time.Duration

interface NetworkController {

  /**
   * Request that the service initialize a new registration session.
   *
   * `POST /v1/verification/session`
   */
  suspend fun createSession(e164: String, fcmToken: String?, mcc: String?, mnc: String?): RegistrationNetworkResult<SessionMetadata, CreateSessionError>

  /**
   * Retrieve current status of a registration session.
   *
   * `GET /v1/verification/session/{session-id}`
   */
  suspend fun getSession(sessionId: String): RegistrationNetworkResult<SessionMetadata, GetSessionStatusError>

  /**
   * Update the session with new information.
   *
   * `PATCH /v1/verification/session/{session-id}`
   */
  suspend fun updateSession(sessionId: String?, pushChallengeToken: String?, captchaToken: String?): RegistrationNetworkResult<SessionMetadata, UpdateSessionError>

  /**
   * Request an SMS verification code. On success, the server will send an SMS verification code to this Signal user.
   *
   * `POST /v1/verification/session/{session-id}/code`
   *
   * @param androidSmsRetrieverSupported whether the system framework will automatically parse the incoming verification message.
   */
  suspend fun requestVerificationCode(
    sessionId: String,
    locale: Locale?,
    androidSmsRetrieverSupported: Boolean,
    transport: VerificationCodeTransport
  ): RegistrationNetworkResult<SessionMetadata, RequestVerificationCodeError>

  /**
   * Submit a verification code sent by the service via one of the supported channels (SMS, phone call) to prove the registrant's control of the phone number.
   *
   * `PUT /v1/verification/session/{session-id}/code`
   */
  suspend fun submitVerificationCode(sessionId: String, verificationCode: String): RegistrationNetworkResult<SessionMetadata, SubmitVerificationCodeError>

  /**
   * Officially register an account.
   * Must provide one of ([sessionId], [recoveryPassword]), but not both.
   *
   * `POST /v1/registration`
   *
   * @param e164 The phone number in E.164 format (used as username for basic auth)
   * @param password The password for basic auth
   */
  suspend fun registerAccount(
    e164: String,
    password: String,
    sessionId: String?,
    recoveryPassword: String?,
    attributes: AccountAttributes,
    aciPreKeys: PreKeyCollection,
    pniPreKeys: PreKeyCollection,
    fcmToken: String?,
    skipDeviceTransfer: Boolean
  ): RegistrationNetworkResult<RegisterAccountResponse, RegisterAccountError>

  /**
   * Retrieves an FCM token, if possible. Null means that this device does not support FCM.
   */
  suspend fun getFcmToken(): String?

  /**
   * Returns the URL to load in the WebView for captcha verification.
   */
  fun getCaptchaUrl(): String

  // TODO
//  /**
//   * Validates the provided SVR2 auth credentials, returning information on their usability.
//   *
//   * `POST /v2/svr/auth/check`
//   */
//  suspend fun validateSvr2AuthCredential(e164: String, usernamePasswords: List<String>)
//
//  /**
//   * Validates the provided SVR3 auth credentials, returning information on their usability.
//   *
//   * `POST /v3/backup/auth/check`
//   */
//  suspend fun validateSvr3AuthCredential(e164: String, usernamePasswords: List<String>)
//
//  /**
//   * Set [RestoreMethod] enum on the server for use by the old device to update UX.
//   */
//  suspend fun setRestoreMethod(token: String, method: RestoreMethod)
//
//  /**
//   * Registers a device as a linked device on a pre-existing account.
//   *
//   * `PUT /v1/devices/link`
//   *
//   * - 403: Incorrect account verification
//   * - 409: Device missing required account capability
//   * - 411: Account reached max number of linked devices
//   * - 422: Request is invalid
//   * - 429: Rate limited
//   */
//  suspend fun registerAsSecondaryDevice(verificationCode: String, attributes: AccountAttributes, aciPreKeys: PreKeyCollection, pniPreKeys: PreKeyCollection, fcmToken: String?)

  sealed interface RegistrationNetworkResult<out SuccessModel, out FailureModel> {
    data class Success<T>(val data: T) : RegistrationNetworkResult<T, Nothing>
    data class Failure<T>(val error: T) : RegistrationNetworkResult<Nothing, T>
    data class NetworkError(val exception: IOException) : RegistrationNetworkResult<Nothing, Nothing>
    data class ApplicationError(val exception: Throwable) : RegistrationNetworkResult<Nothing, Nothing>
  }

  sealed class CreateSessionError() {
    data class InvalidRequest(val message: String) : CreateSessionError()
    data class RateLimited(val retryAfter: Duration) : CreateSessionError()
  }

  sealed class GetSessionStatusError() {
    data class InvalidSessionId(val message: String) : GetSessionStatusError()
    data class SessionNotFound(val message: String) : GetSessionStatusError()
    data class InvalidRequest(val message: String) : GetSessionStatusError()
  }

  sealed class UpdateSessionError() {
    data class RejectedUpdate(val message: String) : UpdateSessionError()
    data class InvalidRequest(val message: String) : UpdateSessionError()
    data class RateLimited(val retryAfter: Duration, val session: SessionMetadata) : UpdateSessionError()
  }

  sealed class RequestVerificationCodeError() {
    data class InvalidSessionId(val message: String) : RequestVerificationCodeError()
    data class SessionNotFound(val message: String) : RequestVerificationCodeError()
    data class MissingRequestInformationOrAlreadyVerified(val session: SessionMetadata) : RequestVerificationCodeError()
    data class CouldNotFulfillWithRequestedTransport(val session: SessionMetadata) : RequestVerificationCodeError()
    data class InvalidRequest(val message: String) : RequestVerificationCodeError()
    data class RateLimited(val retryAfter: Duration, val session: SessionMetadata) : RequestVerificationCodeError()
    data class ThirdPartyServiceError(val data: ThirdPartyServiceErrorResponse) : RequestVerificationCodeError()
  }

  sealed class SubmitVerificationCodeError() {
    data class IncorrectVerificationCode(val message: String) : SubmitVerificationCodeError()
    data class SessionNotFound(val message: String) : SubmitVerificationCodeError()
    data class SessionAlreadyVerifiedOrNoCodeRequested(val session: SessionMetadata) : SubmitVerificationCodeError()
    data class RateLimited(val retryAfter: Duration, val session: SessionMetadata) : SubmitVerificationCodeError()
  }

  sealed class RegisterAccountError() {
    data class RegistrationRecoveryPasswordIncorrect(val message: String) : RegisterAccountError()
    data object DeviceTransferPossible : RegisterAccountError()
    data class InvalidRequest(val message: String) : RegisterAccountError()
    data class RegistrationLock(val data: RegistrationLockResponse) : RegisterAccountError()
    data class RateLimited(val retryAfter: Duration) : RegisterAccountError()
  }

  @Serializable
  @Parcelize
  data class SessionMetadata(
    val id: String,
    val nextSms: Long?,
    val nextCall: Long?,
    val nextVerificationAttempt: Long?,
    val allowedToRequestCode: Boolean,
    val requestedInformation: List<String>,
    val verified: Boolean
  ) : Parcelable

  @Serializable
  class AccountAttributes(
    val signalingKey: String?,
    val registrationId: Int,
    val voice: Boolean = true,
    val video: Boolean = true,
    val fetchesMessages: Boolean,
    val registrationLock: String?,
    @Serializable(with = ByteArrayToBase64Serializer::class)
    val unidentifiedAccessKey: ByteArray?,
    val unrestrictedUnidentifiedAccess: Boolean,
    val discoverableByPhoneNumber: Boolean,
    val capabilities: Capabilities?,
    val name: String?,
    val pniRegistrationId: Int,
    val recoveryPassword: String?
  ) {

    @Serializable
    data class Capabilities(
      val storage: Boolean,
      val versionedExpirationTimer: Boolean,
      val attachmentBackfill: Boolean,
      val spqr: Boolean
    )
  }

  @Serializable
  @Parcelize
  data class RegisterAccountResponse(
    @SerialName("uuid") val aci: String,
    val pni: String,
    @SerialName("number") val e164: String,
    val usernameHash: String?,
    val usernameLinkHandle: String?,
    val storageCapable: Boolean,
    val entitlements: Entitlements?,
    val reregistration: Boolean
  ) : Parcelable {
    @Serializable
    @Parcelize
    data class Entitlements(
      val badges: List<Badge>,
      val backup: Backup?
    ) : Parcelable

    @Serializable
    @Parcelize
    data class Badge(
      val id: String,
      val expirationSeconds: Long,
      val visible: Boolean
    ) : Parcelable

    @Serializable
    @Parcelize
    data class Backup(
      val backupLevel: Long,
      val expirationSeconds: Long
    ) : Parcelable
  }

  @Serializable
  data class RegistrationLockResponse(
    val timeRemaining: Long,
    val svr2Credentials: SvrCredentials
  ) {

    @Serializable
    data class SvrCredentials(
      val username: String,
      val password: String
    )
  }

  @Serializable
  data class ThirdPartyServiceErrorResponse(
    val reason: String,
    val permanentFailure: Boolean
  )

  data class PreKeyCollection(
    val identityKey: IdentityKey,
    val signedPreKey: SignedPreKeyRecord,
    val lastResortKyberPreKey: KyberPreKeyRecord
  )

  enum class VerificationCodeTransport {
    SMS, VOICE
  }
}
