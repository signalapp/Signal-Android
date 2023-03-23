package org.whispersystems.signalservice.api.svr

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import okio.ByteString.Companion.toByteString
import org.signal.libsignal.svr2.PinHash
import org.signal.svr2.proto.BackupRequest
import org.signal.svr2.proto.DeleteRequest
import org.signal.svr2.proto.Request
import org.signal.svr2.proto.RestoreRequest
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.api.kbs.PinHashUtil
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import java.io.IOException
import org.signal.svr2.proto.BackupResponse as ProtoBackupResponse
import org.signal.svr2.proto.RestoreResponse as ProtoRestoreResponse

/**
 * An interface for working with V2 of the Secure Value Recovery service.
 */
class SecureValueRecoveryV2(
  private val serviceConfiguration: SignalServiceConfiguration,
  private val mrEnclave: String,
  private val pushServiceSocket: PushServiceSocket
) {

  /**
   * Sets the provided data on the SVR service with the provided PIN.
   *
   * @param pin The user-specified PIN.
   * @param masterKey The data to set on SVR.
   */
  fun setPin(pin: PinHash, masterKey: MasterKey): Single<BackupResponse> {
    val data = PinHashUtil.createNewKbsData(pin, masterKey)

    val request = Request(
      backup = BackupRequest(
        pin = data.kbsAccessKey.toByteString(),
        data_ = data.cipherText.toByteString(),
        maxTries = 10
      )
    )

    return getAuthorization()
      .flatMap { auth -> Svr2Socket(serviceConfiguration, mrEnclave).makeRequest(auth, request) }
      .map { response ->
        when (response.backup?.status) {
          ProtoBackupResponse.Status.OK -> {
            BackupResponse.Success
          }
          ProtoBackupResponse.Status.REQUEST_INVALID -> {
            BackupResponse.ApplicationError(InvalidRequestException("BackupResponse returned status code for REQUEST_INVALID"))
          }
          else -> {
            BackupResponse.ApplicationError(IllegalStateException("Unknown status: ${response.backup?.status}"))
          }
        }
      }
      .onErrorReturn { throwable ->
        when (throwable) {
          is NonSuccessfulResponseCodeException -> BackupResponse.ApplicationError(throwable)
          is IOException -> BackupResponse.NetworkError(throwable)
          else -> BackupResponse.ApplicationError(throwable)
        }
      }
      .subscribeOn(Schedulers.io())
  }

  /**
   * Restores the user's SVR data from the service. Intended to be called in the situation where the user is not yet registered.
   * Currently, this will only happen during a reglock challenge. When in this state, the user is not registered, and will instead
   * be provided credentials in a service response to give the user an opportunity to restore SVR data and generate the reglock proof.
   *
   * If the user is already registered, use [restoreDataPostRegistration]
   */
  fun restoreDataPreRegistration(authorization: String, pinHash: PinHash): Single<RestoreResponse> {
    return restoreData(Single.just(authorization), pinHash)
  }

  /**
   * Restores data from SVR. Only intended to be called if the user is already registered. If the user is not yet registered, use [restoreDataPreRegistration]
   */
  fun restoreDataPostRegistration(pinHash: PinHash): Single<RestoreResponse> {
    return restoreData(getAuthorization(), pinHash)
  }

  /**
   * Deletes the user's SVR data from the service.
   */
  fun deleteData(): Single<DeleteResponse> {
    val request = Request(delete = DeleteRequest())

    return getAuthorization()
      .flatMap { auth -> Svr2Socket(serviceConfiguration, mrEnclave).makeRequest(auth, request) }
      .map { DeleteResponse.Success as DeleteResponse }
      .onErrorReturn { throwable ->
        when (throwable) {
          is NonSuccessfulResponseCodeException -> DeleteResponse.ApplicationError(throwable)
          is IOException -> DeleteResponse.NetworkError(throwable)
          else -> DeleteResponse.ApplicationError(throwable)
        }
      }
      .subscribeOn(Schedulers.io())
  }

  private fun restoreData(authorization: Single<String>, pinHash: PinHash): Single<RestoreResponse> {
    val request = Request(
      restore = RestoreRequest(pin = pinHash.accessKey().toByteString())
    )

    return authorization
      .flatMap { auth -> Svr2Socket(serviceConfiguration, mrEnclave).makeRequest(auth, request) }
      .map { response ->
        when (response.restore?.status) {
          ProtoRestoreResponse.Status.OK -> {
            val ciphertext: ByteArray = response.restore.data_.toByteArray()
            try {
              val masterKey: MasterKey = PinHashUtil.decryptKbsDataIVCipherText(pinHash, ciphertext).masterKey
              RestoreResponse.Success(masterKey)
            } catch (e: InvalidCiphertextException) {
              RestoreResponse.ApplicationError(e)
            }
          }
          ProtoRestoreResponse.Status.MISSING -> {
            RestoreResponse.Missing
          }
          ProtoRestoreResponse.Status.PIN_MISMATCH -> {
            RestoreResponse.PinMismatch(response.restore.tries)
          }
          ProtoRestoreResponse.Status.REQUEST_INVALID -> {
            RestoreResponse.ApplicationError(InvalidRequestException("RestoreResponse returned status code for REQUEST_INVALID"))
          }
          else -> {
            RestoreResponse.ApplicationError(IllegalStateException("Unknown status: ${response.backup?.status}"))
          }
        }
      }
      .onErrorReturn { throwable ->
        when (throwable) {
          is NonSuccessfulResponseCodeException -> RestoreResponse.ApplicationError(throwable)
          is IOException -> RestoreResponse.NetworkError(throwable)
          else -> RestoreResponse.ApplicationError(throwable)
        }
      }
      .subscribeOn(Schedulers.io())
  }

  private fun getAuthorization(): Single<String> {
    return Single.fromCallable { pushServiceSocket.svr2Authorization }
  }

  /** Response for setting a PIN. */
  sealed class BackupResponse {
    /** Operation completed successfully. */
    object Success : BackupResponse()

    /** There as a network error. Not a bad response, but rather interference or some other inability to make a network request. */
    data class NetworkError(val exception: IOException) : BackupResponse()

    /** Something went wrong when making the request that is related to application logic. */
    data class ApplicationError(val exception: Throwable) : BackupResponse()
  }

  /** Response for restoring data with you PIN. */
  sealed class RestoreResponse {
    /** Operation completed successfully. Includes the restored data. */
    data class Success(val masterKey: MasterKey) : RestoreResponse()

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

    /** There as a network error. Not a bad response, but rather interference or some other inability to make a network request. */
    data class NetworkError(val exception: IOException) : DeleteResponse()

    /** Something went wrong when making the request that is related to application logic. */
    data class ApplicationError(val exception: Throwable) : DeleteResponse()
  }

  /** Exception indicating that we received a response from the service that our request was invalid. */
  class InvalidRequestException(message: String) : Exception(message)
}
