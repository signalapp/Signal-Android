/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.os.Parcelable
import kotlinx.coroutines.flow.Flow
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.signal.core.models.MasterKey
import org.signal.core.util.serialization.ByteArrayToBase64Serializer
import org.signal.libsignal.net.BadRequestError
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.util.Locale
import kotlin.time.Duration

interface NetworkController {

  /**
   * Request that the service initialize a new registration session.
   *
   * `POST /v1/verification/session`
   */
  suspend fun createSession(e164: String, fcmToken: String?, mcc: String?, mnc: String?): RequestResult<SessionMetadata, CreateSessionError>

  /**
   * Retrieve current status of a registration session.
   *
   * `GET /v1/verification/session/{session-id}`
   */
  suspend fun getSession(sessionId: String): RequestResult<SessionMetadata, GetSessionStatusError>

  /**
   * Update the session with new information.
   *
   * `PATCH /v1/verification/session/{session-id}`
   */
  suspend fun updateSession(sessionId: String?, pushChallengeToken: String?, captchaToken: String?): RequestResult<SessionMetadata, UpdateSessionError>

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
  ): RequestResult<SessionMetadata, RequestVerificationCodeError>

  /**
   * Submit a verification code sent by the service via one of the supported channels (SMS, phone call) to prove the registrant's control of the phone number.
   *
   * `PUT /v1/verification/session/{session-id}/code`
   */
  suspend fun submitVerificationCode(sessionId: String, verificationCode: String): RequestResult<SessionMetadata, SubmitVerificationCodeError>

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
  ): RequestResult<RegisterAccountResponse, RegisterAccountError>

  /**
   * Retrieves an FCM token, if possible. Null means that this device does not support FCM.
   */
  suspend fun getFcmToken(): String?

  /**
   * Waits for a push challenge token to arrive via FCM.
   * This is a suspending function that will complete when the token arrives.
   * The caller should wrap this in withTimeoutOrNull to handle timeout scenarios.
   *
   * @return The push challenge token, or null if cancelled/unavailable.
   */
  suspend fun awaitPushChallengeToken(): String?

  /**
   * Returns the URL to load in the WebView for captcha verification.
   */
  fun getCaptchaUrl(): String

  /**
   * Attempts to restore the master key from SVR using the provided credentials and PIN.
   *
   * This is called when the user encounters a registration lock and needs to prove
   * they know their PIN to proceed with registration.
   *
   * @param svrCredentials The SVR2 credentials provided by the server during the registration lock response.
   * @param pin The user-entered PIN.
   * @return The restored master key on success, or an appropriate error.
   */
  suspend fun restoreMasterKeyFromSvr(
    svrCredentials: SvrCredentials,
    pin: String
  ): RequestResult<MasterKeyResponse, RestoreMasterKeyError>

  /**
   * Backs up the master key to SVR, protected by the user's PIN.
   *
   * @param pin The user-chosen PIN to protect the backup.
   * @param masterKey The master key to backup.
   * @return Success or an appropriate error.
   */
  suspend fun setPinAndMasterKeyOnSvr(
    pin: String,
    masterKey: MasterKey
  ): RequestResult<SvrCredentials?, BackupMasterKeyError>

  /**
   * Requests that the currently-set PIN and [MasterKey] are backed up to SVR.
   * It should always be the case that when this is called, you should have a stored PIN and [MasterKey].
   * If you do not, you should probably crash.
   */
  suspend fun enqueueSvrGuessResetJob()

  /**
   * Enables registration lock on the account using the registration lock token
   * derived from the master key.
   *
   * @return Success or an appropriate error.
   */
  suspend fun enableRegistrationLock(): RequestResult<Unit, SetRegistrationLockError>

  /**
   * Disables registration lock on the account.
   *
   * @return Success or an appropriate error.
   */
  suspend fun disableRegistrationLock(): RequestResult<Unit, SetRegistrationLockError>

  /**
   * Retrieves SVR2 authentication credentials for the authenticated account.
   *
   * `GET /v2/svr/auth`
   *
   * @return SVR credentials on success, or an appropriate error.
   */
  suspend fun getSvrCredentials(): RequestResult<SvrCredentials, GetSvrCredentialsError>

  /**
   * Checks if the SVR2 credentials are valid for the given phone number.
   *
   * `POST /v2/svr/auth/check`
   *
   * @return A response containing a mapping of which credentials are matches.
   */
  suspend fun checkSvrCredentials(e164: String, credentials: List<SvrCredentials>): RequestResult<CheckSvrCredentialsResponse, CheckSvrCredentialsError>

  /**
   * Updates account attributes on the server.
   *
   * `PUT /v1/accounts/attributes`
   *
   * @param attributes The account attributes to set.
   * @return Success or an appropriate error.
   */
  suspend fun setAccountAttributes(attributes: AccountAttributes): RequestResult<Unit, SetAccountAttributesError>

  /**
   * Starts a provisioning session for QR-based quick restore.
   *
   * The returned flow emits [ProvisioningEvent]s:
   * - [ProvisioningEvent.QrCodeReady] whenever a new QR code URL is available (e.g. due to socket rotation).
   * - [ProvisioningEvent.MessageReceived] when the old device scans the QR code and sends provisioning data.
   * - [ProvisioningEvent.Error] if the provisioning session encounters an unrecoverable error.
   *
   * The flow will manage socket lifecycle (rotation, keep-alive) internally.
   * Cancel the collecting coroutine to stop provisioning.
   */
  fun startProvisioning(): Flow<ProvisioningEvent>

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

  sealed class CreateSessionError() : BadRequestError {
    data class InvalidRequest(val message: String) : CreateSessionError()
    data class RateLimited(val retryAfter: Duration) : CreateSessionError()
  }

  sealed class GetSessionStatusError() : BadRequestError {
    data class InvalidSessionId(val message: String) : GetSessionStatusError()
    data class SessionNotFound(val message: String) : GetSessionStatusError()
    data class InvalidRequest(val message: String) : GetSessionStatusError()
  }

  sealed class UpdateSessionError() : BadRequestError {
    data class RejectedUpdate(val message: String) : UpdateSessionError()
    data class InvalidRequest(val message: String) : UpdateSessionError()
    data class RateLimited(val retryAfter: Duration, val session: SessionMetadata) : UpdateSessionError()
  }

  sealed class RequestVerificationCodeError() : BadRequestError {
    data class InvalidSessionId(val message: String) : RequestVerificationCodeError()
    data class SessionNotFound(val message: String) : RequestVerificationCodeError()
    data class MissingRequestInformationOrAlreadyVerified(val session: SessionMetadata) : RequestVerificationCodeError()
    data class CouldNotFulfillWithRequestedTransport(val session: SessionMetadata) : RequestVerificationCodeError()
    data class InvalidRequest(val message: String) : RequestVerificationCodeError()
    data class RateLimited(val retryAfter: Duration, val session: SessionMetadata) : RequestVerificationCodeError()
    data class ThirdPartyServiceError(val data: ThirdPartyServiceErrorResponse) : RequestVerificationCodeError()
  }

  sealed class SubmitVerificationCodeError() : BadRequestError {
    data class InvalidSessionIdOrVerificationCode(val message: String) : SubmitVerificationCodeError()
    data class SessionNotFound(val message: String) : SubmitVerificationCodeError()
    data class SessionAlreadyVerifiedOrNoCodeRequested(val session: SessionMetadata) : SubmitVerificationCodeError()
    data class RateLimited(val retryAfter: Duration, val session: SessionMetadata) : SubmitVerificationCodeError()
  }

  sealed class RegisterAccountError() : BadRequestError {
    data class SessionNotFoundOrNotVerified(val message: String) : RegisterAccountError()
    data class RegistrationRecoveryPasswordIncorrect(val message: String) : RegisterAccountError()
    data object DeviceTransferPossible : RegisterAccountError()
    data class InvalidRequest(val message: String) : RegisterAccountError()
    data class RegistrationLock(val data: RegistrationLockResponse) : RegisterAccountError()
    data class RateLimited(val retryAfter: Duration) : RegisterAccountError()
  }

  sealed class RestoreMasterKeyError() : BadRequestError {
    data class WrongPin(val triesRemaining: Int) : RestoreMasterKeyError()
    data object NoDataFound : RestoreMasterKeyError()
  }

  sealed class BackupMasterKeyError() : BadRequestError {
    data object EnclaveNotFound : BackupMasterKeyError()
    data object NotRegistered : BackupMasterKeyError()
  }

  sealed class SetRegistrationLockError() : BadRequestError {
    data class InvalidRequest(val message: String) : SetRegistrationLockError()
    data object Unauthorized : SetRegistrationLockError()
    data object NotRegistered : SetRegistrationLockError()
    data object NoPinSet : SetRegistrationLockError()
  }

  sealed class SetAccountAttributesError() : BadRequestError {
    data class InvalidRequest(val message: String) : SetAccountAttributesError()
    data object Unauthorized : SetAccountAttributesError()
  }

  sealed class GetSvrCredentialsError() : BadRequestError {
    data object Unauthorized : GetSvrCredentialsError()
    data object NoServiceCredentialsAvailable : GetSvrCredentialsError()
  }

  sealed class CheckSvrCredentialsError() : BadRequestError {
    data object Unauthorized : CheckSvrCredentialsError()
    data class InvalidRequest(val message: String) : CheckSvrCredentialsError()
  }

  data class MasterKeyResponse(
    val masterKey: MasterKey
  )

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
  )

  @Serializable
  @Parcelize
  data class SvrCredentials(
    val username: String,
    val password: String
  ) : Parcelable

  @Serializable
  data class CheckSvrCredentialsResponse(
    val matches: Map<String, String>
  ) {
    /**
     * The first valid credential, if any.
     *
     * The response is structured like this:
     * {
     *   matches: {
     *     <token>: "match|no-match|invalid"
     *   }
     * }
     *
     * So we find the first map entry with "match". The token is "username:password", so we split it apart.
     * Important: The password can have ":" in it, so we need to make sure to just split on the first ":".
     */
    val validCredential: SvrCredentials? by lazy {
      matches.entries.firstOrNull { it.value == "match" }?.key?.split(":", limit = 2)?.let { SvrCredentials(it[0], it[1]) }
    }
  }

  @Serializable
  data class CheckSvrCredentialsRequest(
    val number: String,
    val tokens: List<String>
  ) {
    companion object {
      fun createForCredentials(number: String, credentials: List<SvrCredentials>): CheckSvrCredentialsRequest {
        return CheckSvrCredentialsRequest(
          number = number,
          tokens = credentials.map { "${it.username}:${it.password}" }
        )
      }
    }
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

  /**
   * Data received from the old device during QR-based provisioning.
   */
  data class ProvisioningMessage(
    val accountEntropyPool: String,
    val e164: String,
    val pin: String?,
    val aciIdentityKeyPair: IdentityKeyPair,
    val pniIdentityKeyPair: IdentityKeyPair,
    val platform: Platform,
    val tier: Tier?,
    val backupTimestampMs: Long?,
    val backupSizeBytes: Long?,
    val restoreMethodToken: String,
    val backupVersion: Long
  ) {
    enum class Platform { ANDROID, IOS }
    enum class Tier { FREE, PAID }
  }

  /**
   * Events emitted during a provisioning session.
   */
  sealed interface ProvisioningEvent {
    /** A new QR code URL is available for display. */
    data class QrCodeReady(val url: String) : ProvisioningEvent

    /** The old device has scanned the QR code and sent provisioning data. */
    data class MessageReceived(val message: ProvisioningMessage) : ProvisioningEvent

    /** The provisioning session encountered an error. */
    data class Error(val cause: Throwable?) : ProvisioningEvent
  }
}
