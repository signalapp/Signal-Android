/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.network.NetworkResult
import org.signal.registration.LinkAndSyncWaitResult
import org.signal.registration.NetworkController
import org.signal.registration.NetworkController.AccountAttributes
import org.signal.registration.NetworkController.BackupMasterKeyError
import org.signal.registration.NetworkController.CheckSvrCredentialsError
import org.signal.registration.NetworkController.CheckSvrCredentialsResponse
import org.signal.registration.NetworkController.CreateSessionError
import org.signal.registration.NetworkController.DeviceAttributes
import org.signal.registration.NetworkController.GetSessionStatusError
import org.signal.registration.NetworkController.GetSvrCredentialsError
import org.signal.registration.NetworkController.LinkDeviceProvisioningEvent
import org.signal.registration.NetworkController.LinkDeviceProvisioningMessage
import org.signal.registration.NetworkController.LinkDeviceResponse
import org.signal.registration.NetworkController.PreKeyCollection
import org.signal.registration.NetworkController.ProvisioningEvent
import org.signal.registration.NetworkController.ProvisioningMessage
import org.signal.registration.NetworkController.RegisterAccountError
import org.signal.registration.NetworkController.RegisterAccountResponse
import org.signal.registration.NetworkController.RegisterAsLinkedDeviceError
import org.signal.registration.NetworkController.RegistrationLockResponse
import org.signal.registration.NetworkController.RequestVerificationCodeError
import org.signal.registration.NetworkController.RestoreAccountRecordError
import org.signal.registration.NetworkController.RestoreMasterKeyError
import org.signal.registration.NetworkController.SessionMetadata
import org.signal.registration.NetworkController.SetAccountAttributesError
import org.signal.registration.NetworkController.SetProfileError
import org.signal.registration.NetworkController.SetRegistrationLockError
import org.signal.registration.NetworkController.SetRestoreMethodError
import org.signal.registration.NetworkController.SubmitVerificationCodeError
import org.signal.registration.NetworkController.SvrCredentials
import org.signal.registration.NetworkController.ThirdPartyServiceErrorResponse
import org.signal.registration.NetworkController.UpdateSessionError
import org.signal.registration.NetworkController.VerificationCodeTransport
import org.signal.registration.NetworkController.VerifyBackupKeyError
import org.signal.registration.proto.RegistrationProvisionMessage
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.RestoreTimestampResult
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.gcm.FcmUtil
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileContentUpdateJob
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileKeyUpdateJob
import org.thoughtcrime.securesms.jobs.ProfileUploadJob
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob
import org.thoughtcrime.securesms.jobs.ResetSvrGuessCountJob
import org.thoughtcrime.securesms.jobs.StorageAccountRestoreJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.pin.SvrRepository
import org.thoughtcrime.securesms.pin.SvrWrongPinException
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.fcm.PushChallengeRequest
import org.thoughtcrime.securesms.registration.ui.restore.StorageServiceRestore
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.thoughtcrime.securesms.registration.viewmodel.SvrAuthCredentialSet
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.SvrNoDataException
import org.whispersystems.signalservice.api.archive.ArchiveServiceAccess
import org.whispersystems.signalservice.api.link.TransferArchiveResponse
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage
import org.whispersystems.signalservice.api.provisioning.ProvisioningSocket
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.BackupResponse
import org.whispersystems.signalservice.internal.crypto.SecondaryProvisioningCipher
import org.whispersystems.signalservice.internal.push.AuthCredentials
import org.whispersystems.signalservice.internal.push.GcmRegistrationId
import org.whispersystems.signalservice.internal.push.KyberPreKeyEntity
import org.whispersystems.signalservice.internal.push.ProvisionMessage
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import org.whispersystems.signalservice.internal.push.RegisterAsSecondaryDeviceRequest
import org.whispersystems.signalservice.internal.push.SyncMessage
import java.io.Closeable
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.whispersystems.signalservice.api.account.AccountAttributes as ServiceAccountAttributes
import org.whispersystems.signalservice.api.account.DeviceAttributes as ServiceDeviceAttributes
import org.whispersystems.signalservice.api.account.PreKeyCollection as ServicePreKeyCollection
import org.whispersystems.signalservice.api.provisioning.RestoreMethod as ServiceRestoreMethod

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
    private val RETRY_BACKOFF = 5.seconds
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

  override suspend fun enqueueSvrGuessResetJobIfPossible(): Boolean {
    if (SignalStore.svr.pin == null) {
      return false
    }

    AppDependencies.jobManager.add(ResetSvrGuessCountJob())
    return true
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

  override suspend fun getRemoteBackupInfo(aep: AccountEntropyPool): RequestResult<NetworkController.GetBackupInfoResponse, NetworkController.GetBackupInfoError> = withContext(Dispatchers.IO) {
    val aci = SignalStore.account.aci ?: return@withContext RequestResult.ApplicationError(IllegalStateException("ACI not available"))

    val currentTime = System.currentTimeMillis()
    val messageBackupKey = aep.deriveMessageBackupKey()
    val messageCredential = SignalStore.backup.messageCredentials.byDay.getForCurrentTime(currentTime.milliseconds)

    val access = if (messageCredential != null) {
      ArchiveServiceAccess(messageCredential, messageBackupKey)
    } else {
      when (val credResult = SignalNetwork.archive.getServiceCredentials(currentTime)) {
        is NetworkResult.Success -> {
          SignalStore.backup.messageCredentials.add(credResult.result.messageCredentials)
          SignalStore.backup.messageCredentials.clearOlderThan(currentTime)
          val credential = SignalStore.backup.messageCredentials.byDay.getForCurrentTime(currentTime.milliseconds)
            ?: return@withContext RequestResult.ApplicationError(IllegalStateException("Failed to obtain backup credentials after fetch"))
          ArchiveServiceAccess(credential, messageBackupKey)
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

  override suspend fun enqueueAccountAttributesSyncJob() {
    AppDependencies.jobManager.add(RefreshAttributesJob())
  }

  override suspend fun setProfile(
    givenName: String,
    familyName: String,
    avatar: ByteArray?,
    discoverableByPhoneNumber: Boolean
  ): RequestResult<Unit, SetProfileError> = withContext(Dispatchers.IO) {
    if (!SignalStore.account.isRegistered) {
      Log.w(TAG, "[setProfile] Not registered.")
      return@withContext RequestResult.NonSuccess(SetProfileError.NotRegistered)
    }

    val profileName = ProfileName.fromParts(givenName, familyName)
    SignalDatabase.recipients.setProfileName(Recipient.self().id, profileName)

    if (avatar != null) {
      try {
        AvatarHelper.setAvatar(context, Recipient.self().id, java.io.ByteArrayInputStream(avatar))
      } catch (e: IOException) {
        Log.w(TAG, "[setProfile] Failed to write avatar.", e)
        return@withContext RequestResult.NonSuccess(SetProfileError.IOError(e))
      }
      SignalStore.misc.hasEverHadAnAvatar = true
    }

    SignalStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode = if (discoverableByPhoneNumber) {
      org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode.DISCOVERABLE
    } else {
      org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode.NOT_DISCOVERABLE
    }

    AppDependencies.jobManager
      .startChain(ProfileUploadJob())
      .then(listOf(MultiDeviceProfileKeyUpdateJob(), MultiDeviceProfileContentUpdateJob()))
      .enqueue()

    RegistrationUtil.maybeMarkRegistrationComplete()

    RequestResult.Success(Unit)
  }

  override suspend fun restoreAccountRecord(
    timeout: Duration
  ): RequestResult<Unit, RestoreAccountRecordError> = withContext(Dispatchers.IO) {
    Log.i(TAG, "[restoreAccountRecord] Enqueuing StorageAccountRestoreJob (timeout=${timeout.inWholeSeconds}s).")
    val state = AppDependencies.jobManager.runSynchronously(StorageAccountRestoreJob(), timeout.inWholeMilliseconds)
    if (state.isPresent) {
      Log.i(TAG, "[restoreAccountRecord] Completed within timeout: ${state.get()}")
      RequestResult.Success(Unit)
    } else {
      Log.w(TAG, "[restoreAccountRecord] Timed out. Job continues in background.")
      RequestResult.NonSuccess(RestoreAccountRecordError.Timeout)
    }
  }

  override suspend fun setRestoreMethod(token: String, method: NetworkController.RestoreMethod): RequestResult<Unit, SetRestoreMethodError> = withContext(Dispatchers.IO) {
    val serviceMethod = when (method) {
      NetworkController.RestoreMethod.REMOTE_BACKUP -> ServiceRestoreMethod.REMOTE_BACKUP
      NetworkController.RestoreMethod.LOCAL_BACKUP -> ServiceRestoreMethod.LOCAL_BACKUP
      NetworkController.RestoreMethod.DEVICE_TRANSFER -> ServiceRestoreMethod.DEVICE_TRANSFER
      NetworkController.RestoreMethod.DECLINE -> ServiceRestoreMethod.DECLINE
    }
    when (val result = AppDependencies.registrationApi.setRestoreMethod(token, serviceMethod)) {
      is NetworkResult.Success -> RequestResult.Success(Unit)
      is NetworkResult.StatusCodeError -> {
        when (result.code) {
          429 -> RequestResult.NonSuccess(SetRestoreMethodError.RateLimited(0.seconds))
          else -> RequestResult.NonSuccess(SetRestoreMethodError.InvalidRequest("HTTP ${result.code}"))
        }
      }
      is NetworkResult.NetworkError -> RequestResult.RetryableNetworkError(result.exception)
      is NetworkResult.ApplicationError -> RequestResult.ApplicationError(result.throwable)
    }
  }

  override suspend fun getBackupFileLastModified(
    aep: AccountEntropyPool,
    backupInfo: NetworkController.GetBackupInfoResponse
  ): RequestResult<Long, NetworkController.GetBackupInfoError> = withContext(Dispatchers.IO) {
    val aci = SignalStore.account.aci ?: return@withContext RequestResult.ApplicationError(IllegalStateException("ACI not available"))
    val cdn = backupInfo.cdn ?: return@withContext RequestResult.ApplicationError(IllegalStateException("CDN number not available"))
    val backupDir = backupInfo.backupDir ?: return@withContext RequestResult.ApplicationError(IllegalStateException("Backup dir not available"))
    val backupName = backupInfo.backupName ?: return@withContext RequestResult.ApplicationError(IllegalStateException("Backup name not available"))

    val currentTime = System.currentTimeMillis()
    val messageCredential = SignalStore.backup.messageCredentials.byDay.getForCurrentTime(currentTime.milliseconds)
      ?: return@withContext RequestResult.ApplicationError(IllegalStateException("No message credential available"))

    val access = ArchiveServiceAccess(messageCredential, aep.deriveMessageBackupKey())

    val cdnCredentials = when (val cdnResult = SignalNetwork.archive.getCdnReadCredentials(cdn, aci, access)) {
      is NetworkResult.Success -> cdnResult.result.headers
      is NetworkResult.StatusCodeError -> return@withContext RequestResult.ApplicationError(IllegalStateException("Failed to get CDN credentials: ${cdnResult.code}"))
      is NetworkResult.NetworkError -> return@withContext RequestResult.RetryableNetworkError(cdnResult.exception)
      is NetworkResult.ApplicationError -> return@withContext RequestResult.ApplicationError(cdnResult.throwable)
    }

    try {
      val lastModified = AppDependencies.signalServiceMessageReceiver.getCdnLastModifiedTime(cdn, cdnCredentials, "backups/$backupDir/$backupName")
      RequestResult.Success(lastModified.toInstant().toEpochMilli())
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      RequestResult.ApplicationError(e)
    }
  }

  override suspend fun verifyBackupKeyAssociatedWithAccount(aep: AccountEntropyPool): RequestResult<Unit, VerifyBackupKeyError> = withContext(Dispatchers.IO) {
    val aci = SignalStore.account.aci ?: return@withContext RequestResult.ApplicationError(IllegalStateException("ACI not available"))

    when (val result = BackupRepository.verifyBackupKeyAssociatedWithAccount(aci, aep)) {
      is RestoreTimestampResult.Success -> RequestResult.Success(Unit)
      RestoreTimestampResult.NotFound,
      RestoreTimestampResult.BackupsNotEnabled -> RequestResult.NonSuccess(VerifyBackupKeyError.NoBackup)
      RestoreTimestampResult.VerificationFailure -> RequestResult.NonSuccess(VerifyBackupKeyError.IncorrectKey)
      is RestoreTimestampResult.RateLimited -> RequestResult.NonSuccess(VerifyBackupKeyError.RateLimited(result.retryAfter))
      // Failure is the catch-all for "couldn't check the backup"; the specific outcomes are already broken out above, so
      // this is overwhelmingly a connectivity/transport issue (e.g. no network).
      RestoreTimestampResult.Failure -> RequestResult.RetryableNetworkError(IOException("Failed to verify backup key associated with account"))
    }
  }

  override fun startNewDeviceTransferServer(context: Context, aep: AccountEntropyPool) {
    val pendingIntent = android.app.PendingIntent.getActivity(
      context,
      0,
      org.thoughtcrime.securesms.MainActivity.clearTop(context),
      org.signal.core.util.PendingIntentFlags.mutable()
    )
    val notificationData = org.signal.devicetransfer.DeviceToDeviceTransferService.TransferNotificationData(
      org.thoughtcrime.securesms.notifications.NotificationIds.DEVICE_TRANSFER,
      org.thoughtcrime.securesms.notifications.NotificationChannels.getInstance().BACKUPS,
      org.thoughtcrime.securesms.R.drawable.ic_signal_backup
    )
    org.signal.devicetransfer.DeviceToDeviceTransferService.startServer(
      context,
      org.thoughtcrime.securesms.devicetransfer.newdevice.NewDeviceServerTask(),
      notificationData,
      pendingIntent
    )
  }

  override fun startProvisioning(): Flow<ProvisioningEvent> = callbackFlow {
    val socketHandles = mutableListOf<Closeable>()
    val configuration = AppDependencies.signalServiceNetworkAccess.getConfiguration()

    fun startSocket() {
      val handle = ProvisioningSocket.start<RegistrationProvisionMessage>(
        mode = ProvisioningSocket.Mode.Rereg,
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
        delay(ProvisioningSocket.LIFESPAN / 2)
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

  override fun startLinkDeviceProvisioning(allowLinkAndSync: Boolean): Flow<LinkDeviceProvisioningEvent> = callbackFlow {
    val socketHandles = mutableListOf<Closeable>()
    val configuration = AppDependencies.signalServiceNetworkAccess.getConfiguration()

    fun startSocket() {
      val handle = ProvisioningSocket.start<ProvisionMessage>(
        mode = ProvisioningSocket.Mode.Link(linkAndSyncCapable = allowLinkAndSync),
        identityKeyPair = IdentityKeyPair.generate(),
        configuration = configuration,
        handler = { id, t ->
          Log.w(TAG, "[startLinkDeviceProvisioning] Socket [$id] failed", t)
          trySend(LinkDeviceProvisioningEvent.Error(t))
        }
      ) { socket ->
        val url = socket.getProvisioningUrl()
        trySend(LinkDeviceProvisioningEvent.QrCodeReady(url))

        val result = socket.getProvisioningMessageDecryptResult()

        if (result is SecondaryProvisioningCipher.ProvisioningDecryptResult.Success) {
          val msg = result.message
          val aci = msg.aciBinary?.let { ACI.parseOrThrow(it) } ?: ACI.parseOrThrow(msg.aci)
          val pni = msg.pniBinary?.let { PNI.parseOrThrow(it) } ?: PNI.parseOrThrow(msg.pni)

          trySend(
            LinkDeviceProvisioningEvent.MessageReceived(
              LinkDeviceProvisioningMessage(
                e164 = msg.number!!,
                provisioningCode = msg.provisioningCode!!,
                aci = aci.toString(),
                pni = pni.toString(),
                aciIdentityKeyPair = IdentityKeyPair(IdentityKey(msg.aciIdentityKeyPublic!!.toByteArray()), ECPrivateKey(msg.aciIdentityKeyPrivate!!.toByteArray())),
                pniIdentityKeyPair = IdentityKeyPair(IdentityKey(msg.pniIdentityKeyPublic!!.toByteArray()), ECPrivateKey(msg.pniIdentityKeyPrivate!!.toByteArray())),
                profileKey = msg.profileKey!!.toByteArray(),
                ephemeralBackupKey = msg.ephemeralBackupKey,
                accountEntropyPool = msg.accountEntropyPool,
                mediaRootBackupKey = msg.mediaRootBackupKey,
                readReceipts = msg.readReceipts
              )
            )
          )
          channel.close()
        } else {
          Log.w(TAG, "[startLinkDeviceProvisioning] Failed to decrypt provisioning message")
          trySend(LinkDeviceProvisioningEvent.Error(IOException("Failed to decrypt provisioning message")))
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
        delay(ProvisioningSocket.LIFESPAN / 2)
        if (isActive) {
          startSocket()
          count++
          Log.d(TAG, "[startLinkDeviceProvisioning] Rotated socket, count: $count")
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

  override suspend fun registerAsLinkedDevice(
    e164: String,
    password: String,
    provisioningCode: String,
    deviceAttributes: DeviceAttributes,
    aciPreKeys: PreKeyCollection,
    pniPreKeys: PreKeyCollection,
    fcmToken: String?
  ): RequestResult<LinkDeviceResponse, RegisterAsLinkedDeviceError> = withContext(Dispatchers.IO) {
    try {
      pushServiceSocket.registerAsSecondaryDevice(
        e164,
        password,
        RegisterAsSecondaryDeviceRequest(
          verificationCode = provisioningCode,
          accountAttributes = deviceAttributes.toServiceDeviceAttributes(),
          aciSignedPreKey = SignedPreKeyEntity(aciPreKeys.signedPreKey.id.toLong(), aciPreKeys.signedPreKey.keyPair.publicKey, aciPreKeys.signedPreKey.signature),
          pniSignedPreKey = SignedPreKeyEntity(pniPreKeys.signedPreKey.id.toLong(), pniPreKeys.signedPreKey.keyPair.publicKey, pniPreKeys.signedPreKey.signature),
          aciPqLastResortPreKey = KyberPreKeyEntity(aciPreKeys.lastResortKyberPreKey.id.toLong(), aciPreKeys.lastResortKyberPreKey.keyPair.publicKey, aciPreKeys.lastResortKyberPreKey.signature),
          pniPqLastResortPreKey = KyberPreKeyEntity(pniPreKeys.lastResortKyberPreKey.id.toLong(), pniPreKeys.lastResortKyberPreKey.keyPair.publicKey, pniPreKeys.lastResortKyberPreKey.signature),
          gcmToken = fcmToken?.let { GcmRegistrationId(it, true) }
        )
      ).use { response ->
        when (response.code) {
          200 -> RequestResult.Success(json.decodeFromString<LinkDeviceResponse>(response.body.string()))
          403 -> RequestResult.NonSuccess(RegisterAsLinkedDeviceError.IncorrectVerification)
          409 -> RequestResult.NonSuccess(RegisterAsLinkedDeviceError.MissingCapability)
          411 -> RequestResult.NonSuccess(RegisterAsLinkedDeviceError.MaxLinkedDevices)
          422 -> RequestResult.NonSuccess(RegisterAsLinkedDeviceError.InvalidRequest(response.body.string()))
          429 -> RequestResult.NonSuccess(RegisterAsLinkedDeviceError.RateLimited(response.retryAfter()))
          else -> RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}, body: ${response.body.string()}"))
        }
      }
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      RequestResult.ApplicationError(e)
    }
  }

  override suspend fun onLinkedDeviceRegistered() = withContext(Dispatchers.IO) {
    try {
      RemoteConfig.refreshSync()
    } catch (e: IOException) {
      Log.w(TAG, "[onLinkedDeviceRegistered] Failed to refresh remote config.", e)
    }

    for (type in SyncMessage.Request.Type.entries) {
      if (type == SyncMessage.Request.Type.UNKNOWN) {
        continue
      }

      Log.i(TAG, "[onLinkedDeviceRegistered] Sending sync request for $type")
      try {
        retryWithBackoff {
          AppDependencies.signalServiceMessageSender.sendSyncMessage(
            SignalServiceSyncMessage.forRequest(RequestMessage(SyncMessage.Request(type = type)))
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "[onLinkedDeviceRegistered] Failed to send sync request for $type after retries; continuing.", e)
      }
    }
  }

  private suspend fun <T> retryWithBackoff(maxAttempts: Int = 3, initialDelay: Duration = 1.seconds, block: suspend () -> T): T {
    var attempt = 0
    while (true) {
      try {
        return block()
      } catch (e: IOException) {
        attempt++
        if (attempt >= maxAttempts) {
          throw e
        }
        val backoff = initialDelay * attempt
        Log.w(TAG, "[retryWithBackoff] Attempt $attempt failed; retrying in $backoff.", e)
        delay(backoff)
      }
    }
  }

  override suspend fun restoreLinkedDeviceFromStorageService() = withContext(Dispatchers.IO) {
    if (SignalStore.account.restoredAccountEntropyPoolFromPrimary) {
      Log.i(TAG, "[restoreLinkedDeviceFromStorageService] Restoring account data from storage service.")
      try {
        StorageServiceRestore.restore()
      } catch (e: CancellationException) {
        Log.i(TAG, "[restoreLinkedDeviceFromStorageService] Restoring account cancelled.", e)
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "[restoreLinkedDeviceFromStorageService] Storage service restore failed.", e)
      }
    } else {
      Log.i(TAG, "[restoreLinkedDeviceFromStorageService] No account entropy pool from primary; skipping storage service restore.")
    }
  }

  override suspend fun awaitLinkAndSyncArchive(): LinkAndSyncWaitResult = withContext(Dispatchers.IO) {
    val response = awaitTransferArchiveFromPrimary()
    val result = when {
      response == null -> LinkAndSyncWaitResult.ContinueWithoutBackup
      response.error == TransferArchiveResponse.ERROR_RELINK_REQUESTED -> LinkAndSyncWaitResult.RelinkRequired
      response.error == TransferArchiveResponse.ERROR_CONTINUE_WITHOUT_UPLOAD -> LinkAndSyncWaitResult.ContinueWithoutBackup
      response.hasArchive -> LinkAndSyncWaitResult.ArchiveAvailable(cdn = response.cdn!!, key = response.key!!)
      else -> LinkAndSyncWaitResult.ContinueWithoutBackup
    }
    Log.i(TAG, "[awaitLinkAndSyncArchive] Result: $result")
    result
  }

  /**
   * Waits for the primary device to make a link-and-sync transfer archive available, long-polling
   * [org.signal.network.api.LinkDeviceApi.waitForPrimaryDevice] and retrying transient errors until
   * [maxWaitTime] elapses. Returns null if no archive becomes available.
   */
  private suspend fun awaitTransferArchiveFromPrimary(maxWaitTime: Duration = 1.hours): TransferArchiveResponse? {
    val startTime = System.currentTimeMillis()
    var timeRemaining = maxWaitTime.inWholeMilliseconds

    while (timeRemaining > 0 && coroutineContext.isActive) {
      Log.d(TAG, "[awaitTransferArchiveFromPrimary] Willing to wait for $timeRemaining ms...")

      when (val result = SignalNetwork.linkDevice.waitForPrimaryDevice(timeout = 60.seconds)) {
        is NetworkResult.Success -> {
          Log.i(TAG, "[awaitTransferArchiveFromPrimary] Primary responded (hasArchive=${result.result.hasArchive}, error=${result.result.error})")
          return result.result
        }
        is NetworkResult.ApplicationError -> {
          Log.w(TAG, "[awaitTransferArchiveFromPrimary] Error processing response", result.throwable)
          return null
        }
        is NetworkResult.NetworkError -> {
          Log.w(TAG, "[awaitTransferArchiveFromPrimary] Network error while waiting; will retry after $RETRY_BACKOFF.", result.exception)
          delay(RETRY_BACKOFF)
        }
        is NetworkResult.StatusCodeError -> {
          when (result.code) {
            400 -> {
              Log.w(TAG, "[awaitTransferArchiveFromPrimary] Invalid timeout.")
              return null
            }
            429 -> {
              Log.w(TAG, "[awaitTransferArchiveFromPrimary] Rate-limited; will retry after ${result.retryAfter()}.")
              result.retryAfter()?.let { delay(it) }
            }
            else -> {
              Log.w(TAG, "[awaitTransferArchiveFromPrimary] Unexpected status ${result.code}; will retry after $RETRY_BACKOFF.")
              delay(RETRY_BACKOFF)
            }
          }
        }
      }

      timeRemaining = maxWaitTime.inWholeMilliseconds - (System.currentTimeMillis() - startTime)
    }

    Log.w(TAG, "[awaitTransferArchiveFromPrimary] No transfer archive from primary within $maxWaitTime.")
    return null
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
      null,
      pniRegistrationId,
      recoveryPassword
    )
  }

  private fun AccountAttributes.Capabilities.toServiceCapabilities(): ServiceAccountAttributes.Capabilities {
    return ServiceAccountAttributes.Capabilities(
      storage,
      versionedExpirationTimer,
      attachmentBackfill,
      spqr,
      usernameChangeSyncMessage
    )
  }

  private fun DeviceAttributes.toServiceDeviceAttributes(): ServiceDeviceAttributes {
    return ServiceDeviceAttributes(
      fetchesMessages = fetchesMessages,
      registrationId = registrationId,
      pniRegistrationId = pniRegistrationId,
      name = name,
      capabilities = capabilities?.toServiceCapabilities()
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
