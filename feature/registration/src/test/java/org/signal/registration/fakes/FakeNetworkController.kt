/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.registration.LinkAndSyncWaitResult
import org.signal.registration.NetworkController
import org.signal.registration.NetworkController.AccountAttributes
import org.signal.registration.NetworkController.BackupMasterKeyError
import org.signal.registration.NetworkController.CheckSvrCredentialsError
import org.signal.registration.NetworkController.CheckSvrCredentialsResponse
import org.signal.registration.NetworkController.CreateSessionError
import org.signal.registration.NetworkController.DeviceAttributes
import org.signal.registration.NetworkController.GetBackupInfoError
import org.signal.registration.NetworkController.GetBackupInfoResponse
import org.signal.registration.NetworkController.GetSessionStatusError
import org.signal.registration.NetworkController.GetSvrCredentialsError
import org.signal.registration.NetworkController.LinkDeviceProvisioningEvent
import org.signal.registration.NetworkController.LinkDeviceResponse
import org.signal.registration.NetworkController.MasterKeyResponse
import org.signal.registration.NetworkController.PreKeyCollection
import org.signal.registration.NetworkController.ProvisioningEvent
import org.signal.registration.NetworkController.ProvisioningMessage
import org.signal.registration.NetworkController.RegisterAccountError
import org.signal.registration.NetworkController.RegisterAccountResponse
import org.signal.registration.NetworkController.RegisterAsLinkedDeviceError
import org.signal.registration.NetworkController.RequestVerificationCodeError
import org.signal.registration.NetworkController.RestoreAccountRecordError
import org.signal.registration.NetworkController.RestoreMasterKeyError
import org.signal.registration.NetworkController.RestoreMethod
import org.signal.registration.NetworkController.SessionMetadata
import org.signal.registration.NetworkController.SetAccountAttributesError
import org.signal.registration.NetworkController.SetProfileError
import org.signal.registration.NetworkController.SetRegistrationLockError
import org.signal.registration.NetworkController.SetRestoreMethodError
import org.signal.registration.NetworkController.SubmitVerificationCodeError
import org.signal.registration.NetworkController.SvrCredentials
import org.signal.registration.NetworkController.UpdateSessionError
import org.signal.registration.NetworkController.VerificationCodeTransport
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration

/**
 * An in-memory [NetworkController] whose responses can be customized per-test.
 *
 * Every method the registration flow exercises delegates to an overridable `on<Method>` handler. The defaults play
 * the part of a well-behaved server for a fresh registration, so tests only override the responses they care about:
 *
 * ```
 * networkController.onRegisterAccount = {
 *   RequestResult.NonSuccess(RegisterAccountError.RateLimited(retryAfter = 30.seconds))
 * }
 * ```
 *
 * Requests are recorded (see `last*` properties) no matter which handler serves them, so tests can assert on what
 * the flow actually sent. Methods that no flow under test should reach fail loudly.
 */
class FakeNetworkController(
  private val correctVerificationCode: String = DEFAULT_VERIFICATION_CODE
) : NetworkController {

  companion object {
    const val DEFAULT_VERIFICATION_CODE = "123456"
    const val SESSION_ID = "fake-session-id"
  }

  data class UpdateSessionRequest(val sessionId: String?, val pushChallengeToken: String?, val captchaToken: String?)
  data class RegisterAccountRequest(val e164: String, val sessionId: String?, val recoveryPassword: String?, val registrationLock: String?)
  data class SetPinRequest(val pin: String, val masterKey: MasterKey)
  data class RestoreMasterKeyRequest(val svrCredentials: SvrCredentials, val pin: String)
  data class SetRestoreMethodRequest(val token: String, val method: RestoreMethod)

  // -- Recorded requests, populated regardless of which handler serves them.

  var lastCreateSessionE164: String? = null
    private set
  var lastUpdateSessionRequest: UpdateSessionRequest? = null
    private set
  var lastRequestedCodeTransport: VerificationCodeTransport? = null
    private set
  var lastSubmittedVerificationCode: String? = null
    private set
  var lastRegisterAccountRequest: RegisterAccountRequest? = null
    private set
  var lastSetPinRequest: SetPinRequest? = null
    private set
  var lastRestoreMasterKeyRequest: RestoreMasterKeyRequest? = null
    private set
  var lastSetRestoreMethodRequest: SetRestoreMethodRequest? = null
    private set
  var accountAttributesSyncJobEnqueued = false
    private set

  /**
   * Whether the fake session has been verified. Set by the default [onSubmitVerificationCode] handler when the
   * correct code is submitted, and reflected in the sessions built by [session].
   */
  var sessionVerified = false

  /** Returned by [getFcmToken]. Null means the device does not support FCM. */
  var fcmToken: String? = null

  /** Returned by [awaitPushChallengeToken]. Null means no push challenge ever arrives. */
  var pushChallengeToken: String? = null

  // -- Response handlers. Override these in tests to change how the fake server responds.

  var onCreateSession: suspend (e164: String) -> RequestResult<SessionMetadata, CreateSessionError> = {
    RequestResult.Success(session())
  }

  var onGetSession: suspend (sessionId: String) -> RequestResult<SessionMetadata, GetSessionStatusError> = {
    RequestResult.Success(session())
  }

  var onUpdateSession: suspend (UpdateSessionRequest) -> RequestResult<SessionMetadata, UpdateSessionError> = {
    RequestResult.Success(session())
  }

  var onRequestVerificationCode: suspend (sessionId: String) -> RequestResult<SessionMetadata, RequestVerificationCodeError> = {
    RequestResult.Success(session())
  }

  var onSubmitVerificationCode: suspend (code: String) -> RequestResult<SessionMetadata, SubmitVerificationCodeError> = { code ->
    if (code == correctVerificationCode) {
      sessionVerified = true
    }
    RequestResult.Success(session())
  }

  var onRegisterAccount: suspend (RegisterAccountRequest) -> RequestResult<RegisterAccountResponse, RegisterAccountError> = { request ->
    check(sessionVerified || request.recoveryPassword != null) { "Attempted to register with a session before it was verified!" }
    RequestResult.Success(registerAccountResponse(request.e164))
  }

  var onSetPinAndMasterKeyOnSvr: suspend (SetPinRequest) -> RequestResult<SvrCredentials?, BackupMasterKeyError> = {
    RequestResult.Success(null)
  }

  var onRestoreMasterKeyFromSvr: suspend (RestoreMasterKeyRequest) -> RequestResult<MasterKeyResponse, RestoreMasterKeyError> = {
    notExpected()
  }

  var onGetSvrCredentials: suspend () -> RequestResult<SvrCredentials, GetSvrCredentialsError> = {
    notExpected()
  }

  var onCheckSvrCredentials: suspend (e164: String, credentials: List<SvrCredentials>) -> RequestResult<CheckSvrCredentialsResponse, CheckSvrCredentialsError> = { _, _ ->
    notExpected()
  }

  var onRestoreAccountRecord: suspend () -> RequestResult<Unit, RestoreAccountRecordError> = {
    RequestResult.Success(Unit)
  }

  var onGetRemoteBackupInfo: suspend (AccountEntropyPool) -> RequestResult<GetBackupInfoResponse, GetBackupInfoError> = {
    RequestResult.Success(GetBackupInfoResponse(cdn = 3, backupDir = "backup-dir", mediaDir = "media-dir", backupName = "backup", usedSpace = 1_000_000))
  }

  var onGetBackupFileLastModified: suspend (AccountEntropyPool) -> RequestResult<Long, GetBackupInfoError> = {
    RequestResult.Success(1_700_000_000_000)
  }

  var onVerifyBackupKey: suspend (AccountEntropyPool) -> RequestResult<Unit, NetworkController.VerifyBackupKeyError> = {
    RequestResult.Success(Unit)
  }

  /**
   * By default a QR code is shown but no old device ever scans it. Quick-restore tests should override this to also
   * emit [ProvisioningEvent.MessageReceived] with a [provisioningMessage], simulating the old device scanning the code.
   */
  var onStartProvisioning: () -> Flow<ProvisioningEvent> = {
    flowOf(ProvisioningEvent.QrCodeReady("https://signal.test/qr"))
  }

  var onSetRestoreMethod: suspend (SetRestoreMethodRequest) -> RequestResult<Unit, SetRestoreMethodError> = {
    RequestResult.Success(Unit)
  }

  // -- Response factories with happy-path defaults, for handlers that only want to tweak a field or two.

  fun session(
    verified: Boolean = sessionVerified,
    allowedToRequestCode: Boolean = true,
    requestedInformation: List<String> = emptyList(),
    nextSms: Long? = null,
    nextCall: Long? = null,
    nextVerificationAttempt: Long? = null
  ): SessionMetadata {
    return SessionMetadata(
      id = SESSION_ID,
      nextSms = nextSms,
      nextCall = nextCall,
      nextVerificationAttempt = nextVerificationAttempt,
      allowedToRequestCode = allowedToRequestCode,
      requestedInformation = requestedInformation,
      verified = verified
    )
  }

  /**
   * The data an old device sends after scanning the quick-restore QR code. [tier] describes the old device's backup
   * plan; null means it has no remote backup.
   */
  fun provisioningMessage(
    aep: AccountEntropyPool,
    e164: String,
    tier: ProvisioningMessage.Tier? = ProvisioningMessage.Tier.PAID,
    pin: String? = null,
    restoreMethodToken: String = "restore-method-token"
  ): ProvisioningMessage {
    return ProvisioningMessage(
      accountEntropyPool = aep.value,
      e164 = e164,
      pin = pin,
      aciIdentityKeyPair = IdentityKeyPair.generate(),
      pniIdentityKeyPair = IdentityKeyPair.generate(),
      platform = ProvisioningMessage.Platform.ANDROID,
      tier = tier,
      backupTimestampMs = 1_700_000_000_000,
      backupSizeBytes = 1024,
      restoreMethodToken = restoreMethodToken,
      backupVersion = 1
    )
  }

  fun registerAccountResponse(
    e164: String,
    storageCapable: Boolean = false,
    reregistration: Boolean = false
  ): RegisterAccountResponse {
    return RegisterAccountResponse(
      aci = UUID.randomUUID().toString(),
      pni = UUID.randomUUID().toString(),
      e164 = e164,
      usernameHash = null,
      usernameLinkHandle = null,
      storageCapable = storageCapable,
      entitlements = null,
      reregistration = reregistration
    )
  }

  // -- NetworkController implementation: record the request, then delegate to the handler.

  override suspend fun createSession(e164: String, fcmToken: String?, mcc: String?, mnc: String?): RequestResult<SessionMetadata, CreateSessionError> {
    lastCreateSessionE164 = e164
    return onCreateSession(e164)
  }

  override suspend fun getSession(sessionId: String): RequestResult<SessionMetadata, GetSessionStatusError> {
    return onGetSession(sessionId)
  }

  override suspend fun updateSession(sessionId: String?, pushChallengeToken: String?, captchaToken: String?): RequestResult<SessionMetadata, UpdateSessionError> {
    val request = UpdateSessionRequest(sessionId, pushChallengeToken, captchaToken)
    lastUpdateSessionRequest = request
    return onUpdateSession(request)
  }

  override suspend fun requestVerificationCode(
    sessionId: String,
    locale: Locale?,
    androidSmsRetrieverSupported: Boolean,
    transport: VerificationCodeTransport
  ): RequestResult<SessionMetadata, RequestVerificationCodeError> {
    lastRequestedCodeTransport = transport
    return onRequestVerificationCode(sessionId)
  }

  override suspend fun submitVerificationCode(sessionId: String, verificationCode: String): RequestResult<SessionMetadata, SubmitVerificationCodeError> {
    lastSubmittedVerificationCode = verificationCode
    return onSubmitVerificationCode(verificationCode)
  }

  override suspend fun registerAccount(
    e164: String,
    password: String,
    sessionId: String?,
    recoveryPassword: String?,
    attributes: AccountAttributes,
    aciPreKeys: PreKeyCollection,
    pniPreKeys: PreKeyCollection,
    fcmToken: String?,
    skipDeviceTransfer: Boolean
  ): RequestResult<RegisterAccountResponse, RegisterAccountError> {
    val request = RegisterAccountRequest(e164, sessionId, recoveryPassword, attributes.registrationLock)
    lastRegisterAccountRequest = request
    return onRegisterAccount(request)
  }

  override suspend fun getFcmToken(): String? = fcmToken

  override suspend fun awaitPushChallengeToken(): String? = pushChallengeToken

  override fun getCaptchaUrl(): String = "https://example.com/captcha"

  override suspend fun restoreMasterKeyFromSvr(svrCredentials: SvrCredentials, pin: String): RequestResult<MasterKeyResponse, RestoreMasterKeyError> {
    val request = RestoreMasterKeyRequest(svrCredentials, pin)
    lastRestoreMasterKeyRequest = request
    return onRestoreMasterKeyFromSvr(request)
  }

  override suspend fun setPinAndMasterKeyOnSvr(pin: String, masterKey: MasterKey): RequestResult<SvrCredentials?, BackupMasterKeyError> {
    val request = SetPinRequest(pin, masterKey)
    lastSetPinRequest = request
    return onSetPinAndMasterKeyOnSvr(request)
  }

  override suspend fun enqueueSvrGuessResetJobIfPossible(): Boolean = true

  override suspend fun enableRegistrationLock(): RequestResult<Unit, SetRegistrationLockError> = notExpected()

  override suspend fun disableRegistrationLock(): RequestResult<Unit, SetRegistrationLockError> = notExpected()

  override suspend fun getSvrCredentials(): RequestResult<SvrCredentials, GetSvrCredentialsError> {
    return onGetSvrCredentials()
  }

  override suspend fun checkSvrCredentials(e164: String, credentials: List<SvrCredentials>): RequestResult<CheckSvrCredentialsResponse, CheckSvrCredentialsError> {
    return onCheckSvrCredentials(e164, credentials)
  }

  override suspend fun setAccountAttributes(attributes: AccountAttributes): RequestResult<Unit, SetAccountAttributesError> = notExpected()

  override suspend fun enqueueAccountAttributesSyncJob() {
    accountAttributesSyncJobEnqueued = true
  }

  override suspend fun getRemoteBackupInfo(aep: AccountEntropyPool): RequestResult<GetBackupInfoResponse, GetBackupInfoError> {
    return onGetRemoteBackupInfo(aep)
  }

  override suspend fun getBackupFileLastModified(aep: AccountEntropyPool, backupInfo: GetBackupInfoResponse): RequestResult<Long, GetBackupInfoError> {
    return onGetBackupFileLastModified(aep)
  }

  override suspend fun verifyBackupKeyAssociatedWithAccount(aep: AccountEntropyPool): RequestResult<Unit, NetworkController.VerifyBackupKeyError> {
    return onVerifyBackupKey(aep)
  }

  override fun startProvisioning(): Flow<ProvisioningEvent> {
    return onStartProvisioning()
  }

  override fun startLinkDeviceProvisioning(allowLinkAndSync: Boolean): Flow<LinkDeviceProvisioningEvent> = notExpected()

  override suspend fun registerAsLinkedDevice(
    e164: String,
    password: String,
    provisioningCode: String,
    deviceAttributes: DeviceAttributes,
    aciPreKeys: PreKeyCollection,
    pniPreKeys: PreKeyCollection,
    fcmToken: String?
  ): RequestResult<LinkDeviceResponse, RegisterAsLinkedDeviceError> = notExpected()

  override suspend fun onLinkedDeviceRegistered() = notExpected()

  override suspend fun awaitLinkAndSyncArchive(): LinkAndSyncWaitResult = notExpected()

  override suspend fun restoreLinkedDeviceFromStorageService() = notExpected()

  override fun startNewDeviceTransferServer(context: android.content.Context, aep: AccountEntropyPool) = notExpected()

  override suspend fun setRestoreMethod(token: String, method: RestoreMethod): RequestResult<Unit, SetRestoreMethodError> {
    val request = SetRestoreMethodRequest(token, method)
    lastSetRestoreMethodRequest = request
    return onSetRestoreMethod(request)
  }

  override suspend fun restoreAccountRecord(timeout: Duration): RequestResult<Unit, RestoreAccountRecordError> {
    return onRestoreAccountRecord()
  }

  override suspend fun setProfile(givenName: String, familyName: String, avatar: ByteArray?, discoverableByPhoneNumber: Boolean): RequestResult<Unit, SetProfileError> {
    return RequestResult.Success(Unit)
  }

  private fun notExpected(): Nothing {
    throw NotImplementedError("This method is not expected to be called in the flow under test.")
  }
}
