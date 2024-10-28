package org.thoughtcrime.securesms.linkdevice

import android.net.Uri
import org.signal.core.util.Base64
import org.signal.core.util.Stopwatch
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.logW
import org.signal.libsignal.protocol.ecc.Curve
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.devicelist.protos.DeviceName
import org.thoughtcrime.securesms.jobs.LinkedDeviceInactiveCheckJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.registration.secondary.DeviceNameCipher
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.backup.BackupKey
import org.whispersystems.signalservice.api.link.LinkedDeviceVerificationCodeResponse
import org.whispersystems.signalservice.api.link.WaitForLinkedDeviceResponse
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.InvalidKeyException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Repository for linked devices and its various actions (linking, unlinking, listing).
 */
object LinkDeviceRepository {

  private val TAG = Log.tag(LinkDeviceRepository::class)

  fun removeDevice(deviceId: Int): Boolean {
    return try {
      val accountManager = AppDependencies.signalServiceAccountManager
      accountManager.removeDevice(deviceId)
      LinkedDeviceInactiveCheckJob.enqueue()
      true
    } catch (e: IOException) {
      Log.w(TAG, e)
      false
    }
  }

  fun loadDevices(): List<Device>? {
    val accountManager = AppDependencies.signalServiceAccountManager
    return try {
      val devices: List<Device> = accountManager.getDevices()
        .filter { d: DeviceInfo -> d.getId() != SignalServiceAddress.DEFAULT_DEVICE_ID }
        .map { deviceInfo: DeviceInfo -> deviceInfo.toDevice() }
        .sortedBy { it.createdMillis }
        .toList()
      devices
    } catch (e: IOException) {
      Log.w(TAG, e)
      null
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
   * @param ephemeralBackupKey An ephemeral key to provide the linked device to sync existing message content. Do not set if link+sync is unsupported.
   */
  fun addDevice(uri: Uri, ephemeralBackupKey: BackupKey?): LinkDeviceResult {
    if (!isValidQr(uri)) {
      Log.w(TAG, "Bad URI! $uri")
      return LinkDeviceResult.BadCode
    }

    val verificationCodeResult: LinkedDeviceVerificationCodeResponse = when (val result = SignalNetwork.linkDevice.getDeviceVerificationCode()) {
      is NetworkResult.Success -> result.result
      is NetworkResult.ApplicationError -> throw result.throwable
      is NetworkResult.NetworkError -> return LinkDeviceResult.NetworkError
      is NetworkResult.StatusCodeError -> {
        return when (result.code) {
          411 -> LinkDeviceResult.LimitExceeded
          429 -> LinkDeviceResult.NetworkError
          else -> LinkDeviceResult.NetworkError
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
      masterKey = SignalStore.svr.getOrCreateMasterKey(),
      code = verificationCodeResult.verificationCode,
      ephemeralBackupKey = ephemeralBackupKey
    )

    return when (deviceLinkResult) {
      is NetworkResult.Success -> {
        SignalStore.account.hasLinkedDevices = true
        LinkDeviceResult.Success(verificationCodeResult.tokenIdentifier)
      }
      is NetworkResult.ApplicationError -> throw deviceLinkResult.throwable
      is NetworkResult.NetworkError -> LinkDeviceResult.NetworkError
      is NetworkResult.StatusCodeError -> {
        when (deviceLinkResult.code) {
          403 -> LinkDeviceResult.NoDevice
          409 -> LinkDeviceResult.NoDevice
          411 -> LinkDeviceResult.LimitExceeded
          422 -> LinkDeviceResult.NetworkError
          429 -> LinkDeviceResult.NetworkError
          else -> LinkDeviceResult.NetworkError
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
    val startTime = System.currentTimeMillis()
    var timeRemaining = maxWaitTime.inWholeMilliseconds

    while (timeRemaining > 0) {
      Log.d(TAG, "[waitForDeviceToBeLinked] Willing to wait for $timeRemaining ms...")
      val result = SignalNetwork.linkDevice.waitForLinkedDevice(
        token = token,
        timeoutSeconds = timeRemaining.milliseconds.inWholeSeconds.toInt()
      )

      when (result) {
        is NetworkResult.Success -> {
          return result.result
        }
        is NetworkResult.ApplicationError -> {
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
  fun createAndUploadArchive(ephemeralBackupKey: BackupKey, deviceId: Int, deviceCreatedAt: Long): LinkUploadArchiveResult {
    val stopwatch = Stopwatch("link-archive")
    val tempBackupFile = BlobProvider.getInstance().forNonAutoEncryptingSingleSessionOnDisk(AppDependencies.application)
    val outputStream = FileOutputStream(tempBackupFile)

    try {
      BackupRepository.export(outputStream = outputStream, append = { tempBackupFile.appendBytes(it) }, backupKey = ephemeralBackupKey, mediaBackupEnabled = false)
    } catch (e: Exception) {
      return LinkUploadArchiveResult.BackupCreationFailure(e)
    }
    stopwatch.split("create-backup")

    val uploadForm = when (val result = SignalNetwork.attachments.getAttachmentV4UploadForm()) {
      is NetworkResult.Success -> result.result
      is NetworkResult.ApplicationError -> throw result.throwable
      is NetworkResult.NetworkError -> return LinkUploadArchiveResult.NetworkError(result.exception).logW(TAG, "Network error when fetching form.", result.exception)
      is NetworkResult.StatusCodeError -> return LinkUploadArchiveResult.NetworkError(result.exception).logW(TAG, "Status code error when fetching form.", result.exception)
    }

    when (val result = uploadArchive(tempBackupFile, uploadForm)) {
      is NetworkResult.Success -> Log.i(TAG, "Successfully uploaded backup.")
      is NetworkResult.NetworkError -> return LinkUploadArchiveResult.NetworkError(result.exception).logW(TAG, "Network error when uploading archive.", result.exception)
      is NetworkResult.StatusCodeError -> return LinkUploadArchiveResult.NetworkError(result.exception).logW(TAG, "Status code error when uploading archive.", result.exception)
      is NetworkResult.ApplicationError -> throw result.throwable
    }
    stopwatch.split("upload-backup")

    val transferSetResult = SignalNetwork.linkDevice.setTransferArchive(
      destinationDeviceId = deviceId,
      destinationDeviceCreated = deviceCreatedAt,
      cdn = uploadForm.cdn,
      cdnKey = uploadForm.key
    )

    when (transferSetResult) {
      is NetworkResult.Success -> Log.i(TAG, "Successfully set transfer archive.")
      is NetworkResult.ApplicationError -> throw transferSetResult.throwable
      is NetworkResult.NetworkError -> return LinkUploadArchiveResult.NetworkError(transferSetResult.exception).logW(TAG, "Network error when setting transfer archive.", transferSetResult.exception)
      is NetworkResult.StatusCodeError -> {
        return when (transferSetResult.code) {
          422 -> LinkUploadArchiveResult.BadRequest(transferSetResult.exception).logW(TAG, "422 when setting transfer archive.", transferSetResult.exception)
          else -> LinkUploadArchiveResult.NetworkError(transferSetResult.exception).logW(TAG, "Status code error when setting transfer archive.", transferSetResult.exception)
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
    val resumableUploadUrl = when (val result = SignalNetwork.attachments.getResumableUploadUrl(uploadForm)) {
      is NetworkResult.Success -> result.result
      is NetworkResult.NetworkError -> return result.map { Unit }.logW(TAG, "Network error when fetching upload URL.", result.exception)
      is NetworkResult.StatusCodeError -> return result.map { Unit }.logW(TAG, "Status code error when fetching upload URL.", result.exception)
      is NetworkResult.ApplicationError -> throw result.throwable
    }

    val maxRetries = 5
    var attemptCount = 0

    while (attemptCount < maxRetries) {
      Log.i(TAG, "Starting upload attempt ${attemptCount + 1}/$maxRetries")
      val uploadResult = FileInputStream(backupFile).use {
        SignalNetwork.attachments.uploadPreEncryptedFileToAttachmentV4(
          uploadForm = uploadForm,
          resumableUploadUrl = resumableUploadUrl,
          inputStream = backupFile.inputStream(),
          inputStreamLength = backupFile.length()
        )
      }

      when (uploadResult) {
        is NetworkResult.Success -> return uploadResult
        is NetworkResult.NetworkError -> Log.w(TAG, "Hit network error while uploading. May retry.", uploadResult.exception)
        is NetworkResult.StatusCodeError -> return uploadResult.logW(TAG, "Status code error when uploading archive.", uploadResult.exception)
        is NetworkResult.ApplicationError -> throw uploadResult.throwable
      }

      attemptCount++
    }

    Log.w(TAG, "Hit the max retry count of $maxRetries. Failing.")
    return NetworkResult.NetworkError(IOException("Hit max retries!"))
  }

  sealed interface LinkDeviceResult {
    data object None : LinkDeviceResult
    data class Success(val token: String) : LinkDeviceResult
    data object NoDevice : LinkDeviceResult
    data object NetworkError : LinkDeviceResult
    data object KeyError : LinkDeviceResult
    data object LimitExceeded : LinkDeviceResult
    data object BadCode : LinkDeviceResult
  }

  sealed interface LinkUploadArchiveResult {
    data object Success : LinkUploadArchiveResult
    data class BackupCreationFailure(val exception: Exception) : LinkUploadArchiveResult
    data class BadRequest(val exception: IOException) : LinkUploadArchiveResult
    data class NetworkError(val exception: IOException) : LinkUploadArchiveResult
  }
}
