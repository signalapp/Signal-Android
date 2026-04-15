/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.app.backup.BackupManager
import android.content.Context
import android.net.Uri
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString
import org.signal.archive.LocalBackupRestoreProgress
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.util.Base64
import org.signal.core.util.Util
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
import org.signal.registration.proto.ProvisioningData
import org.signal.registration.proto.SvrCredential
import org.signal.registration.screens.localbackuprestore.LocalBackupInfo
import org.signal.registration.util.SensitiveLog
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class RegistrationRepository(val context: Context, val networkController: NetworkController, val storageController: StorageController) {

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

  fun getDefaultRegionCode(): String {
    val maybeRegionCode = Util.getNetworkCountryIso(context)
    val maybeCountryCode = PhoneNumberUtil.getInstance().getCountryCodeForRegion(maybeRegionCode)
    return if (maybeRegionCode != null && maybeCountryCode != 0) {
      maybeRegionCode
    } else {
      Log.w(TAG, "Invalid region or country code. Defaulting to US.")
      "US"
    }
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
    isAlphanumeric: Boolean,
    forRegistrationLock: Boolean
  ): RequestResult<MasterKeyResponse, RestoreMasterKeyError> = withContext(Dispatchers.IO) {
    networkController.restoreMasterKeyFromSvr(
      svrCredentials = svrCredentials,
      pin = pin
    ).also {
      if (it is RequestResult.Success) {
        storageController.updateInProgressRegistrationData {
          this.pin = pin
          this.pinIsAlphanumeric = isAlphanumeric
          this.temporaryMasterKey = it.result.masterKey.serialize().toByteString()
          this.registrationLockEnabled = forRegistrationLock
          this.svrCredentials += SvrCredential(username = svrCredentials.username, password = svrCredentials.password)
        }
        storageController.commitRegistrationData()
      }
    }
  }

  /**
   * See [NetworkController.enqueueSvrGuessResetJob]
   */
  suspend fun enqueueSvrResetGuessCountJob() {
    networkController.enqueueSvrGuessResetJob()
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
      existingPniIdentityKeyPair = preExistingRegistrationData?.pniIdentityKeyPair
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
   * Registers an account using data received from the old device via QR provisioning.
   *
   * This method:
   * 1. Saves provisioning metadata (restore token, backup info) to storage
   * 2. Re-uses the identity key pairs and AEP from the old device
   * 3. Derives the recovery password from the provisioned AEP
   * 4. Registers the account
   */
  suspend fun registerAccountWithProvisioningData(
    provisioningMessage: NetworkController.ProvisioningMessage
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
    }

    val aep = AccountEntropyPool(provisioningMessage.accountEntropyPool)
    val recoveryPassword = aep.deriveMasterKey().deriveRegistrationRecoveryPassword()

    registerAccount(
      e164 = provisioningMessage.e164,
      sessionId = null,
      recoveryPassword = recoveryPassword,
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
    existingPniIdentityKeyPair: IdentityKeyPair? = null
  ): RequestResult<Pair<RegisterAccountResponse, KeyMaterial>, RegisterAccountError> = withContext(Dispatchers.IO) {
    check(sessionId != null || recoveryPassword != null) { "Either sessionId or recoveryPassword must be provided" }
    check(sessionId == null || recoveryPassword == null) { "Either sessionId or recoveryPassword must be provided, but not both" }

    Log.i(TAG, "[registerAccount] Starting registration for $e164. sessionId: ${sessionId != null}, recoveryPassword: ${recoveryPassword != null}, registrationLock: ${registrationLock != null}, skipDeviceTransfer: $skipDeviceTransfer, existingAep: ${existingAccountEntropyPool != null}")

    val keyMaterial = generateKeyMaterial(
      existingAccountEntropyPool = existingAccountEntropyPool,
      existingAciIdentityKeyPair = existingAciIdentityKeyPair,
      existingPniIdentityKeyPair = existingPniIdentityKeyPair
    )

    storageController.updateInProgressRegistrationData {
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
      this.accountEntropyPool = keyMaterial.accountEntropyPool.value
    }

    val fcmToken = networkController.getFcmToken()

    val newMasterKey = keyMaterial.accountEntropyPool.deriveMasterKey()
    val newRecoveryPassword = newMasterKey.deriveRegistrationRecoveryPassword()

    SensitiveLog.d(TAG, "[registerAccount] Using master key [${org.signal.libsignal.protocol.util.Hex.toStringCondensed(newMasterKey.serialize())}] and RRP [$newRecoveryPassword]")

    val accountAttributes = AccountAttributes(
      signalingKey = null,
      registrationId = keyMaterial.aciRegistrationId,
      voice = true,
      video = true,
      fetchesMessages = fcmToken == null,
      registrationLock = registrationLock,
      unidentifiedAccessKey = keyMaterial.unidentifiedAccessKey,
      unrestrictedUnidentifiedAccess = false,
      discoverableByPhoneNumber = false, // Important -- this should be false initially, and then the user should be given a choice as to whether to turn it on later
      capabilities = AccountAttributes.Capabilities(
        storage = true, // True initially -- can turn off later if users opt-out
        versionedExpirationTimer = true,
        attachmentBackfill = true,
        spqr = true
      ),
      name = null,
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
        this.e164 = result.result.e164
        this.aci = result.result.aci
        this.pni = result.result.pni
        this.servicePassword = keyMaterial.servicePassword
        this.accountEntropyPool = keyMaterial.accountEntropyPool.value
      }
      storageController.commitRegistrationData()
    }

    result.map { it to keyMaterial }
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
        this.pinIsAlphanumeric = isAlphanumeric
        result.result?.let { credential ->
          this.svrCredentials += SvrCredential(username = credential.username, password = credential.password)
        }
      }
      storageController.commitRegistrationData()
    }

    result
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
      val masterKey = data.temporaryMasterKey.takeIf { it.size > 0 }?.let { MasterKey(it.toByteArray()) }
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
    val data = storageController.readInProgressRegistrationData()
    data.aci.isNotEmpty() && data.pni.isNotEmpty()
  }

  fun restoreV1Backup(uri: Uri, passphrase: String): Flow<LocalBackupRestoreProgress> {
    return storageController.restoreLocalBackupV1(uri, passphrase)
  }

  fun restoreV2Backup(rootUri: Uri, backupUri: Uri, aep: AccountEntropyPool): Flow<LocalBackupRestoreProgress> {
    return storageController.restoreLocalBackupV2(rootUri, backupUri, aep)
  }

  suspend fun scanLocalBackupFolder(folderUri: Uri): List<LocalBackupInfo> = withContext(Dispatchers.IO) {
    storageController.scanLocalBackupFolder(folderUri)
  }

  private fun generateKeyMaterial(
    existingAccountEntropyPool: AccountEntropyPool? = null,
    existingAciIdentityKeyPair: IdentityKeyPair? = null,
    existingPniIdentityKeyPair: IdentityKeyPair? = null
  ): KeyMaterial {
    val accountEntropyPool = existingAccountEntropyPool ?: AccountEntropyPool.generate()
    val aciIdentityKeyPair = existingAciIdentityKeyPair ?: IdentityKeyPair.generate()
    val pniIdentityKeyPair = existingPniIdentityKeyPair ?: IdentityKeyPair.generate()

    val timestamp = System.currentTimeMillis()

    val aciSignedPreKey = generateSignedPreKey(generatePreKeyId(), timestamp, aciIdentityKeyPair)
    val pniSignedPreKey = generateSignedPreKey(generatePreKeyId(), timestamp, pniIdentityKeyPair)
    val aciLastResortKyberPreKey = generateKyberPreKey(generatePreKeyId(), timestamp, aciIdentityKeyPair)
    val pniLastResortKyberPreKey = generateKyberPreKey(generatePreKeyId(), timestamp, pniIdentityKeyPair)

    val profileKey = generateProfileKey()

    return KeyMaterial(
      aciIdentityKeyPair = aciIdentityKeyPair,
      aciSignedPreKey = aciSignedPreKey,
      aciLastResortKyberPreKey = aciLastResortKyberPreKey,
      pniIdentityKeyPair = pniIdentityKeyPair,
      pniSignedPreKey = pniSignedPreKey,
      pniLastResortKyberPreKey = pniLastResortKyberPreKey,
      aciRegistrationId = generateRegistrationId(),
      pniRegistrationId = generateRegistrationId(),
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

  private fun deriveUnidentifiedAccessKey(profileKey: ProfileKey): ByteArray {
    val nonce = ByteArray(12)
    val input = ByteArray(16)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(profileKey.serialize(), "AES"), GCMParameterSpec(128, nonce))

    val ciphertext = cipher.doFinal(input)
    return ciphertext.copyOf(16)
  }
}
