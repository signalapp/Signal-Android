/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.models.MasterKey
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.registration.NetworkController.AccountAttributes
import org.signal.registration.NetworkController.CreateSessionError
import org.signal.registration.NetworkController.MasterKeyResponse
import org.signal.registration.NetworkController.PreKeyCollection
import org.signal.registration.NetworkController.RegisterAccountError
import org.signal.registration.NetworkController.RegisterAccountResponse
import org.signal.registration.NetworkController.RegistrationNetworkResult
import org.signal.registration.NetworkController.RequestVerificationCodeError
import org.signal.registration.NetworkController.RestoreMasterKeyError
import org.signal.registration.NetworkController.SessionMetadata
import org.signal.registration.NetworkController.SvrCredentials
import org.signal.registration.NetworkController.UpdateSessionError
import java.util.Locale

class RegistrationRepository(val networkController: NetworkController, val storageController: StorageController) {

  suspend fun createSession(e164: String): RegistrationNetworkResult<SessionMetadata, CreateSessionError> = withContext(Dispatchers.IO) {
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
  ): RegistrationNetworkResult<SessionMetadata, RequestVerificationCodeError> = withContext(Dispatchers.IO) {
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
  ): RegistrationNetworkResult<SessionMetadata, UpdateSessionError> = withContext(Dispatchers.IO) {
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
  ): RegistrationNetworkResult<SessionMetadata, UpdateSessionError> = withContext(Dispatchers.IO) {
    networkController.updateSession(
      sessionId = sessionId,
      pushChallengeToken = pushChallengeToken,
      captchaToken = null
    )
  }

  suspend fun submitVerificationCode(
    sessionId: String,
    verificationCode: String
  ): RegistrationNetworkResult<SessionMetadata, NetworkController.SubmitVerificationCodeError> = withContext(Dispatchers.IO) {
    networkController.submitVerificationCode(
      sessionId = sessionId,
      verificationCode = verificationCode
    )
  }

  suspend fun getSvrCredentials(): RegistrationNetworkResult<SvrCredentials, NetworkController.GetSvrCredentialsError> = withContext(Dispatchers.IO) {
    networkController.getSvrCredentials()
  }

  suspend fun restoreMasterKeyFromSvr(
    svr2Credentials: SvrCredentials,
    pin: String,
    isAlphanumeric: Boolean,
    forRegistrationLock: Boolean
  ): RegistrationNetworkResult<MasterKeyResponse, RestoreMasterKeyError> = withContext(Dispatchers.IO) {
    networkController.restoreMasterKeyFromSvr(
      svr2Credentials = svr2Credentials,
      pin = pin
    ).also {
      if (it is RegistrationNetworkResult.Success) {
        // TODO consider whether we should save this now, or whether we should keep in app state and then hand it back to the library user at the end of the flow
        storageController.saveValidatedPinAndTemporaryMasterKey(pin, isAlphanumeric, it.data.masterKey, forRegistrationLock)
      }
    }
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
  suspend fun registerAccount(
    e164: String,
    sessionId: String,
    registrationLock: String? = null,
    skipDeviceTransfer: Boolean = true
  ): RegistrationNetworkResult<Pair<RegisterAccountResponse, KeyMaterial>, RegisterAccountError> = withContext(Dispatchers.IO) {
    val keyMaterial = storageController.generateAndStoreKeyMaterial()
    val fcmToken = networkController.getFcmToken()

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
      recoveryPassword = null
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
      recoveryPassword = null,
      attributes = accountAttributes,
      aciPreKeys = aciPreKeys,
      pniPreKeys = pniPreKeys,
      fcmToken = fcmToken,
      skipDeviceTransfer = skipDeviceTransfer
    )

    if (result is RegistrationNetworkResult.Success) {
      storageController.saveNewRegistrationData(
        NewRegistrationData(
          e164 = result.data.e164,
          aci = ACI.parseOrThrow(result.data.aci),
          pni = PNI.parseOrThrow(result.data.pni),
          servicePassword = keyMaterial.servicePassword,
          aep = keyMaterial.accountEntropyPool
        )
      )
    }

    result.mapSuccess { it to keyMaterial }
  }

  suspend fun setNewlyCreatedPin(
    pin: String,
    isAlphanumeric: Boolean,
    masterKey: MasterKey
  ): RegistrationNetworkResult<Unit, NetworkController.BackupMasterKeyError> = withContext(Dispatchers.IO) {
    val result = networkController.setPinAndMasterKeyOnSvr(pin, masterKey)

    if (result is RegistrationNetworkResult.Success) {
      storageController.saveNewlyCreatedPin(pin, isAlphanumeric)
    }

    result
  }
}
