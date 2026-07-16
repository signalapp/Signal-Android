/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.debug

import kotlinx.coroutines.flow.Flow
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.registration.LinkAndSyncWaitResult
import org.signal.registration.NetworkController
import org.signal.registration.NetworkController.AccountAttributes
import org.signal.registration.NetworkController.BackupMasterKeyError
import org.signal.registration.NetworkController.CheckSvrCredentialsError
import org.signal.registration.NetworkController.CheckSvrCredentialsResponse
import org.signal.registration.NetworkController.CreateSessionError
import org.signal.registration.NetworkController.DeviceAttributes
import org.signal.registration.NetworkController.GetBackupInfoError
import org.signal.registration.NetworkController.GetBackupInfoResponse
import org.signal.registration.NetworkController.GetSessionStatusError
import org.signal.registration.NetworkController.GetSvrCredentialsError
import org.signal.registration.NetworkController.MasterKeyResponse
import org.signal.registration.NetworkController.PreKeyCollection
import org.signal.registration.NetworkController.ProvisioningEvent
import org.signal.registration.NetworkController.RegisterAccountError
import org.signal.registration.NetworkController.RegisterAccountResponse
import org.signal.registration.NetworkController.RequestVerificationCodeError
import org.signal.registration.NetworkController.RestoreAccountRecordError
import org.signal.registration.NetworkController.RestoreMasterKeyError
import org.signal.registration.NetworkController.RestoreMethod
import org.signal.registration.NetworkController.SessionMetadata
import org.signal.registration.NetworkController.SetAccountAttributesError
import org.signal.registration.NetworkController.SetProfileError
import org.signal.registration.NetworkController.SetRegistrationLockError
import org.signal.registration.NetworkController.SetRestoreMethodError
import org.signal.registration.NetworkController.SubmitVerificationCodeError
import org.signal.registration.NetworkController.SvrCredentials
import org.signal.registration.NetworkController.UpdateSessionError
import org.signal.registration.NetworkController.VerificationCodeTransport
import java.util.Locale

/**
 * Debug wrapper for NetworkController that allows forcing specific responses.
 *
 * When an override is set for a method via [NetworkDebugState], this controller
 * returns the forced result instead of calling the delegate.
 *
 * This is useful for testing error handling, edge cases, and UI states without
 * needing a real backend connection.
 */
class DebugNetworkController(
  private val delegate: NetworkController
) : NetworkController {

  companion object {
    private val TAG = Log.tag(DebugNetworkController::class)
  }

  override suspend fun createSession(
    e164: String,
    fcmToken: String?,
    mcc: String?,
    mnc: String?
  ): RequestResult<SessionMetadata, CreateSessionError> {
    NetworkDebugState.getOverride<RequestResult<SessionMetadata, CreateSessionError>>("createSession")?.let {
      Log.d(TAG, "[createSession] Returning debug override")
      return it
    }
    return delegate.createSession(e164, fcmToken, mcc, mnc)
  }

  override suspend fun getSession(sessionId: String): RequestResult<SessionMetadata, GetSessionStatusError> {
    NetworkDebugState.getOverride<RequestResult<SessionMetadata, GetSessionStatusError>>("getSession")?.let {
      Log.d(TAG, "[getSession] Returning debug override")
      return it
    }
    return delegate.getSession(sessionId)
  }

  override suspend fun updateSession(
    sessionId: String?,
    pushChallengeToken: String?,
    captchaToken: String?
  ): RequestResult<SessionMetadata, UpdateSessionError> {
    NetworkDebugState.getOverride<RequestResult<SessionMetadata, UpdateSessionError>>("updateSession")?.let {
      Log.d(TAG, "[updateSession] Returning debug override")
      return it
    }
    return delegate.updateSession(sessionId, pushChallengeToken, captchaToken)
  }

  override suspend fun requestVerificationCode(
    sessionId: String,
    locale: Locale?,
    androidSmsRetrieverSupported: Boolean,
    transport: VerificationCodeTransport
  ): RequestResult<SessionMetadata, RequestVerificationCodeError> {
    NetworkDebugState.getOverride<RequestResult<SessionMetadata, RequestVerificationCodeError>>("requestVerificationCode")?.let {
      Log.d(TAG, "[requestVerificationCode] Returning debug override")
      return it
    }
    return delegate.requestVerificationCode(sessionId, locale, androidSmsRetrieverSupported, transport)
  }

  override suspend fun submitVerificationCode(
    sessionId: String,
    verificationCode: String
  ): RequestResult<SessionMetadata, SubmitVerificationCodeError> {
    NetworkDebugState.getOverride<RequestResult<SessionMetadata, SubmitVerificationCodeError>>("submitVerificationCode")?.let {
      Log.d(TAG, "[submitVerificationCode] Returning debug override")
      return it
    }
    return delegate.submitVerificationCode(sessionId, verificationCode)
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
  ): RequestResult<RegisterAccountResponse, RegisterAccountError> {
    NetworkDebugState.getOverride<RequestResult<RegisterAccountResponse, RegisterAccountError>>("registerAccount")?.let {
      Log.d(TAG, "[registerAccount] Returning debug override")
      return it
    }
    return delegate.registerAccount(e164, password, sessionId, recoveryPassword, attributes, aciPreKeys, pniPreKeys, fcmToken, skipDeviceTransfer)
  }

  override suspend fun getFcmToken(): String? {
    // No override support for simple value methods
    return delegate.getFcmToken()
  }

  override suspend fun awaitPushChallengeToken(): String? {
    if (NetworkDebugState.skipPushChallenge.value) {
      Log.d(TAG, "[awaitPushChallengeToken] Skipping push challenge (debug override)")
      return null
    }
    return delegate.awaitPushChallengeToken()
  }

  override fun getCaptchaUrl(): String {
    // No override support for simple value methods
    return delegate.getCaptchaUrl()
  }

  override suspend fun restoreMasterKeyFromSvr(
    svrCredentials: SvrCredentials,
    pin: String
  ): RequestResult<MasterKeyResponse, RestoreMasterKeyError> {
    NetworkDebugState.getOverride<RequestResult<MasterKeyResponse, RestoreMasterKeyError>>("restoreMasterKeyFromSvr")?.let {
      Log.d(TAG, "[restoreMasterKeyFromSvr] Returning debug override")
      return it
    }
    return delegate.restoreMasterKeyFromSvr(svrCredentials, pin)
  }

  override suspend fun setPinAndMasterKeyOnSvr(
    pin: String,
    masterKey: MasterKey
  ): RequestResult<SvrCredentials?, BackupMasterKeyError> {
    NetworkDebugState.getOverride<RequestResult<SvrCredentials?, BackupMasterKeyError>>("setPinAndMasterKeyOnSvr")?.let {
      Log.d(TAG, "[setPinAndMasterKeyOnSvr] Returning debug override")
      return it
    }
    return delegate.setPinAndMasterKeyOnSvr(pin, masterKey)
  }

  override suspend fun enqueueSvrGuessResetJobIfPossible(): Boolean {
    // No override support for simple value methods
    return delegate.enqueueSvrGuessResetJobIfPossible()
  }

  override suspend fun enableRegistrationLock(): RequestResult<Unit, SetRegistrationLockError> {
    NetworkDebugState.getOverride<RequestResult<Unit, SetRegistrationLockError>>("enableRegistrationLock")?.let {
      Log.d(TAG, "[enableRegistrationLock] Returning debug override")
      return it
    }
    return delegate.enableRegistrationLock()
  }

  override suspend fun disableRegistrationLock(): RequestResult<Unit, SetRegistrationLockError> {
    NetworkDebugState.getOverride<RequestResult<Unit, SetRegistrationLockError>>("disableRegistrationLock")?.let {
      Log.d(TAG, "[disableRegistrationLock] Returning debug override")
      return it
    }
    return delegate.disableRegistrationLock()
  }

  override suspend fun setAccountAttributes(attributes: AccountAttributes): RequestResult<Unit, SetAccountAttributesError> {
    NetworkDebugState.getOverride<RequestResult<Unit, SetAccountAttributesError>>("setAccountAttributes")?.let {
      Log.d(TAG, "[setAccountAttributes] Returning debug override")
      return it
    }
    return delegate.setAccountAttributes(attributes)
  }

  override suspend fun enqueueAccountAttributesSyncJob() {
    delegate.enqueueAccountAttributesSyncJob()
  }

  override suspend fun setProfile(
    givenName: String,
    familyName: String,
    avatar: ByteArray?,
    discoverableByPhoneNumber: Boolean
  ): RequestResult<Unit, SetProfileError> {
    NetworkDebugState.getOverride<RequestResult<Unit, SetProfileError>>("setProfile")?.let {
      Log.d(TAG, "[setProfile] Returning debug override")
      return it
    }
    return delegate.setProfile(givenName, familyName, avatar, discoverableByPhoneNumber)
  }

  override suspend fun restoreAccountRecord(timeout: kotlin.time.Duration): RequestResult<Unit, RestoreAccountRecordError> {
    NetworkDebugState.getOverride<RequestResult<Unit, RestoreAccountRecordError>>("restoreAccountRecord")?.let {
      Log.d(TAG, "[restoreAccountRecord] Returning debug override")
      return it
    }
    return delegate.restoreAccountRecord(timeout)
  }

  override suspend fun setRestoreMethod(token: String, method: RestoreMethod): RequestResult<Unit, SetRestoreMethodError> {
    NetworkDebugState.getOverride<RequestResult<Unit, SetRestoreMethodError>>("setRestoreMethod")?.let {
      Log.d(TAG, "[setRestoreMethod] Returning debug override")
      return it
    }
    return delegate.setRestoreMethod(token, method)
  }

  override suspend fun getSvrCredentials(): RequestResult<SvrCredentials, GetSvrCredentialsError> {
    NetworkDebugState.getOverride<RequestResult<SvrCredentials, GetSvrCredentialsError>>("getSvrCredentials")?.let {
      Log.d(TAG, "[getSvrCredentials] Returning debug override")
      return it
    }
    return delegate.getSvrCredentials()
  }

  override fun startProvisioning(): Flow<ProvisioningEvent> {
    return delegate.startProvisioning()
  }

  override fun startLinkDeviceProvisioning(allowLinkAndSync: Boolean): Flow<NetworkController.LinkDeviceProvisioningEvent> {
    return delegate.startLinkDeviceProvisioning(allowLinkAndSync)
  }

  override suspend fun registerAsLinkedDevice(
    e164: String,
    password: String,
    provisioningCode: String,
    deviceAttributes: DeviceAttributes,
    aciPreKeys: PreKeyCollection,
    pniPreKeys: PreKeyCollection,
    fcmToken: String?
  ): RequestResult<NetworkController.LinkDeviceResponse, NetworkController.RegisterAsLinkedDeviceError> {
    return delegate.registerAsLinkedDevice(e164, password, provisioningCode, deviceAttributes, aciPreKeys, pniPreKeys, fcmToken)
  }

  override suspend fun onLinkedDeviceRegistered() {
    delegate.onLinkedDeviceRegistered()
  }

  override suspend fun awaitLinkAndSyncArchive(): LinkAndSyncWaitResult {
    return delegate.awaitLinkAndSyncArchive()
  }

  override suspend fun restoreLinkedDeviceFromStorageService() {
    delegate.restoreLinkedDeviceFromStorageService()
  }

  override fun startNewDeviceTransferServer(context: android.content.Context, aep: AccountEntropyPool) {
    if (NetworkDebugState.fakeDeviceTransfer.value) {
      Log.d(TAG, "[startNewDeviceTransferServer] Fake device transfer enabled (debug override)")
      org.signal.registration.sample.dependencies.FakeDeviceTransferRunner.start()
    } else {
      delegate.startNewDeviceTransferServer(context, aep)
    }
  }

  override suspend fun checkSvrCredentials(
    e164: String,
    credentials: List<SvrCredentials>
  ): RequestResult<CheckSvrCredentialsResponse, CheckSvrCredentialsError> {
    NetworkDebugState.getOverride<RequestResult<CheckSvrCredentialsResponse, CheckSvrCredentialsError>>("checkSvrCredentials")?.let {
      Log.d(TAG, "[checkSvrCredentials] Returning debug override")
      return it
    }

    return delegate.checkSvrCredentials(e164, credentials)
  }

  override suspend fun getRemoteBackupInfo(aep: AccountEntropyPool): RequestResult<GetBackupInfoResponse, GetBackupInfoError> {
    NetworkDebugState.getOverride<RequestResult<GetBackupInfoResponse, GetBackupInfoError>>("getRemoteBackupInfo")?.let {
      Log.d(TAG, "[getRemoteBackupInfo] Returning debug override")
      return it
    }
    return delegate.getRemoteBackupInfo(aep)
  }

  override suspend fun getBackupFileLastModified(aep: AccountEntropyPool, backupInfo: NetworkController.GetBackupInfoResponse): RequestResult<Long, GetBackupInfoError> {
    NetworkDebugState.getOverride<RequestResult<Long, GetBackupInfoError>>("getBackupFileLastModified")?.let {
      Log.d(TAG, "[getBackupFileLastModified] Returning debug override")
      return it
    }
    return delegate.getBackupFileLastModified(aep, backupInfo)
  }

  override suspend fun verifyBackupKeyAssociatedWithAccount(aep: AccountEntropyPool): RequestResult<Unit, NetworkController.VerifyBackupKeyError> {
    NetworkDebugState.getOverride<RequestResult<Unit, NetworkController.VerifyBackupKeyError>>("verifyBackupKeyAssociatedWithAccount")?.let {
      Log.d(TAG, "[verifyBackupKeyAssociatedWithAccount] Returning debug override")
      return it
    }
    return delegate.verifyBackupKeyAssociatedWithAccount(aep)
  }
}
