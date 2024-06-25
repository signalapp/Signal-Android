package org.whispersystems.signalservice.api.svr

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import okio.ByteString.Companion.toByteString
import org.signal.libsignal.protocol.logging.Log
import org.signal.libsignal.svr2.PinHash
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
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.SvrVersion
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.push.AuthCredentials
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import org.whispersystems.signalservice.internal.util.Hex
import org.whispersystems.signalservice.internal.util.JsonUtil
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

  companion object {
    private val TAG = SecureValueRecoveryV2::class.java.simpleName
  }

  override val svrVersion: SvrVersion = SvrVersion.SVR2

  override fun setPin(userPin: String, masterKey: MasterKey): PinChangeSession {
    return Svr2PinChangeSession(userPin, masterKey)
  }

  override fun resumePinChangeSession(userPin: String, masterKey: MasterKey, serializedChangeSession: String): PinChangeSession {
    val data: Svr2SessionData = JsonUtil.fromJson(serializedChangeSession, Svr2SessionData::class.java)

    if (data.userPin == userPin && data.masterKey == masterKey) {
      return Svr2PinChangeSession(data.userPin, data.masterKey, data.setupComplete)
    } else {
      return setPin(userPin, masterKey)
    }
  }

  override fun restoreDataPreRegistration(authorization: AuthCredentials, shareSet: ByteArray?, userPin: String): RestoreResponse {
    return restoreData({ authorization }, userPin)
  }

  override fun restoreDataPostRegistration(userPin: String): RestoreResponse {
    return restoreData({ authorization() }, userPin)
  }

  override fun deleteData(): DeleteResponse {
    val request = Request(delete = DeleteRequest())

    return try {
      val authorization: AuthCredentials = authorization()

      // noinspection CheckResult The only possible result is a successful one
      Svr2Socket(serviceConfiguration, mrEnclave).makeRequest(authorization, request)

      DeleteResponse.Success
    } catch (e: NonSuccessfulResponseCodeException) {
      Log.w(TAG, "[Delete] Failed with a non-successful response code exception!", e)
      DeleteResponse.ApplicationError(e)
    } catch (e: IOException) {
      Log.w(TAG, "[Delete] Failed with a network exception!", e)
      DeleteResponse.NetworkError(e)
    } catch (e: Exception) {
      Log.w(TAG, "[Delete] Failed with a generic exception!", e)
      DeleteResponse.ApplicationError(e)
    }
  }

  @Throws(IOException::class)
  override fun authorization(): AuthCredentials {
    return pushServiceSocket.svr2Authorization
  }

  override fun toString(): String {
    return "SVR2::$mrEnclave"
  }

  private fun restoreData(fetchAuth: () -> AuthCredentials, userPin: String): RestoreResponse {
    val normalizedPin: ByteArray = PinHashUtil.normalize(userPin)

    return try {
      val authorization: AuthCredentials = fetchAuth()

      val response = Svr2Socket(serviceConfiguration, mrEnclave).makeRequest(
        authorization = fetchAuth(),
        clientRequest = Request(
          restore = RestoreRequest(
            pin = PinHash.svr2(normalizedPin, authorization.username(), Hex.fromStringCondensed(mrEnclave)).accessKey().toByteString()
          )
        )
      )

      when (response.restore?.status) {
        ProtoRestoreResponse.Status.OK -> {
          val ciphertext: ByteArray = response.restore.data_.toByteArray()
          try {
            val pinHash = PinHash.svr2(normalizedPin, authorization.username(), Hex.fromStringCondensed(mrEnclave))
            val masterKey: MasterKey = PinHashUtil.decryptSvrDataIVCipherText(pinHash, ciphertext).masterKey
            RestoreResponse.Success(masterKey, authorization)
          } catch (e: InvalidCiphertextException) {
            Log.w(TAG, "[Restore] Failed with an invalid cipher text exception!", e)
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
    } catch (e: NonSuccessfulResponseCodeException) {
      Log.w(TAG, "[Restore] Failed with a non-successful response code exception!", e)
      RestoreResponse.ApplicationError(e)
    } catch (e: IOException) {
      Log.w(TAG, "[Restore] Failed with a network exception!", e)
      RestoreResponse.NetworkError(e)
    } catch (e: Exception) {
      Log.w(TAG, "[Restore] Failed with a generic exception!", e)
      RestoreResponse.ApplicationError(e)
    }
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
    @JsonProperty("user_pin")
    val userPin: String,

    @JsonProperty("master_key")
    @JsonSerialize(using = JsonUtil.MasterKeySerializer::class)
    @JsonDeserialize(using = JsonUtil.MasterKeyDeserializer::class)
    val masterKey: MasterKey,

    @JsonProperty("setup_complete")
    private var setupComplete: Boolean = false
  ) : PinChangeSession {

    /**
     * Performs the PIN change operation. This is safe to call repeatedly if you get back a retryable error.
     */
    override fun execute(): BackupResponse {
      val normalizedPin: ByteArray = PinHashUtil.normalize(userPin)

      return try {
        val authorization: AuthCredentials = authorization()
        val response: ProtoBackupResponse = if (setupComplete) {
          ProtoBackupResponse(status = ProtoBackupResponse.Status.OK)
        } else {
          getBackupResponse(authorization, normalizedPin)
        }.also {
          setupComplete = true
        }

        when (response.status) {
          ProtoBackupResponse.Status.OK -> {
            getExposeResponse(authorization, normalizedPin)
          }
          ProtoBackupResponse.Status.REQUEST_INVALID -> {
            BackupResponse.ApplicationError(InvalidRequestException("BackupResponse returned status code for REQUEST_INVALID"))
          }
          else -> {
            BackupResponse.ApplicationError(IllegalStateException("Unknown status: ${response.status}"))
          }
        }
      } catch (e: NonSuccessfulResponseCodeException) {
        Log.w(TAG, "[Set] Failed with a non-successful response code exception!", e)
        BackupResponse.ApplicationError(e)
      } catch (e: IOException) {
        Log.w(TAG, "[Set] Failed with a network exception!", e)
        BackupResponse.NetworkError(e)
      } catch (e: Exception) {
        Log.w(TAG, "[Set] Failed with a generic exception!", e)
        BackupResponse.ApplicationError(e)
      }
    }

    override fun serialize(): String {
      return JsonUtil.toJson(Svr2SessionData(userPin, masterKey, setupComplete))
    }

    private fun getBackupResponse(authorization: AuthCredentials, normalizedPin: ByteArray): ProtoBackupResponse {
      val hashedPin = PinHash.svr2(normalizedPin, authorization.username(), Hex.fromStringCondensed(mrEnclave))
      val data = PinHashUtil.createNewKbsData(hashedPin, masterKey)
      val request = Request(
        backup = BackupRequest(
          pin = data.kbsAccessKey.toByteString(),
          data_ = data.cipherText.toByteString(),
          maxTries = 10
        )
      )

      return Svr2Socket(serviceConfiguration, mrEnclave)
        .makeRequest(authorization, request)
        .let { response -> response.backup ?: throw IllegalStateException("Backup response not set!") }
    }

    private fun getExposeResponse(authorization: AuthCredentials, normalizedPin: ByteArray): BackupResponse {
      val hashedPin = PinHash.svr2(normalizedPin, authorization.username(), Hex.fromStringCondensed(mrEnclave))
      val data = PinHashUtil.createNewKbsData(hashedPin, masterKey)
      val request = Request(
        expose = ExposeRequest(
          data_ = data.cipherText.toByteString()
        )
      )

      return Svr2Socket(serviceConfiguration, mrEnclave)
        .makeRequest(authorization, request)
        .let { response ->
          when (response.expose?.status) {
            ProtoExposeResponse.Status.OK -> {
              BackupResponse.Success(masterKey, authorization, SvrVersion.SVR2)
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

  data class Svr2SessionData(
    @JsonProperty("user_pin")
    val userPin: String,

    @JsonProperty("master_key")
    @JsonSerialize(using = JsonUtil.MasterKeySerializer::class)
    @JsonDeserialize(using = JsonUtil.MasterKeyDeserializer::class)
    val masterKey: MasterKey,

    @JsonProperty("setup_complete")
    val setupComplete: Boolean = false
  )
}
