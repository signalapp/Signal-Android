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
import okio.ByteString
import org.signal.core.models.AccountEntropyPool
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
   *
   * @return True if a job was successfully enqueued, otherwise false. Enqueueing will fail if a PIN is unavailable, which can happen in some restoration flows.
   */
  suspend fun enqueueSvrGuessResetJobIfPossible(): Boolean

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
   * Enqueue a durable unit of work to sync your account attributes based on the current state of your own storage.
   * This is typically done at the end of the registration process to clean up any possible changes to the AEP
   * that may be made post-registration (for instance, you may restore a backup post-registration with a new AEP that
   * we'd like to re-use).
   */
  suspend fun enqueueAccountAttributesSyncJob()

  /**
   * Fetches metadata about your current backup. This will be different for different key/credential pairs. For example, message credentials will always
   * return 0 for used space since that is stored under the media key/credential.
   *
   * GET /v1/archives
   * - 200: Success
   * - 400: Bad arguments. The request may have been made on an authenticated channel.
   * - 401: The provided backup auth credential presentation could not be verified or the public key signature was invalid or there is no backup associated with
   *        the backup-id in the presentation or the credential was of the wrong type (messages/media)
   * - 403: Forbidden
   * - 404: No backup
   * - 429: Rate limited
   */
  suspend fun getRemoteBackupInfo(aep: AccountEntropyPool): RequestResult<GetBackupInfoResponse, GetBackupInfoError>

  /**
   * Gets the last-modified timestamp of the backup file on the CDN.
   * Requires [GetBackupInfoResponse] to know the CDN location of the backup.
   *
   * @param aep The Account Entropy Pool used to derive backup credentials.
   * @param backupInfo The backup info response containing CDN location details.
   * @return The last-modified time as epoch milliseconds, or an appropriate error.
   */
  suspend fun getBackupFileLastModified(aep: AccountEntropyPool, backupInfo: GetBackupInfoResponse): RequestResult<Long, GetBackupInfoError>

  /**
   * Verifies that [aep] is the correct backup key for the current account by checking it against the remote backup.
   * Used to detect an incorrect backup passphrase before attempting a full restore, so the user can be given the
   * chance to re-enter it.
   *
   * A [VerifyBackupKeyError.IncorrectKey] result means the key failed zk verification (i.e. it does not match the
   * account's backup).
   */
  suspend fun verifyBackupKeyAssociatedWithAccount(aep: AccountEntropyPool): RequestResult<Unit, VerifyBackupKeyError>

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

  /**
   * Starts a provisioning session for QR-based device linking (registering this device as a secondary
   * device on a pre-existing account).
   *
   * The returned flow emits [LinkDeviceProvisioningEvent]s:
   * - [LinkDeviceProvisioningEvent.QrCodeReady] whenever a new QR code URL is available (e.g. due to socket rotation).
   * - [LinkDeviceProvisioningEvent.MessageReceived] when the primary device scans the QR code and sends provisioning data.
   * - [LinkDeviceProvisioningEvent.Error] if the provisioning session encounters an unrecoverable error.
   *
   * The flow manages socket lifecycle (rotation, keep-alive) internally. Cancel the collecting coroutine to stop provisioning.
   *
   * @param allowLinkAndSync Whether we allow data sync during linking. Normally allowed, but disabled for re-links.
   */
  fun startLinkDeviceProvisioning(allowLinkAndSync: Boolean): Flow<LinkDeviceProvisioningEvent>

  /**
   * Performs the network call to register this device as a linked (secondary) device on a pre-existing
   * account (`PUT /v1/devices/link`), authenticated via basic auth with [e164] and [password].
   *
   * This only performs the network request and returns the assigned device id. The caller is responsible
   * for committing the account locally (via [StorageController.commitRegistrationData]) and performing the
   * post-registration housekeeping (via [onLinkedDeviceRegistered]) and any restores.
   */
  suspend fun registerAsLinkedDevice(
    e164: String,
    password: String,
    provisioningCode: String,
    deviceAttributes: DeviceAttributes,
    aciPreKeys: PreKeyCollection,
    pniPreKeys: PreKeyCollection,
    fcmToken: String?
  ): RequestResult<LinkDeviceResponse, RegisterAsLinkedDeviceError>

  /**
   * Performs the network-side post-registration work for a freshly linked device, after the account has been
   * committed locally via [StorageController.commitRegistrationData]: refreshes remote config and requests the
   * initial sync messages from the primary.
   *
   * Intentionally does *not* include the link-and-sync backup restore (see [StorageController.restoreLinkAndSyncBackup])
   * or the storage-service restore (see [restoreLinkedDeviceFromStorageService]); the registration module
   * sequences those separately so progress can be surfaced and timing controlled. Local-state finalization (e.g.
   * the read-receipts preference) is applied as part of [StorageController.commitRegistrationData].
   */
  suspend fun onLinkedDeviceRegistered()

  /**
   * Waits for the primary device to make a decision on a link-and-sync transfer (a long-poll that may be
   * retried internally up to ~1 hour).
   *
   * Intended to be called while showing a spinner before navigating to the message-sync screen.
   */
  suspend fun awaitLinkAndSyncArchive(): LinkAndSyncWaitResult

  /**
   * Restores account data from the storage service after this device has been linked as a secondary device.
   *
   * The registration module decides *when* to call this: immediately after [registerAsLinkedDevice] when there
   * is no link-and-sync backup, or only after the link-and-sync backup has been applied when there is one.
   *
   * Implementations should be best-effort and may no-op when there is nothing to restore (e.g. when the primary
   * did not share an account entropy pool).
   */
  suspend fun restoreLinkedDeviceFromStorageService()

  /**
   * Starts `DeviceToDeviceTransferService` in server mode on the new device. The concrete
   * [org.signal.devicetransfer.ServerTask] that receives and imports the backup lives in the app
   * module (it references SignalDatabase / FullBackupImporter / SignalStore), as does the
   * foreground-service notification channel and the tap-through `PendingIntent`. Consolidating
   * the start call here keeps this module free of app-specific notification plumbing.
   *
   * @param aep The user's [AccountEntropyPool]. The production implementation ignores this (it
   *   pulls the AEP from `SignalStore.account` directly); demo/test implementations need it
   *   passed in because they have no equivalent store.
   */
  fun startNewDeviceTransferServer(context: android.content.Context, aep: AccountEntropyPool)

  /**
   * Reports the user's chosen restore method to the server so the old device's quick-restore UI can update.
   * The [token] is the `restoreMethodToken` delivered in the [ProvisioningMessage].
   *
   * `PUT /v1/devices/restore_account/{token}`
   */
  suspend fun setRestoreMethod(token: String, method: RestoreMethod): RequestResult<Unit, SetRestoreMethodError>

  /**
   * Best-effort restore of the AccountRecord from the storage service. Implementations should
   * always kick off the restore (typically via a durable job) so that work continues in the
   * background, but this call must return within [timeout]. A timeout is reported as a non-success
   * result, but the underlying restore may still complete shortly after.
   *
   * Intended to be invoked once the user has set/verified their PIN, so that subsequent screens
   * (e.g. the create-profile screen) can pre-seed themselves from any data that was restored.
   */
  suspend fun restoreAccountRecord(timeout: Duration): RequestResult<Unit, RestoreAccountRecordError>

  /**
   * Persists the user's chosen profile name (and optional avatar) for the freshly-registered account
   * and arranges for it to be synced to the service. Implementations may save the data locally and
   * enqueue a durable job to perform the actual upload, since profile sync is allowed to happen in
   * the background.
   *
   * Also persists [discoverableByPhoneNumber] as the user's choice for whether other users can find
   * them on Signal by their phone number.
   *
   * @param givenName The user's given/first name. Must be non-blank.
   * @param familyName The user's family/last name. May be blank.
   * @param avatar Raw avatar bytes, or null to leave the avatar unchanged/cleared.
   * @param discoverableByPhoneNumber If true, anyone who has the user's phone number can find them
   *   on Signal; if false, the user is only reachable via existing chats.
   */
  suspend fun setProfile(
    givenName: String,
    familyName: String,
    avatar: ByteArray?,
    discoverableByPhoneNumber: Boolean
  ): RequestResult<Unit, SetProfileError>

  sealed class CreateSessionError : BadRequestError {
    data class InvalidRequest(val message: String) : CreateSessionError()
    data class RateLimited(val retryAfter: Duration) : CreateSessionError()
  }

  sealed class GetSessionStatusError : BadRequestError {
    data class InvalidSessionId(val message: String) : GetSessionStatusError()
    data class SessionNotFound(val message: String) : GetSessionStatusError()
    data class InvalidRequest(val message: String) : GetSessionStatusError()
  }

  sealed class UpdateSessionError : BadRequestError {
    data class RejectedUpdate(val message: String) : UpdateSessionError()
    data class InvalidRequest(val message: String) : UpdateSessionError()
    data class RateLimited(val retryAfter: Duration, val session: SessionMetadata) : UpdateSessionError()
  }

  sealed class RequestVerificationCodeError : BadRequestError {
    data class InvalidSessionId(val message: String) : RequestVerificationCodeError()
    data class SessionNotFound(val message: String) : RequestVerificationCodeError()
    data class MissingRequestInformationOrAlreadyVerified(val session: SessionMetadata) : RequestVerificationCodeError()
    data class CouldNotFulfillWithRequestedTransport(val session: SessionMetadata) : RequestVerificationCodeError()
    data class InvalidRequest(val message: String) : RequestVerificationCodeError()
    data class RateLimited(val retryAfter: Duration, val session: SessionMetadata) : RequestVerificationCodeError()
    data class ThirdPartyServiceError(val data: ThirdPartyServiceErrorResponse) : RequestVerificationCodeError()
  }

  sealed class SubmitVerificationCodeError : BadRequestError {
    data class InvalidSessionIdOrVerificationCode(val message: String) : SubmitVerificationCodeError()
    data class SessionNotFound(val message: String) : SubmitVerificationCodeError()
    data class SessionAlreadyVerifiedOrNoCodeRequested(val session: SessionMetadata) : SubmitVerificationCodeError()
    data class RateLimited(val retryAfter: Duration, val session: SessionMetadata) : SubmitVerificationCodeError()
  }

  sealed class RegisterAccountError : BadRequestError {
    data class SessionNotFoundOrNotVerified(val message: String) : RegisterAccountError()
    data class RegistrationRecoveryPasswordIncorrect(val message: String) : RegisterAccountError()
    data object DeviceTransferPossible : RegisterAccountError()
    data class InvalidRequest(val message: String) : RegisterAccountError()
    data class RegistrationLock(val data: RegistrationLockResponse) : RegisterAccountError()
    data class RateLimited(val retryAfter: Duration) : RegisterAccountError()
  }

  sealed class RestoreMasterKeyError : BadRequestError {
    data class WrongPin(val triesRemaining: Int) : RestoreMasterKeyError()
    data object NoDataFound : RestoreMasterKeyError()
  }

  sealed class BackupMasterKeyError : BadRequestError {
    data object EnclaveNotFound : BackupMasterKeyError()
    data object NotRegistered : BackupMasterKeyError()
  }

  sealed class SetRegistrationLockError : BadRequestError {
    data class InvalidRequest(val message: String) : SetRegistrationLockError()
    data object Unauthorized : SetRegistrationLockError()
    data object NotRegistered : SetRegistrationLockError()
    data object NoPinSet : SetRegistrationLockError()
  }

  sealed class SetAccountAttributesError : BadRequestError {
    data class InvalidRequest(val message: String) : SetAccountAttributesError()
    data object Unauthorized : SetAccountAttributesError()
  }

  sealed class GetSvrCredentialsError : BadRequestError {
    data object Unauthorized : GetSvrCredentialsError()
    data object NoServiceCredentialsAvailable : GetSvrCredentialsError()
  }

  sealed class CheckSvrCredentialsError : BadRequestError {
    data object Unauthorized : CheckSvrCredentialsError()
    data class InvalidRequest(val message: String) : CheckSvrCredentialsError()
  }

  sealed class SetRestoreMethodError : BadRequestError {
    data class InvalidRequest(val message: String) : SetRestoreMethodError()
    data class RateLimited(val retryAfter: Duration) : SetRestoreMethodError()
  }

  sealed class SetProfileError : BadRequestError {
    data object NotRegistered : SetProfileError()
    data class IOError(val cause: Throwable) : SetProfileError()
    data class InvalidRequest(val message: String) : SetProfileError()
  }

  sealed class RestoreAccountRecordError : BadRequestError {
    data object Timeout : RestoreAccountRecordError()
    data class IOError(val cause: Throwable) : RestoreAccountRecordError()
  }

  sealed class GetBackupInfoError : BadRequestError {
    data class BadArguments(val body: String? = null) : GetBackupInfoError()
    data class BadAuthCredential(val body: String? = null) : GetBackupInfoError()
    data class Forbidden(val body: String? = null) : GetBackupInfoError()
    data object NoBackup : GetBackupInfoError()
    data class RateLimited(val retryAfter: Duration) : GetBackupInfoError()
  }

  sealed class VerifyBackupKeyError : BadRequestError {
    /** The entered key failed zk verification -- it is not the correct backup key for this account. */
    data object IncorrectKey : VerifyBackupKeyError()

    /** The key verified, but no backup exists for this account. */
    data object NoBackup : VerifyBackupKeyError()

    data class RateLimited(val retryAfter: Duration?) : VerifyBackupKeyError()
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
    val pniRegistrationId: Int,
    val recoveryPassword: String?
  ) {

    @Serializable
    data class Capabilities(
      val storage: Boolean,
      val versionedExpirationTimer: Boolean,
      val attachmentBackfill: Boolean,
      val spqr: Boolean,
      val usernameChangeSyncMessage: Boolean
    )
  }

  @Serializable
  class DeviceAttributes(
    val fetchesMessages: Boolean,
    val registrationId: Int,
    val pniRegistrationId: Int,
    val name: String?,
    val capabilities: AccountAttributes.Capabilities?
  )

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
   * The user's chosen restore method, reported back to the old device via [setRestoreMethod] so its UX can update.
   */
  enum class RestoreMethod {
    REMOTE_BACKUP, LOCAL_BACKUP, DEVICE_TRANSFER, DECLINE
  }

  @Serializable
  data class GetBackupInfoResponse(
    val cdn: Int?,
    val backupDir: String?,
    val mediaDir: String?,
    val backupName: String?,
    val usedSpace: Long?
  )

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

  /**
   * Data received from the primary device during QR-based device linking.
   *
   * The ACI/PNI are resolved to their canonical string form by the implementation. Identity keys are
   * provided by the primary so this device shares the account's identity.
   */
  class LinkDeviceProvisioningMessage(
    val e164: String,
    val provisioningCode: String,
    val aci: String,
    val pni: String,
    val aciIdentityKeyPair: IdentityKeyPair,
    val pniIdentityKeyPair: IdentityKeyPair,
    val profileKey: ByteArray,
    val ephemeralBackupKey: ByteString?,
    val accountEntropyPool: String?,
    val mediaRootBackupKey: ByteString?,
    val readReceipts: Boolean?
  )

  /**
   * Events emitted during a device-linking provisioning session.
   */
  sealed interface LinkDeviceProvisioningEvent {
    /** A new QR code URL is available for display. */
    data class QrCodeReady(val url: String) : LinkDeviceProvisioningEvent

    /** The primary device has scanned the QR code and sent provisioning data. */
    data class MessageReceived(val message: LinkDeviceProvisioningMessage) : LinkDeviceProvisioningEvent

    /** The provisioning session encountered an error. */
    data class Error(val cause: Throwable?) : LinkDeviceProvisioningEvent
  }

  /** Minimal view of the `PUT /v1/devices/link` success body; we only need the assigned device id. */
  @Serializable
  data class LinkDeviceResponse(
    val deviceId: Int
  )

  sealed interface RegisterAsLinkedDeviceError : BadRequestError {
    data object IncorrectVerification : RegisterAsLinkedDeviceError
    data object MissingCapability : RegisterAsLinkedDeviceError
    data object MaxLinkedDevices : RegisterAsLinkedDeviceError
    data class InvalidRequest(val message: String? = null) : RegisterAsLinkedDeviceError
    data class RateLimited(val retryAfter: Duration?) : RegisterAsLinkedDeviceError
  }
}

/**
 * Result of waiting for the primary's link-and-sync transfer archive.
 */
sealed interface LinkAndSyncWaitResult {
  /** The primary made a backup available at the given CDN location; proceed to download + apply it. */
  data class ArchiveAvailable(val cdn: Int, val key: String) : LinkAndSyncWaitResult

  /** The primary declined to sync, or never delivered an archive in time. */
  data object ContinueWithoutBackup : LinkAndSyncWaitResult

  /** The primary asked this device to re-link. Registration should be reset. */
  data object RelinkRequired : LinkAndSyncWaitResult
}
