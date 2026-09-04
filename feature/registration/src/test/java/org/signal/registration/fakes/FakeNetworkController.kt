/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.models.ServiceId.ACI
import org.signal.core.util.Util
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.usernames.Username
import org.signal.libsignal.zkgroup.ServerSecretParams
import org.signal.libsignal.zkgroup.VerificationFailedException
import org.signal.libsignal.zkgroup.receipts.ClientZkReceiptOperations
import org.signal.libsignal.zkgroup.receipts.ReceiptCredential
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialRequest
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialRequestContext
import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialResponse
import org.signal.libsignal.zkgroup.receipts.ReceiptSerial
import org.signal.libsignal.zkgroup.receipts.ServerZkReceiptOperations
import org.signal.network.api.RegistrationApiV2.AccountAttributes
import org.signal.network.api.RegistrationApiV2.CheckSvrCredentialsError
import org.signal.network.api.RegistrationApiV2.CheckSvrCredentialsResponse
import org.signal.network.api.RegistrationApiV2.CreateLoginReceiptCredentialError
import org.signal.network.api.RegistrationApiV2.CreateLoginReceiptCredentialResult
import org.signal.network.api.RegistrationApiV2.CreateSessionError
import org.signal.network.api.RegistrationApiV2.DeviceAttributes
import org.signal.network.api.RegistrationApiV2.GetLoginConfigurationError
import org.signal.network.api.RegistrationApiV2.GetSessionStatusError
import org.signal.network.api.RegistrationApiV2.LinkDeviceResponse
import org.signal.network.api.RegistrationApiV2.LoginConfiguration
import org.signal.network.api.RegistrationApiV2.LoginPurchasePaymentProvider
import org.signal.network.api.RegistrationApiV2.PreKeyCollection
import org.signal.network.api.RegistrationApiV2.RegisterAccountError
import org.signal.network.api.RegistrationApiV2.RegisterAccountResponse
import org.signal.network.api.RegistrationApiV2.RegisterAsLinkedDeviceError
import org.signal.network.api.RegistrationApiV2.RequestVerificationCodeError
import org.signal.network.api.RegistrationApiV2.RestoreMethod
import org.signal.network.api.RegistrationApiV2.SessionMetadata
import org.signal.network.api.RegistrationApiV2.SetRestoreMethodError
import org.signal.network.api.RegistrationApiV2.SubmitVerificationCodeError
import org.signal.network.api.RegistrationApiV2.SvrCredentials
import org.signal.network.api.RegistrationApiV2.UpdateSessionError
import org.signal.network.api.RegistrationApiV2.VerificationCodeTransport
import org.signal.network.service.UsernameService.ConfirmUsernameError
import org.signal.network.service.UsernameService.ConfirmedUsername
import org.signal.network.service.UsernameService.ReserveUsernameError
import org.signal.registration.LinkAndSyncWaitResult
import org.signal.registration.NetworkController
import org.signal.registration.NetworkController.BackupMasterKeyError
import org.signal.registration.NetworkController.GetBackupInfoError
import org.signal.registration.NetworkController.GetBackupInfoResponse
import org.signal.registration.NetworkController.GetSvrCredentialsError
import org.signal.registration.NetworkController.LinkDeviceProvisioningEvent
import org.signal.registration.NetworkController.MasterKeyResponse
import org.signal.registration.NetworkController.ProvisioningEvent
import org.signal.registration.NetworkController.ProvisioningMessage
import org.signal.registration.NetworkController.ReserveBackupIdError
import org.signal.registration.NetworkController.RestoreAccountRecordError
import org.signal.registration.NetworkController.RestoreMasterKeyError
import org.signal.registration.NetworkController.SetAccountAttributesError
import org.signal.registration.NetworkController.SetProfileError
import org.signal.registration.NetworkController.SetRegistrationLockError
import org.signal.registration.ReceiptCredentialResult
import org.whispersystems.signalservice.api.push.UsernameLinkComponents
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

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

    /** The receipt level a Signal Login purchase is worth. */
    const val LOGIN_RECEIPT_LEVEL = 300L

    /** How long after the purchase the service sets a Signal Login receipt to expire. */
    val LOGIN_RECEIPT_LIFESPAN = (5 * 366).days
    const val LOGIN_PLAY_PRODUCT_ID = "signup"
  }

  data class UpdateSessionRequest(val sessionId: String?, val pushChallengeToken: String?, val captchaToken: String?)
  data class RegisterAccountRequest(val e164: String?, val sessionId: String?, val recoveryPassword: String?, val registrationLock: String?, val aci: ACI? = null, val pniPreKeys: PreKeyCollection? = null, val pniRegistrationId: Int? = null, val receiptCredentialPresentation: ReceiptCredentialPresentation? = null, val totp: Int? = null)
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
  var lastReservedNickname: String? = null
    private set
  var lastReservedDiscriminator: String? = null
    private set
  var lastConfirmedUsername: Username? = null
    private set
  var lastLoginPurchaseIdentifier: String? = null
    private set
  val loginReceiptCredentialRequests = mutableListOf<ReceiptCredentialRequest>()

  /** How many times the flow re-committed the backup-id. */
  var reserveBackupIdCount = 0
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

  /** Stands in for the service's zkgroup receipt params. */
  val receiptServerSecretParams: ServerSecretParams by lazy { ServerSecretParams.generate() }

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
    check(sessionVerified || request.recoveryPassword != null || request.receiptCredentialPresentation != null) {
      "Attempted to register with a session before it was verified!"
    }
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

  var onReserveBackupId: suspend (AccountEntropyPool) -> RequestResult<Unit, ReserveBackupIdError> = {
    RequestResult.Success(Unit)
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

  var onReserveUsername: suspend (nickname: String, discriminator: String?) -> RequestResult<Username, ReserveUsernameError> = { nickname, discriminator ->
    RequestResult.Success(Username("$nickname.${discriminator ?: "42"}"))
  }

  var onConfirmUsername: suspend (Username) -> RequestResult<ConfirmedUsername, ConfirmUsernameError> = { username ->
    RequestResult.Success(ConfirmedUsername(username, UsernameLinkComponents(ByteArray(32), UUID.randomUUID())))
  }

  var onGetLoginConfiguration: suspend () -> RequestResult<LoginConfiguration, GetLoginConfigurationError> = {
    RequestResult.Success(LoginConfiguration(level = LOGIN_RECEIPT_LEVEL, playProductId = LOGIN_PLAY_PRODUCT_ID))
  }

  var onCreateLoginPurchaseReceiptCredential: (ReceiptCredentialRequest) -> RequestResult<CreateLoginReceiptCredentialResult, CreateLoginReceiptCredentialError> = { request ->
    RequestResult.Success(issueLoginReceiptCredential(request))
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
    e164: String?,
    storageCapable: Boolean = false,
    reregistration: Boolean = false
  ): RegisterAccountResponse {
    return RegisterAccountResponse(
      aci = UUID.randomUUID().toString(),
      // An account with no phone number has no PNI, exactly as the service reports it.
      pni = if (e164 != null) UUID.randomUUID().toString() else null,
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

  override suspend fun updateSession(sessionId: String, pushChallengeToken: String?, captchaToken: String?): RequestResult<SessionMetadata, UpdateSessionError> {
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
    e164: String?,
    password: String,
    sessionId: String?,
    recoveryPassword: String?,
    receiptCredentialPresentation: ReceiptCredentialPresentation?,
    attributes: AccountAttributes,
    aciPreKeys: PreKeyCollection,
    pniPreKeys: PreKeyCollection?,
    fcmToken: String?,
    skipDeviceTransfer: Boolean,
    aci: ACI?,
    totp: Int?
  ): RequestResult<RegisterAccountResponse, RegisterAccountError> {
    val request = RegisterAccountRequest(e164, sessionId, recoveryPassword, attributes.registrationLock, aci, pniPreKeys, attributes.pniRegistrationId, receiptCredentialPresentation, totp)
    lastRegisterAccountRequest = request
    return onRegisterAccount(request)
  }

  override suspend fun getLoginConfiguration(): RequestResult<LoginConfiguration, GetLoginConfigurationError> = onGetLoginConfiguration()

  override suspend fun createLoginPurchaseReceiptCredential(
    purchaseIdentifier: String,
    receiptCredentialRequest: ReceiptCredentialRequest,
    paymentProvider: LoginPurchasePaymentProvider
  ): RequestResult<CreateLoginReceiptCredentialResult, CreateLoginReceiptCredentialError> {
    lastLoginPurchaseIdentifier = purchaseIdentifier
    loginReceiptCredentialRequests += receiptCredentialRequest
    return onCreateLoginPurchaseReceiptCredential(receiptCredentialRequest)
  }

  override fun createReceiptCredentialRequestContext(): ReceiptCredentialRequestContext {
    val serial = ReceiptSerial(Util.getSecretBytes(ReceiptSerial.SIZE))
    return ClientZkReceiptOperations(receiptServerSecretParams.publicParams).createReceiptCredentialRequestContext(SecureRandom(), serial)
  }

  override fun receiveReceiptCredential(requestContext: ReceiptCredentialRequestContext, response: ReceiptCredentialResponse): ReceiptCredentialResult<ReceiptCredential> {
    return try {
      ReceiptCredentialResult.Success(ClientZkReceiptOperations(receiptServerSecretParams.publicParams).receiveReceiptCredential(requestContext, response))
    } catch (e: VerificationFailedException) {
      ReceiptCredentialResult.VerificationFailed
    }
  }

  override fun createReceiptCredentialPresentation(receiptCredential: ReceiptCredential): ReceiptCredentialResult<ReceiptCredentialPresentation> {
    return try {
      ReceiptCredentialResult.Success(ClientZkReceiptOperations(receiptServerSecretParams.publicParams).createReceiptCredentialPresentation(receiptCredential))
    } catch (e: VerificationFailedException) {
      ReceiptCredentialResult.VerificationFailed
    }
  }

  /**
   * Issues a receipt credential the way the service would, with a caller-chosen level and expiration.
   */
  fun issueLoginReceiptCredential(
    request: ReceiptCredentialRequest,
    level: Long = LOGIN_RECEIPT_LEVEL,
    expirationSeconds: Long = defaultReceiptExpirationSeconds()
  ): CreateLoginReceiptCredentialResult {
    val response = ServerZkReceiptOperations(receiptServerSecretParams).issueReceiptCredential(request, expirationSeconds, level)
    return CreateLoginReceiptCredentialResult.Issued(response)
  }

  fun defaultReceiptExpirationSeconds(purchaseTimeMs: Long = System.currentTimeMillis()): Long {
    return Instant.ofEpochMilli(purchaseTimeMs)
      .truncatedTo(ChronoUnit.DAYS)
      .plusSeconds(LOGIN_RECEIPT_LIFESPAN.inWholeSeconds)
      .epochSecond
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

  override suspend fun reserveBackupId(aep: AccountEntropyPool): RequestResult<Unit, ReserveBackupIdError> {
    reserveBackupIdCount++
    return onReserveBackupId(aep)
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
    aci: ACI,
    password: String,
    provisioningCode: String,
    deviceAttributes: DeviceAttributes,
    aciPreKeys: PreKeyCollection,
    pniPreKeys: PreKeyCollection?,
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

  override suspend fun reserveUsername(nickname: String, discriminator: String?): RequestResult<Username, ReserveUsernameError> {
    lastReservedNickname = nickname
    lastReservedDiscriminator = discriminator
    return onReserveUsername(nickname, discriminator)
  }

  override suspend fun confirmUsername(username: Username): RequestResult<ConfirmedUsername, ConfirmUsernameError> {
    lastConfirmedUsername = username
    return onConfirmUsername(username)
  }

  private fun notExpected(): Nothing {
    throw NotImplementedError("This method is not expected to be called in the flow under test.")
  }
}
