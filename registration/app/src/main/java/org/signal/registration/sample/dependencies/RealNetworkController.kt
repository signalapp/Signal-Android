/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.dependencies

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Response
import org.signal.core.util.logging.Log
import org.signal.registration.NetworkController
import org.signal.registration.NetworkController.AccountAttributes
import org.signal.registration.NetworkController.CreateSessionError
import org.signal.registration.NetworkController.GetSessionStatusError
import org.signal.registration.NetworkController.PreKeyCollection
import org.signal.registration.NetworkController.RegisterAccountError
import org.signal.registration.NetworkController.RegisterAccountResponse
import org.signal.registration.NetworkController.RegistrationLockResponse
import org.signal.registration.NetworkController.RegistrationNetworkResult
import org.signal.registration.NetworkController.RequestVerificationCodeError
import org.signal.registration.NetworkController.SessionMetadata
import org.signal.registration.NetworkController.SubmitVerificationCodeError
import org.signal.registration.NetworkController.ThirdPartyServiceErrorResponse
import org.signal.registration.NetworkController.UpdateSessionError
import org.signal.registration.NetworkController.VerificationCodeTransport
import org.signal.registration.sample.fcm.FcmUtil
import org.signal.registration.sample.fcm.PushChallengeReceiver
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import java.io.IOException
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.whispersystems.signalservice.api.account.AccountAttributes as ServiceAccountAttributes
import org.whispersystems.signalservice.api.account.PreKeyCollection as ServicePreKeyCollection

class RealNetworkController(
  private val context: android.content.Context,
  private val pushServiceSocket: PushServiceSocket
) : NetworkController {

  companion object {
    private val TAG = Log.tag(RealNetworkController::class)
  }

  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun createSession(
    e164: String,
    fcmToken: String?,
    mcc: String?,
    mnc: String?
  ): RegistrationNetworkResult<SessionMetadata, CreateSessionError> = withContext(Dispatchers.IO) {
    try {
      pushServiceSocket.createVerificationSessionV2(e164, fcmToken, mcc, mnc).use { response ->
        when (response.code) {
          200 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RegistrationNetworkResult.Success(session)
          }
          422 -> {
            RegistrationNetworkResult.Failure(CreateSessionError.InvalidRequest(response.body.string()))
          }
          429 -> {
            RegistrationNetworkResult.Failure(CreateSessionError.RateLimited(response.retryAfter()))
          }
          else -> {
            RegistrationNetworkResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}, body: ${response.body.string()}"))
          }
        }
      }
    } catch (e: IOException) {
      RegistrationNetworkResult.NetworkError(e)
    } catch (e: Exception) {
      RegistrationNetworkResult.ApplicationError(e)
    }
  }

  override suspend fun getSession(sessionId: String): RegistrationNetworkResult<SessionMetadata, GetSessionStatusError> = withContext(Dispatchers.IO) {
    try {
      pushServiceSocket.getSessionStatusV2(sessionId).use { response ->
        when (response.code) {
          200 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RegistrationNetworkResult.Success(session)
          }
          400 -> {
            RegistrationNetworkResult.Failure(GetSessionStatusError.InvalidRequest(response.body.string()))
          }
          404 -> {
            RegistrationNetworkResult.Failure(GetSessionStatusError.SessionNotFound(response.body.string()))
          }
          422 -> {
            RegistrationNetworkResult.Failure(GetSessionStatusError.InvalidSessionId(response.body.string()))
          }
          else -> {
            RegistrationNetworkResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}, body: ${response.body.string()}"))
          }
        }
      }
    } catch (e: IOException) {
      RegistrationNetworkResult.NetworkError(e)
    } catch (e: Exception) {
      RegistrationNetworkResult.ApplicationError(e)
    }
  }

  override suspend fun updateSession(
    sessionId: String?,
    pushChallengeToken: String?,
    captchaToken: String?
  ): RegistrationNetworkResult<SessionMetadata, UpdateSessionError> = withContext(Dispatchers.IO) {
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
            RegistrationNetworkResult.Success(session)
          }
          400 -> {
            RegistrationNetworkResult.Failure(UpdateSessionError.InvalidRequest(response.body.string()))
          }
          409 -> {
            RegistrationNetworkResult.Failure(UpdateSessionError.RejectedUpdate(response.body.string()))
          }
          429 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RegistrationNetworkResult.Failure(UpdateSessionError.RateLimited(response.retryAfter(), session))
          }
          else -> {
            RegistrationNetworkResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}, body: ${response.body.string()}"))
          }
        }
      }
    } catch (e: IOException) {
      RegistrationNetworkResult.NetworkError(e)
    } catch (e: Exception) {
      RegistrationNetworkResult.ApplicationError(e)
    }
  }

  override suspend fun requestVerificationCode(
    sessionId: String,
    locale: Locale?,
    androidSmsRetrieverSupported: Boolean,
    transport: VerificationCodeTransport
  ): RegistrationNetworkResult<SessionMetadata, RequestVerificationCodeError> = withContext(Dispatchers.IO) {
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
            RegistrationNetworkResult.Success(session)
          }
          400 -> {
            RegistrationNetworkResult.Failure(RequestVerificationCodeError.InvalidSessionId(response.body.string()))
          }
          404 -> {
            RegistrationNetworkResult.Failure(RequestVerificationCodeError.SessionNotFound(response.body.string()))
          }
          409 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RegistrationNetworkResult.Failure(RequestVerificationCodeError.MissingRequestInformationOrAlreadyVerified(session))
          }
          418 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RegistrationNetworkResult.Failure(RequestVerificationCodeError.CouldNotFulfillWithRequestedTransport(session))
          }
          429 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RegistrationNetworkResult.Failure(RequestVerificationCodeError.RateLimited(response.retryAfter(), session))
          }
          440 -> {
            val errorBody = json.decodeFromString<ThirdPartyServiceErrorResponse>(response.body.string())
            RegistrationNetworkResult.Failure(RequestVerificationCodeError.ThirdPartyServiceError(errorBody))
          }
          else -> {
            RegistrationNetworkResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}, body: ${response.body.string()}"))
          }
        }
      }
    } catch (e: IOException) {
      RegistrationNetworkResult.NetworkError(e)
    } catch (e: Exception) {
      RegistrationNetworkResult.ApplicationError(e)
    }
  }

  override suspend fun submitVerificationCode(
    sessionId: String,
    verificationCode: String
  ): RegistrationNetworkResult<SessionMetadata, SubmitVerificationCodeError> = withContext(Dispatchers.IO) {
    try {
      pushServiceSocket.submitVerificationCodeV2(sessionId, verificationCode).use { response ->
        when (response.code) {
          200 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RegistrationNetworkResult.Success(session)
          }
          400 -> {
            RegistrationNetworkResult.Failure(SubmitVerificationCodeError.InvalidSessionIdOrVerificationCode(response.body.string()))
          }
          404 -> {
            RegistrationNetworkResult.Failure(SubmitVerificationCodeError.SessionNotFound(response.body.string()))
          }
          409 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RegistrationNetworkResult.Failure(SubmitVerificationCodeError.SessionAlreadyVerifiedOrNoCodeRequested(session))
          }
          429 -> {
            val session = json.decodeFromString<SessionMetadata>(response.body.string())
            RegistrationNetworkResult.Failure(SubmitVerificationCodeError.RateLimited(response.retryAfter(), session))
          }
          else -> {
            RegistrationNetworkResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}, body: ${response.body.string()}"))
          }
        }
      }
    } catch (e: IOException) {
      RegistrationNetworkResult.NetworkError(e)
    } catch (e: Exception) {
      RegistrationNetworkResult.ApplicationError(e)
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
  ): RegistrationNetworkResult<RegisterAccountResponse, RegisterAccountError> = withContext(Dispatchers.IO) {
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
            RegistrationNetworkResult.Success(result)
          }
          403 -> {
            RegistrationNetworkResult.Failure(RegisterAccountError.RegistrationRecoveryPasswordIncorrect(response.body.string()))
          }
          409 -> {
            RegistrationNetworkResult.Failure(RegisterAccountError.DeviceTransferPossible)
          }
          422 -> {
            RegistrationNetworkResult.Failure(RegisterAccountError.InvalidRequest(response.body.string()))
          }
          423 -> {
            val lockResponse = json.decodeFromString<RegistrationLockResponse>(response.body.string())
            RegistrationNetworkResult.Failure(RegisterAccountError.RegistrationLock(lockResponse))
          }
          429 -> {
            RegistrationNetworkResult.Failure(RegisterAccountError.RateLimited(response.retryAfter()))
          }
          else -> {
            RegistrationNetworkResult.ApplicationError(IllegalStateException("Unexpected response code: ${response.code}, body: ${response.body.string()}"))
          }
        }
      }
    } catch (e: IOException) {
      RegistrationNetworkResult.NetworkError(e)
    } catch (e: Exception) {
      RegistrationNetworkResult.ApplicationError(e)
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
