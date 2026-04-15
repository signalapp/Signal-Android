/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.signal.core.models.MasterKey
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.registration.NetworkController
import org.signal.registration.NetworkController.AccountAttributes
import org.signal.registration.NetworkController.BackupMasterKeyError
import org.signal.registration.NetworkController.CheckSvrCredentialsError
import org.signal.registration.NetworkController.CheckSvrCredentialsResponse
import org.signal.registration.NetworkController.CreateSessionError
import org.signal.registration.NetworkController.GetSessionStatusError
import org.signal.registration.NetworkController.GetSvrCredentialsError
import org.signal.registration.NetworkController.PreKeyCollection
import org.signal.registration.NetworkController.ProvisioningEvent
import org.signal.registration.NetworkController.ProvisioningMessage
import org.signal.registration.NetworkController.RegisterAccountError
import org.signal.registration.NetworkController.RegisterAccountResponse
import org.signal.registration.NetworkController.RegistrationLockResponse
import org.signal.registration.NetworkController.RequestVerificationCodeError
import org.signal.registration.NetworkController.RestoreMasterKeyError
import org.signal.registration.NetworkController.SessionMetadata
import org.signal.registration.NetworkController.SetAccountAttributesError
import org.signal.registration.NetworkController.SetRegistrationLockError
import org.signal.registration.NetworkController.SubmitVerificationCodeError
import org.signal.registration.NetworkController.SvrCredentials
import org.signal.registration.NetworkController.ThirdPartyServiceErrorResponse
import org.signal.registration.NetworkController.UpdateSessionError
import org.signal.registration.NetworkController.VerificationCodeTransport
import org.signal.registration.proto.RegistrationProvisionMessage
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.gcm.FcmUtil
import org.thoughtcrime.securesms.jobs.ResetSvrGuessCountJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.pin.SvrRepository
import org.thoughtcrime.securesms.pin.SvrWrongPinException
import org.thoughtcrime.securesms.registration.fcm.PushChallengeRequest
import org.thoughtcrime.securesms.registration.viewmodel.SvrAuthCredentialSet
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.SvrNoDataException
import org.whispersystems.signalservice.api.archive.ArchiveServiceAccess
import org.whispersystems.signalservice.api.provisioning.ProvisioningSocket
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.BackupResponse
import org.whispersystems.signalservice.internal.crypto.SecondaryProvisioningCipher
import org.whispersystems.signalservice.internal.push.AuthCredentials
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import java.io.IOException
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.whispersystems.signalservice.api.account.AccountAttributes as ServiceAccountAttributes
import org.whispersystems.signalservice.api.account.PreKeyCollection as ServicePreKeyCollection

/**
 * Implementation of [NetworkController] that bridges to the app's existing network infrastructure.
 */
class AppRegistrationNetworkController(
  private val context: Context,
  private val pushServiceSocket: PushServiceSocket
) : NetworkController {

  companion object {
    private val TAG = Log.tag(AppRegistrationNetworkController::class)
    private val PUSH_REQUEST_TIMEOUT = 5.seconds.inWholeMilliseconds
  }

  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun createSession(
    e164: String,
    fcmToken: String?,
    mcc: String?,
    mnc: String?
  ): RequestResult<SessionMetadata, CreateSessionError> = withContext(Dispatchers.IO) {
    try {
      pushServiceSocket.createVerificationSessionV2(e164, fcmToken, mcc, mnc).use { response ->
        when (response.code) {
          200 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RequestResult.Success(session)
          }
          422 -> {
            RequestResult.NonSuccess(CreateSessionError.InvalidRequest(response.body.string()))
          }
          429 -> {
            RequestResult.NonSuccess(CreateSessionError.RateLimited(response.retryAfter()))
          }
          else -> {
            RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}, body: ${response.body.string()}"))
          }
        }
      }
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      RequestResult.ApplicationError(e)
    }
  }

  override suspend fun getSession(sessionId: String): RequestResult<SessionMetadata, GetSessionStatusError> = withContext(Dispatchers.IO) {
    try {
      pushServiceSocket.getSessionStatusV2(sessionId).use { response ->
        when (response.code) {
          200 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RequestResult.Success(session)
          }
          400 -> {
            RequestResult.NonSuccess(GetSessionStatusError.InvalidRequest(response.body.string()))
          }
          404 -> {
            RequestResult.NonSuccess(GetSessionStatusError.SessionNotFound(response.body.string()))
          }
          422 -> {
            RequestResult.NonSuccess(GetSessionStatusError.InvalidSessionId(response.body.string()))
          }
          else -> {
            RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}, body: ${response.body.string()}"))
          }
        }
      }
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      RequestResult.ApplicationError(e)
    }
  }

  override suspend fun updateSession(
    sessionId: String?,
    pushChallengeToken: String?,
    captchaToken: String?
  ): RequestResult<SessionMetadata, UpdateSessionError> = withContext(Dispatchers.IO) {
    try {
      pushServiceSocket.patchVerificationSessionV2(
        sessionId,
        null,
        null,
        null,
        captchaToken,
        pushChallengeToken
      ).use { response ->
        when (response.code) {
          200 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RequestResult.Success(session)
          }
          400 -> {
            RequestResult.NonSuccess(UpdateSessionError.InvalidRequest(response.body.string()))
          }
          409 -> {
            RequestResult.NonSuccess(UpdateSessionError.RejectedUpdate(response.body.string()))
          }
          429 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RequestResult.NonSuccess(UpdateSessionError.RateLimited(response.retryAfter(), session))
          }
          else -> {
            RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}, body: ${response.body.string()}"))
          }
        }
      }
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      RequestResult.ApplicationError(e)
    }
  }

  override suspend fun requestVerificationCode(
    sessionId: String,
    locale: Locale?,
    androidSmsRetrieverSupported: Boolean,
    transport: VerificationCodeTransport
  ): RequestResult<SessionMetadata, RequestVerificationCodeError> = withContext(Dispatchers.IO) {
    try {
      val socketTransport = when (transport) {
        VerificationCodeTransport.SMS -> PushServiceSocket.VerificationCodeTransport.SMS
        VerificationCodeTransport.VOICE -> PushServiceSocket.VerificationCodeTransport.VOICE
      }

      pushServiceSocket.requestVerificationCodeV2(
        sessionId,
        locale,
        androidSmsRetrieverSupported,
        socketTransport
      ).use { response ->
        when (response.code) {
          200 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RequestResult.Success(session)
          }
          400 -> {
            RequestResult.NonSuccess(RequestVerificationCodeError.InvalidSessionId(response.body.string()))
          }
          404 -> {
            RequestResult.NonSuccess(RequestVerificationCodeError.SessionNotFound(response.body.string()))
          }
          409 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RequestResult.NonSuccess(RequestVerificationCodeError.MissingRequestInformationOrAlreadyVerified(session))
          }
          418 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RequestResult.NonSuccess(RequestVerificationCodeError.CouldNotFulfillWithRequestedTransport(session))
          }
          429 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RequestResult.NonSuccess(RequestVerificationCodeError.RateLimited(response.retryAfter(), session))
          }
          440 -> {
            val errorBody = json.decodeFromString<ThirdPartyServiceErrorResponse>(response.body.string())
            RequestResult.NonSuccess(RequestVerificationCodeError.ThirdPartyServiceError(errorBody))
          }
          else -> {
            RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}, body: ${response.body.string()}"))
          }
        }
      }
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      RequestResult.ApplicationError(e)
    }
  }

  override suspend fun submitVerificationCode(
    sessionId: String,
    verificationCode: String
  ): RequestResult<SessionMetadata, SubmitVerificationCodeError> = withContext(Dispatchers.IO) {
    try {
      pushServiceSocket.submitVerificationCodeV2(sessionId, verificationCode).use { response ->
        when (response.code) {
          200 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RequestResult.Success(session)
          }
          400 -> {
            RequestResult.NonSuccess(SubmitVerificationCodeError.InvalidSessionIdOrVerificationCode(response.body.string()))
          }
          404 -> {
            RequestResult.NonSuccess(SubmitVerificationCodeError.SessionNotFound(response.body.string()))
          }
          409 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RequestResult.NonSuccess(SubmitVerificationCodeError.SessionAlreadyVerifiedOrNoCodeRequested(session))
          }
          429 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RequestResult.NonSuccess(SubmitVerificationCodeError.RateLimited(response.retryAfter(), session))
          }
          else -> {
            RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}, body: ${response.body.string()}"))
          }
        }
      }
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      RequestResult.ApplicationError(e)
    }
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
  ): RequestResult<RegisterAccountResponse, RegisterAccountError> = withContext(Dispatchers.IO) {
    check(sessionId != null || recoveryPassword != null) { "Either sessionId or recoveryPassword must be provided" }
    check(sessionId == null || recoveryPassword == null) { "Either sessionId or recoveryPassword must be provided, but not both" }

    try {
      pushServiceSocket.submitRegistrationRequestV2(
        e164,
        password,
        sessionId,
        recoveryPassword,
        attributes.toServiceAccountAttributes(),
        aciPreKeys.toServicePreKeyCollection(),
        pniPreKeys.toServicePreKeyCollection(),
        fcmToken,
        skipDeviceTransfer
      ).use { response ->
        when (response.code) {
          200 -> {
            val result = json.decodeFromString<RegisterAccountResponse>(response.body.string())
            RequestResult.Success(result)
          }
          401 -> {
            RequestResult.NonSuccess(RegisterAccountError.SessionNotFoundOrNotVerified(response.body.string()))
          }
          403 -> {
            RequestResult.NonSuccess(RegisterAccountError.RegistrationRecoveryPasswordIncorrect(response.body.string()))
          }
          409 -> {
            RequestResult.NonSuccess(RegisterAccountError.DeviceTransferPossible)
          }
          422 -> {
            RequestResult.NonSuccess(RegisterAccountError.InvalidRequest(response.body.string()))
          }
          423 -> {
            val lockResponse = json.decodeFromString<RegistrationLockResponse>(response.body.string())
            RequestResult.NonSuccess(RegisterAccountError.RegistrationLock(lockResponse))
          }
          429 -> {
            RequestResult.NonSuccess(RegisterAccountError.RateLimited(response.retryAfter()))
          }
          else -> {
            RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}, body: ${response.body.string()}"))
          }
        }
      }
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      RequestResult.ApplicationError(e)
    }
  }

  override suspend fun getFcmToken(): String? {
    return try {
      FcmUtil.getToken(context).orElse(null)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to get FCM token", e)
      null
    }
  }

  override suspend fun awaitPushChallengeToken(): String? = withContext(Dispatchers.IO) {
    try {
      val latch = java.util.concurrent.CountDownLatch(1)
      val challenge = java.util.concurrent.atomic.AtomicReference<String>()

      val subscriber = object {
        @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.POSTING)
        fun onChallengeEvent(event: PushChallengeRequest.PushChallengeEvent) {
          challenge.set(event.challenge)
          latch.countDown()
        }
      }

      val eventBus = org.greenrobot.eventbus.EventBus.getDefault()
      eventBus.register(subscriber)
      try {
        latch.await(PUSH_REQUEST_TIMEOUT, java.util.concurrent.TimeUnit.MILLISECONDS)
        challenge.get()
      } finally {
        eventBus.unregister(subscriber)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to await push challenge token", e)
      null
    }
  }

  override fun getCaptchaUrl(): String {
    return BuildConfig.SIGNAL_CAPTCHA_URL
  }

  override suspend fun restoreMasterKeyFromSvr(
    svrCredentials: SvrCredentials,
    pin: String
  ): RequestResult<NetworkController.MasterKeyResponse, RestoreMasterKeyError> = withContext(Dispatchers.IO) {
    try {
      val authCredentials = AuthCredentials.create(svrCredentials.username, svrCredentials.password)
      val credentialSet = SvrAuthCredentialSet(svr2Credentials = authCredentials, svr3Credentials = null)

      val masterKey = SvrRepository.restoreMasterKeyPreRegistration(credentialSet, pin)
      RequestResult.Success(NetworkController.MasterKeyResponse(masterKey))
    } catch (e: SvrWrongPinException) {
      RequestResult.NonSuccess(RestoreMasterKeyError.WrongPin(e.triesRemaining))
    } catch (e: SvrNoDataException) {
      RequestResult.NonSuccess(RestoreMasterKeyError.NoDataFound)
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      RequestResult.ApplicationError(e)
    }
  }

  override suspend fun setPinAndMasterKeyOnSvr(
    pin: String,
    masterKey: MasterKey
  ): RequestResult<SvrCredentials?, BackupMasterKeyError> = withContext(Dispatchers.IO) {
    try {
      val svr2 = AppDependencies.signalServiceAccountManager.getSecureValueRecoveryV2(BuildConfig.SVR2_MRENCLAVE)
      val session = svr2.setPin(pin, masterKey)
      when (val response = session.execute()) {
        is BackupResponse.Success -> {
          RequestResult.Success(SvrCredentials(response.authorization.username(), response.authorization.password()))
        }
        is BackupResponse.EnclaveNotFound -> {
          RequestResult.NonSuccess(BackupMasterKeyError.EnclaveNotFound)
        }
        is BackupResponse.ExposeFailure -> {
          RequestResult.Success(null)
        }
        is BackupResponse.NetworkError -> {
          RequestResult.RetryableNetworkError(response.exception)
        }
        is BackupResponse.ApplicationError -> {
          RequestResult.ApplicationError(response.exception)
        }
        is BackupResponse.ServerRejected -> {
          RequestResult.RetryableNetworkError(IOException("Server rejected backup request"))
        }
        is BackupResponse.RateLimited -> {
          RequestResult.RetryableNetworkError(IOException("Rate limited"))
        }
      }
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      RequestResult.ApplicationError(e)
    }
  }

  override suspend fun enqueueSvrGuessResetJob() {
    AppDependencies.jobManager.add(ResetSvrGuessCountJob())
  }

  override suspend fun enableRegistrationLock(): RequestResult<Unit, SetRegistrationLockError> = withContext(Dispatchers.IO) {
    val masterKey = SignalStore.svr.masterKey
    if (masterKey == null) {
      return@withContext RequestResult.NonSuccess(SetRegistrationLockError.NoPinSet)
    }

    when (val result = SignalNetwork.account.enableRegistrationLock(masterKey.deriveRegistrationLock())) {
      is NetworkResult.Success -> RequestResult.Success(Unit)
      is NetworkResult.StatusCodeError -> {
        when (result.code) {
          401 -> RequestResult.NonSuccess(SetRegistrationLockError.Unauthorized)
          422 -> RequestResult.NonSuccess(SetRegistrationLockError.InvalidRequest(result.toString()))
          else -> RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${result.code}"))
        }
      }
      is NetworkResult.NetworkError -> RequestResult.RetryableNetworkError(result.exception)
      is NetworkResult.ApplicationError -> RequestResult.ApplicationError(result.throwable)
    }
  }

  override suspend fun disableRegistrationLock(): RequestResult<Unit, SetRegistrationLockError> = withContext(Dispatchers.IO) {
    when (val result = SignalNetwork.account.disableRegistrationLock()) {
      is NetworkResult.Success -> RequestResult.Success(Unit)
      is NetworkResult.StatusCodeError -> {
        when (result.code) {
          401 -> RequestResult.NonSuccess(SetRegistrationLockError.Unauthorized)
          else -> RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${result.code}"))
        }
      }
      is NetworkResult.NetworkError -> RequestResult.RetryableNetworkError(result.exception)
      is NetworkResult.ApplicationError -> RequestResult.ApplicationError(result.throwable)
    }
  }

  override suspend fun getSvrCredentials(): RequestResult<SvrCredentials, GetSvrCredentialsError> = withContext(Dispatchers.IO) {
    try {
      val svr2 = AppDependencies.signalServiceAccountManager.getSecureValueRecoveryV2(BuildConfig.SVR2_MRENCLAVE)
      val auth = svr2.authorization()
      RequestResult.Success(SvrCredentials(auth.username(), auth.password()))
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      RequestResult.ApplicationError(e)
    }
  }

  override suspend fun checkSvrCredentials(
    e164: String,
    credentials: List<SvrCredentials>
  ): RequestResult<CheckSvrCredentialsResponse, CheckSvrCredentialsError> = withContext(Dispatchers.IO) {
    try {
      val tokens = credentials.map { "${it.username}:${it.password}" }
      pushServiceSocket.checkSvr2AuthCredentialsV2(e164, tokens).use { response ->
        when (response.code) {
          200 -> {
            val result = json.decodeFromString<CheckSvrCredentialsResponse>(response.body.string())
            RequestResult.Success(result)
          }
          400, 422 -> {
            RequestResult.NonSuccess(CheckSvrCredentialsError.InvalidRequest(response.body.string()))
          }
          401 -> {
            RequestResult.NonSuccess(CheckSvrCredentialsError.Unauthorized)
          }
          else -> {
            RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}, body: ${response.body.string()}"))
          }
        }
      }
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      RequestResult.ApplicationError(e)
    }
  }

  override suspend fun setAccountAttributes(
    attributes: AccountAttributes
  ): RequestResult<Unit, SetAccountAttributesError> = withContext(Dispatchers.IO) {
    when (val result = SignalNetwork.account.setAccountAttributes(attributes.toServiceAccountAttributes())) {
      is NetworkResult.Success -> RequestResult.Success(Unit)
      is NetworkResult.StatusCodeError -> {
        when (result.code) {
          401 -> RequestResult.NonSuccess(SetAccountAttributesError.Unauthorized)
          422 -> RequestResult.NonSuccess(SetAccountAttributesError.InvalidRequest(result.toString()))
          else -> RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${result.code}"))
        }
      }
      is NetworkResult.NetworkError -> RequestResult.RetryableNetworkError(result.exception)
      is NetworkResult.ApplicationError -> RequestResult.ApplicationError(result.throwable)
    }
  }

  override suspend fun getRemoteBackupInfo(): RequestResult<NetworkController.GetBackupInfoResponse, NetworkController.GetBackupInfoError> = withContext(Dispatchers.IO) {
    val aci = SignalStore.account.aci ?: return@withContext RequestResult.ApplicationError(IllegalStateException("ACI not available"))

    val currentTime = System.currentTimeMillis()
    val messageCredential = SignalStore.backup.messageCredentials.byDay.getForCurrentTime(currentTime.milliseconds)

    val access = if (messageCredential != null) {
      ArchiveServiceAccess(messageCredential, SignalStore.backup.messageBackupKey)
    } else {
      when (val credResult = SignalNetwork.archive.getServiceCredentials(currentTime)) {
        is NetworkResult.Success -> {
          SignalStore.backup.messageCredentials.add(credResult.result.messageCredentials)
          SignalStore.backup.messageCredentials.clearOlderThan(currentTime)
          val credential = SignalStore.backup.messageCredentials.byDay.getForCurrentTime(currentTime.milliseconds)
            ?: return@withContext RequestResult.ApplicationError(IllegalStateException("Failed to obtain backup credentials after fetch"))
          ArchiveServiceAccess(credential, SignalStore.backup.messageBackupKey)
        }
        is NetworkResult.StatusCodeError -> return@withContext RequestResult.ApplicationError(IllegalStateException("Failed to fetch backup credentials: ${credResult.code}"))
        is NetworkResult.NetworkError -> return@withContext RequestResult.RetryableNetworkError(credResult.exception)
        is NetworkResult.ApplicationError -> return@withContext RequestResult.ApplicationError(credResult.throwable)
      }
    }

    when (val result = SignalNetwork.archive.getBackupInfo(aci, access)) {
      is NetworkResult.Success -> {
        val info = result.result
        RequestResult.Success(
          NetworkController.GetBackupInfoResponse(
            cdn = info.cdn,
            backupDir = info.backupDir,
            mediaDir = info.mediaDir,
            backupName = info.backupName,
            usedSpace = info.usedSpace
          )
        )
      }
      is NetworkResult.StatusCodeError -> {
        when (result.code) {
          400 -> RequestResult.NonSuccess(NetworkController.GetBackupInfoError.BadArguments(result.stringBody))
          401 -> RequestResult.NonSuccess(NetworkController.GetBackupInfoError.BadAuthCredential(result.stringBody))
          403 -> RequestResult.NonSuccess(NetworkController.GetBackupInfoError.Forbidden(result.stringBody))
          404 -> RequestResult.NonSuccess(NetworkController.GetBackupInfoError.NoBackup)
          429 -> RequestResult.NonSuccess(NetworkController.GetBackupInfoError.RateLimited(0.seconds))
          else -> RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${result.code}"))
        }
      }
      is NetworkResult.NetworkError -> RequestResult.RetryableNetworkError(result.exception)
      is NetworkResult.ApplicationError -> RequestResult.ApplicationError(result.throwable)
    }
  }

  override fun startProvisioning(): Flow<ProvisioningEvent> = callbackFlow {
    val socketHandles = mutableListOf<java.io.Closeable>()
    val configuration = AppDependencies.signalServiceNetworkAccess.getConfiguration()

    fun startSocket() {
      val handle = ProvisioningSocket.start<RegistrationProvisionMessage>(
        mode = ProvisioningSocket.Mode.REREG,
        identityKeyPair = IdentityKeyPair.generate(),
        configuration = configuration,
        handler = { id, t ->
          Log.w(TAG, "[startProvisioning] Socket [$id] failed", t)
          trySend(ProvisioningEvent.Error(t))
        }
      ) { socket ->
        val url = socket.getProvisioningUrl()
        trySend(ProvisioningEvent.QrCodeReady(url))

        val result = socket.getProvisioningMessageDecryptResult()

        if (result is SecondaryProvisioningCipher.ProvisioningDecryptResult.Success) {
          val msg = result.message
          trySend(
            ProvisioningEvent.MessageReceived(
              ProvisioningMessage(
                accountEntropyPool = msg.accountEntropyPool,
                e164 = msg.e164,
                pin = msg.pin,
                aciIdentityKeyPair = IdentityKeyPair(IdentityKey(msg.aciIdentityKeyPublic.toByteArray()), ECPrivateKey(msg.aciIdentityKeyPrivate.toByteArray())),
                pniIdentityKeyPair = IdentityKeyPair(IdentityKey(msg.pniIdentityKeyPublic.toByteArray()), ECPrivateKey(msg.pniIdentityKeyPrivate.toByteArray())),
                platform = when (msg.platform) {
                  RegistrationProvisionMessage.Platform.ANDROID -> ProvisioningMessage.Platform.ANDROID
                  RegistrationProvisionMessage.Platform.IOS -> ProvisioningMessage.Platform.IOS
                },
                tier = when (msg.tier) {
                  RegistrationProvisionMessage.Tier.FREE -> ProvisioningMessage.Tier.FREE
                  RegistrationProvisionMessage.Tier.PAID -> ProvisioningMessage.Tier.PAID
                  null -> null
                },
                backupTimestampMs = msg.backupTimestampMs,
                backupSizeBytes = msg.backupSizeBytes,
                restoreMethodToken = msg.restoreMethodToken,
                backupVersion = msg.backupVersion
              )
            )
          )
          channel.close()
        } else {
          Log.w(TAG, "[startProvisioning] Failed to decrypt provisioning message")
          trySend(ProvisioningEvent.Error(IOException("Failed to decrypt provisioning message")))
        }
      }

      synchronized(socketHandles) {
        socketHandles += handle
        if (socketHandles.size > 2) {
          socketHandles.removeAt(0).close()
        }
      }
    }

    startSocket()

    val rotationJob = launch {
      var count = 0
      while (count < 5 && isActive) {
        kotlinx.coroutines.delay(ProvisioningSocket.LIFESPAN / 2)
        if (isActive) {
          startSocket()
          count++
          Log.d(TAG, "[startProvisioning] Rotated socket, count: $count")
        }
      }
    }

    awaitClose {
      rotationJob.cancel()
      synchronized(socketHandles) {
        socketHandles.forEach { it.close() }
        socketHandles.clear()
      }
    }
  }

  private fun AccountAttributes.toServiceAccountAttributes(): ServiceAccountAttributes {
    return ServiceAccountAttributes(
      signalingKey,
      registrationId,
      fetchesMessages,
      registrationLock,
      unidentifiedAccessKey,
      unrestrictedUnidentifiedAccess,
      capabilities?.toServiceCapabilities(),
      discoverableByPhoneNumber,
      name,
      pniRegistrationId,
      recoveryPassword
    )
  }

  private fun AccountAttributes.Capabilities.toServiceCapabilities(): ServiceAccountAttributes.Capabilities {
    return ServiceAccountAttributes.Capabilities(
      storage,
      versionedExpirationTimer,
      attachmentBackfill,
      spqr
    )
  }

  private fun PreKeyCollection.toServicePreKeyCollection(): ServicePreKeyCollection {
    return ServicePreKeyCollection(
      identityKey = identityKey,
      signedPreKey = signedPreKey,
      lastResortKyberPreKey = lastResortKyberPreKey
    )
  }

  private fun okhttp3.Response.retryAfter(): Duration {
    return this.header("Retry-After")?.toLongOrNull()?.seconds ?: 0.seconds
  }
}
