package org.thoughtcrime.securesms.linkdevice

import android.net.Uri
import org.signal.core.util.Base64.decode
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.ecc.Curve
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.devicelist.protos.DeviceName
import org.thoughtcrime.securesms.jobs.LinkedDeviceInactiveCheckJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.secondary.DeviceNameCipher
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException
import org.whispersystems.signalservice.internal.push.DeviceLimitExceededException
import java.io.IOException
import java.security.InvalidKeyException

/**
 * Repository for linked devices and its various actions (linking, unlinking, listing).
 */
object LinkDeviceRepository {

  private val TAG = Log.tag(LinkDeviceRepository::class)

  fun removeDevice(deviceId: Long): Boolean {
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

  private fun DeviceInfo.toDevice(): Device {
    val defaultDevice = Device(getId().toLong(), getName(), getCreated(), getLastSeen())
    try {
      if (getName().isNullOrEmpty() || getName().length < 4) {
        Log.w(TAG, "Invalid DeviceInfo name.")
        return defaultDevice
      }

      val deviceName = DeviceName.ADAPTER.decode(decode(getName()))
      if (deviceName.ciphertext == null || deviceName.ephemeralPublic == null || deviceName.syntheticIv == null) {
        Log.w(TAG, "Got a DeviceName that wasn't properly populated.")
        return defaultDevice
      }

      val plaintext = DeviceNameCipher.decryptDeviceName(deviceName, SignalStore.account.aciIdentityKey)
      if (plaintext == null) {
        Log.w(TAG, "Failed to decrypt device name.")
        return defaultDevice
      }

      return Device(getId().toLong(), String(plaintext), getCreated(), getLastSeen())
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

  fun addDevice(uri: Uri): LinkDeviceResult {
    return try {
      val accountManager = AppDependencies.signalServiceAccountManager
      val verificationCode = accountManager.getNewDeviceVerificationCode()
      if (!isValidQr(uri)) {
        LinkDeviceResult.BAD_CODE
      } else {
        val ephemeralId: String? = uri.getQueryParameter("uuid")
        val publicKeyEncoded: String? = uri.getQueryParameter("pub_key")
        val publicKey = Curve.decodePoint(publicKeyEncoded?.let { decode(it) }, 0)
        val aciIdentityKeyPair = SignalStore.account.aciIdentityKey
        val pniIdentityKeyPair = SignalStore.account.pniIdentityKey
        val profileKey = ProfileKeyUtil.getSelfProfileKey()

        accountManager.addDevice(ephemeralId, publicKey, aciIdentityKeyPair, pniIdentityKeyPair, profileKey, SignalStore.svr.getOrCreateMasterKey(), verificationCode)
        TextSecurePreferences.setMultiDevice(AppDependencies.application, true)
        LinkDeviceResult.SUCCESS
      }
    } catch (e: NotFoundException) {
      LinkDeviceResult.NO_DEVICE
    } catch (e: DeviceLimitExceededException) {
      LinkDeviceResult.LIMIT_EXCEEDED
    } catch (e: IOException) {
      LinkDeviceResult.NETWORK_ERROR
    } catch (e: InvalidKeyException) {
      LinkDeviceResult.KEY_ERROR
    }
  }

  enum class LinkDeviceResult {
    SUCCESS,
    NO_DEVICE,
    NETWORK_ERROR,
    KEY_ERROR,
    LIMIT_EXCEEDED,
    BAD_CODE,
    UNKNOWN
  }
}
