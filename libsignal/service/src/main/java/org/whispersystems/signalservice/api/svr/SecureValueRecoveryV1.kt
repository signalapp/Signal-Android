/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.svr

import org.signal.libsignal.svr2.PinHash
import org.whispersystems.signalservice.api.KeyBackupService
import org.whispersystems.signalservice.api.KeyBackupServicePinException
import org.whispersystems.signalservice.api.SvrNoDataException
import org.whispersystems.signalservice.api.SvrPinData
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.api.kbs.PinHashUtil
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.BackupResponse
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.DeleteResponse
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.PinChangeSession
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.RestoreResponse
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException
import org.whispersystems.signalservice.internal.push.AuthCredentials
import java.io.IOException
import kotlin.jvm.Throws

/**
 * An implementation of the [SecureValueRecovery] interface backed by the [KeyBackupService].
 */
class SecureValueRecoveryV1(private val kbs: KeyBackupService) : SecureValueRecovery {

  companion object {
    const val TAG = "SVR1"
  }

  override fun setPin(userPin: String, masterKey: MasterKey): PinChangeSession {
    return Svr1PinChangeSession(userPin, masterKey)
  }

  override fun resumePinChangeSession(userPin: String, masterKey: MasterKey, serializedChangeSession: String): PinChangeSession {
    return setPin(userPin, masterKey)
  }

  override fun restoreDataPreRegistration(authorization: AuthCredentials, userPin: String): RestoreResponse {
    return restoreData({ authorization }, userPin)
  }

  override fun restoreDataPostRegistration(userPin: String): RestoreResponse {
    return restoreData({ kbs.authorization }, userPin)
  }

  override fun deleteData(): DeleteResponse {
    return try {
      kbs.newPinChangeSession().removePin()
      DeleteResponse.Success
    } catch (e: UnauthenticatedResponseException) {
      DeleteResponse.ApplicationError(e)
    } catch (e: NonSuccessfulResponseCodeException) {
      when (e.code) {
        404 -> DeleteResponse.EnclaveNotFound
        508 -> DeleteResponse.ServerRejected
        else -> DeleteResponse.NetworkError(e)
      }
    } catch (e: IOException) {
      DeleteResponse.NetworkError(e)
    }
  }

  @Throws(IOException::class)
  override fun authorization(): AuthCredentials {
    return kbs.authorization
  }

  override fun toString(): String {
    return "SVR1::${kbs.enclaveName}::${kbs.mrenclave}"
  }

  private fun restoreData(fetchAuthorization: () -> AuthCredentials, userPin: String): RestoreResponse {
    return try {
      val authorization: AuthCredentials = fetchAuthorization()
      val session = kbs.newRegistrationSession(authorization.asBasic(), null)
      val pinHash: PinHash = PinHashUtil.hashPin(userPin, session.hashSalt())

      val data: SvrPinData = session.restorePin(pinHash)
      RestoreResponse.Success(data.masterKey, authorization)
    } catch (e: SvrNoDataException) {
      RestoreResponse.Missing
    } catch (e: KeyBackupServicePinException) {
      RestoreResponse.PinMismatch(e.triesRemaining)
    } catch (e: IOException) {
      RestoreResponse.NetworkError(e)
    } catch (e: Exception) {
      RestoreResponse.ApplicationError(e)
    }
  }

  inner class Svr1PinChangeSession(
    private val userPin: String,
    private val masterKey: MasterKey
  ) : PinChangeSession {
    override fun execute(): BackupResponse {
      return try {
        val session = kbs.newPinChangeSession()
        val pinHash: PinHash = PinHashUtil.hashPin(userPin, session.hashSalt())

        val data: SvrPinData = session.setPin(pinHash, masterKey)
        BackupResponse.Success(data.masterKey, kbs.authorization)
      } catch (e: UnauthenticatedResponseException) {
        BackupResponse.ApplicationError(e)
      } catch (e: NonSuccessfulResponseCodeException) {
        when (e.code) {
          404 -> BackupResponse.EnclaveNotFound
          508 -> BackupResponse.ServerRejected
          else -> BackupResponse.NetworkError(e)
        }
      } catch (e: IOException) {
        BackupResponse.NetworkError(e)
      }
    }

    /** No real need to serialize */
    override fun serialize(): String {
      return ""
    }
  }
}
