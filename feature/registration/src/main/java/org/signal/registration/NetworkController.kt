/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import okio.ByteString
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.BadRequestError
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialRequest
import org.signal.network.api.RegistrationApiV2.AccountAttributes
import org.signal.network.api.RegistrationApiV2.CheckSvrCredentialsError
import org.signal.network.api.RegistrationApiV2.CheckSvrCredentialsResponse
import org.signal.network.api.RegistrationApiV2.CreateLoginReceiptCredentialError
import org.signal.network.api.RegistrationApiV2.CreateLoginReceiptCredentialResult
import org.signal.network.api.RegistrationApiV2.CreateSessionError
import org.signal.network.api.RegistrationApiV2.DeviceAttributes
import org.signal.network.api.RegistrationApiV2.GetSessionStatusError
import org.signal.network.api.RegistrationApiV2.LinkDeviceResponse
import org.signal.network.api.RegistrationApiV2.LoginPurchasePaymentProvider
import org.signal.network.api.RegistrationApiV2.PreKeyCollection
import org.signal.network.api.RegistrationApiV2.RegisterAccountError
import org.signal.network.api.RegistrationApiV2.RegisterAccountResponse
import org.signal.network.api.RegistrationApiV2.RegisterAccountWithoutPhoneNumberError
import org.signal.network.api.RegistrationApiV2.RegisterAsLinkedDeviceError
import org.signal.network.api.RegistrationApiV2.RequestVerificationCodeError
import org.signal.network.api.RegistrationApiV2.RestoreMethod
import org.signal.network.api.RegistrationApiV2.SessionMetadata
import org.signal.network.api.RegistrationApiV2.SetRestoreMethodError
import org.signal.network.api.RegistrationApiV2.SubmitVerificationCodeError
import org.signal.network.api.RegistrationApiV2.SvrCredentials
import org.signal.network.api.RegistrationApiV2.UpdateSessionError
import org.signal.network.api.RegistrationApiV2.VerificationCodeTransport
import org.whispersystems.signalservice.internal.push.ProvisionMessage
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
  suspend fun updateSession(sessionId: String, pushChallengeToken: String?, captchaToken: String?): RequestResult<SessionMetadata, UpdateSessionError>

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
   * Redeems a completed one-time Signal Login purchase for a receipt credential, which can then be presented to
   * [registerAccountWithoutPhoneNumber] to create an account that has no phone number.
   *
   * Retries for the same [purchaseIdentifier] must reuse the same [receiptCredentialRequest].
   *
   * Implementations must apply the same validations to the returned credential as are applied to one-time donation
   * receipts, and the expected receipt expiration is `purchaseDate + 5 * 366` days, plus padding for clock skew.
   *
   * `POST /v1/login-purchase/receipt_credentials`
   */
  suspend fun createLoginPurchaseReceiptCredential(
    purchaseIdentifier: String,
    receiptCredentialRequest: ReceiptCredentialRequest,
    paymentProvider: LoginPurchasePaymentProvider
  ): RequestResult<CreateLoginReceiptCredentialResult, CreateLoginReceiptCredentialError>

  /**
   * Officially register an account that has no phone number, redeeming the [receiptCredentialPresentation] built from
   * the credential issued by [createLoginPurchaseReceiptCredential].
   *
   * This is the numberless counterpart to [registerAccount]: there is no verification session, no recovery password,
   * and no PNI key material, so [attributes] must have a null `pniRegistrationId` and a null
   * `discoverableByPhoneNumber`.
   *
   * `POST /v1/registration`
   *
   * @param password The password for basic auth. The username is generated by the implementation, since the service
   *   ignores it for a fresh numberless registration.
   */
  suspend fun registerAccountWithoutPhoneNumber(
    password: String,
    receiptCredentialPresentation: ReceiptCredentialPresentation,
    attributes: AccountAttributes,
    aciPreKeys: PreKeyCollection,
    fcmToken: String?,
    skipDeviceTransfer: Boolean
  ): RequestResult<RegisterAccountResponse, RegisterAccountWithoutPhoneNumberError>

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
   * Re-commits the backup-id derived from [aep] so that subsequent auth credentials the service issues are bound to it.
   *
   * Repeated calls are safe. Implementations must discard any cached auth credential, since anything cached was issued
   * against the previous backup-id.
   *
   * PUT /v1/archives/backupid
   */
  suspend fun reserveBackupId(aep: AccountEntropyPool): RequestResult<Unit, ReserveBackupIdError>

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
   * account (`PUT /v1/devices/link`), authenticated via basic auth with [password] and [aci].
   *
   * This only performs the network request and returns the assigned device id. The caller is responsible
   * for committing the account locally (via [StorageController.commitRegistrationData]) and performing the
   * post-registration housekeeping (via [onLinkedDeviceRegistered]) and any restores.
   *
   * @param pniPreKeys The PNI pre-keys, or null if the account has no PNI.
   */
  suspend fun registerAsLinkedDevice(
    aci: ACI,
    password: String,
    provisioningCode: String,
    deviceAttributes: DeviceAttributes,
    aciPreKeys: PreKeyCollection,
    pniPreKeys: PreKeyCollection?,
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

    /**
     * The auth credential the service issued failed zk verification against the key it was requested with. Either the key
     * doesn't belong to the account, or the backup-id the service is issuing against is stale (e.g. the account was
     * re-registered with a new AEP without re-committing the backup-id). See [NetworkController.reserveBackupId].
     */
    data object CredentialVerificationFailed : GetBackupInfoError()
  }

  sealed class ReserveBackupIdError : BadRequestError {
    /** The zkgroup credential request was rejected. */
    data object InvalidCredential : ReserveBackupIdError()

    /** The account credentials the request was made with were rejected. */
    data object Unauthorized : ReserveBackupIdError()

    data class RateLimited(val retryAfter: Duration?) : ReserveBackupIdError()
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
   * The ACI is resolved to its canonical string form by the implementation. Identity keys are
   * provided by the primary so this device shares the account's identity.
   */
  class LinkDeviceProvisioningMessage(
    val provisioningCode: String,
    val aci: String,
    val aciIdentityKeyPair: IdentityKeyPair,
    val phoneNumberData: PhoneNumberData?,
    val profileKey: ByteArray,
    val ephemeralBackupKey: ByteString?,
    val accountEntropyPool: String?,
    val mediaRootBackupKey: ByteString?,
    val readReceipts: Boolean?
  ) {
    /**
     * The phone-number-linked half of the provisioning data. Absent when the account has no phone number.
     *
     * This is deliberately all-or-nothing: the primary either sends the E164, PNI, and PNI identity key together
     * or we ignore the lot, since a partial set can't be used to register the PNI identity.
     */
    class PhoneNumberData(
      val e164: String,
      val pni: String,
      val pniIdentityKeyPair: IdentityKeyPair
    ) {
      companion object {
        private val TAG = Log.tag(PhoneNumberData::class)

        /**
         * Reads the phone-number-linked fields out of a provisioning message, or returns null if the primary didn't send
         * a complete set. A primary on an account with no phone number omits all of it.
         *
         * Note that [ProvisionMessage.pni] is deprecated in favor of [ProvisionMessage.pniBinary], so neither is
         * required on its own.
         */
        fun fromProvisionMessage(message: ProvisionMessage): PhoneNumberData? {
          val e164 = message.number
          val pni = message.pniBinary?.let { PNI.parseOrNull(it) } ?: PNI.parseOrNull(message.pni)
          val pniIdentityKeyPublic = message.pniIdentityKeyPublic
          val pniIdentityKeyPrivate = message.pniIdentityKeyPrivate

          if (e164 == null || pni == null || pniIdentityKeyPublic == null || pniIdentityKeyPrivate == null) {
            Log.i(TAG, "[fromProvisionMessage] No usable phone number data. hasNumber: ${e164 != null}, hasPni: ${pni != null}, hasPniIdentityKey: ${pniIdentityKeyPublic != null && pniIdentityKeyPrivate != null}. Ignoring all of it.")
            return null
          }

          return PhoneNumberData(
            e164 = e164,
            pni = pni.toString(),
            pniIdentityKeyPair = IdentityKeyPair(IdentityKey(pniIdentityKeyPublic.toByteArray()), ECPrivateKey(pniIdentityKeyPrivate.toByteArray()))
          )
        }
      }
    }
  }

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
