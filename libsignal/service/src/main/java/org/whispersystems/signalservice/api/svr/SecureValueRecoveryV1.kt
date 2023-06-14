/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.svr

import io.reactivex.rxjava3.core.Single
import org.signal.libsignal.svr2.PinHash
import org.whispersystems.signalservice.api.KbsPinData
import org.whispersystems.signalservice.api.KeyBackupService
import org.whispersystems.signalservice.api.KeyBackupServicePinException
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException
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

/**
 * An implementation of the [SecureValueRecovery] interface backed by the [KeyBackupService].
 */
class SecureValueRecoveryV1(private val kbs: KeyBackupService) : SecureValueRecovery {

  override fun setPin(userPin: String, masterKey: MasterKey): PinChangeSession {
    return Svr1PinChangeSession(userPin, masterKey)
  }

  override fun restoreDataPreRegistration(authorization: AuthCredentials, userPin: String): Single<RestoreResponse> {
    return restoreData(Single.just(authorization.asBasic()), userPin)
  }

  override fun restoreDataPostRegistration(userPin: String): Single<RestoreResponse> {
    return restoreData(Single.fromCallable { kbs.authorization }, userPin)
  }

  override fun deleteData(): Single<DeleteResponse> {
    return Single.fromCallable {
      try {
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
  }

  private fun restoreData(authorization: Single<String>, userPin: String): Single<RestoreResponse> {
    return authorization
      .flatMap { auth ->
        Single.fromCallable {
          try {
            val session = kbs.newRegistrationSession(auth, null)
            val pinHash: PinHash = PinHashUtil.hashPin(userPin, session.hashSalt())

            val data: KbsPinData = session.restorePin(pinHash)
            RestoreResponse.Success(data.masterKey)
          } catch (e: KeyBackupSystemNoDataException) {
            RestoreResponse.Missing
          } catch (e: KeyBackupServicePinException) {
            RestoreResponse.PinMismatch(e.triesRemaining)
          } catch (e: IOException) {
            RestoreResponse.NetworkError(e)
          }
        }
      }
  }

  inner class Svr1PinChangeSession(
    private val userPin: String,
    private val masterKey: MasterKey
  ) : PinChangeSession {
    override fun execute(): Single<BackupResponse> {
      return Single.fromCallable {
        try {
          val session = kbs.newPinChangeSession()
          val pinHash: PinHash = PinHashUtil.hashPin(userPin, session.hashSalt())

          val data: KbsPinData = session.setPin(pinHash, masterKey)
          BackupResponse.Success(data.masterKey)
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
    }
  }
}
