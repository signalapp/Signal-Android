/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.dependencies

import android.app.PendingIntent
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.models.ServiceId
import org.signal.core.models.backup.MessageBackupKey
import org.signal.core.util.Base64
import org.signal.core.util.Hex
import org.signal.core.util.SleepTimer
import org.signal.core.util.logging.Log
import org.signal.devicetransfer.DeviceToDeviceTransferService
import org.signal.libsignal.net.Network
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.zkgroup.GenericServerPublicParams
import org.signal.libsignal.zkgroup.VerificationFailedException
import org.signal.libsignal.zkgroup.backups.BackupAuthCredentialRequestContext
import org.signal.libsignal.zkgroup.backups.BackupAuthCredentialResponse
import org.signal.network.NetworkResult
import org.signal.network.api.LinkDeviceApi
import org.signal.network.service.StorageServiceService
import org.signal.registration.LinkAndSyncWaitResult
import org.signal.registration.NetworkController
import org.signal.registration.NetworkController.AccountAttributes
import org.signal.registration.NetworkController.CheckSvrCredentialsRequest
import org.signal.registration.NetworkController.CheckSvrCredentialsResponse
import org.signal.registration.NetworkController.CreateSessionError
import org.signal.registration.NetworkController.DeviceAttributes
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
import org.signal.registration.sample.MainActivity
import org.signal.registration.sample.fcm.FcmUtil
import org.signal.registration.sample.fcm.PushChallengeReceiver
import org.signal.registration.sample.storage.RegistrationPreferences
import org.whispersystems.signalservice.api.link.TransferArchiveResponse
import org.whispersystems.signalservice.api.provisioning.ProvisioningSocket
import org.whispersystems.signalservice.api.registration.RegistrationApi
import org.whispersystems.signalservice.api.storage.StorageServiceApi
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.BackupResponse
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.RestoreResponse
import org.whispersystems.signalservice.api.svr.SecureValueRecoveryV2
import org.whispersystems.signalservice.api.websocket.HealthMonitor
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.api.websocket.WebSocketFactory
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.crypto.SecondaryProvisioningCipher
import org.whispersystems.signalservice.internal.push.AuthCredentials
import org.whispersystems.signalservice.internal.push.ProvisionMessage
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider
import org.whispersystems.signalservice.internal.websocket.LibSignalChatConnection
import java.io.Closeable
import java.io.IOException
import java.time.Instant
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.whispersystems.signalservice.api.account.AccountAttributes as ServiceAccountAttributes
import org.whispersystems.signalservice.api.account.DeviceAttributes as ServiceDeviceAttributes
import org.whispersystems.signalservice.api.account.PreKeyCollection as ServicePreKeyCollection

class DemoNetworkController(
  private val context: android.content.Context,
  private val pushServiceSocket: PushServiceSocket,
  private val serviceConfiguration: SignalServiceConfiguration,
  private val svr2MrEnclave: String
) : NetworkController {

  companion object {
    private val TAG = Log.tag(DemoNetworkController::class)
    const val DEVICE_TRANSFER_NOTIFICATION_CHANNEL_ID = "device_transfer"
    private const val DEVICE_TRANSFER_NOTIFICATION_ID = 4321
    private const val USER_AGENT = "Signal-Android-Registration-Sample"
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

  override fun startNewDeviceTransferServer(context: android.content.Context, aep: AccountEntropyPool) {
    val pendingIntent = PendingIntent.getActivity(
      context,
      0,
      Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val notificationData = DeviceToDeviceTransferService.TransferNotificationData(
      DEVICE_TRANSFER_NOTIFICATION_ID,
      DEVICE_TRANSFER_NOTIFICATION_CHANNEL_ID,
      android.R.drawable.stat_sys_download
    )
    DeviceToDeviceTransferService.startServer(
      context,
      DemoDeviceTransferServerTask(aep.value),
      notificationData,
      pendingIntent
    )
  }

  override fun startLinkDeviceProvisioning(allowLinkAndSync: Boolean): Flow<NetworkController.LinkDeviceProvisioningEvent> = callbackFlow {
    val socketHandles = mutableListOf<Closeable>()

    fun startSocket() {
      val handle = ProvisioningSocket.start<ProvisionMessage>(
        mode = ProvisioningSocket.Mode.Link(linkAndSyncCapable = allowLinkAndSync),
        identityKeyPair = IdentityKeyPair.generate(),
        configuration = serviceConfiguration,
        handler = { id, t ->
          Log.w(TAG, "[startLinkDeviceProvisioning] Socket [$id] failed", t)
          trySend(NetworkController.LinkDeviceProvisioningEvent.Error(t))
        }
      ) { socket ->
        val url = socket.getProvisioningUrl()
        trySend(NetworkController.LinkDeviceProvisioningEvent.QrCodeReady(url))

        val result = socket.getProvisioningMessageDecryptResult()

        if (result is SecondaryProvisioningCipher.ProvisioningDecryptResult.Success) {
          val msg = result.message
          val aci = msg.aciBinary?.let { ServiceId.ACI.parseOrThrow(it) } ?: ServiceId.ACI.parseOrThrow(msg.aci)
          val pni = msg.pniBinary?.let { ServiceId.PNI.parseOrThrow(it) } ?: ServiceId.PNI.parseOrThrow(msg.pni)

          trySend(
            NetworkController.LinkDeviceProvisioningEvent.MessageReceived(
              NetworkController.LinkDeviceProvisioningMessage(
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
          trySend(NetworkController.LinkDeviceProvisioningEvent.Error(IOException("Failed to decrypt provisioning message")))
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
  ): RequestResult<NetworkController.LinkDeviceResponse, NetworkController.RegisterAsLinkedDeviceError> = withContext(Dispatchers.IO) {
    try {
      // The link endpoint authenticates via basic auth (e164:password) since this device has no ACI yet.
      val credentialsProvider = StaticCredentialsProvider(null, null, e164, 1, password)
      val linkSocket = PushServiceSocket(serviceConfiguration, credentialsProvider, USER_AGENT, true)

      val result = RegistrationApi(linkSocket).registerAsSecondaryDevice(
        provisioningCode,
        deviceAttributes.toServiceDeviceAttributes(),
        aciPreKeys.toServicePreKeyCollection(),
        pniPreKeys.toServicePreKeyCollection(),
        fcmToken
      )

      when (result) {
        is NetworkResult.Success -> {
          Log.i(TAG, "[registerAsLinkedDevice] Linked successfully (deviceId=${result.result.deviceId}).")
          RequestResult.Success(NetworkController.LinkDeviceResponse(deviceId = result.result.deviceId.toInt()))
        }
        is NetworkResult.ApplicationError -> RequestResult.ApplicationError(result.throwable)
        is NetworkResult.NetworkError<*> -> RequestResult.RetryableNetworkError(result.exception)
        is NetworkResult.StatusCodeError -> {
          Log.w(TAG, "[registerAsLinkedDevice] Status code error: ${result.code}")
          val error = when (result.code) {
            403 -> NetworkController.RegisterAsLinkedDeviceError.IncorrectVerification
            409 -> NetworkController.RegisterAsLinkedDeviceError.MissingCapability
            411 -> NetworkController.RegisterAsLinkedDeviceError.MaxLinkedDevices
            422 -> NetworkController.RegisterAsLinkedDeviceError.InvalidRequest(result.exception.message)
            429 -> NetworkController.RegisterAsLinkedDeviceError.RateLimited(result.retryAfter())
            else -> return@withContext RequestResult.ApplicationError(result.exception)
          }
          RequestResult.NonSuccess(error)
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "[registerAsLinkedDevice] Failed to register as linked device.", e)
      RequestResult.ApplicationError(e)
    }
  }

  override suspend fun onLinkedDeviceRegistered() {
    // The demo stops before importing the backup proto / setting up app state, so there is no real
    // post-registration housekeeping to do here. The account data is persisted via commitRegistrationData().
    Log.i(TAG, "[onLinkedDeviceRegistered] No-op in demo.")
  }

  override suspend fun restoreLinkedDeviceFromStorageService() {
    // The demo can do a real storage-service restore -- reuse the same account-record restore the
    // normal flow uses. It no-ops gracefully if credentials/master key aren't available.
    Log.i(TAG, "[restoreLinkedDeviceFromStorageService] Restoring account record from storage service...")
    when (val result = restoreAccountRecord(timeout = 30.seconds)) {
      is RequestResult.Success -> Log.i(TAG, "[restoreLinkedDeviceFromStorageService] Storage service restore complete.")
      else -> Log.w(TAG, "[restoreLinkedDeviceFromStorageService] Storage service restore did not complete: $result")
    }
  }

  override suspend fun awaitLinkAndSyncArchive(): LinkAndSyncWaitResult = withContext(Dispatchers.IO) {
    val response = awaitTransferArchive()
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
   * Connects an authenticated websocket as the linked device and long-polls (retrying up to ~1 hour, mirroring
   * Desktop) for the primary to make a transfer archive available. Returns the primary's response (which may carry
   * a [TransferArchiveResponse.error] instead of an archive), or null if the primary never responded in time.
   */
  private suspend fun awaitTransferArchive(): TransferArchiveResponse? {
    val aci = RegistrationPreferences.aci
    val pni = RegistrationPreferences.pni
    val e164 = RegistrationPreferences.e164
    val password = RegistrationPreferences.servicePassword
    val deviceId = RegistrationPreferences.linkedDeviceId

    if (aci == null || e164 == null || password == null || deviceId <= 0) {
      Log.w(TAG, "[awaitTransferArchive] Missing linked-device credentials.")
      return null
    }

    val network = Network(Network.Environment.STAGING, USER_AGENT, emptyMap(), Network.BuildVariant.PRODUCTION)
    val credentialsProvider = StaticCredentialsProvider(aci, pni, e164, deviceId, password)
    val healthMonitor = object : HealthMonitor {
      override fun onKeepAliveResponse(sentTimestamp: Long, isIdentifiedWebSocket: Boolean) {}
      override fun onMessageError(status: Int, isIdentifiedWebSocket: Boolean) {}
      override fun onReceivedAlerts(alerts: Array<out String>, isIdentifiedWebSocket: Boolean) {}
    }
    val libSignalConnection = LibSignalChatConnection(
      name = "LinkAndSync",
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

    return try {
      authWebSocket.connect()
      val deadline = System.currentTimeMillis() + 1.hours.inWholeMilliseconds
      var archive: TransferArchiveResponse? = null
      while (archive == null && System.currentTimeMillis() < deadline) {
        Log.i(TAG, "[awaitTransferArchive] Waiting for primary to provide a transfer archive...")
        when (val result = LinkDeviceApi(authWebSocket).waitForPrimaryDevice(timeout = 30.seconds)) {
          is NetworkResult.Success -> archive = result.result
          else -> Log.d(TAG, "[awaitTransferArchive] No archive yet; continuing to wait.")
        }
      }
      archive
    } finally {
      authWebSocket.disconnect()
    }
  }

  /** The device id for authenticated calls: the linked-device id if this is a secondary device, else 1 (primary). */
  private fun currentDeviceId(): Int = RegistrationPreferences.linkedDeviceId.takeIf { it > 0 } ?: 1

  /** Basic-auth username for authenticated REST calls. A secondary device must authenticate as "<aci>.<deviceId>". */
  private fun authUsername(aci: ServiceId.ACI): String {
    val deviceId = currentDeviceId()
    return if (deviceId != 1) "$aci.$deviceId" else aci.toString()
  }

  override fun startProvisioning(): Flow<ProvisioningEvent> = callbackFlow {
    val socketHandles = mutableListOf<Closeable>()

    fun startSocket() {
      val handle = ProvisioningSocket.start<RegistrationProvisionMessage>(
        mode = ProvisioningSocket.Mode.Rereg,
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
      val credentialsProvider = StaticCredentialsProvider(aci, pni, e164, currentDeviceId(), password)
      val healthMonitor = object : HealthMonitor {
        override fun onKeepAliveResponse(sentTimestamp: Long, isIdentifiedWebSocket: Boolean) {}
        override fun onMessageError(status: Int, isIdentifiedWebSocket: Boolean) {}
        override fun onReceivedAlerts(alerts: Array<out String>, isIdentifiedWebSocket: Boolean) {}
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

  override suspend fun enqueueSvrGuessResetJobIfPossible(): Boolean {
    if (RegistrationPreferences.pin == null) {
      return false
    }

    val pin = checkNotNull(RegistrationPreferences.pin) { "Pin is not set!" }
    val masterKey = checkNotNull(RegistrationPreferences.masterKey) { "Master key is not set!" }

    val result = setPinAndMasterKeyOnSvr(pin, masterKey)
    if (result !is RequestResult.Success) {
      Log.w(TAG, "Failed to set pin and master key on SVR! A real app would retry. Result: $result")
    }

    return true
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
      val credentials = okhttp3.Credentials.basic(authUsername(aci), password)
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
      val credentials = okhttp3.Credentials.basic(authUsername(aci), password)
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
      val credentials = okhttp3.Credentials.basic(authUsername(aci), password)
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
      val credentials = okhttp3.Credentials.basic(authUsername(aci), password)
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

  override suspend fun enqueueAccountAttributesSyncJob() = withContext(Dispatchers.IO) {
    val result = setAccountAttributes(buildCurrentAccountAttributes())
    if (result !is RequestResult.Success) {
      Log.w(TAG, "[enqueueAccountAttributesSyncJob] Failed to sync attributes: $result")
    }
  }

  override suspend fun setProfile(
    givenName: String,
    familyName: String,
    avatar: ByteArray?,
    discoverableByPhoneNumber: Boolean
  ): RequestResult<Unit, NetworkController.SetProfileError> = withContext(Dispatchers.IO) {
    val aci = RegistrationPreferences.aci
    if (aci == null) {
      Log.w(TAG, "[setProfile] Not registered.")
      return@withContext RequestResult.NonSuccess(NetworkController.SetProfileError.NotRegistered)
    }
    Log.i(TAG, "[setProfile] Pretending to upload profile (givenName=${givenName.length} chars, familyName=${familyName.length} chars, avatar=${avatar?.size ?: 0} bytes, discoverable=$discoverableByPhoneNumber).")
    RegistrationPreferences.profileGivenName = givenName
    RegistrationPreferences.profileFamilyName = familyName
    RegistrationPreferences.profileAvatar = avatar
    RegistrationPreferences.profileDiscoverableByPhoneNumber = discoverableByPhoneNumber
    RequestResult.Success(Unit)
  }

  override suspend fun restoreAccountRecord(
    timeout: kotlin.time.Duration
  ): RequestResult<Unit, NetworkController.RestoreAccountRecordError> = withContext(Dispatchers.IO) {
    val aci = RegistrationPreferences.aci
    val pni = RegistrationPreferences.pni
    val e164 = RegistrationPreferences.e164
    val password = RegistrationPreferences.servicePassword
    val masterKey = RegistrationPreferences.temporaryMasterKey ?: RegistrationPreferences.masterKey

    if (aci == null || pni == null || e164 == null || password == null || masterKey == null) {
      Log.w(TAG, "[restoreAccountRecord] Missing credentials or master key.")
      return@withContext RequestResult.Success(Unit)
    }

    val storageKey = masterKey.deriveStorageServiceKey()

    val network = Network(Network.Environment.STAGING, "Signal-Android-Registration-Sample", emptyMap(), Network.BuildVariant.PRODUCTION)
    val credentialsProvider = StaticCredentialsProvider(aci, pni, e164, currentDeviceId(), password)
    val healthMonitor = object : HealthMonitor {
      override fun onKeepAliveResponse(sentTimestamp: Long, isIdentifiedWebSocket: Boolean) {}
      override fun onMessageError(status: Int, isIdentifiedWebSocket: Boolean) {}
      override fun onReceivedAlerts(alerts: Array<out String>, isIdentifiedWebSocket: Boolean) {}
    }
    val libSignalConnection = LibSignalChatConnection(
      name = "Storage-Restore",
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

    try {
      withTimeout(timeout) {
        authWebSocket.connect()
        val storageService = StorageServiceService(StorageServiceApi(authWebSocket, pushServiceSocket))

        Log.i(TAG, "[restoreAccountRecord] Retrieving manifest...")
        val manifest = when (val result = storageService.getStorageManifest(storageKey)) {
          is StorageServiceService.ManifestResult.Success -> result.manifest
          is StorageServiceService.ManifestResult.NotFoundError -> {
            Log.w(TAG, "[restoreAccountRecord] No manifest found.")
            return@withTimeout RequestResult.Success(Unit)
          }
          is StorageServiceService.ManifestResult.DecryptionError -> {
            Log.w(TAG, "[restoreAccountRecord] Manifest decryption failed.", result.exception)
            return@withTimeout RequestResult.Success(Unit)
          }
          is StorageServiceService.ManifestResult.NetworkError -> return@withTimeout RequestResult.NonSuccess(NetworkController.RestoreAccountRecordError.IOError(result.exception))
          is StorageServiceService.ManifestResult.StatusCodeError -> return@withTimeout RequestResult.ApplicationError(result.exception)
        }

        val accountId = manifest.accountStorageId
        if (!accountId.isPresent) {
          Log.w(TAG, "[restoreAccountRecord] Manifest had no account record.")
          return@withTimeout RequestResult.Success(Unit)
        }

        Log.i(TAG, "[restoreAccountRecord] Retrieving account record...")
        val records = when (val result = storageService.readStorageRecords(storageKey, manifest.recordIkm, listOf(accountId.get()))) {
          is StorageServiceService.StorageRecordResult.Success -> result.records
          is StorageServiceService.StorageRecordResult.DecryptionError -> {
            Log.w(TAG, "[restoreAccountRecord] Account record decryption failed.", result.exception)
            return@withTimeout RequestResult.Success(Unit)
          }
          is StorageServiceService.StorageRecordResult.NetworkError -> return@withTimeout RequestResult.NonSuccess(NetworkController.RestoreAccountRecordError.IOError(result.exception))
          is StorageServiceService.StorageRecordResult.StatusCodeError -> return@withTimeout RequestResult.ApplicationError(result.exception)
        }

        val account = records.firstOrNull()?.proto?.account
        if (account == null) {
          Log.w(TAG, "[restoreAccountRecord] Storage record did not contain an account.")
          return@withTimeout RequestResult.Success(Unit)
        }

        RegistrationPreferences.profileGivenName = account.givenName
        RegistrationPreferences.profileFamilyName = account.familyName
        RegistrationPreferences.profileDiscoverableByPhoneNumber = !account.unlistedPhoneNumber
        Log.i(TAG, "[restoreAccountRecord] Restored profile: givenName=${account.givenName.length} chars, familyName=${account.familyName.length} chars, discoverable=${!account.unlistedPhoneNumber}, avatarUrlPath='${account.avatarUrlPath}' (not downloaded in demo).")

        RequestResult.Success(Unit)
      }
    } catch (e: TimeoutCancellationException) {
      Log.w(TAG, "[restoreAccountRecord] Timed out.")
      RequestResult.NonSuccess(NetworkController.RestoreAccountRecordError.Timeout)
    } catch (e: IOException) {
      Log.w(TAG, "[restoreAccountRecord] IOException", e)
      RequestResult.NonSuccess(NetworkController.RestoreAccountRecordError.IOError(e))
    } catch (e: Exception) {
      Log.w(TAG, "[restoreAccountRecord] Exception", e)
      RequestResult.ApplicationError(e)
    } finally {
      authWebSocket.disconnect()
    }
  }

  override suspend fun setRestoreMethod(token: String, method: NetworkController.RestoreMethod): RequestResult<Unit, NetworkController.SetRestoreMethodError> = withContext(Dispatchers.IO) {
    try {
      val baseUrl = serviceConfiguration.signalServiceUrls[0].url
      val body = json.encodeToString(SetRestoreMethodRequest.serializer(), SetRestoreMethodRequest(method))
        .toRequestBody("application/json".toMediaType())

      val request = okhttp3.Request.Builder()
        .url("$baseUrl/v1/devices/restore_account/${java.net.URLEncoder.encode(token, "UTF-8")}")
        .put(body)
        .build()

      okHttpClient.newCall(request).execute().use { response ->
        when (response.code) {
          200, 204 -> {
            Log.i(TAG, "[setRestoreMethod] Successfully reported restore method: $method")
            RequestResult.Success(Unit)
          }
          429 -> RequestResult.NonSuccess(NetworkController.SetRestoreMethodError.RateLimited(0.seconds))
          else -> RequestResult.NonSuccess(NetworkController.SetRestoreMethodError.InvalidRequest("HTTP ${response.code}: ${response.body.string()}"))
        }
      }
    } catch (e: IOException) {
      Log.w(TAG, "[setRestoreMethod] IOException", e)
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      Log.w(TAG, "[setRestoreMethod] Exception", e)
      RequestResult.ApplicationError(e)
    }
  }

  private fun buildCurrentAccountAttributes(): AccountAttributes {
    val aep = RegistrationPreferences.aep
    val registrationLock = if (RegistrationPreferences.registrationLockEnabled && aep != null) {
      aep.deriveMasterKey().deriveRegistrationLock()
    } else {
      null
    }
    val recoveryPassword = aep?.deriveMasterKey()?.deriveRegistrationRecoveryPassword()
    val profileKey = RegistrationPreferences.profileKey
    val unidentifiedAccessKey = profileKey?.let { deriveUnidentifiedAccessKey(it) }

    return AccountAttributes(
      signalingKey = null,
      registrationId = RegistrationPreferences.aciRegistrationId,
      fetchesMessages = RegistrationPreferences.fetchesMessages,
      registrationLock = registrationLock,
      unidentifiedAccessKey = unidentifiedAccessKey,
      unrestrictedUnidentifiedAccess = false,
      discoverableByPhoneNumber = false,
      capabilities = AccountAttributes.Capabilities(
        storage = !RegistrationPreferences.pinsOptedOut,
        versionedExpirationTimer = true,
        attachmentBackfill = true,
        spqr = true,
        usernameChangeSyncMessage = true
      ),
      pniRegistrationId = RegistrationPreferences.pniRegistrationId,
      recoveryPassword = recoveryPassword
    )
  }

  private fun deriveUnidentifiedAccessKey(profileKey: org.signal.libsignal.zkgroup.profiles.ProfileKey): ByteArray {
    val nonce = ByteArray(12)
    val input = ByteArray(16)
    val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(profileKey.serialize(), "AES"), javax.crypto.spec.GCMParameterSpec(128, nonce))
    return cipher.doFinal(input).copyOf(16)
  }

  override suspend fun getRemoteBackupInfo(aep: AccountEntropyPool): RequestResult<NetworkController.GetBackupInfoResponse, NetworkController.GetBackupInfoError> = withContext(Dispatchers.IO) {
    val aci = RegistrationPreferences.aci
    val password = RegistrationPreferences.servicePassword

    if (aci == null || password == null) {
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
    messageBackupKey: MessageBackupKey,
    aci: ServiceId.ACI,
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

  override suspend fun verifyBackupKeyAssociatedWithAccount(aep: AccountEntropyPool): RequestResult<Unit, NetworkController.VerifyBackupKeyError> = withContext(Dispatchers.IO) {
    when (val result = getRemoteBackupInfo(aep)) {
      is RequestResult.Success -> RequestResult.Success(Unit)
      is RequestResult.NonSuccess -> when (val error = result.error) {
        is NetworkController.GetBackupInfoError.NoBackup -> RequestResult.NonSuccess(NetworkController.VerifyBackupKeyError.NoBackup)
        is NetworkController.GetBackupInfoError.RateLimited -> RequestResult.NonSuccess(NetworkController.VerifyBackupKeyError.RateLimited(error.retryAfter))
        else -> RequestResult.NonSuccess(NetworkController.VerifyBackupKeyError.IncorrectKey)
      }
      is RequestResult.RetryableNetworkError -> RequestResult.RetryableNetworkError(result.networkError)
      is RequestResult.ApplicationError -> {
        if (result.cause is VerificationFailedException) {
          RequestResult.NonSuccess(NetworkController.VerifyBackupKeyError.IncorrectKey)
        } else {
          RequestResult.ApplicationError(result.cause)
        }
      }
    }
  }

  override suspend fun getBackupFileLastModified(
    aep: AccountEntropyPool,
    backupInfo: NetworkController.GetBackupInfoResponse
  ): RequestResult<Long, NetworkController.GetBackupInfoError> = withContext(Dispatchers.IO) {
    val aci = RegistrationPreferences.aci
    val password = RegistrationPreferences.servicePassword
    val cdn = backupInfo.cdn
    val backupDir = backupInfo.backupDir
    val backupName = backupInfo.backupName

    if (aci == null || password == null) {
      return@withContext RequestResult.ApplicationError(IllegalStateException("Credentials not available"))
    }

    if (cdn == null || backupDir == null || backupName == null) {
      return@withContext RequestResult.ApplicationError(IllegalStateException("Backup info incomplete"))
    }

    try {
      val messageBackupKey = aep.deriveMessageBackupKey()
      val credential = fetchArchiveServiceCredential(aci.toString(), password)
        ?: return@withContext RequestResult.ApplicationError(IllegalStateException("Failed to fetch archive credentials"))

      val zkHeaders = buildZkAuthHeaders(messageBackupKey, aci, credential)

      val cdnCredentials = fetchCdnReadCredentials(cdn, zkHeaders)
        ?: return@withContext RequestResult.ApplicationError(IllegalStateException("Failed to fetch CDN read credentials"))

      val cdnUrls = serviceConfiguration.signalCdnUrlMap[cdn]
        ?: return@withContext RequestResult.ApplicationError(IllegalStateException("No CDN URL for CDN $cdn"))

      val cdnUrl = cdnUrls[0].url
      val request = okhttp3.Request.Builder()
        .url("$cdnUrl/backups/$backupDir/$backupName")
        .head()
        .apply { cdnCredentials.forEach { (k, v) -> header(k, v) } }
        .build()

      okHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          return@withContext RequestResult.ApplicationError(IllegalStateException("CDN HEAD failed: ${response.code}"))
        }

        val lastModified = response.header("Last-Modified")
          ?: return@withContext RequestResult.ApplicationError(IllegalStateException("No Last-Modified header"))

        val dateTime = java.time.ZonedDateTime.parse(lastModified, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
        RequestResult.Success(dateTime.toInstant().toEpochMilli())
      }
    } catch (e: IOException) {
      Log.w(TAG, "[getBackupFileLastModified] IOException", e)
      RequestResult.RetryableNetworkError(e)
    } catch (e: Exception) {
      Log.w(TAG, "[getBackupFileLastModified] Exception", e)
      RequestResult.ApplicationError(e)
    }
  }

  /**
   * Fetches CDN read credentials via GET /v1/archives/auth/read with ZK auth headers.
   */
  private fun fetchCdnReadCredentials(cdn: Int, zkHeaders: Map<String, String>): Map<String, String>? {
    val baseUrl = serviceConfiguration.signalServiceUrls[0].url
    val request = okhttp3.Request.Builder()
      .url("$baseUrl/v1/archives/auth/read?cdn=$cdn")
      .get()
      .apply { zkHeaders.forEach { (k, v) -> header(k, v) } }
      .build()

    okHttpClient.newCall(request).execute().use { response ->
      if (response.code != 200) {
        Log.w(TAG, "[fetchCdnReadCredentials] Unexpected response code: ${response.code}")
        return null
      }

      val body = response.body.string()
      val parsed = json.decodeFromString<CdnReadCredentialsResponse>(body)
      return parsed.headers
    }
  }

  @Serializable
  private data class CdnReadCredentialsResponse(
    val headers: Map<String, String>
  )

  @Serializable
  private data class ArchiveCredentialsResponse(
    val credentials: Map<String, List<ArchiveCredential>>
  )

  @Serializable
  private data class ArchiveCredential(
    val credential: String,
    val redemptionTime: Long
  )

  @Serializable
  private data class SetRestoreMethodRequest(val method: NetworkController.RestoreMethod)

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

  private fun Response.retryAfter(): Duration {
    return this.header("Retry-After")?.toLongOrNull()?.seconds ?: 0.seconds
  }
}
