/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.dependencies

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.signal.core.models.MasterKey
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.Network
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.util.Hex
import org.signal.libsignal.zkgroup.GenericServerPublicParams
import org.signal.libsignal.zkgroup.backups.BackupAuthCredentialRequestContext
import org.signal.libsignal.zkgroup.backups.BackupAuthCredentialResponse
import org.signal.registration.NetworkController
import org.signal.registration.NetworkController.AccountAttributes
import org.signal.registration.NetworkController.CheckSvrCredentialsRequest
import org.signal.registration.NetworkController.CheckSvrCredentialsResponse
import org.signal.registration.NetworkController.CreateSessionError
import org.signal.registration.NetworkController.GetSessionStatusError
import org.signal.registration.NetworkController.PreKeyCollection
import org.signal.registration.NetworkController.ProvisioningEvent
import org.signal.registration.NetworkController.ProvisioningMessage
import org.signal.registration.NetworkController.RegisterAccountError
import org.signal.registration.NetworkController.RegisterAccountResponse
import org.signal.registration.NetworkController.RegistrationLockResponse
import org.signal.registration.NetworkController.RequestVerificationCodeError
import org.signal.registration.NetworkController.SessionMetadata
import org.signal.registration.NetworkController.SubmitVerificationCodeError
import org.signal.registration.NetworkController.ThirdPartyServiceErrorResponse
import org.signal.registration.NetworkController.UpdateSessionError
import org.signal.registration.NetworkController.VerificationCodeTransport
import org.signal.registration.proto.RegistrationProvisionMessage
import org.signal.registration.sample.fcm.FcmUtil
import org.signal.registration.sample.fcm.PushChallengeReceiver
import org.signal.registration.sample.storage.RegistrationPreferences
import org.whispersystems.signalservice.api.provisioning.ProvisioningSocket
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.BackupResponse
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.RestoreResponse
import org.whispersystems.signalservice.api.svr.SecureValueRecoveryV2
import org.whispersystems.signalservice.api.util.SleepTimer
import org.whispersystems.signalservice.api.websocket.HealthMonitor
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.api.websocket.WebSocketFactory
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.crypto.SecondaryProvisioningCipher
import org.whispersystems.signalservice.internal.push.AuthCredentials
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider
import org.whispersystems.signalservice.internal.websocket.LibSignalChatConnection
import java.io.IOException
import java.time.Instant
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.whispersystems.signalservice.api.account.AccountAttributes as ServiceAccountAttributes
import org.whispersystems.signalservice.api.account.PreKeyCollection as ServicePreKeyCollection

class DemoNetworkController(
  private val context: android.content.Context,
  private val pushServiceSocket: PushServiceSocket,
  private val serviceConfiguration: SignalServiceConfiguration,
  private val svr2MrEnclave: String
) : NetworkController {

  companion object {
    private val TAG = Log.tag(DemoNetworkController::class)
  }

  private val json = Json { ignoreUnknownKeys = true }

  private val okHttpClient: okhttp3.OkHttpClient by lazy {
    val trustStore = serviceConfiguration.signalServiceUrls[0].trustStore
    val keyStore = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType())
    keyStore.load(trustStore.keyStoreInputStream, trustStore.keyStorePassword.toCharArray())

    val tmf = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm())
    tmf.init(keyStore)

    val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
    sslContext.init(null, tmf.trustManagers, null)

    val trustManager = tmf.trustManagers[0] as javax.net.ssl.X509TrustManager

    okhttp3.OkHttpClient.Builder()
      .sslSocketFactory(sslContext.socketFactory, trustManager)
      .build()
  }

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
        null, // pushToken
        null, // mcc
        null, // mnc
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
      val serviceAttributes = attributes.toServiceAccountAttributes()
      val serviceAciPreKeys = aciPreKeys.toServicePreKeyCollection()
      val servicePniPreKeys = pniPreKeys.toServicePreKeyCollection()

      pushServiceSocket.submitRegistrationRequestV2(
        e164,
        password,
        sessionId,
        recoveryPassword,
        serviceAttributes,
        serviceAciPreKeys,
        servicePniPreKeys,
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
      FcmUtil.getToken(context)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to get FCM token", e)
      null
    }
  }

  override suspend fun awaitPushChallengeToken(): String? {
    return try {
      PushChallengeReceiver.awaitChallenge()
    } catch (e: Exception) {
      Log.w(TAG, "Failed to await push challenge token", e)
      null
    }
  }

  override fun getCaptchaUrl(): String {
    return "https://signalcaptchas.org/staging/registration/generate.html"
  }

  override fun startProvisioning(): Flow<ProvisioningEvent> = callbackFlow {
    val socketHandles = mutableListOf<java.io.Closeable>()

    fun startSocket() {
      val handle = ProvisioningSocket.start<RegistrationProvisionMessage>(
        mode = ProvisioningSocket.Mode.REREG,
        identityKeyPair = IdentityKeyPair.generate(),
        configuration = serviceConfiguration,
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
                  RegistrationProvisionMessage.Platform.ANDROID -> NetworkController.ProvisioningMessage.Platform.ANDROID
                  RegistrationProvisionMessage.Platform.IOS -> NetworkController.ProvisioningMessage.Platform.IOS
                },
                tier = when (msg.tier) {
                  RegistrationProvisionMessage.Tier.FREE -> NetworkController.ProvisioningMessage.Tier.FREE
                  RegistrationProvisionMessage.Tier.PAID -> NetworkController.ProvisioningMessage.Tier.PAID
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

  override suspend fun restoreMasterKeyFromSvr(
    svrCredentials: NetworkController.SvrCredentials,
    pin: String
  ): RequestResult<NetworkController.MasterKeyResponse, NetworkController.RestoreMasterKeyError> = withContext(Dispatchers.IO) {
    try {
      val authCredentials = AuthCredentials.create(svrCredentials.username, svrCredentials.password)

      // Create a stub websocket that will never be used for pre-registration restore
      val stubWebSocketFactory = WebSocketFactory { throw UnsupportedOperationException("WebSocket not available during pre-registration") }
      val stubWebSocket = SignalWebSocket.AuthenticatedWebSocket(
        stubWebSocketFactory,
        { false },
        object : SleepTimer {
          override fun sleep(millis: Long) = Thread.sleep(millis)
        },
        0
      )

      val svr2 = SecureValueRecoveryV2(serviceConfiguration, svr2MrEnclave, stubWebSocket)

      when (val response = svr2.restoreDataPreRegistration(authCredentials, null, pin)) {
        is RestoreResponse.Success -> {
          Log.i(TAG, "[restoreMasterKeyFromSvr] Successfully restored master key from SVR2. Value: ${Hex.toStringCondensed(response.masterKey.serialize())}")
          RequestResult.Success(NetworkController.MasterKeyResponse(response.masterKey))
        }
        is RestoreResponse.PinMismatch -> {
          Log.w(TAG, "[restoreMasterKeyFromSvr] PIN mismatch. Tries remaining: ${response.triesRemaining}")
          RequestResult.NonSuccess(NetworkController.RestoreMasterKeyError.WrongPin(response.triesRemaining))
        }
        is RestoreResponse.Missing -> {
          Log.w(TAG, "[restoreMasterKeyFromSvr] No SVR data found for user")
          RequestResult.NonSuccess(NetworkController.RestoreMasterKeyError.NoDataFound)
        }
        is RestoreResponse.NetworkError -> {
          Log.w(TAG, "[restoreMasterKeyFromSvr] Network error", response.exception)
          RequestResult.RetryableNetworkError(response.exception)
        }
        is RestoreResponse.ApplicationError -> {
          Log.w(TAG, "[restoreMasterKeyFromSvr] Application error", response.exception)
          RequestResult.ApplicationError(response.exception)
        }
        is RestoreResponse.EnclaveNotFound -> {
          Log.w(TAG, "[restoreMasterKeyFromSvr] Enclave not found")
          RequestResult.ApplicationError(IllegalStateException("SVR2 enclave not found"))
        }
      }
    } catch (e: IOException) {
      Log.w(TAG, "[restoreMasterKeyFromSvr] IOException", e)
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      Log.w(TAG, "[restoreMasterKeyFromSvr] Exception", e)
      RequestResult.ApplicationError(e)
    }
  }

  override suspend fun setPinAndMasterKeyOnSvr(
    pin: String,
    masterKey: MasterKey
  ): RequestResult<NetworkController.SvrCredentials?, NetworkController.BackupMasterKeyError> = withContext(Dispatchers.IO) {
    try {
      val aci = RegistrationPreferences.aci
      val pni = RegistrationPreferences.pni
      val e164 = RegistrationPreferences.e164
      val password = RegistrationPreferences.servicePassword

      if (aci == null || e164 == null || password == null) {
        Log.w(TAG, "[backupMasterKeyToSvr] Credentials not available, cannot authenticate")
        return@withContext RequestResult.NonSuccess(NetworkController.BackupMasterKeyError.NotRegistered)
      }

      val network = Network(Network.Environment.STAGING, "Signal-Android-Registration-Sample", emptyMap(), Network.BuildVariant.PRODUCTION)
      val credentialsProvider = StaticCredentialsProvider(aci, pni, e164, 1, password)
      val healthMonitor = object : HealthMonitor {
        override fun onKeepAliveResponse(sentTimestamp: Long, isIdentifiedWebSocket: Boolean) {}
        override fun onMessageError(status: Int, isIdentifiedWebSocket: Boolean) {}
      }

      val libSignalConnection = LibSignalChatConnection(
        name = "SVR-Backup",
        network = network,
        credentialsProvider = credentialsProvider,
        receiveStories = false,
        healthMonitor = healthMonitor
      )

      val authWebSocket = SignalWebSocket.AuthenticatedWebSocket(
        connectionFactory = { libSignalConnection },
        canConnect = { true },
        sleepTimer = { millis -> Thread.sleep(millis) },
        disconnectTimeoutMs = 60.seconds.inWholeMilliseconds
      )

      authWebSocket.connect()

      val svr2 = SecureValueRecoveryV2(serviceConfiguration, svr2MrEnclave, authWebSocket)
      val session = svr2.setPin(pin, masterKey)
      val response = session.execute()

      authWebSocket.disconnect()

      when (response) {
        is BackupResponse.Success -> {
          Log.i(TAG, "[backupMasterKeyToSvr] Successfully backed up master key to SVR2. Value: ${Hex.toStringCondensed(masterKey.serialize())}")
          RequestResult.Success(NetworkController.SvrCredentials(response.authorization.username(), response.authorization.password()))
        }
        is BackupResponse.ApplicationError -> {
          Log.w(TAG, "[backupMasterKeyToSvr] Application error", response.exception)
          RequestResult.ApplicationError(response.exception)
        }
        is BackupResponse.NetworkError -> {
          Log.w(TAG, "[backupMasterKeyToSvr] Network error", response.exception)
          RequestResult.RetryableNetworkError(response.exception)
        }
        is BackupResponse.EnclaveNotFound -> {
          Log.w(TAG, "[backupMasterKeyToSvr] Enclave not found")
          RequestResult.NonSuccess(NetworkController.BackupMasterKeyError.EnclaveNotFound)
        }
        is BackupResponse.ExposeFailure -> {
          Log.w(TAG, "[backupMasterKeyToSvr] Expose failure -- per spec, treat as success.")
          RequestResult.Success(null)
        }
        is BackupResponse.ServerRejected -> {
          Log.w(TAG, "[backupMasterKeyToSvr] Server rejected")
          RequestResult.RetryableNetworkError(IOException("Server rejected backup request"))
        }
        is BackupResponse.RateLimited -> {
          RequestResult.RetryableNetworkError(IOException("Rate limited"))
        }
      }
    } catch (e: IOException) {
      Log.w(TAG, "[backupMasterKeyToSvr] IOException", e)
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      Log.w(TAG, "[backupMasterKeyToSvr] Exception", e)
      RequestResult.ApplicationError(e)
    }
  }

  override suspend fun enqueueSvrGuessResetJob() {
    val pin = checkNotNull(RegistrationPreferences.pin) { "Pin is not set!" }
    val masterKey = checkNotNull(RegistrationPreferences.masterKey) { "Master key is not set!" }

    val result = setPinAndMasterKeyOnSvr(pin, masterKey)
    if (result !is RequestResult.Success) {
      Log.w(TAG, "Failed to set pin and master key on SVR! A real app would retry. Result: $result")
    }
  }

  override suspend fun enableRegistrationLock(): RequestResult<Unit, NetworkController.SetRegistrationLockError> = withContext(Dispatchers.IO) {
    val aci = RegistrationPreferences.aci
    val password = RegistrationPreferences.servicePassword
    val masterKey = RegistrationPreferences.masterKey

    if (aci == null || password == null) {
      Log.w(TAG, "[enableRegistrationLock] Credentials not available")
      return@withContext RequestResult.NonSuccess(NetworkController.SetRegistrationLockError.NotRegistered)
    }

    if (masterKey == null) {
      Log.w(TAG, "[enableRegistrationLock] Master key not available")
      return@withContext RequestResult.NonSuccess(NetworkController.SetRegistrationLockError.NoPinSet)
    }

    val registrationLockToken = masterKey.deriveRegistrationLock()

    try {
      val credentials = okhttp3.Credentials.basic(aci.toString(), password)
      val baseUrl = serviceConfiguration.signalServiceUrls[0].url
      val requestBody = """{"registrationLock":"$registrationLockToken"}"""
        .toRequestBody("application/json".toMediaType())

      val request = okhttp3.Request.Builder()
        .url("$baseUrl/v1/accounts/registration_lock")
        .put(requestBody)
        .header("Authorization", credentials)
        .build()

      okHttpClient.newCall(request).execute().use { response ->
        when (response.code) {
          200, 204 -> {
            Log.i(TAG, "[enableRegistrationLock] Successfully enabled registration lock")
            RequestResult.Success(Unit)
          }
          401 -> {
            RequestResult.NonSuccess(NetworkController.SetRegistrationLockError.Unauthorized)
          }
          422 -> {
            RequestResult.NonSuccess(NetworkController.SetRegistrationLockError.InvalidRequest(response.body?.string() ?: ""))
          }
          else -> {
            RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}"))
          }
        }
      }
    } catch (e: IOException) {
      Log.w(TAG, "[enableRegistrationLock] IOException", e)
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      Log.w(TAG, "[enableRegistrationLock] Exception", e)
      RequestResult.ApplicationError(e)
    }
  }

  override suspend fun disableRegistrationLock(): RequestResult<Unit, NetworkController.SetRegistrationLockError> = withContext(Dispatchers.IO) {
    val aci = RegistrationPreferences.aci
    val password = RegistrationPreferences.servicePassword

    if (aci == null || password == null) {
      Log.w(TAG, "[disableRegistrationLock] Credentials not available")
      return@withContext RequestResult.NonSuccess(NetworkController.SetRegistrationLockError.NotRegistered)
    }

    try {
      val credentials = okhttp3.Credentials.basic(aci.toString(), password)
      val baseUrl = serviceConfiguration.signalServiceUrls[0].url

      val request = okhttp3.Request.Builder()
        .url("$baseUrl/v1/accounts/registration_lock")
        .delete()
        .header("Authorization", credentials)
        .build()

      okHttpClient.newCall(request).execute().use { response ->
        when (response.code) {
          200, 204 -> {
            Log.i(TAG, "[disableRegistrationLock] Successfully disabled registration lock")
            RequestResult.Success(Unit)
          }
          401 -> {
            RequestResult.NonSuccess(NetworkController.SetRegistrationLockError.Unauthorized)
          }
          else -> {
            RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}"))
          }
        }
      }
    } catch (e: IOException) {
      Log.w(TAG, "[disableRegistrationLock] IOException", e)
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      Log.w(TAG, "[disableRegistrationLock] Exception", e)
      RequestResult.ApplicationError(e)
    }
  }

  override suspend fun setAccountAttributes(
    attributes: AccountAttributes
  ): RequestResult<Unit, NetworkController.SetAccountAttributesError> = withContext(Dispatchers.IO) {
    val aci = RegistrationPreferences.aci
    val password = RegistrationPreferences.servicePassword

    if (aci == null || password == null) {
      Log.w(TAG, "[setAccountAttributes] Credentials not available")
      return@withContext RequestResult.NonSuccess(NetworkController.SetAccountAttributesError.Unauthorized)
    }

    try {
      val credentials = okhttp3.Credentials.basic(aci.toString(), password)
      val baseUrl = serviceConfiguration.signalServiceUrls[0].url
      val requestBody = json.encodeToString(AccountAttributes.serializer(), attributes)
        .toRequestBody("application/json".toMediaType())

      val request = okhttp3.Request.Builder()
        .url("$baseUrl/v1/accounts/attributes")
        .put(requestBody)
        .header("Authorization", credentials)
        .build()

      okHttpClient.newCall(request).execute().use { response ->
        when (response.code) {
          200, 204 -> {
            Log.i(TAG, "[setAccountAttributes] Successfully updated account attributes")
            RequestResult.Success(Unit)
          }
          401 -> {
            RequestResult.NonSuccess(NetworkController.SetAccountAttributesError.Unauthorized)
          }
          422 -> {
            RequestResult.NonSuccess(NetworkController.SetAccountAttributesError.InvalidRequest(response.body?.string() ?: ""))
          }
          else -> {
            RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}, body: ${response.body?.string()}"))
          }
        }
      }
    } catch (e: IOException) {
      Log.w(TAG, "[setAccountAttributes] IOException", e)
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      Log.w(TAG, "[setAccountAttributes] Exception", e)
      RequestResult.ApplicationError(e)
    }
  }

  override suspend fun getSvrCredentials(): RequestResult<NetworkController.SvrCredentials, NetworkController.GetSvrCredentialsError> = withContext(Dispatchers.IO) {
    val aci = RegistrationPreferences.aci
    val password = RegistrationPreferences.servicePassword

    if (aci == null || password == null) {
      Log.w(TAG, "[getSvrCredentials] Credentials not available")
      return@withContext RequestResult.NonSuccess(NetworkController.GetSvrCredentialsError.NoServiceCredentialsAvailable)
    }

    try {
      val credentials = okhttp3.Credentials.basic(aci.toString(), password)
      val baseUrl = serviceConfiguration.signalServiceUrls[0].url

      val request = okhttp3.Request.Builder()
        .url("$baseUrl/v2/svr/auth")
        .get()
        .header("Authorization", credentials)
        .build()

      okHttpClient.newCall(request).execute().use { response ->
        when (response.code) {
          200 -> {
            val svrCredentials = json.decodeFromString<NetworkController.SvrCredentials>(response.body.string())
            RequestResult.Success(svrCredentials)
          }
          401 -> {
            RequestResult.NonSuccess(NetworkController.GetSvrCredentialsError.Unauthorized)
          }
          else -> {
            RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}, body: ${response.body.string()}"))
          }
        }
      }
    } catch (e: IOException) {
      Log.w(TAG, "[getSvrCredentials] IOException", e)
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      Log.w(TAG, "[getSvrCredentials] Exception", e)
      RequestResult.ApplicationError(e)
    }
  }

  override suspend fun checkSvrCredentials(
    e164: String,
    credentials: List<NetworkController.SvrCredentials>
  ): RequestResult<CheckSvrCredentialsResponse, NetworkController.CheckSvrCredentialsError> = withContext(Dispatchers.IO) {
    try {
      val baseUrl = serviceConfiguration.signalServiceUrls[0].url

      val requestBody = json.encodeToString(
        CheckSvrCredentialsRequest.serializer(),
        CheckSvrCredentialsRequest.createForCredentials(number = e164, credentials)
      ).toRequestBody("application/json".toMediaType())

      val request = okhttp3.Request.Builder()
        .url("$baseUrl/v2/svr/auth/check")
        .post(requestBody)
        .build()

      okHttpClient.newCall(request).execute().use { response ->
        when (response.code) {
          200 -> {
            val result = json.decodeFromString<CheckSvrCredentialsResponse>(response.body.string())
            RequestResult.Success(result)
          }
          400, 422 -> {
            RequestResult.NonSuccess(NetworkController.CheckSvrCredentialsError.InvalidRequest(response.body.string()))
          }
          401 -> {
            RequestResult.NonSuccess(NetworkController.CheckSvrCredentialsError.Unauthorized)
          }
          else -> {
            RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}, body: ${response.body?.string()}"))
          }
        }
      }
    } catch (e: IOException) {
      Log.w(TAG, "[checkSvrCredentials] IOException", e)
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      Log.w(TAG, "[checkSvrCredentials] Exception", e)
      RequestResult.ApplicationError(e)
    }
  }

  override suspend fun getRemoteBackupInfo(): RequestResult<NetworkController.GetBackupInfoResponse, NetworkController.GetBackupInfoError> = withContext(Dispatchers.IO) {
    val aci = RegistrationPreferences.aci
    val password = RegistrationPreferences.servicePassword
    val aep = RegistrationPreferences.aep

    if (aci == null || password == null || aep == null) {
      Log.w(TAG, "[getRemoteBackupInfo] Credentials not available")
      return@withContext RequestResult.ApplicationError(IllegalStateException("Credentials not available"))
    }

    try {
      val messageBackupKey = aep.deriveMessageBackupKey()

      // Remember, this is a demo app
      val credential = fetchArchiveServiceCredential(aci.toString(), password)
        ?: return@withContext RequestResult.RetryableNetworkError(IOException("Failed to fetch archive credentials"))

      val headers = buildZkAuthHeaders(messageBackupKey, aci, credential)

      val baseUrl = serviceConfiguration.signalServiceUrls[0].url
      val request = okhttp3.Request.Builder()
        .url("$baseUrl/v1/archives")
        .get()
        .apply { headers.forEach { (k, v) -> header(k, v) } }
        .build()

      okHttpClient.newCall(request).execute().use { response ->
        when (response.code) {
          200 -> {
            val info = json.decodeFromString<NetworkController.GetBackupInfoResponse>(response.body.string())
            RequestResult.Success(info)
          }
          400 -> RequestResult.NonSuccess(NetworkController.GetBackupInfoError.BadArguments(response.body.string()))
          401 -> RequestResult.NonSuccess(NetworkController.GetBackupInfoError.BadAuthCredential(response.body.string()))
          403 -> RequestResult.NonSuccess(NetworkController.GetBackupInfoError.Forbidden(response.body.string()))
          404 -> RequestResult.NonSuccess(NetworkController.GetBackupInfoError.NoBackup)
          429 -> RequestResult.NonSuccess(NetworkController.GetBackupInfoError.RateLimited(response.retryAfter()))
          else -> RequestResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}, body: ${response.body?.string()}"))
        }
      }
    } catch (e: IOException) {
      Log.w(TAG, "[getRemoteBackupInfo] IOException", e)
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      Log.w(TAG, "[getRemoteBackupInfo] Exception", e)
      RequestResult.ApplicationError(e)
    }
  }

  /**
   * Fetches an archive service credential for today by calling GET /v1/archives/auth on the authenticated channel.
   */
  private fun fetchArchiveServiceCredential(aci: String, password: String): ArchiveCredential? {
    val currentTime = System.currentTimeMillis()
    val roundedToNearestDay = currentTime.milliseconds.inWholeDays.days
    val endTime = roundedToNearestDay + 7.days
    val startSeconds = roundedToNearestDay.inWholeSeconds
    val endSeconds = endTime.inWholeSeconds

    val credentials = okhttp3.Credentials.basic(aci, password)
    val baseUrl = serviceConfiguration.signalServiceUrls[0].url
    val request = okhttp3.Request.Builder()
      .url("$baseUrl/v1/archives/auth?redemptionStartSeconds=$startSeconds&redemptionEndSeconds=$endSeconds")
      .get()
      .header("Authorization", credentials)
      .build()

    okHttpClient.newCall(request).execute().use { response ->
      if (response.code != 200) {
        Log.w(TAG, "[fetchArchiveServiceCredential] Unexpected response code: ${response.code}")
        return null
      }

      val body = response.body.string()
      val parsed = json.decodeFromString<ArchiveCredentialsResponse>(body)
      val todaySeconds = roundedToNearestDay.inWholeSeconds

      return parsed.credentials["messages"]?.firstOrNull { it.redemptionTime == todaySeconds }
    }
  }

  /**
   * Builds the ZK auth headers (X-Signal-ZK-Auth, X-Signal-ZK-Auth-Signature) needed for
   * anonymous archive requests.
   */
  private fun buildZkAuthHeaders(
    messageBackupKey: org.signal.core.models.backup.MessageBackupKey,
    aci: org.signal.core.models.ServiceId.ACI,
    credential: ArchiveCredential
  ): Map<String, String> {
    val backupServerPublicParams = GenericServerPublicParams(serviceConfiguration.backupServerPublicParams)
    val backupRequestContext = BackupAuthCredentialRequestContext.create(messageBackupKey.value, aci.rawUuid)
    val backupAuthCredentialResponse = BackupAuthCredentialResponse(Base64.decode(credential.credential))
    val backupAuthCredential = backupRequestContext.receiveResponse(
      backupAuthCredentialResponse,
      Instant.ofEpochSecond(credential.redemptionTime),
      backupServerPublicParams
    )

    val presentation = backupAuthCredential.present(backupServerPublicParams).serialize()
    val privateKey = messageBackupKey.deriveAnonymousCredentialPrivateKey(aci)
    val signedPresentation = privateKey.calculateSignature(presentation)

    return mapOf(
      "X-Signal-ZK-Auth" to Base64.encodeWithPadding(presentation),
      "X-Signal-ZK-Auth-Signature" to Base64.encodeWithPadding(signedPresentation)
    )
  }

  @Serializable
  private data class ArchiveCredentialsResponse(
    val credentials: Map<String, List<ArchiveCredential>>
  )

  @Serializable
  private data class ArchiveCredential(
    val credential: String,
    val redemptionTime: Long
  )

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

  private fun Response.retryAfter(): Duration {
    return this.header("Retry-After")?.toLongOrNull()?.seconds ?: 0.seconds
  }
}
