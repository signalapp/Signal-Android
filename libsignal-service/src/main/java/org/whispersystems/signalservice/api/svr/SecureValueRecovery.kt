/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.svr

import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.internal.push.AuthCredentials
import java.io.IOException
import kotlin.jvm.Throws

interface SecureValueRecovery {

  /**
   * Which version of SVR this is running against.
   */
  val svrVersion: SvrVersion

  /**
   * Begins a PIN change.
   *
   * Under the hood, setting a PIN is a two-phase process. This is abstracted through the [PinChangeSession].
   * To use it, simply call [PinChangeSession.execute], which will return the result of the operation.
   * If the operation is not successful and warrants a retry, it is extremely important to use the same [PinChangeSession].
   *
   * Do not have any automated retry system that calls [setPin] unconditionally. Always reuse the same [PinChangeSession]
   * for as long as it is still valid (i.e. as long as you're still trying to set the same PIN).
   *
   * @param userPin The user-specified PIN.
   * @param masterKey The data to set on SVR.
   */
  fun setPin(userPin: String, masterKey: MasterKey): PinChangeSession

  /**
   * Resumes a PIN change session that you previously started via [setPin] using serialized data obtained via
   * [PinChangeSession.serialize]. The provided [userPin] and [masterKey] will be checked against the
   * serialized [PinChangeSession], and if the data no longer matches, a new [PinChangeSession] will be created.
   */
  fun resumePinChangeSession(userPin: String, masterKey: MasterKey, serializedChangeSession: String): PinChangeSession

  /**
   * Restores the user's SVR data from the service. Intended to be called in the situation where the user is not yet registered.
   * Currently, this will only happen during a reglock challenge. When in this state, the user is not registered, and will instead
   * be provided credentials in a service response to give the user an opportunity to restore SVR data and generate the reglock proof.
   *
   * If the user is already registered, use [restoreDataPostRegistration].
   *
   * @param shareSet Only used for SVR3, where the value is required. For SVR2, this should be null.
   */
  fun restoreDataPreRegistration(authorization: AuthCredentials, shareSet: ByteArray?, userPin: String): RestoreResponse

  /**
   * Restores data from SVR. Only intended to be called if the user is already registered. If the user is not yet registered, use [restoreDataPreRegistration].
   */
  fun restoreDataPostRegistration(userPin: String): RestoreResponse

  /**
   * Deletes the user's SVR data from the service.
   */
  fun deleteData(): DeleteResponse

  /**
   * Retrieves an auth credential that could be used to talk with the service.
   */
  @Throws(IOException::class)
  fun authorization(): AuthCredentials

  interface PinChangeSession {
    fun execute(): BackupResponse
    fun serialize(): String
  }

  /** Response for setting a PIN. */
  sealed class BackupResponse {
    /** Operation completed successfully. */
    data class Success(val masterKey: MasterKey, val authorization: AuthCredentials, val svrVersion: SvrVersion) : BackupResponse()

    /** The operation failed because the server was unable to expose the backup data we created. There is no further action that can be taken besides logging the error and treating it as a success. */
    object ExposeFailure : BackupResponse()

    /** The target enclave could not be found. */
    object EnclaveNotFound : BackupResponse()

    /** The server rejected the request with a 508. Do not retry. */
    object ServerRejected : BackupResponse()

    /** There as a network error. Not a bad response, but rather interference or some other inability to make a network request. */
    data class NetworkError(val exception: IOException) : BackupResponse()

    /** Something went wrong when making the request that is related to application logic. */
    data class ApplicationError(val exception: Throwable) : BackupResponse()
  }

  /** Response for restoring data with you PIN. */
  sealed class RestoreResponse {
    /** Operation completed successfully. Includes the restored data. */
    data class Success(val masterKey: MasterKey, val authorization: AuthCredentials) : RestoreResponse()

    /** No data was found for this user. Could mean that none ever existed, or that the service deleted the data after too many incorrect PIN guesses. */
    object Missing : RestoreResponse()

    /** The PIN was incorrect. Includes the number of attempts the user has remaining. */
    data class PinMismatch(val triesRemaining: Int) : RestoreResponse()

    /** There as a network error. Not a bad response, but rather interference or some other inability to make a network request. */
    data class NetworkError(val exception: IOException) : RestoreResponse()

    /** Something went wrong when making the request that is related to application logic. */
    data class ApplicationError(val exception: Throwable) : RestoreResponse()
  }

  /** Response for deleting data. */
  sealed class DeleteResponse {
    /** Operation completed successfully. */
    object Success : DeleteResponse()

    /** The target enclave could not be found. */
    object EnclaveNotFound : DeleteResponse()

    /** The server rejected the request with a 508. Do not retry. */
    object ServerRejected : DeleteResponse()

    /** There as a network error. Not a bad response, but rather interference or some other inability to make a network request. */
    data class NetworkError(val exception: IOException) : DeleteResponse()

    /** Something went wrong when making the request that is related to application logic. */
    data class ApplicationError(val exception: Throwable) : DeleteResponse()
  }

  /** Exception indicating that we received a response from the service that our request was invalid. */
  class InvalidRequestException(message: String) : Exception(message)

  enum class SvrVersion {
    SVR2,
    SVR3
  }
}
