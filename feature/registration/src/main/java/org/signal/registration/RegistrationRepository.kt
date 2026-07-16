/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.Manifest
import android.annotation.SuppressLint
import android.app.backup.BackupManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString
import org.signal.archive.LocalBackupRestoreProgress
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.core.util.Base64
import org.signal.core.util.Hex
import org.signal.core.util.Util
import org.signal.core.util.crypto.DeviceNameCipher
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.signal.registration.NetworkController.AccountAttributes
import org.signal.registration.NetworkController.CreateSessionError
import org.signal.registration.NetworkController.DeviceAttributes
import org.signal.registration.NetworkController.MasterKeyResponse
import org.signal.registration.NetworkController.PreKeyCollection
import org.signal.registration.NetworkController.ProvisioningEvent
import org.signal.registration.NetworkController.RegisterAccountError
import org.signal.registration.NetworkController.RegisterAccountResponse
import org.signal.registration.NetworkController.RequestVerificationCodeError
import org.signal.registration.NetworkController.RestoreMasterKeyError
import org.signal.registration.NetworkController.SessionMetadata
import org.signal.registration.NetworkController.SvrCredentials
import org.signal.registration.NetworkController.UpdateSessionError
import org.signal.registration.proto.AccountData
import org.signal.registration.proto.LinkedDeviceData
import org.signal.registration.proto.ProvisioningData
import org.signal.registration.proto.SvrCredential
import org.signal.registration.screens.countrycode.CountryUtils
import org.signal.registration.screens.localbackuprestore.LocalBackupInfo
import org.signal.registration.screens.messagesync.LinkAndSyncProgress
import org.signal.registration.screens.remotebackuprestore.RemoteBackupRestoreProgress
import org.signal.registration.util.SensitiveLog
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RegistrationRepository(val context: Context, val networkController: NetworkController, val storageController: StorageController, val isLinkAndSyncAvailable: Boolean) {

  companion object {
    private val TAG = Log.tag(RegistrationRepository::class)
    private val json = Json { ignoreUnknownKeys = true }
  }

  suspend fun createSession(e164: String): RequestResult<SessionMetadata, CreateSessionError> = withContext(Dispatchers.IO) {
    val fcmToken = networkController.getFcmToken()
    networkController.createSession(
      e164 = e164,
      fcmToken = fcmToken,
      mcc = null,
      mnc = null
    )
  }

  suspend fun requestVerificationCode(
    sessionId: String,
    smsAutoRetrieveCodeSupported: Boolean,
    transport: NetworkController.VerificationCodeTransport
  ): RequestResult<SessionMetadata, RequestVerificationCodeError> = withContext(Dispatchers.IO) {
    networkController.requestVerificationCode(
      sessionId = sessionId,
      locale = Locale.getDefault(),
      androidSmsRetrieverSupported = smsAutoRetrieveCodeSupported,
      transport = transport
    )
  }

  /**
   * Starts the Play Services SMS retriever so an incoming verification code can be automatically entered.
   *
   * The listener [lives for 5 minutes](https://developers.google.com/android/reference/com/google/android/gms/auth/api/phone/SmsRetrieverApi).
   * Callers should pass the result as `smsAutoRetrieveCodeSupported` when requesting a code so the server formats the
   * SMS for retrieval.
   *
   * @return whether the Play Services SMS retriever was successfully started.
   */
  suspend fun registerSmsListener(): Boolean {
    Log.d(TAG, "Attempting to start verification code SMS retriever.")
    val started = withTimeoutOrNull(5.seconds.inWholeMilliseconds) {
      try {
        SmsRetriever.getClient(context).startSmsRetriever().await()
        Log.d(TAG, "Successfully started verification code SMS retriever.")
        true
      } catch (ex: Exception) {
        Log.w(TAG, "Could not start verification code SMS retriever due to exception.", ex)
        false
      }
    }

    if (started == null) {
      Log.w(TAG, "Could not start verification code SMS retriever due to timeout.")
    }

    return started == true
  }

  fun getCaptchaUrl(): String = networkController.getCaptchaUrl()

  suspend fun submitCaptchaToken(
    sessionId: String,
    captchaToken: String
  ): RequestResult<SessionMetadata, UpdateSessionError> = withContext(Dispatchers.IO) {
    networkController.updateSession(
      sessionId = sessionId,
      pushChallengeToken = null,
      captchaToken = captchaToken
    )
  }

  suspend fun awaitPushChallengeToken(): String? = withContext(Dispatchers.IO) {
    networkController.awaitPushChallengeToken()
  }

  suspend fun submitPushChallengeToken(
    sessionId: String,
    pushChallengeToken: String
  ): RequestResult<SessionMetadata, UpdateSessionError> = withContext(Dispatchers.IO) {
    networkController.updateSession(
      sessionId = sessionId,
      pushChallengeToken = pushChallengeToken,
      captchaToken = null
    )
  }

  suspend fun submitVerificationCode(
    sessionId: String,
    verificationCode: String
  ): RequestResult<SessionMetadata, NetworkController.SubmitVerificationCodeError> = withContext(Dispatchers.IO) {
    networkController.submitVerificationCode(
      sessionId = sessionId,
      verificationCode = verificationCode
    )
  }

  suspend fun getSvrCredentials(): RequestResult<SvrCredentials, NetworkController.GetSvrCredentialsError> = withContext(Dispatchers.IO) {
    networkController.getSvrCredentials().also {
      if (it is RequestResult.Success) {
        storageController.updateInProgressRegistrationData {
          svrCredentials = svrCredentials + SvrCredential(username = it.result.username, password = it.result.password)
        }
        BackupManager(context).dataChanged()
      }
    }
  }

  /**
   * Determines the region code to default the country picker to. In priority order:
   * 1. The region of the device's own phone number, if the phone permission is granted and the number is readable.
   * 2. The network operator's country.
   * 3. The SIM's home country.
   * 4. A best-guess region derived from the device locale.
   * 5. US, as a last resort.
   */
  fun getDefaultRegionCode(): String {
    return deviceNumberRegionCode()
      ?: Util.getNetworkCountryIso(context).takeIfValidRegion()
      ?: Util.getSimCountryIso(context).orElse(null).takeIfValidRegion()
      ?: CountryUtils.localeToRegionCode(Locale.getDefault()).takeIfValidRegion()
      ?: run {
        Log.w(TAG, "No usable region from telephony or locale. Defaulting to US.")
        "US"
      }
  }

  @SuppressLint("MissingPermission")
  private fun deviceNumberRegionCode(): String? {
    val hasPhonePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED

    if (!hasPhonePermission) {
      return null
    }

    val deviceNumber = Util.getDeviceNumber(context).orElse(null) ?: return null
    val phoneNumberUtil = PhoneNumberUtil.getInstance()
    return (phoneNumberUtil.getRegionCodeForNumber(deviceNumber) ?: phoneNumberUtil.getRegionCodeForCountryCode(deviceNumber.countryCode)).takeIfValidRegion()
  }

  private fun String?.takeIfValidRegion(): String? {
    return this?.takeIf { it.isNotEmpty() && PhoneNumberUtil.getInstance().getCountryCodeForRegion(it) != 0 }
  }

  suspend fun getRestoredSvrCredentials(): List<SvrCredentials> = withContext(Dispatchers.IO) {
    val data = storageController.readInProgressRegistrationData()
    data.svrCredentials.map { SvrCredentials(username = it.username, password = it.password) }
  }

  suspend fun checkSvrCredentials(e164: String, credentials: List<SvrCredentials>): RequestResult<NetworkController.CheckSvrCredentialsResponse, NetworkController.CheckSvrCredentialsError> = withContext(Dispatchers.IO) {
    networkController.checkSvrCredentials(e164, credentials)
  }

  suspend fun restoreMasterKeyFromSvr(
    svrCredentials: SvrCredentials,
    pin: String,
    forRegistrationLock: Boolean
  ): RequestResult<MasterKeyResponse, RestoreMasterKeyError> = withContext(Dispatchers.IO) {
    networkController.restoreMasterKeyFromSvr(
      svrCredentials = svrCredentials,
      pin = pin
    ).also {
      if (it is RequestResult.Success) {
        storageController.updateInProgressRegistrationData {
          this.pin = pin
          this.masterKeyForInitialDataRestore = it.result.masterKey.serialize().toByteString()
          this.registrationLockEnabled = forRegistrationLock
          this.svrCredentials += SvrCredential(username = svrCredentials.username, password = svrCredentials.password)
        }
        storageController.commitRegistrationData()
      }
    }
  }

  /**
   * See [NetworkController.enqueueSvrGuessResetJobIfPossible]
   */
  suspend fun enqueueSvrResetGuessCountJob() {
    check(networkController.enqueueSvrGuessResetJobIfPossible()) { "Failed to enqueue SVR guess! Should not happen in this flow." }
  }

  /**
   * Registers a new account using a recovery password derived from the user's [MasterKey].
   *
   * This method:
   * 1. Generates and stores all required cryptographic key material
   * 2. Creates account attributes with registration IDs and capabilities
   * 3. Calls the network controller to register the account
   * 4. On success, saves the registration data to persistent storage
   *
   * @param e164 The phone number in E.164 format (used for basic auth)
   * @param recoveryPassword The recovery password, derived from the user's [MasterKey], which allows us to forgo session creation.
   * @param registrationLock The registration lock token derived from the master key, if unlocking a reglocked account. Must be null if the account is not reglocked.
   * @param skipDeviceTransfer Whether to skip device transfer flow
   * @return The registration result containing account information or an error
   */
  suspend fun registerAccountWithRecoveryPassword(
    e164: String,
    recoveryPassword: String,
    registrationLock: String? = null,
    skipDeviceTransfer: Boolean = true,
    preExistingRegistrationData: PreExistingRegistrationData? = null,
    existingAccountEntropyPool: AccountEntropyPool? = null
  ): RequestResult<Pair<RegisterAccountResponse, KeyMaterial>, RegisterAccountError> = withContext(Dispatchers.IO) {
    registerAccount(
      e164 = e164,
      sessionId = null,
      recoveryPassword = recoveryPassword,
      registrationLock = registrationLock,
      skipDeviceTransfer = skipDeviceTransfer,
      existingAccountEntropyPool = existingAccountEntropyPool ?: preExistingRegistrationData?.aep,
      existingAciIdentityKeyPair = preExistingRegistrationData?.aciIdentityKeyPair,
      existingPniIdentityKeyPair = preExistingRegistrationData?.pniIdentityKeyPair,
      unrestrictedUnidentifiedAccess = preExistingRegistrationData?.unrestrictedUnidentifiedAccess ?: false
    )
  }

  /**
   * Registers a new account after successful phone number verification.
   *
   * This method:
   * 1. Generates and stores all required cryptographic key material
   * 2. Creates account attributes with registration IDs and capabilities
   * 3. Calls the network controller to register the account
   * 4. On success, saves the registration data to persistent storage
   *
   * @param e164 The phone number in E.164 format (used for basic auth)
   * @param sessionId The verified session ID from phone number verification
   * @param registrationLock The registration lock token derived from the master key (if unlocking a reglocked account)
   * @param skipDeviceTransfer Whether to skip device transfer flow
   * @return The registration result containing account information or an error
   */
  suspend fun registerAccountWithSession(
    e164: String,
    sessionId: String,
    registrationLock: String? = null,
    skipDeviceTransfer: Boolean = true
  ): RequestResult<Pair<RegisterAccountResponse, KeyMaterial>, RegisterAccountError> = withContext(Dispatchers.IO) {
    registerAccount(e164, sessionId, recoveryPassword = null, registrationLock, skipDeviceTransfer)
  }

  /**
   * Starts a provisioning session for QR-based quick restore.
   * See [NetworkController.startProvisioning].
   */
  fun startProvisioning(): Flow<ProvisioningEvent> {
    return networkController.startProvisioning()
  }

  /**
   * Starts a provisioning session for QR-based device linking.
   * See [NetworkController.startLinkDeviceProvisioning].
   */
  fun startLinkDeviceProvisioning(): Flow<NetworkController.LinkDeviceProvisioningEvent> = flow {
    emitAll(networkController.startLinkDeviceProvisioning(allowLinkAndSync = isCleanStart()))
  }

  /**
   * True if this device is linked to a different account (different ACI) than the one advertised in
   * [message]. Returns false for a fresh (never-registered) device.
   */
  suspend fun isProvisioningForDifferentAccount(message: NetworkController.LinkDeviceProvisioningMessage): Boolean {
    val previousAci = storageController.getPreExistingRegistrationData()?.aci ?: return false
    return previousAci != ACI.parseOrThrow(message.aci)
  }

  /**
   * True if this device has no pre-existing registration (a fresh, never-registered device).
   */
  suspend fun isCleanStart(): Boolean {
    return storageController.getPreExistingRegistrationData() == null
  }

  /**
   * Registers this device as a linked (secondary) device using data from the primary.
   * See [NetworkController.registerAsLinkedDevice].
   */
  suspend fun registerAsLinkedDevice(
    message: NetworkController.LinkDeviceProvisioningMessage,
    deviceName: String
  ): RequestResult<LinkedDeviceResult, NetworkController.RegisterAsLinkedDeviceError> = withContext(Dispatchers.IO) {
    checkNotNull(message.accountEntropyPool) { "Link provisioning message missing account entropy pool" }

    val e164 = message.e164
    val accountEntropyPool = AccountEntropyPool(message.accountEntropyPool)
    val aci = ACI.parseOrThrow(message.aci)
    val pni = PNI.parseOrThrow(message.pni)
    val aciIdentityKeyPair = message.aciIdentityKeyPair
    val pniIdentityKeyPair = message.pniIdentityKeyPair
    val profileKey = ProfileKey(message.profileKey)
    val provisioningCode = message.provisioningCode

    val keyMaterial = generateKeyMaterial(
      existingAccountEntropyPool = accountEntropyPool,
      existingAciIdentityKeyPair = aciIdentityKeyPair,
      existingPniIdentityKeyPair = pniIdentityKeyPair,
      profileKey = profileKey
    )

    storageController.updateInProgressRegistrationData {
      this.profileKey = keyMaterial.profileKey.toByteString()
      this.accountEntropyPool = keyMaterial.accountEntropyPool.value
    }
    updateAccountData {
      this.aciIdentityKeyPair = keyMaterial.aciIdentityKeyPair.serialize().toByteString()
      this.pniIdentityKeyPair = keyMaterial.pniIdentityKeyPair.serialize().toByteString()
      this.aciSignedPreKey = keyMaterial.aciSignedPreKey.serialize().toByteString()
      this.pniSignedPreKey = keyMaterial.pniSignedPreKey.serialize().toByteString()
      this.aciLastResortKyberPreKey = keyMaterial.aciLastResortKyberPreKey.serialize().toByteString()
      this.pniLastResortKyberPreKey = keyMaterial.pniLastResortKyberPreKey.serialize().toByteString()
      this.aciRegistrationId = keyMaterial.aciRegistrationId
      this.pniRegistrationId = keyMaterial.pniRegistrationId
      this.unidentifiedAccessKey = keyMaterial.unidentifiedAccessKey.toByteString()
      this.servicePassword = keyMaterial.servicePassword
    }

    val fcmToken = networkController.getFcmToken()

    updateAccountData {
      this.fetchesMessages = fcmToken == null
    }

    val encryptedDeviceName = DeviceNameCipher.encryptDeviceName(deviceName.toByteArray(StandardCharsets.UTF_8), aciIdentityKeyPair)

    val deviceAttributes = DeviceAttributes(
      fetchesMessages = fcmToken == null,
      registrationId = keyMaterial.aciRegistrationId,
      pniRegistrationId = keyMaterial.pniRegistrationId,
      name = Base64.encodeWithPadding(encryptedDeviceName),
      capabilities = getAccountCapabilities()
    )

    val aciPreKeys = PreKeyCollection(
      identityKey = keyMaterial.aciIdentityKeyPair.publicKey,
      signedPreKey = keyMaterial.aciSignedPreKey,
      lastResortKyberPreKey = keyMaterial.aciLastResortKyberPreKey
    )

    val pniPreKeys = PreKeyCollection(
      identityKey = keyMaterial.pniIdentityKeyPair.publicKey,
      signedPreKey = keyMaterial.pniSignedPreKey,
      lastResortKyberPreKey = keyMaterial.pniLastResortKyberPreKey
    )

    val result = networkController.registerAsLinkedDevice(
      e164 = e164,
      password = keyMaterial.servicePassword,
      provisioningCode = provisioningCode,
      deviceAttributes = deviceAttributes,
      aciPreKeys = aciPreKeys,
      pniPreKeys = pniPreKeys,
      fcmToken = fcmToken
    )

    if (result is RequestResult.Success) {
      updateAccountData {
        this.e164 = e164
        this.aci = aci.toString()
        this.pni = pni.toString()
        this.linkedDeviceData = LinkedDeviceData(
          deviceId = result.result.deviceId.toInt(),
          deviceName = deviceName,
          ephemeralBackupKey = message.ephemeralBackupKey,
          mediaRootBackupKey = message.mediaRootBackupKey,
          readReceipts = message.readReceipts
        )
      }

      // Commits the account locally (writes keys, identity, the LinkedDeviceInfo and read-receipts pref built from linkedDeviceData).
      storageController.commitRegistrationData()

      // Network post-registration work: refresh remote config and request the initial sync from the primary.
      // The link-and-sync backup restore and storage-service restore are sequenced separately by the caller.
      networkController.onLinkedDeviceRegistered()
    }

    result.map { LinkedDeviceResult(hasLinkAndSyncBackup = message.ephemeralBackupKey != null) }
  }

  /**
   * Restores the link-and-sync message backup made available by the primary device, surfacing progress.
   * See [StorageController.restoreLinkAndSyncBackup].
   */
  fun restoreLinkAndSyncBackup(): Flow<LinkAndSyncProgress> = flow {
    emit(LinkAndSyncProgress.Waiting)
    when (val result = networkController.awaitLinkAndSyncArchive()) {
      is LinkAndSyncWaitResult.ArchiveAvailable -> emitAll(storageController.restoreLinkAndSyncBackup(result.cdn, result.key))
      LinkAndSyncWaitResult.ContinueWithoutBackup -> {
        Log.w(TAG, "[restoreLinkAndSyncBackup] Primary declined to provide a backup; continuing without one.")
        emit(LinkAndSyncProgress.Complete)
      }
      LinkAndSyncWaitResult.RelinkRequired -> {
        Log.w(TAG, "[restoreLinkAndSyncBackup] Primary requested re-link; local registration is invalid.")
        emit(LinkAndSyncProgress.RelinkRequired)
      }
    }
  }

  /**
   * Waits for the primary to make a link-and-sync archive available.
   */
  suspend fun awaitLinkAndSyncArchive(): LinkAndSyncWaitResult = withContext(Dispatchers.IO) {
    val ephemeralBackupKey = storageController.readInProgressRegistrationData().accountData?.linkedDeviceData?.ephemeralBackupKey
    if (ephemeralBackupKey == null) {
      Log.i(TAG, "[awaitLinkAndSyncArchive] No ephemeral backup key in registration data; no archive expected.")
      return@withContext LinkAndSyncWaitResult.ContinueWithoutBackup
    }
    networkController.awaitLinkAndSyncArchive()
  }

  /**
   * Restores account data from the storage service after linking. Call immediately after linking when there
   * is no link-and-sync backup, or only after the backup has been applied when there is one.
   *
   * See [NetworkController.restoreLinkedDeviceFromStorageService].
   */
  suspend fun restoreLinkedDeviceFromStorageService() = withContext(Dispatchers.IO) {
    networkController.restoreLinkedDeviceFromStorageService()
    setRestoreDecision(RestoreDecision.NEW_ACCOUNT)
  }

  /**
   * Reports the user's chosen restore method to the server so the old (quick-restore) device's UI can update.
   * See [NetworkController.setRestoreMethod].
   */
  suspend fun setRestoreMethod(
    token: String,
    method: NetworkController.RestoreMethod
  ): RequestResult<Unit, NetworkController.SetRestoreMethodError> = withContext(Dispatchers.IO) {
    networkController.setRestoreMethod(token, method)
  }

  /**
   * Registers an account using data received from the old device via QR provisioning.
   *
   * This method:
   * 1. Saves provisioning metadata (restore token, backup info) to storage
   * 2. Re-uses the identity key pairs and AEP from the old device
   * 3. Derives the recovery password from the provisioned AEP
   * 4. Registers the account
   *
   * @param provideRegistrationLock Whether to include the reglock token derived from the provisioned AEP, if unlocking a reglocked account.
   */
  suspend fun registerAccountWithProvisioningData(
    provisioningMessage: NetworkController.ProvisioningMessage,
    provideRegistrationLock: Boolean = false
  ): RequestResult<Pair<RegisterAccountResponse, KeyMaterial>, RegisterAccountError> = withContext(Dispatchers.IO) {
    storageController.updateInProgressRegistrationData {
      provisioningData = ProvisioningData(
        restoreMethodToken = provisioningMessage.restoreMethodToken,
        platform = when (provisioningMessage.platform) {
          NetworkController.ProvisioningMessage.Platform.ANDROID -> ProvisioningData.Platform.ANDROID
          NetworkController.ProvisioningMessage.Platform.IOS -> ProvisioningData.Platform.IOS
        },
        tier = when (provisioningMessage.tier) {
          NetworkController.ProvisioningMessage.Tier.FREE -> ProvisioningData.Tier.FREE
          NetworkController.ProvisioningMessage.Tier.PAID -> ProvisioningData.Tier.PAID
          null -> ProvisioningData.Tier.TIER_UNKNOWN
        },
        backupTimestampMs = provisioningMessage.backupTimestampMs ?: 0,
        backupSizeBytes = provisioningMessage.backupSizeBytes ?: 0,
        backupVersion = provisioningMessage.backupVersion
      )
      pin = provisioningMessage.pin ?: ""
    }

    val aep = AccountEntropyPool(provisioningMessage.accountEntropyPool)
    val masterKey = aep.deriveMasterKey()
    val recoveryPassword = masterKey.deriveRegistrationRecoveryPassword()

    registerAccount(
      e164 = provisioningMessage.e164,
      sessionId = null,
      recoveryPassword = recoveryPassword,
      registrationLock = masterKey.deriveRegistrationLock().takeIf { provideRegistrationLock },
      skipDeviceTransfer = true,
      existingAccountEntropyPool = aep,
      existingAciIdentityKeyPair = provisioningMessage.aciIdentityKeyPair,
      existingPniIdentityKeyPair = provisioningMessage.pniIdentityKeyPair
    )
  }

  /**
   * Registers a new account.
   *
   * This method:
   * 1. Generates and stores all required cryptographic key material
   * 2. Creates account attributes with registration IDs and capabilities
   * 3. Calls the network controller to register the account
   * 4. On success, saves the registration data to persistent storage
   *
   * @param e164 The phone number in E.164 format (used for basic auth)
   * @param sessionId The verified session ID from phone number verification. Must provide if you're not using [recoveryPassword].
   * @param recoveryPassword The recovery password, derived from the user's [MasterKey], which allows us to forgo session creation. Must provide if you're not using [sessionId].
   * @param registrationLock The registration lock token derived from the master key (if unlocking a reglocked account). Important: if you provide this, the user will be registered with reglock enabled.
   * @param skipDeviceTransfer Whether to skip device transfer flow
   * @param preExistingRegistrationData If present, we will use the pre-existing key material from this pre-existing registration rather than generating new key material.
   * @return The registration result containing account information or an error
   */
  private suspend fun registerAccount(
    e164: String,
    sessionId: String?,
    recoveryPassword: String?,
    registrationLock: String? = null,
    skipDeviceTransfer: Boolean = true,
    existingAccountEntropyPool: AccountEntropyPool? = null,
    existingAciIdentityKeyPair: IdentityKeyPair? = null,
    existingPniIdentityKeyPair: IdentityKeyPair? = null,
    unrestrictedUnidentifiedAccess: Boolean = false
  ): RequestResult<Pair<RegisterAccountResponse, KeyMaterial>, RegisterAccountError> = withContext(Dispatchers.IO) {
    check(sessionId != null || recoveryPassword != null) { "Either sessionId or recoveryPassword must be provided" }
    check(sessionId == null || recoveryPassword == null) { "Either sessionId or recoveryPassword must be provided, but not both" }

    Log.i(TAG, "[registerAccount] Starting registration for $e164. sessionId: ${sessionId != null}, recoveryPassword: ${recoveryPassword != null}, registrationLock: ${registrationLock != null}, skipDeviceTransfer: $skipDeviceTransfer, existingAep: ${existingAccountEntropyPool != null}")

    val inProgressData = storageController.readInProgressRegistrationData()
    val resumedAciIdentityKeyPair = inProgressData.accountData?.aciIdentityKeyPair?.takeIf { it.size > 0 }?.let { IdentityKeyPair(it.toByteArray()) }
    val resumedPniIdentityKeyPair = inProgressData.accountData?.pniIdentityKeyPair?.takeIf { it.size > 0 }?.let { IdentityKeyPair(it.toByteArray()) }
    val resumedProfileKey = inProgressData.profileKey.takeIf { it.size > 0 }?.let { ProfileKey(it.toByteArray()) }

    val keyMaterial = generateKeyMaterial(
      existingAccountEntropyPool = existingAccountEntropyPool,
      existingAciIdentityKeyPair = existingAciIdentityKeyPair ?: resumedAciIdentityKeyPair,
      existingPniIdentityKeyPair = existingPniIdentityKeyPair ?: resumedPniIdentityKeyPair,
      profileKey = resumedProfileKey
    )

    storageController.updateInProgressRegistrationData {
      this.profileKey = keyMaterial.profileKey.toByteString()
      this.accountEntropyPool = keyMaterial.accountEntropyPool.value
    }
    updateAccountData {
      this.aciIdentityKeyPair = keyMaterial.aciIdentityKeyPair.serialize().toByteString()
      this.pniIdentityKeyPair = keyMaterial.pniIdentityKeyPair.serialize().toByteString()
      this.aciSignedPreKey = keyMaterial.aciSignedPreKey.serialize().toByteString()
      this.pniSignedPreKey = keyMaterial.pniSignedPreKey.serialize().toByteString()
      this.aciLastResortKyberPreKey = keyMaterial.aciLastResortKyberPreKey.serialize().toByteString()
      this.pniLastResortKyberPreKey = keyMaterial.pniLastResortKyberPreKey.serialize().toByteString()
      this.aciRegistrationId = keyMaterial.aciRegistrationId
      this.pniRegistrationId = keyMaterial.pniRegistrationId
      this.unidentifiedAccessKey = keyMaterial.unidentifiedAccessKey.toByteString()
      this.servicePassword = keyMaterial.servicePassword
    }

    val fcmToken = networkController.getFcmToken()

    updateAccountData {
      this.fetchesMessages = fcmToken == null
    }

    val newMasterKey = keyMaterial.accountEntropyPool.deriveMasterKey()
    val newRecoveryPassword = newMasterKey.deriveRegistrationRecoveryPassword()

    SensitiveLog.d(TAG, "[registerAccount] Using master key [${Hex.toStringCondensed(newMasterKey.serialize())}] and RRP [$newRecoveryPassword]")

    val accountAttributes = AccountAttributes(
      signalingKey = null,
      registrationId = keyMaterial.aciRegistrationId,
      voice = true,
      video = true,
      fetchesMessages = fcmToken == null,
      registrationLock = registrationLock,
      unidentifiedAccessKey = keyMaterial.unidentifiedAccessKey,
      unrestrictedUnidentifiedAccess = unrestrictedUnidentifiedAccess,
      discoverableByPhoneNumber = false, // Important -- this should be false initially, and then the user should be given a choice as to whether to turn it on later
      capabilities = getAccountCapabilities(),
      pniRegistrationId = keyMaterial.pniRegistrationId,
      recoveryPassword = newRecoveryPassword
    )

    val aciPreKeys = PreKeyCollection(
      identityKey = keyMaterial.aciIdentityKeyPair.publicKey,
      signedPreKey = keyMaterial.aciSignedPreKey,
      lastResortKyberPreKey = keyMaterial.aciLastResortKyberPreKey
    )

    val pniPreKeys = PreKeyCollection(
      identityKey = keyMaterial.pniIdentityKeyPair.publicKey,
      signedPreKey = keyMaterial.pniSignedPreKey,
      lastResortKyberPreKey = keyMaterial.pniLastResortKyberPreKey
    )

    val result = networkController.registerAccount(
      e164 = e164,
      password = keyMaterial.servicePassword,
      sessionId = sessionId,
      recoveryPassword = recoveryPassword,
      attributes = accountAttributes,
      aciPreKeys = aciPreKeys,
      pniPreKeys = pniPreKeys,
      fcmToken = fcmToken,
      skipDeviceTransfer = skipDeviceTransfer
    )

    if (result is RequestResult.Success) {
      storageController.updateInProgressRegistrationData {
        this.accountEntropyPool = keyMaterial.accountEntropyPool.value
      }
      updateAccountData {
        this.e164 = result.result.e164
        this.aci = result.result.aci
        this.pni = result.result.pni
        this.servicePassword = keyMaterial.servicePassword
      }
      storageController.commitRegistrationData()
    }

    result.map { it to keyMaterial }
  }

  /**
   * Reads any locally-cached profile data (given/family name, avatar) so the create-profile screen
   * can pre-seed itself or skip outright when the user is re-registering with profile data still on
   * disk. See [StorageController.getStoredProfileData].
   */
  suspend fun getStoredProfileData(): StoredProfileData = withContext(Dispatchers.IO) {
    storageController.getStoredProfileData()
  }

  /**
   * Best-effort restore of the AccountRecord from the storage service, with a timeout for the UI.
   * The work continues in the background even if [timeout] elapses. See [NetworkController.restoreAccountRecord].
   */
  suspend fun restoreAccountRecord(
    timeout: Duration = 10.seconds
  ): RequestResult<Unit, NetworkController.RestoreAccountRecordError> = withContext(Dispatchers.IO) {
    networkController.restoreAccountRecord(timeout)
  }

  /**
   * Persists the freshly-created profile to local storage and arranges for it to be uploaded.
   * See [NetworkController.setProfile].
   */
  suspend fun setProfile(
    givenName: String,
    familyName: String,
    avatar: ByteArray?,
    discoverableByPhoneNumber: Boolean
  ): RequestResult<Unit, NetworkController.SetProfileError> = withContext(Dispatchers.IO) {
    networkController.setProfile(givenName, familyName, avatar, discoverableByPhoneNumber)
  }

  suspend fun setNewlyCreatedPin(
    pin: String,
    isAlphanumeric: Boolean,
    masterKey: MasterKey
  ): RequestResult<SvrCredentials?, NetworkController.BackupMasterKeyError> = withContext(Dispatchers.IO) {
    val result = networkController.setPinAndMasterKeyOnSvr(pin, masterKey)

    if (result is RequestResult.Success) {
      storageController.updateInProgressRegistrationData {
        this.pin = pin
        result.result?.let { credential ->
          this.svrCredentials += SvrCredential(username = credential.username, password = credential.password)
        }
      }
      storageController.commitRegistrationData()
    }

    result
  }

  /**
   * Records that the user has chosen not to create a PIN.
   *
   * This does not perform the opt-out itself -- it simply notes the user's choice in the in-progress
   * registration data and commits it. The app applies the actual opt-out (clearing PIN/registration lock
   * state, refreshing attributes, etc.) when it persists the committed [org.signal.registration.proto.RegistrationData].
   *
   * Any previously-recorded PIN state is cleared so the persisted blob stays internally consistent.
   */
  suspend fun setPinOptedOut(): Unit = withContext(Dispatchers.IO) {
    Log.i(TAG, "[setPinOptedOut] Recording PIN opt-out in registration data.")
    storageController.updateInProgressRegistrationData {
      this.pinOptedOut = true
      this.pin = ""
      this.registrationLockEnabled = false
    }
    storageController.commitRegistrationData()
  }

  /**
   * Persist any data in our scratch storage that was restored as part of a backup (remote or local) so that we don't
   * accidentally overwrite it when we commit it.
   */
  suspend fun persistRestoredBackupState(restoredPin: String?, restoredProfileKey: ProfileKey?) {
    storageController.updateInProgressRegistrationData {
      pin = restoredPin ?: pin
      profileKey = restoredProfileKey?.serialize()?.toByteString() ?: profileKey
    }
  }

  /**
   * Persists identity key pairs restored from a pre-registration local backup into our scratch storage, so that the
   * upcoming registration reuses the device's existing identity rather than generating a fresh one.
   */
  suspend fun persistRestoredIdentityKeys(restoredAciIdentityKey: IdentityKeyPair?, restoredPniIdentityKey: IdentityKeyPair?) {
    updateAccountData {
      aciIdentityKeyPair = restoredAciIdentityKey?.serialize()?.toByteString() ?: aciIdentityKeyPair
      pniIdentityKeyPair = restoredPniIdentityKey?.serialize()?.toByteString() ?: pniIdentityKeyPair
    }
  }

  /**
   * Records the terminal restore decision the user reached (new account, skipped a restore, or successfully restored)
   * and commits it. The app translates this into its own restore-decision state so the rest of the app knows what
   * happened during registration.
   */
  suspend fun setRestoreDecision(decision: RestoreDecision): Unit = withContext(Dispatchers.Default) {
    Log.i(TAG, "[setRestoreDecision] Recording restore decision: $decision")
    storageController.setRestoreDecision(decision)
  }

  suspend fun getPreExistingRegistrationData(): PreExistingRegistrationData? {
    return storageController.getPreExistingRegistrationData()
  }

  /**
   * Persists the current flow state as JSON in the in-progress registration data proto.
   */
  suspend fun saveFlowState(state: RegistrationFlowState) = withContext(Dispatchers.IO) {
    Log.d(TAG, "[saveFlowState] Saving flow state: $state")
    try {
      val json = json.encodeToString(PersistedFlowState.serializer(), state.toPersistedFlowState())
      storageController.updateInProgressRegistrationData {
        flowStateJson = json
      }
    } catch (e: Exception) {
      Log.w(TAG, "[saveFlowState] Failed to save flow state.", e)
    }
  }

  /**
   * Restores the flow state from disk. Returns null if no state is saved or deserialization fails.
   * Reconstructs [RegistrationFlowState.accountEntropyPool] and [RegistrationFlowState.temporaryMasterKey]
   * from their dedicated proto fields, and loads [RegistrationFlowState.preExistingRegistrationData]
   * from permanent storage.
   */
  suspend fun restoreFlowState(): RegistrationFlowState? = withContext(Dispatchers.IO) {
    try {
      val data = storageController.readInProgressRegistrationData()
      if (data.flowStateJson.isEmpty()) return@withContext null

      val persisted = json.decodeFromString(PersistedFlowState.serializer(), data.flowStateJson)

      val aep = data.accountEntropyPool.takeIf { it.isNotEmpty() }?.let { AccountEntropyPool(it) }
      val masterKey = data.masterKeyForInitialDataRestore.takeIf { it.size > 0 }?.let { MasterKey(it.toByteArray()) }
      val preExisting = storageController.getPreExistingRegistrationData()

      persisted.toRegistrationFlowState(
        accountEntropyPool = aep,
        temporaryMasterKey = masterKey,
        preExistingRegistrationData = preExisting
      )
    } catch (e: Exception) {
      Log.w(TAG, "Failed to restore flow state", e)
      null
    }
  }

  /**
   * Wipes all local app data and relaunches the app. Used when the primary asks a freshly-linked device to
   * re-link, which leaves this device's partial local registration invalid.
   */
  suspend fun clearLocalDataAndRestart() {
    storageController.clearLocalDataAndRestart()
  }

  /**
   * Deletes all in-progress registration data. Called once registration is fully complete, so the scratch data is
   * never reused by a later flow.
   */
  suspend fun clearInProgressRegistrationData() = withContext(Dispatchers.IO) {
    storageController.clearAllData()
  }

  /**
   * The time the in-progress registration data was last written, as epoch milliseconds, or null if nothing has been
   * written yet. Read from the in-progress data's `lastUpdatedMillis`, which is stamped on every write.
   */
  suspend fun getInProgressRegistrationDataLastUpdated(): Long? = withContext(Dispatchers.IO) {
    storageController.readInProgressRegistrationData().lastUpdatedMillis.takeIf { it > 0 }
  }

  /**
   * True if a PIN is already known for this registration -- restored from a backup or provided by the old device
   * during a quick restore -- and persisted in the in-progress registration data.
   */
  suspend fun hasKnownPin(): Boolean = withContext(Dispatchers.IO) {
    storageController.readInProgressRegistrationData().pin.isNotEmpty()
  }

  /**
   * Clears any persisted flow state JSON from the in-progress registration data.
   */
  suspend fun clearFlowState() = withContext(Dispatchers.IO) {
    try {
      storageController.updateInProgressRegistrationData {
        flowStateJson = ""
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to clear flow state", e)
    }
  }

  /**
   * Validates a registration session by fetching its current status from the server.
   * Returns fresh [SessionMetadata] on success, or null if the session is expired/invalid.
   */
  suspend fun validateSession(sessionId: String): SessionMetadata? = withContext(Dispatchers.IO) {
    when (val result = networkController.getSession(sessionId)) {
      is RequestResult.Success -> result.result
      else -> null
    }
  }

  /**
   * Checks whether the in-progress registration data indicates a completed registration
   * (i.e. both ACI and PNI have been saved).
   */
  suspend fun isRegistered(): Boolean = withContext(Dispatchers.IO) {
    val accountData = storageController.readInProgressRegistrationData().accountData
    accountData != null && accountData.aci.isNotEmpty() && accountData.pni.isNotEmpty()
  }

  fun restoreV1Backup(rootUri: Uri, backupUri: Uri, passphrase: String): Flow<LocalBackupRestoreProgress> {
    return storageController.restoreLocalBackupV1(rootUri, backupUri, passphrase)
  }

  fun restoreV2Backup(rootUri: Uri, backupUri: Uri, aep: AccountEntropyPool): Flow<LocalBackupRestoreProgress> {
    return storageController.restoreLocalBackupV2(rootUri, backupUri, aep)
  }

  /** Verifies that [aep] can decrypt the V2 local backup at [backupUri] without restoring anything. */
  suspend fun verifyLocalBackupKey(backupUri: Uri, aep: AccountEntropyPool): Boolean = withContext(Dispatchers.IO) {
    storageController.verifyLocalBackupKey(backupUri, aep)
  }

  suspend fun scanLocalBackupFolder(folderUri: Uri): List<LocalBackupInfo> = withContext(Dispatchers.IO) {
    storageController.scanLocalBackupFolder(folderUri)
  }

  suspend fun getRemoteBackupInfo(aep: AccountEntropyPool): RequestResult<NetworkController.GetBackupInfoResponse, NetworkController.GetBackupInfoError> = withContext(Dispatchers.IO) {
    networkController.getRemoteBackupInfo(aep)
  }

  suspend fun getBackupFileLastModified(aep: AccountEntropyPool, backupInfo: NetworkController.GetBackupInfoResponse): RequestResult<Long, NetworkController.GetBackupInfoError> = withContext(Dispatchers.IO) {
    networkController.getBackupFileLastModified(aep, backupInfo)
  }

  suspend fun verifyBackupKeyAssociatedWithAccount(aep: AccountEntropyPool): RequestResult<Unit, NetworkController.VerifyBackupKeyError> = withContext(Dispatchers.IO) {
    networkController.verifyBackupKeyAssociatedWithAccount(aep)
  }

  fun restoreRemoteBackup(aep: AccountEntropyPool): Flow<RemoteBackupRestoreProgress> {
    return storageController.restoreRemoteBackup(aep)
  }

  suspend fun saveVerifiedUserSuppliedAep(aep: AccountEntropyPool): Unit = withContext(Dispatchers.IO) {
    storageController.updateInProgressRegistrationData {
      this.accountEntropyPool = aep.value
    }
    storageController.commitRegistrationData()
  }

  suspend fun commitFinalRegistrationData(): Unit = withContext(Dispatchers.IO) {
    storageController.commitRegistrationData()
    networkController.enqueueAccountAttributesSyncJob()
    networkController.enqueueSvrGuessResetJobIfPossible()
  }

  /**
   * Applies [updater] to the one-time [AccountData] within the in-progress registration data. Only the registration
   * process itself should write account data -- it is frozen once committed.
   */
  private suspend fun updateAccountData(updater: AccountData.Builder.() -> Unit) {
    storageController.updateInProgressRegistrationData {
      accountData = (accountData ?: AccountData()).newBuilder().apply(updater).build()
    }
  }

  private fun generateKeyMaterial(
    existingAccountEntropyPool: AccountEntropyPool? = null,
    existingAciIdentityKeyPair: IdentityKeyPair? = null,
    existingPniIdentityKeyPair: IdentityKeyPair? = null,
    profileKey: ProfileKey? = null
  ): KeyMaterial {
    val accountEntropyPool = existingAccountEntropyPool ?: AccountEntropyPool.generate()
    val aciIdentityKeyPair = existingAciIdentityKeyPair ?: IdentityKeyPair.generate()
    val pniIdentityKeyPair = existingPniIdentityKeyPair ?: IdentityKeyPair.generate()

    val timestamp = System.currentTimeMillis()

    val aciSignedPreKey = generateSignedPreKey(generatePreKeyId(), timestamp, aciIdentityKeyPair)
    val pniSignedPreKey = generateSignedPreKey(generatePreKeyId(), timestamp, pniIdentityKeyPair)
    val aciLastResortKyberPreKey = generateKyberPreKey(generatePreKeyId(), timestamp, aciIdentityKeyPair)
    val pniLastResortKyberPreKey = generateKyberPreKey(generatePreKeyId(), timestamp, pniIdentityKeyPair)

    val profileKey = profileKey ?: generateProfileKey()

    return KeyMaterial(
      aciIdentityKeyPair = aciIdentityKeyPair,
      aciSignedPreKey = aciSignedPreKey,
      aciLastResortKyberPreKey = aciLastResortKyberPreKey,
      pniIdentityKeyPair = pniIdentityKeyPair,
      pniSignedPreKey = pniSignedPreKey,
      pniLastResortKyberPreKey = pniLastResortKyberPreKey,
      aciRegistrationId = generateRegistrationId(),
      pniRegistrationId = generateRegistrationId(),
      profileKey = profileKey.serialize(),
      unidentifiedAccessKey = deriveUnidentifiedAccessKey(profileKey),
      servicePassword = generatePassword(),
      accountEntropyPool = accountEntropyPool
    )
  }

  private fun generateSignedPreKey(id: Int, timestamp: Long, identityKeyPair: IdentityKeyPair): SignedPreKeyRecord {
    val keyPair = ECKeyPair.generate()
    val signature = identityKeyPair.privateKey.calculateSignature(keyPair.publicKey.serialize())
    return SignedPreKeyRecord(id, timestamp, keyPair, signature)
  }

  private fun generateKyberPreKey(id: Int, timestamp: Long, identityKeyPair: IdentityKeyPair): KyberPreKeyRecord {
    val kemKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
    val signature = identityKeyPair.privateKey.calculateSignature(kemKeyPair.publicKey.serialize())
    return KyberPreKeyRecord(id, timestamp, kemKeyPair, signature)
  }

  private fun generatePreKeyId(): Int {
    return SecureRandom().nextInt(Int.MAX_VALUE - 1) + 1
  }

  private fun generateRegistrationId(): Int {
    return SecureRandom().nextInt(16380) + 1
  }

  private fun generateProfileKey(): ProfileKey {
    val keyBytes = ByteArray(32)
    SecureRandom().nextBytes(keyBytes)
    return ProfileKey(keyBytes)
  }

  private fun generatePassword(): String {
    val passwordBytes = ByteArray(18)
    SecureRandom().nextBytes(passwordBytes)
    return Base64.encodeWithPadding(passwordBytes)
  }

  fun getAccountCapabilities(): AccountAttributes.Capabilities {
    return AccountAttributes.Capabilities(
      storage = true, // True initially -- can turn off later if users opt-out
      versionedExpirationTimer = true,
      attachmentBackfill = true,
      spqr = true,
      usernameChangeSyncMessage = true
    )
  }

  private fun deriveUnidentifiedAccessKey(profileKey: ProfileKey): ByteArray {
    val nonce = ByteArray(12)
    val input = ByteArray(16)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(profileKey.serialize(), "AES"), GCMParameterSpec(128, nonce))

    val ciphertext = cipher.doFinal(input)
    return ciphertext.copyOf(16)
  }
}

/**
 * Result of registering as a linked (secondary) device.
 *
 * @param hasLinkAndSyncBackup Whether the primary provided an ephemeral backup key, meaning the caller should
 *   wait for and apply a link-and-sync message backup before finishing.
 */
data class LinkedDeviceResult(
  val hasLinkAndSyncBackup: Boolean
)
