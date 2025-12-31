/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.util

import org.signal.core.models.MasterKey
import org.signal.registration.KeyMaterial
import org.signal.registration.NetworkController
import org.signal.registration.NewRegistrationData
import org.signal.registration.PreExistingRegistrationData
import org.signal.registration.RegistrationDependencies
import org.signal.registration.StorageController
import java.util.Locale

object PreviewRegistrationDependencies {
  fun get(): RegistrationDependencies {
    return RegistrationDependencies(
      networkController = PreviewNewtorkController(),
      storageController = PreviewStorageController()
    )
  }
}

private class PreviewNewtorkController : NetworkController {
  override suspend fun createSession(
    e164: String,
    fcmToken: String?,
    mcc: String?,
    mnc: String?
  ): NetworkController.RegistrationNetworkResult<NetworkController.SessionMetadata, NetworkController.CreateSessionError> {
    TODO("Not yet implemented")
  }

  override suspend fun getSession(sessionId: String): NetworkController.RegistrationNetworkResult<NetworkController.SessionMetadata, NetworkController.GetSessionStatusError> {
    TODO("Not yet implemented")
  }

  override suspend fun updateSession(
    sessionId: String?,
    pushChallengeToken: String?,
    captchaToken: String?
  ): NetworkController.RegistrationNetworkResult<NetworkController.SessionMetadata, NetworkController.UpdateSessionError> {
    TODO("Not yet implemented")
  }

  override suspend fun requestVerificationCode(
    sessionId: String,
    locale: Locale?,
    androidSmsRetrieverSupported: Boolean,
    transport: NetworkController.VerificationCodeTransport
  ): NetworkController.RegistrationNetworkResult<NetworkController.SessionMetadata, NetworkController.RequestVerificationCodeError> {
    TODO("Not yet implemented")
  }

  override suspend fun submitVerificationCode(
    sessionId: String,
    verificationCode: String
  ): NetworkController.RegistrationNetworkResult<NetworkController.SessionMetadata, NetworkController.SubmitVerificationCodeError> {
    TODO("Not yet implemented")
  }

  override suspend fun registerAccount(
    e164: String,
    password: String,
    sessionId: String?,
    recoveryPassword: String?,
    attributes: NetworkController.AccountAttributes,
    aciPreKeys: NetworkController.PreKeyCollection,
    pniPreKeys: NetworkController.PreKeyCollection,
    fcmToken: String?,
    skipDeviceTransfer: Boolean
  ): NetworkController.RegistrationNetworkResult<NetworkController.RegisterAccountResponse, NetworkController.RegisterAccountError> {
    TODO("Not yet implemented")
  }

  override suspend fun getFcmToken(): String? {
    TODO("Not yet implemented")
  }

  override suspend fun awaitPushChallengeToken(): String? {
    TODO("Not yet implemented")
  }

  override fun getCaptchaUrl(): String {
    TODO("Not yet implemented")
  }

  override suspend fun restoreMasterKeyFromSvr(
    svr2Credentials: NetworkController.SvrCredentials,
    pin: String
  ): NetworkController.RegistrationNetworkResult<NetworkController.MasterKeyResponse, NetworkController.RestoreMasterKeyError> {
    TODO("Not yet implemented")
  }

  override suspend fun setPinAndMasterKeyOnSvr(
    pin: String,
    masterKey: MasterKey
  ): NetworkController.RegistrationNetworkResult<Unit, NetworkController.BackupMasterKeyError> {
    TODO("Not yet implemented")
  }

  override suspend fun enableRegistrationLock(): NetworkController.RegistrationNetworkResult<Unit, NetworkController.SetRegistrationLockError> {
    TODO("Not yet implemented")
  }

  override suspend fun disableRegistrationLock(): NetworkController.RegistrationNetworkResult<Unit, NetworkController.SetRegistrationLockError> {
    TODO("Not yet implemented")
  }

  override suspend fun getSvrCredentials(): NetworkController.RegistrationNetworkResult<NetworkController.SvrCredentials, NetworkController.GetSvrCredentialsError> {
    TODO("Not yet implemented")
  }

  override suspend fun setAccountAttributes(attributes: NetworkController.AccountAttributes): NetworkController.RegistrationNetworkResult<Unit, NetworkController.SetAccountAttributesError> {
    TODO("Not yet implemented")
  }
}

private class PreviewStorageController : StorageController {
  override suspend fun generateAndStoreKeyMaterial(): KeyMaterial {
    TODO("Not yet implemented")
  }

  override suspend fun saveNewRegistrationData(newRegistrationData: NewRegistrationData) {
    TODO("Not yet implemented")
  }

  override suspend fun getPreExistingRegistrationData(): PreExistingRegistrationData? {
    TODO("Not yet implemented")
  }

  override suspend fun saveValidatedPinAndTemporaryMasterKey(pin: String, isAlphanumeric: Boolean, masterKey: MasterKey, registrationLockEnabled: Boolean) {
    TODO("Not yet implemented")
  }

  override suspend fun saveNewlyCreatedPin(pin: String, isAlphanumeric: Boolean) {
    TODO("Not yet implemented")
  }

  override suspend fun clearAllData() {
    TODO("Not yet implemented")
  }
}
