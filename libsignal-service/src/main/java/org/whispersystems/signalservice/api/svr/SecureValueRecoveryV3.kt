/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.svr

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.signal.core.util.logging.Log
import org.signal.libsignal.attest.AttestationFailedException
import org.signal.libsignal.net.EnclaveAuth
import org.signal.libsignal.net.Network
import org.signal.libsignal.net.NetworkException
import org.signal.libsignal.sgxsession.SgxCommunicationFailureException
import org.signal.libsignal.svr.DataMissingException
import org.signal.libsignal.svr.RestoreFailedException
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.api.kbs.PinHashUtil
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.BackupResponse
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.DeleteResponse
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.PinChangeSession
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.RestoreResponse
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.SvrVersion
import org.whispersystems.signalservice.internal.push.AuthCredentials
import org.whispersystems.signalservice.internal.push.ByteArrayDeserializerBase64
import org.whispersystems.signalservice.internal.push.ByteArraySerializerBase64NoPadding
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import org.whispersystems.signalservice.internal.util.JsonUtil
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

/**
 * An interface for working with V3 of the Secure Value Recovery service.
 */
class SecureValueRecoveryV3(
  private val network: Network,
  private val pushServiceSocket: PushServiceSocket
) : SecureValueRecovery {

  companion object {
    val TAG = Log.tag(SecureValueRecoveryV3::class)
  }

  override val svrVersion: SvrVersion = SvrVersion.SVR3

  override fun setPin(userPin: String, masterKey: MasterKey): PinChangeSession {
    return Svr3PinChangeSession(userPin, masterKey, null)
  }

  /**
   * Note: Unlike SVR2, there is no concept of "resuming", so this is equivalent to starting a new session.
   */
  override fun resumePinChangeSession(userPin: String, masterKey: MasterKey, serializedChangeSession: String): PinChangeSession {
    val data: Svr3SessionData = JsonUtil.fromJson(serializedChangeSession, Svr3SessionData::class.java)

    return if (data.userPin == userPin && data.masterKey == masterKey) {
      Svr3PinChangeSession(userPin, masterKey, data.shareSet)
    } else {
      Svr3PinChangeSession(userPin, masterKey, null)
    }
  }

  override fun restoreDataPreRegistration(authorization: AuthCredentials, shareSet: ByteArray?, userPin: String): RestoreResponse {
    return restoreData(authorization, shareSet, userPin)
  }

  override fun restoreDataPostRegistration(userPin: String): RestoreResponse {
    val authorization: Svr3Credentials = try {
      pushServiceSocket.svr3Authorization
    } catch (e: NonSuccessfulResponseCodeException) {
      return RestoreResponse.ApplicationError(e)
    } catch (e: IOException) {
      return RestoreResponse.NetworkError(e)
    } catch (e: Exception) {
      return RestoreResponse.ApplicationError(e)
    }

    return restoreData(authorization.toAuthCredential(), authorization.shareSet, userPin)
  }

  /**
   * There's no concept of "deleting" data with SVR3.
   */
  override fun deleteData(): DeleteResponse {
    val authorization: Svr3Credentials = try {
      pushServiceSocket.svr3Authorization
    } catch (e: NonSuccessfulResponseCodeException) {
      return DeleteResponse.ApplicationError(e)
    } catch (e: IOException) {
      return DeleteResponse.NetworkError(e)
    } catch (e: Exception) {
      return DeleteResponse.ApplicationError(e)
    }

    val enclaveAuth = EnclaveAuth(authorization.username, authorization.password)

    return try {
      network.svr3().remove(enclaveAuth).get()
      DeleteResponse.Success
    } catch (e: ExecutionException) {
      when (val cause = e.cause) {
        is NetworkException -> DeleteResponse.NetworkError(cause)
        is AttestationFailedException -> DeleteResponse.ApplicationError(cause)
        is SgxCommunicationFailureException -> DeleteResponse.ApplicationError(cause)
        is IOException -> DeleteResponse.NetworkError(cause)
        else -> DeleteResponse.ApplicationError(cause ?: RuntimeException("Unknown!"))
      }
    } catch (e: InterruptedException) {
      DeleteResponse.ApplicationError(e)
    } catch (e: CancellationException) {
      DeleteResponse.ApplicationError(e)
    }
  }

  @Throws(IOException::class)
  override fun authorization(): AuthCredentials {
    return pushServiceSocket.svr3Authorization.toAuthCredential()
  }

  override fun toString(): String {
    return "SVR3"
  }

  private fun restoreData(authorization: AuthCredentials, shareSet: ByteArray?, userPin: String): RestoreResponse {
    if (shareSet == null) {
      Log.w(TAG, "No share set provided! Assuming no data to restore.")
      return RestoreResponse.Missing
    }

    val normalizedPin: String = PinHashUtil.normalizeToString(userPin)
    val enclaveAuth = EnclaveAuth(authorization.username(), authorization.password())

    return try {
      val result = network.svr3().restore(normalizedPin, shareSet, enclaveAuth).get()
      val masterKey = MasterKey(result.value)
      RestoreResponse.Success(masterKey, authorization)
    } catch (e: ExecutionException) {
      when (val cause = e.cause) {
        is NetworkException -> RestoreResponse.NetworkError(cause)
        is DataMissingException -> RestoreResponse.Missing
        is RestoreFailedException -> RestoreResponse.PinMismatch(cause.triesRemaining)
        is AttestationFailedException -> RestoreResponse.ApplicationError(cause)
        is SgxCommunicationFailureException -> RestoreResponse.ApplicationError(cause)
        is IOException -> RestoreResponse.NetworkError(cause)
        else -> RestoreResponse.ApplicationError(cause ?: RuntimeException("Unknown!"))
      }
    } catch (e: InterruptedException) {
      return RestoreResponse.ApplicationError(e)
    } catch (e: CancellationException) {
      return RestoreResponse.ApplicationError(e)
    }
  }

  private fun Svr3Credentials.toAuthCredential(): AuthCredentials {
    return AuthCredentials.create(username, password)
  }

  inner class Svr3PinChangeSession(
    private val userPin: String,
    private val masterKey: MasterKey,
    private var shareSet: ByteArray?
  ) : PinChangeSession {

    /**
     * Performs the PIN change operation. This is safe to call repeatedly if you get back a retryable error.
     */
    override fun execute(): BackupResponse {
      val rawAuth: Svr3Credentials = try {
        pushServiceSocket.svr3Authorization
      } catch (e: NonSuccessfulResponseCodeException) {
        return BackupResponse.ApplicationError(e)
      } catch (e: IOException) {
        return BackupResponse.NetworkError(e)
      } catch (e: Exception) {
        return BackupResponse.ApplicationError(e)
      }

      if (shareSet == null) {
        val normalizedPin: String = PinHashUtil.normalizeToString(userPin)
        val enclaveAuth = EnclaveAuth(rawAuth.username, rawAuth.password)

        try {
          shareSet = network.svr3().backup(masterKey.serialize(), normalizedPin, 10, enclaveAuth).get()
        } catch (e: ExecutionException) {
          when (val cause = e.cause) {
            is NetworkException -> BackupResponse.NetworkError(cause)
            is AttestationFailedException -> BackupResponse.ApplicationError(cause)
            is SgxCommunicationFailureException -> BackupResponse.ApplicationError(cause)
            is IOException -> BackupResponse.NetworkError(cause)
            else -> BackupResponse.ApplicationError(cause ?: RuntimeException("Unknown!"))
          }
        } catch (e: InterruptedException) {
          BackupResponse.ApplicationError(e)
        } catch (e: CancellationException) {
          BackupResponse.ApplicationError(e)
        }
      }

      return try {
        pushServiceSocket.setShareSet(shareSet)
        BackupResponse.Success(masterKey, pushServiceSocket.svr3Authorization.toAuthCredential(), SvrVersion.SVR3)
      } catch (e: NonSuccessfulResponseCodeException) {
        BackupResponse.ApplicationError(e)
      } catch (e: IOException) {
        BackupResponse.NetworkError(e)
      } catch (e: Exception) {
        return BackupResponse.ApplicationError(e)
      }
    }

    override fun serialize(): String {
      return JsonUtil.toJson(Svr3SessionData(userPin, masterKey, shareSet))
    }
  }

  class Svr3SessionData(
    @JsonProperty
    val userPin: String,

    @JsonProperty
    @JsonSerialize(using = JsonUtil.MasterKeySerializer::class)
    @JsonDeserialize(using = JsonUtil.MasterKeyDeserializer::class)
    val masterKey: MasterKey,

    @JsonProperty
    @JsonSerialize(using = ByteArraySerializerBase64NoPadding::class)
    @JsonDeserialize(using = ByteArrayDeserializerBase64::class)
    val shareSet: ByteArray?
  )
}
