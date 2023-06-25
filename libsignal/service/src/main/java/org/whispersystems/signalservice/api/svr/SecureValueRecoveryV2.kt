package org.whispersystems.signalservice.api.svr

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import okio.ByteString.Companion.toByteString
import org.signal.svr2.proto.BackupRequest
import org.signal.svr2.proto.DeleteRequest
import org.signal.svr2.proto.ExposeRequest
import org.signal.svr2.proto.Request
import org.signal.svr2.proto.RestoreRequest
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.api.kbs.PinHashUtil
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.BackupResponse
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.DeleteResponse
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.InvalidRequestException
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.PinChangeSession
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.RestoreResponse
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.push.AuthCredentials
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import java.io.IOException
import org.signal.svr2.proto.BackupResponse as ProtoBackupResponse
import org.signal.svr2.proto.ExposeResponse as ProtoExposeResponse
import org.signal.svr2.proto.RestoreResponse as ProtoRestoreResponse

/**
 * An interface for working with V2 of the Secure Value Recovery service.
 */
class SecureValueRecoveryV2(
  private val serviceConfiguration: SignalServiceConfiguration,
  private val mrEnclave: String,
  private val pushServiceSocket: PushServiceSocket
) : SecureValueRecovery {

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
   * @param pin The user-specified PIN.
   * @param masterKey The data to set on SVR.
   */
  override fun setPin(userPin: String, masterKey: MasterKey): PinChangeSession {
    return Svr2PinChangeSession(userPin, masterKey)
  }

  /**
   * Restores the user's SVR data from the service. Intended to be called in the situation where the user is not yet registered.
   * Currently, this will only happen during a reglock challenge. When in this state, the user is not registered, and will instead
   * be provided credentials in a service response to give the user an opportunity to restore SVR data and generate the reglock proof.
   *
   * If the user is already registered, use [restoreDataPostRegistration]
   */
  override fun restoreDataPreRegistration(authorization: AuthCredentials, userPin: String): Single<RestoreResponse> {
    return restoreData(Single.just(authorization), userPin)
  }

  /**
   * Restores data from SVR. Only intended to be called if the user is already registered. If the user is not yet registered, use [restoreDataPreRegistration]
   */
  override fun restoreDataPostRegistration(userPin: String): Single<RestoreResponse> {
    return restoreData(getAuthorization(), userPin)
  }

  /**
   * Deletes the user's SVR data from the service.
   */
  override fun deleteData(): Single<DeleteResponse> {
    val request: (Svr2PinHasher) -> Request = { Request(delete = DeleteRequest()) }

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

  private fun restoreData(authorization: Single<AuthCredentials>, userPin: String): Single<RestoreResponse> {
    val normalizedPin: ByteArray = PinHashUtil.normalize(userPin)

    return authorization
      .flatMap { auth ->
        Svr2Socket(serviceConfiguration, mrEnclave).makeRequest(auth) { pinHasher ->
          val pinHash = pinHasher.hash(normalizedPin)

          Request(
            restore = RestoreRequest(
              pin = pinHash.accessKey().toByteString()
            )
          )
        }
      }
      .map { (response, pinHasher) ->
        when (response.restore?.status) {
          ProtoRestoreResponse.Status.OK -> {
            val ciphertext: ByteArray = response.restore.data_.toByteArray()
            try {
              val pinHash = pinHasher.hash(normalizedPin)
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

  private fun getAuthorization(): Single<AuthCredentials> {
    return Single.fromCallable { pushServiceSocket.svr2Authorization }
  }

  /**
   * This class is responsible for doing all the work necessary for changing a PIN.
   *
   * It's primary purpose is to serve as an abstraction over the fact that there are actually two separate requests that need to be made:
   *
   * (1) Create the backup data (which resets the guess count), and
   * (2) Expose that data, making it eligible to be restored.
   *
   * The first should _never_ be retried after it completes successfully, and this class will help ensure that doesn't happen by doing the
   * proper bookkeeping.
   */
  inner class Svr2PinChangeSession(
    val userPin: String,
    val masterKey: MasterKey,
    private var setupComplete: Boolean = false
  ) : PinChangeSession {

    /**
     * Performs the PIN change operation. This is safe to call repeatedly if you get back a retryable error.
     */
    override fun execute(): Single<BackupResponse> {
      val normalizedPin: ByteArray = PinHashUtil.normalize(userPin)

      return getAuthorization()
        .flatMap { auth ->
          if (setupComplete) {
            Single.just(auth to ProtoBackupResponse(status = ProtoBackupResponse.Status.OK))
          } else {
            getBackupResponse(auth, normalizedPin).map { auth to it }
          }
        }
        .doOnSuccess { (_, response) ->
          if (response.status == ProtoBackupResponse.Status.OK) {
            setupComplete = true
          }
        }
        .flatMap { (auth, response) ->
          when (response.status) {
            ProtoBackupResponse.Status.OK -> {
              getExposeResponse(auth, normalizedPin)
            }
            ProtoBackupResponse.Status.REQUEST_INVALID -> {
              Single.just(BackupResponse.ApplicationError(InvalidRequestException("BackupResponse returned status code for REQUEST_INVALID")))
            }
            else -> {
              Single.just(BackupResponse.ApplicationError(IllegalStateException("Unknown status: ${response.status}")))
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

    private fun getBackupResponse(authorization: AuthCredentials, normalizedPin: ByteArray): Single<ProtoBackupResponse> {
      val request: (Svr2PinHasher) -> Request = { pinHasher ->
        val hashedPin = pinHasher.hash(normalizedPin)
        val data = PinHashUtil.createNewKbsData(hashedPin, masterKey)

        Request(
          backup = BackupRequest(
            pin = data.kbsAccessKey.toByteString(),
            data_ = data.cipherText.toByteString(),
            maxTries = 10
          )
        )
      }

      return Svr2Socket(serviceConfiguration, mrEnclave)
        .makeRequest(authorization, request)
        .map { (response, _) -> response.backup ?: throw IllegalStateException("Backup response not set!") }
    }

    private fun getExposeResponse(authorization: AuthCredentials, normalizedPin: ByteArray): Single<BackupResponse> {
      val request: (Svr2PinHasher) -> Request = { pinHasher ->
        val hashedPin = pinHasher.hash(normalizedPin)
        val data = PinHashUtil.createNewKbsData(hashedPin, masterKey)

        Request(
          expose = ExposeRequest(
            data_ = data.cipherText.toByteString()
          )
        )
      }

      return Svr2Socket(serviceConfiguration, mrEnclave)
        .makeRequest(authorization, request)
        .map { (response, _) ->
          when (response.expose?.status) {
            ProtoExposeResponse.Status.OK -> {
              BackupResponse.Success(masterKey)
            }
            ProtoExposeResponse.Status.ERROR -> {
              BackupResponse.ExposeFailure
            }
            else -> {
              BackupResponse.ApplicationError(IllegalStateException("Backup response not set!"))
            }
          }
        }
    }
  }
}
