/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.debug

import org.signal.core.models.MasterKey
import org.signal.core.util.logging.Log
import org.signal.registration.NetworkController
import org.signal.registration.NetworkController.AccountAttributes
import org.signal.registration.NetworkController.BackupMasterKeyError
import org.signal.registration.NetworkController.CreateSessionError
import org.signal.registration.NetworkController.GetSessionStatusError
import org.signal.registration.NetworkController.GetSvrCredentialsError
import org.signal.registration.NetworkController.MasterKeyResponse
import org.signal.registration.NetworkController.PreKeyCollection
import org.signal.registration.NetworkController.RegisterAccountError
import org.signal.registration.NetworkController.RegisterAccountResponse
import org.signal.registration.NetworkController.RegistrationNetworkResult
import org.signal.registration.NetworkController.RequestVerificationCodeError
import org.signal.registration.NetworkController.RestoreMasterKeyError
import org.signal.registration.NetworkController.SessionMetadata
import org.signal.registration.NetworkController.SetAccountAttributesError
import org.signal.registration.NetworkController.SetRegistrationLockError
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
  ): RegistrationNetworkResult<SessionMetadata, CreateSessionError> {
    NetworkDebugState.getOverride<RegistrationNetworkResult<SessionMetadata, CreateSessionError>>("createSession")?.let {
      Log.d(TAG, "[createSession] Returning debug override")
      return it
    }
    return delegate.createSession(e164, fcmToken, mcc, mnc)
  }

  override suspend fun getSession(sessionId: String): RegistrationNetworkResult<SessionMetadata, GetSessionStatusError> {
    NetworkDebugState.getOverride<RegistrationNetworkResult<SessionMetadata, GetSessionStatusError>>("getSession")?.let {
      Log.d(TAG, "[getSession] Returning debug override")
      return it
    }
    return delegate.getSession(sessionId)
  }

  override suspend fun updateSession(
    sessionId: String?,
    pushChallengeToken: String?,
    captchaToken: String?
  ): RegistrationNetworkResult<SessionMetadata, UpdateSessionError> {
    NetworkDebugState.getOverride<RegistrationNetworkResult<SessionMetadata, UpdateSessionError>>("updateSession")?.let {
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
  ): RegistrationNetworkResult<SessionMetadata, RequestVerificationCodeError> {
    NetworkDebugState.getOverride<RegistrationNetworkResult<SessionMetadata, RequestVerificationCodeError>>("requestVerificationCode")?.let {
      Log.d(TAG, "[requestVerificationCode] Returning debug override")
      return it
    }
    return delegate.requestVerificationCode(sessionId, locale, androidSmsRetrieverSupported, transport)
  }

  override suspend fun submitVerificationCode(
    sessionId: String,
    verificationCode: String
  ): RegistrationNetworkResult<SessionMetadata, SubmitVerificationCodeError> {
    NetworkDebugState.getOverride<RegistrationNetworkResult<SessionMetadata, SubmitVerificationCodeError>>("submitVerificationCode")?.let {
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
  ): RegistrationNetworkResult<RegisterAccountResponse, RegisterAccountError> {
    NetworkDebugState.getOverride<RegistrationNetworkResult<RegisterAccountResponse, RegisterAccountError>>("registerAccount")?.let {
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
    // No override support for simple value methods
    return delegate.awaitPushChallengeToken()
  }

  override fun getCaptchaUrl(): String {
    // No override support for simple value methods
    return delegate.getCaptchaUrl()
  }

  override suspend fun restoreMasterKeyFromSvr(
    svr2Credentials: SvrCredentials,
    pin: String
  ): RegistrationNetworkResult<MasterKeyResponse, RestoreMasterKeyError> {
    NetworkDebugState.getOverride<RegistrationNetworkResult<MasterKeyResponse, RestoreMasterKeyError>>("restoreMasterKeyFromSvr")?.let {
      Log.d(TAG, "[restoreMasterKeyFromSvr] Returning debug override")
      return it
    }
    return delegate.restoreMasterKeyFromSvr(svr2Credentials, pin)
  }

  override suspend fun setPinAndMasterKeyOnSvr(
    pin: String,
    masterKey: MasterKey
  ): RegistrationNetworkResult<Unit, BackupMasterKeyError> {
    NetworkDebugState.getOverride<RegistrationNetworkResult<Unit, BackupMasterKeyError>>("setPinAndMasterKeyOnSvr")?.let {
      Log.d(TAG, "[setPinAndMasterKeyOnSvr] Returning debug override")
      return it
    }
    return delegate.setPinAndMasterKeyOnSvr(pin, masterKey)
  }

  override suspend fun enableRegistrationLock(): RegistrationNetworkResult<Unit, SetRegistrationLockError> {
    NetworkDebugState.getOverride<RegistrationNetworkResult<Unit, SetRegistrationLockError>>("enableRegistrationLock")?.let {
      Log.d(TAG, "[enableRegistrationLock] Returning debug override")
      return it
    }
    return delegate.enableRegistrationLock()
  }

  override suspend fun disableRegistrationLock(): RegistrationNetworkResult<Unit, SetRegistrationLockError> {
    NetworkDebugState.getOverride<RegistrationNetworkResult<Unit, SetRegistrationLockError>>("disableRegistrationLock")?.let {
      Log.d(TAG, "[disableRegistrationLock] Returning debug override")
      return it
    }
    return delegate.disableRegistrationLock()
  }

  override suspend fun setAccountAttributes(attributes: AccountAttributes): RegistrationNetworkResult<Unit, SetAccountAttributesError> {
    NetworkDebugState.getOverride<RegistrationNetworkResult<Unit, SetAccountAttributesError>>("setAccountAttributes")?.let {
      Log.d(TAG, "[setAccountAttributes] Returning debug override")
      return it
    }
    return delegate.setAccountAttributes(attributes)
  }

  override suspend fun getSvrCredentials(): RegistrationNetworkResult<SvrCredentials, GetSvrCredentialsError> {
    NetworkDebugState.getOverride<RegistrationNetworkResult<SvrCredentials, GetSvrCredentialsError>>("getSvrCredentials")?.let {
      Log.d(TAG, "[getSvrCredentials] Returning debug override")
      return it
    }
    return delegate.getSvrCredentials()
  }
}
