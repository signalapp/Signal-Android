/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.svr

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
import org.whispersystems.signalservice.internal.push.AuthCredentials
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

/**
 * An interface for working with V3 of the Secure Value Recovery service.
 */
class SecureValueRecoveryV3(
  private val network: Network,
  private val pushServiceSocket: PushServiceSocket,
  private val shareSetStorage: ShareSetStorage
) : SecureValueRecovery {

  companion object {
    val TAG = Log.tag(SecureValueRecoveryV3::class)
  }

  override fun setPin(userPin: String, masterKey: MasterKey): PinChangeSession {
    return Svr3PinChangeSession(userPin, masterKey)
  }

  /**
   * Note: Unlike SVR2, there is no concept of "resuming", so this is equivalent to starting a new session.
   */
  override fun resumePinChangeSession(userPin: String, masterKey: MasterKey, serializedChangeSession: String): PinChangeSession {
    return Svr3PinChangeSession(userPin, masterKey)
  }

  override fun restoreDataPreRegistration(authorization: AuthCredentials, userPin: String): RestoreResponse {
    val normalizedPin: String = PinHashUtil.normalizeToString(userPin)
    val shareSet = shareSetStorage.read() ?: return RestoreResponse.ApplicationError(IllegalStateException("No share set found!"))
    val enclaveAuth = EnclaveAuth(authorization.username(), authorization.password())

    return try {
      val result = network.svr3().restore(normalizedPin, shareSet, enclaveAuth).get()
      val masterKey = MasterKey(result)
      RestoreResponse.Success(masterKey, authorization)
    } catch (e: ExecutionException) {
      when (val cause = e.cause) {
        is NetworkException -> RestoreResponse.NetworkError(IOException(cause)) // TODO [svr3] Update when we get to IOException
        is DataMissingException -> RestoreResponse.Missing
        is RestoreFailedException -> RestoreResponse.PinMismatch(1) // TODO [svr3] Get proper API for this
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

  override fun restoreDataPostRegistration(userPin: String): RestoreResponse {
    val authorization: AuthCredentials = try {
      pushServiceSocket.svrAuthorization
    } catch (e: NonSuccessfulResponseCodeException) {
      return RestoreResponse.ApplicationError(e)
    } catch (e: IOException) {
      return RestoreResponse.NetworkError(e)
    } catch (e: Exception) {
      return RestoreResponse.ApplicationError(e)
    }

    return restoreDataPreRegistration(authorization, userPin)
  }

  /**
   * There's no concept of "deleting" data with SVR3.
   */
  override fun deleteData(): DeleteResponse {
    return DeleteResponse.Success
  }

  @Throws(IOException::class)
  override fun authorization(): AuthCredentials {
    return pushServiceSocket.svrAuthorization
  }

  inner class Svr3PinChangeSession(
    private val userPin: String,
    private val masterKey: MasterKey
  ) : PinChangeSession {
    override fun execute(): BackupResponse {
      val normalizedPin: String = PinHashUtil.normalizeToString(userPin)
      val rawAuth = try {
        pushServiceSocket.svrAuthorization
      } catch (e: NonSuccessfulResponseCodeException) {
        return BackupResponse.ApplicationError(e)
      } catch (e: IOException) {
        return BackupResponse.NetworkError(e)
      } catch (e: Exception) {
        return BackupResponse.ApplicationError(e)
      }

      val enclaveAuth = EnclaveAuth(rawAuth.username(), rawAuth.password())

      return try {
        val result = network.svr3().backup(masterKey.serialize(), normalizedPin, 10, enclaveAuth).get()
        shareSetStorage.write(result)
        BackupResponse.Success(masterKey, rawAuth)
      } catch (e: ExecutionException) {
        when (val cause = e.cause) {
          is NetworkException -> BackupResponse.NetworkError(IOException(cause)) // TODO [svr] Update when we move to IOException
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

    override fun serialize(): String {
      // There is no "resuming" SVR3, so we don't need to serialize anything
      return ""
    }
  }

  /**
   * An interface to allow reading and writing the "share set" to persistent storage.
   */
  interface ShareSetStorage {
    fun write(data: ByteArray)
    fun read(): ByteArray?
  }
}
