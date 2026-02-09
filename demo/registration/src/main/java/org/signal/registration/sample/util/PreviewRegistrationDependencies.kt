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
    throw NotImplementedError()
  }

  override suspend fun getSession(sessionId: String): NetworkController.RegistrationNetworkResult<NetworkController.SessionMetadata, NetworkController.GetSessionStatusError> {
    throw NotImplementedError()
  }

  override suspend fun updateSession(
    sessionId: String?,
    pushChallengeToken: String?,
    captchaToken: String?
  ): NetworkController.RegistrationNetworkResult<NetworkController.SessionMetadata, NetworkController.UpdateSessionError> {
    throw NotImplementedError()
  }

  override suspend fun requestVerificationCode(
    sessionId: String,
    locale: Locale?,
    androidSmsRetrieverSupported: Boolean,
    transport: NetworkController.VerificationCodeTransport
  ): NetworkController.RegistrationNetworkResult<NetworkController.SessionMetadata, NetworkController.RequestVerificationCodeError> {
    throw NotImplementedError()
  }

  override suspend fun submitVerificationCode(
    sessionId: String,
    verificationCode: String
  ): NetworkController.RegistrationNetworkResult<NetworkController.SessionMetadata, NetworkController.SubmitVerificationCodeError> {
    throw NotImplementedError()
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
    throw NotImplementedError()
  }

  override suspend fun getFcmToken(): String? {
    throw NotImplementedError()
  }

  override suspend fun awaitPushChallengeToken(): String? {
    throw NotImplementedError()
  }

  override fun getCaptchaUrl(): String {
    throw NotImplementedError()
  }

  override suspend fun restoreMasterKeyFromSvr(
    svr2Credentials: NetworkController.SvrCredentials,
    pin: String
  ): NetworkController.RegistrationNetworkResult<NetworkController.MasterKeyResponse, NetworkController.RestoreMasterKeyError> {
    throw NotImplementedError()
  }

  override suspend fun setPinAndMasterKeyOnSvr(
    pin: String,
    masterKey: MasterKey
  ): NetworkController.RegistrationNetworkResult<Unit, NetworkController.BackupMasterKeyError> {
    throw NotImplementedError()
  }

  override suspend fun enableRegistrationLock(): NetworkController.RegistrationNetworkResult<Unit, NetworkController.SetRegistrationLockError> {
    throw NotImplementedError()
  }

  override suspend fun disableRegistrationLock(): NetworkController.RegistrationNetworkResult<Unit, NetworkController.SetRegistrationLockError> {
    throw NotImplementedError()
  }

  override suspend fun getSvrCredentials(): NetworkController.RegistrationNetworkResult<NetworkController.SvrCredentials, NetworkController.GetSvrCredentialsError> {
    throw NotImplementedError()
  }

  override suspend fun setAccountAttributes(attributes: NetworkController.AccountAttributes): NetworkController.RegistrationNetworkResult<Unit, NetworkController.SetAccountAttributesError> {
    throw NotImplementedError()
  }
}

private class PreviewStorageController : StorageController {
  override suspend fun generateAndStoreKeyMaterial(): KeyMaterial {
    throw NotImplementedError()
  }

  override suspend fun saveNewRegistrationData(newRegistrationData: NewRegistrationData) {
    throw NotImplementedError()
  }

  override suspend fun getPreExistingRegistrationData(): PreExistingRegistrationData? {
    throw NotImplementedError()
  }

  override suspend fun saveValidatedPinAndTemporaryMasterKey(pin: String, isAlphanumeric: Boolean, masterKey: MasterKey, registrationLockEnabled: Boolean) {
    throw NotImplementedError()
  }

  override suspend fun saveNewlyCreatedPin(pin: String, isAlphanumeric: Boolean) {
    throw NotImplementedError()
  }

  override suspend fun clearAllData() {
    throw NotImplementedError()
  }
}
