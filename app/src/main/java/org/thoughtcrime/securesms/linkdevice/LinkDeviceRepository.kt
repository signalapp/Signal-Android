package org.thoughtcrime.securesms.linkdevice

import android.net.Uri
import org.signal.core.util.Base64
import org.signal.core.util.Stopwatch
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.logD
import org.signal.core.util.logging.logI
import org.signal.core.util.logging.logW
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.ecc.Curve
import org.thoughtcrime.securesms.backup.v2.ArchiveValidator
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.devicelist.protos.DeviceName
import org.thoughtcrime.securesms.jobs.DeviceNameChangeJob
import org.thoughtcrime.securesms.jobs.LinkedDeviceInactiveCheckJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.registration.secondary.DeviceNameCipher
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.backup.MessageBackupKey
import org.whispersystems.signalservice.api.link.LinkedDeviceVerificationCodeResponse
import org.whispersystems.signalservice.api.link.TransferArchiveError
import org.whispersystems.signalservice.api.link.WaitForLinkedDeviceResponse
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Repository for linked devices and its various actions (linking, unlinking, listing).
 */
object LinkDeviceRepository {

  private val TAG = Log.tag(LinkDeviceRepository::class)

  fun removeDevice(deviceId: Int): Boolean {
    return when (val result = AppDependencies.linkDeviceApi.removeDevice(deviceId)) {
      is NetworkResult.Success -> {
        LinkedDeviceInactiveCheckJob.enqueue()
        true
      }
      else -> {
        Log.w(TAG, "Unable to remove device", result.getCause())
        false
      }
    }
  }

  fun loadDevices(): List<Device>? {
    return when (val result = AppDependencies.linkDeviceApi.getDevices()) {
      is NetworkResult.Success -> {
        result
          .result
          .filter { d: DeviceInfo -> d.getId() != SignalServiceAddress.DEFAULT_DEVICE_ID }
          .map { deviceInfo: DeviceInfo -> deviceInfo.toDevice() }
          .sortedBy { it.createdMillis }
          .toList()
      }
      else -> {
        Log.w(TAG, "Unable to load device", result.getCause())
        null
      }
    }
  }

  fun WaitForLinkedDeviceResponse.getPlaintextDeviceName(): String {
    val response = this
    return DeviceInfo().apply {
      id = response.id
      name = response.name
      created = response.created
      lastSeen = response.lastSeen
    }.toDevice().name ?: ""
  }

  private fun DeviceInfo.toDevice(): Device {
    val defaultDevice = Device(getId(), getName(), getCreated(), getLastSeen())
    try {
      if (getName().isNullOrEmpty() || getName().length < 4) {
        Log.w(TAG, "Invalid DeviceInfo name.")
        return defaultDevice
      }

      val deviceName = DeviceName.ADAPTER.decode(Base64.decode(getName()))
      if (deviceName.ciphertext == null || deviceName.ephemeralPublic == null || deviceName.syntheticIv == null) {
        Log.w(TAG, "Got a DeviceName that wasn't properly populated.")
        return defaultDevice
      }

      val plaintext = DeviceNameCipher.decryptDeviceName(deviceName, SignalStore.account.aciIdentityKey)
      if (plaintext == null) {
        Log.w(TAG, "Failed to decrypt device name.")
        return defaultDevice
      }

      return Device(getId(), String(plaintext), getCreated(), getLastSeen())
    } catch (e: Exception) {
      Log.w(TAG, "Failed while reading the protobuf.", e)
    }
    return defaultDevice
  }

  fun isValidQr(uri: Uri): Boolean {
    if (!uri.isHierarchical) {
      return false
    }

    val ephemeralId: String? = uri.getQueryParameter("uuid")
    val publicKeyEncoded: String? = uri.getQueryParameter("pub_key")
    return ephemeralId.isNotNullOrBlank() && publicKeyEncoded.isNotNullOrBlank()
  }

  /**
   * Adds a linked device to the account.
   *
   * @param ephemeralMessageBackupKey An ephemeral key to provide the linked device to sync existing message content. Do not set if link+sync is unsupported.
   */
  fun addDevice(uri: Uri, ephemeralMessageBackupKey: MessageBackupKey?): LinkDeviceResult {
    if (!isValidQr(uri)) {
      Log.w(TAG, "Bad URI! $uri")
      return LinkDeviceResult.BadCode
    }

    val verificationCodeResult: LinkedDeviceVerificationCodeResponse = when (val result = SignalNetwork.linkDevice.getDeviceVerificationCode()) {
      is NetworkResult.Success -> result.result
      is NetworkResult.ApplicationError -> throw result.throwable
      is NetworkResult.NetworkError -> return LinkDeviceResult.NetworkError(result.exception)
      is NetworkResult.StatusCodeError -> {
        return when (result.code) {
          411 -> LinkDeviceResult.LimitExceeded
          429 -> LinkDeviceResult.NetworkError(result.exception)
          else -> LinkDeviceResult.NetworkError(result.exception)
        }
      }
    }

    val ephemeralId: String = uri.getQueryParameter("uuid") ?: return LinkDeviceResult.BadCode
    val publicKey = try {
      val publicKeyEncoded: String = uri.getQueryParameter("pub_key") ?: return LinkDeviceResult.BadCode
      Curve.decodePoint(Base64.decode(publicKeyEncoded), 0)
    } catch (e: InvalidKeyException) {
      return LinkDeviceResult.KeyError
    }

    val deviceLinkResult = SignalNetwork.linkDevice.linkDevice(
      e164 = SignalStore.account.e164!!,
      aci = SignalStore.account.aci!!,
      pni = SignalStore.account.pni!!,
      deviceIdentifier = ephemeralId,
      deviceKey = publicKey,
      aciIdentityKeyPair = SignalStore.account.aciIdentityKey,
      pniIdentityKeyPair = SignalStore.account.pniIdentityKey,
      profileKey = ProfileKeyUtil.getSelfProfileKey(),
      masterKey = SignalStore.svr.masterKey,
      code = verificationCodeResult.verificationCode,
      ephemeralMessageBackupKey = ephemeralMessageBackupKey,
      mediaRootBackupKey = SignalStore.backup.mediaRootBackupKey
    )

    return when (deviceLinkResult) {
      is NetworkResult.Success -> {
        SignalStore.account.hasLinkedDevices = true
        LinkDeviceResult.Success(verificationCodeResult.tokenIdentifier)
      }
      is NetworkResult.ApplicationError -> throw deviceLinkResult.throwable
      is NetworkResult.NetworkError -> LinkDeviceResult.NetworkError(deviceLinkResult.exception)
      is NetworkResult.StatusCodeError -> {
        when (deviceLinkResult.code) {
          403 -> LinkDeviceResult.NoDevice
          409 -> LinkDeviceResult.NoDevice
          411 -> LinkDeviceResult.LimitExceeded
          422 -> LinkDeviceResult.NetworkError(deviceLinkResult.exception)
          429 -> LinkDeviceResult.NetworkError(deviceLinkResult.exception)
          else -> LinkDeviceResult.NetworkError(deviceLinkResult.exception)
        }
      }
    }
  }

  /**
   * Waits up to the specified [maxWaitTime] for a device with the given [token] to be linked.
   *
   * @param token Comes from [LinkDeviceResult.Success]
   */
  fun waitForDeviceToBeLinked(token: String, maxWaitTime: Duration): WaitForLinkedDeviceResponse? {
    Log.d(TAG, "[waitForDeviceToBeLinked] Starting to wait for device.")

    val startTime = System.currentTimeMillis()
    var timeRemaining = maxWaitTime.inWholeMilliseconds

    while (timeRemaining > 0) {
      Log.d(TAG, "[waitForDeviceToBeLinked] Willing to wait for $timeRemaining ms...")
      val result = SignalNetwork.linkDevice.waitForLinkedDevice(
        token = token,
        timeout = timeRemaining.milliseconds
      )

      when (result) {
        is NetworkResult.Success -> {
          Log.d(TAG, "[waitForDeviceToBeLinked] Sucessfully found device after waiting ${System.currentTimeMillis() - startTime} ms.")
          return result.result
        }
        is NetworkResult.ApplicationError -> {
          Log.e(TAG, "[waitForDeviceToBeLinked] Application error!", result.throwable)
          throw result.throwable
        }
        is NetworkResult.NetworkError -> {
          Log.w(TAG, "[waitForDeviceToBeLinked] Hit a network error while waiting for linking. Will try to wait again.", result.exception)
        }
        is NetworkResult.StatusCodeError -> {
          when (result.code) {
            400 -> {
              Log.w(TAG, "[waitForDeviceToBeLinked] Invalid token/timeout!")
              return null
            }
            429 -> {
              Log.w(TAG, "[waitForDeviceToBeLinked] Hit a rate-limit. Will try to wait again.")
            }
            else -> {
              Log.w(TAG, "[waitForDeviceToBeLinked] Hit an unknown status code of ${result.code}. Will try to wait again.")
            }
          }
        }
      }

      timeRemaining = maxWaitTime.inWholeMilliseconds - (System.currentTimeMillis() - startTime)
    }

    Log.w(TAG, "[waitForDeviceToBeLinked] No linked device found in ${System.currentTimeMillis() - startTime} ms. Bailing!")
    return null
  }

  /**
   * Performs the entire process of creating and uploading an archive for a newly-linked device.
   */
  fun createAndUploadArchive(ephemeralMessageBackupKey: MessageBackupKey, deviceId: Int, deviceCreatedAt: Long, cancellationSignal: () -> Boolean): LinkUploadArchiveResult {
    Log.d(TAG, "[createAndUploadArchive] Beginning process.")
    val stopwatch = Stopwatch("link-archive")
    val tempBackupFile = BlobProvider.getInstance().forNonAutoEncryptingSingleSessionOnDisk(AppDependencies.application)
    val outputStream = FileOutputStream(tempBackupFile)

    try {
      Log.d(TAG, "[createAndUploadArchive] Starting the export.")
      BackupRepository.export(
        outputStream = outputStream,
        append = { tempBackupFile.appendBytes(it) },
        messageBackupKey = ephemeralMessageBackupKey,
        mediaBackupEnabled = false,
        forTransfer = true,
        cancellationSignal = cancellationSignal
      )
    } catch (e: Exception) {
      Log.w(TAG, "[createAndUploadArchive] Failed to export a backup!", e)
      return LinkUploadArchiveResult.BackupCreationFailure(e)
    }
    Log.d(TAG, "[createAndUploadArchive] Successfully created backup.")
    stopwatch.split("create-backup")

    if (cancellationSignal()) {
      Log.i(TAG, "[createAndUploadArchive] Backup was cancelled.")
      return LinkUploadArchiveResult.BackupCreationCancelled
    }

    when (val result = ArchiveValidator.validate(tempBackupFile, ephemeralMessageBackupKey, forTransfer = true)) {
      ArchiveValidator.ValidationResult.Success -> {
        Log.d(TAG, "[createAndUploadArchive] Successfully passed validation.")
      }
      is ArchiveValidator.ValidationResult.ReadError -> {
        Log.w(TAG, "[createAndUploadArchive] Failed to read the file during validation!", result.exception)
        return LinkUploadArchiveResult.BackupCreationFailure(result.exception)
      }
      is ArchiveValidator.ValidationResult.MessageValidationError -> {
        Log.w(TAG, "[createAndUploadArchive] The backup file fails validation! Details: ${result.messageDetails}", result.exception)
        return LinkUploadArchiveResult.BackupCreationFailure(result.exception)
      }
      is ArchiveValidator.ValidationResult.RecipientDuplicateE164Error -> {
        Log.w(TAG, "[createAndUploadArchive] The backup file fails validation with a duplicate recipient! Details: ${result.details}", result.exception)
        return LinkUploadArchiveResult.BackupCreationFailure(result.exception)
      }
    }
    stopwatch.split("validate-backup")

    if (cancellationSignal()) {
      Log.i(TAG, "[createAndUploadArchive] Backup was cancelled.")
      return LinkUploadArchiveResult.BackupCreationCancelled
    }

    Log.d(TAG, "[createAndUploadArchive] Fetching an upload form...")
    val uploadForm = when (val result = NetworkResult.withRetry { SignalNetwork.attachments.getAttachmentV4UploadForm() }) {
      is NetworkResult.Success -> result.result.logD(TAG, "[createAndUploadArchive] Successfully retrieved upload form.")
      is NetworkResult.ApplicationError -> throw result.throwable
      is NetworkResult.NetworkError -> return LinkUploadArchiveResult.NetworkError(result.exception).logW(TAG, "[createAndUploadArchive] Network error when fetching form.", result.exception)
      is NetworkResult.StatusCodeError -> return LinkUploadArchiveResult.NetworkError(result.exception).logW(TAG, "[createAndUploadArchive] Status code error when fetching form.", result.exception)
    }

    if (cancellationSignal()) {
      Log.i(TAG, "[createAndUploadArchive] Backup was cancelled.")
      return LinkUploadArchiveResult.BackupCreationCancelled
    }

    when (val result = uploadArchive(tempBackupFile, uploadForm)) {
      is NetworkResult.Success -> Log.i(TAG, "[createAndUploadArchive] Successfully uploaded backup.")
      is NetworkResult.NetworkError -> return LinkUploadArchiveResult.NetworkError(result.exception).logW(TAG, "[createAndUploadArchive] Network error when uploading archive.", result.exception)
      is NetworkResult.StatusCodeError -> return LinkUploadArchiveResult.NetworkError(result.exception).logW(TAG, "[createAndUploadArchive] Status code error when uploading archive.", result.exception)
      is NetworkResult.ApplicationError -> throw result.throwable
    }
    stopwatch.split("upload-backup")

    if (cancellationSignal()) {
      Log.i(TAG, "[createAndUploadArchive] Backup was cancelled.")
      return LinkUploadArchiveResult.BackupCreationCancelled
    }

    Log.d(TAG, "[createAndUploadArchive] Setting the transfer archive...")
    val transferSetResult = NetworkResult.withRetry {
      SignalNetwork.linkDevice.setTransferArchive(
        destinationDeviceId = deviceId,
        destinationDeviceCreated = deviceCreatedAt,
        cdn = uploadForm.cdn,
        cdnKey = uploadForm.key
      )
    }

    when (transferSetResult) {
      is NetworkResult.Success -> Log.i(TAG, "[createAndUploadArchive] Successfully set transfer archive.")
      is NetworkResult.ApplicationError -> throw transferSetResult.throwable.logW(TAG, "[createAndUploadArchive] Hit an error when setting transfer archive!", transferSetResult.throwable)
      is NetworkResult.NetworkError -> return LinkUploadArchiveResult.NetworkError(transferSetResult.exception).logW(TAG, "[createAndUploadArchive] Network error when setting transfer archive.", transferSetResult.exception)
      is NetworkResult.StatusCodeError -> {
        return when (transferSetResult.code) {
          422 -> LinkUploadArchiveResult.BadRequest(transferSetResult.exception).logW(TAG, "[createAndUploadArchive] 422 when setting transfer archive.", transferSetResult.exception)
          else -> LinkUploadArchiveResult.NetworkError(transferSetResult.exception).logW(TAG, "[createAndUploadArchive] Status code error when setting transfer archive.", transferSetResult.exception)
        }
      }
    }
    stopwatch.split("transfer-set")
    stopwatch.stop(TAG)

    return LinkUploadArchiveResult.Success
  }

  /**
   * Handles uploading the archive for [createAndUploadArchive]. Handles resumable uploads and making multiple upload attempts.
   */
  private fun uploadArchive(backupFile: File, uploadForm: AttachmentUploadForm): NetworkResult<Unit> {
    val resumableUploadUrl = when (val result = NetworkResult.withRetry { SignalNetwork.attachments.getResumableUploadUrl(uploadForm) }) {
      is NetworkResult.Success -> result.result
      is NetworkResult.NetworkError -> return result.map { Unit }.logW(TAG, "Network error when fetching upload URL.", result.exception)
      is NetworkResult.StatusCodeError -> return result.map { Unit }.logW(TAG, "Status code error when fetching upload URL.", result.exception)
      is NetworkResult.ApplicationError -> throw result.throwable
    }

    val uploadResult = NetworkResult.withRetry(
      logAttempt = { attempt, maxAttempts -> Log.i(TAG, "Starting upload attempt ${attempt + 1}/$maxAttempts") }
    ) {
      FileInputStream(backupFile).use {
        SignalNetwork.attachments.uploadPreEncryptedFileToAttachmentV4(
          uploadForm = uploadForm,
          resumableUploadUrl = resumableUploadUrl,
          inputStream = it,
          inputStreamLength = backupFile.length()
        )
      }
    }

    return when (uploadResult) {
      is NetworkResult.Success -> uploadResult
      is NetworkResult.NetworkError -> uploadResult.logW(TAG, "Network error while uploading.", uploadResult.exception)
      is NetworkResult.StatusCodeError -> uploadResult.logW(TAG, "Status code error when uploading archive.", uploadResult.exception)
      is NetworkResult.ApplicationError -> throw uploadResult.throwable
    }
  }

  /**
   * If [createAndUploadArchive] fails to upload an archive, alert the linked device of the failure and if the user will try again
   */
  fun sendTransferArchiveError(deviceId: Int, deviceCreatedAt: Long, error: TransferArchiveError) {
    val archiveErrorResult = SignalNetwork.linkDevice.setTransferArchiveError(
      destinationDeviceId = deviceId,
      destinationDeviceCreated = deviceCreatedAt,
      error = error
    )

    when (archiveErrorResult) {
      is NetworkResult.Success -> Log.i(TAG, "[sendTransferArchiveError] Successfully sent transfer archive error.")
      is NetworkResult.ApplicationError -> throw archiveErrorResult.throwable
      is NetworkResult.NetworkError -> Log.w(TAG, "[sendTransferArchiveError] Network error when sending transfer archive error.", archiveErrorResult.exception)
      is NetworkResult.StatusCodeError -> Log.w(TAG, "[sendTransferArchiveError] Status code error when sending transfer archive error.", archiveErrorResult.exception)
    }
  }

  /**
   * Changes the name of a linked device and sends a sync message if successful
   */
  fun changeDeviceName(deviceName: String, deviceId: Int): DeviceNameChangeResult {
    val encryptedDeviceName = Base64.encodeWithoutPadding(DeviceNameCipher.encryptDeviceName(deviceName.toByteArray(StandardCharsets.UTF_8), SignalStore.account.aciIdentityKey))
    return when (val result = SignalNetwork.linkDevice.setDeviceName(encryptedDeviceName, deviceId)) {
      is NetworkResult.Success -> {
        AppDependencies.jobManager.add(DeviceNameChangeJob(deviceId))
        DeviceNameChangeResult.Success.logI(TAG, "Successfully changed device name")
      }
      is NetworkResult.NetworkError -> {
        DeviceNameChangeResult.NetworkError(result.exception).logW(TAG, "Could not change name due to network error.", result.exception)
      }
      is NetworkResult.StatusCodeError -> {
        DeviceNameChangeResult.NetworkError(result.exception).logW(TAG, "Could not change name due to status code error ${result.code}")
      }
      is NetworkResult.ApplicationError -> {
        throw result.throwable.logW(TAG, "Could not change name due to application error.")
      }
    }
  }

  sealed interface LinkDeviceResult {
    data object None : LinkDeviceResult
    data class Success(val token: String) : LinkDeviceResult
    data object NoDevice : LinkDeviceResult
    data class NetworkError(val error: Throwable) : LinkDeviceResult
    data object KeyError : LinkDeviceResult
    data object LimitExceeded : LinkDeviceResult
    data object BadCode : LinkDeviceResult
  }

  sealed interface LinkUploadArchiveResult {
    data object Success : LinkUploadArchiveResult
    data object BackupCreationCancelled : LinkUploadArchiveResult
    data class BackupCreationFailure(val exception: Exception) : LinkUploadArchiveResult
    data class BadRequest(val exception: IOException) : LinkUploadArchiveResult
    data class NetworkError(val exception: IOException) : LinkUploadArchiveResult
  }

  sealed interface DeviceNameChangeResult {
    data object Success : DeviceNameChangeResult
    data class NetworkError(val exception: IOException) : DeviceNameChangeResult
  }
}
