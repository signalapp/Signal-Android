/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.link

import okio.ByteString.Companion.toByteString
import org.signal.core.util.Base64.encodeWithPadding
import org.signal.core.util.urlEncode
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.backup.MediaRootBackupKey
import org.whispersystems.signalservice.api.backup.MessageBackupKey
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.crypto.PrimaryProvisioningCipher
import org.whispersystems.signalservice.internal.delete
import org.whispersystems.signalservice.internal.get
import org.whispersystems.signalservice.internal.push.DeviceInfoList
import org.whispersystems.signalservice.internal.push.ProvisionMessage
import org.whispersystems.signalservice.internal.push.ProvisioningMessage
import org.whispersystems.signalservice.internal.push.ProvisioningVersion
import org.whispersystems.signalservice.internal.put
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Class to interact with device-linking endpoints.
 */
class LinkDeviceApi(
  private val authWebSocket: SignalWebSocket.AuthenticatedWebSocket
) {
  /**
   * Fetches a list of linked devices.
   *
   * GET /v1/devices
   *
   * - 200: Success
   */
  fun getDevices(): NetworkResult<List<DeviceInfo>> {
    val request = WebSocketRequestMessage.get("/v1/devices")
    return NetworkResult
      .fromWebSocketRequest(authWebSocket, request, DeviceInfoList::class)
      .map { it.getDevices() }
  }

  /**
   * Remove and unlink a linked device.
   *
   * DELETE /v1/devices/{id}
   *
   * - 200: Success
   */
  fun removeDevice(deviceId: Int): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.delete("/v1/devices/$deviceId")
    return NetworkResult
      .fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * Fetches a new verification code that lets you link a new device.
   *
   * GET /v1/devices/provisioning/code
   *
   * - 200: Success.
   * - 411: Account is already at the device limit.
   * - 429: Rate-limited.
   */
  fun getDeviceVerificationCode(): NetworkResult<LinkedDeviceVerificationCodeResponse> {
    val request = WebSocketRequestMessage.get("/v1/devices/provisioning/code")
    return NetworkResult
      .fromWebSocketRequest(authWebSocket, request, LinkedDeviceVerificationCodeResponse::class)
  }

  /**
   * Links a new device to the account.
   *
   * PUT /v1/provisioning/[deviceIdentifier]
   *
   * - 200: Success.
   * - 403: Account not found or incorrect verification code.
   * - 409: The new device is missing a required capability.
   * - 411: Account is already at the device limit.
   * - 422: Bad request.
   * - 429: Rate-limited.
   */
  fun linkDevice(
    e164: String,
    aci: ACI,
    pni: PNI,
    deviceIdentifier: String,
    deviceKey: ECPublicKey,
    aciIdentityKeyPair: IdentityKeyPair,
    pniIdentityKeyPair: IdentityKeyPair,
    profileKey: ProfileKey,
    masterKey: MasterKey,
    mediaRootBackupKey: MediaRootBackupKey,
    code: String,
    ephemeralMessageBackupKey: MessageBackupKey?
  ): NetworkResult<Unit> {
    val cipher = PrimaryProvisioningCipher(deviceKey)
    val message = ProvisionMessage(
      aciIdentityKeyPublic = aciIdentityKeyPair.publicKey.serialize().toByteString(),
      aciIdentityKeyPrivate = aciIdentityKeyPair.privateKey.serialize().toByteString(),
      pniIdentityKeyPublic = pniIdentityKeyPair.publicKey.serialize().toByteString(),
      pniIdentityKeyPrivate = pniIdentityKeyPair.privateKey.serialize().toByteString(),
      aci = aci.toString(),
      pni = pni.toStringWithoutPrefix(),
      number = e164,
      profileKey = profileKey.serialize().toByteString(),
      provisioningCode = code,
      provisioningVersion = ProvisioningVersion.CURRENT.value,
      masterKey = masterKey.serialize().toByteString(),
      mediaRootBackupKey = mediaRootBackupKey.value.toByteString(),
      ephemeralBackupKey = ephemeralMessageBackupKey?.value?.toByteString()
    )
    val ciphertext: ByteArray = cipher.encrypt(message)
    val body = ProvisioningMessage(encodeWithPadding(ciphertext))

    val request = WebSocketRequestMessage.put("/v1/provisioning/${deviceIdentifier.urlEncode()}", body)
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * A "long-polling" endpoint that will return once the device has successfully been linked.
   *
   * @param timeout The max amount of time to wait. Capped at 30 seconds.
   *
   * GET /v1/devices/wait_for_linked_device/[token]?timeout=[timeout]
   *
   * - 200: Success, a new device was linked associated with the provided token.
   * - 204: No device was linked before the max waiting time elapsed.
   * - 400: Invalid token/timeout.
   * - 429: Rate-limited.
   */
  fun waitForLinkedDevice(token: String, timeout: Duration = 30.seconds): NetworkResult<WaitForLinkedDeviceResponse> {
    val request = WebSocketRequestMessage.get("/v1/devices/wait_for_linked_device/${token.urlEncode()}?timeout=${timeout.inWholeSeconds}")
    return NetworkResult
      .fromWebSocketRequest(
        signalWebSocket = authWebSocket,
        request = request,
        timeout = timeout,
        webSocketResponseConverter = NetworkResult.LongPollingWebSocketConverter(WaitForLinkedDeviceResponse::class)
      )
  }

  /**
   * After a device has been linked and an archive has been uploaded, you can call this endpoint to share the archive with the linked device.
   *
   * PUT /v1/devices/transfer_archive
   *
   * - 204: Success.
   * - 422: Bad inputs.
   * - 429: Rate-limited.
   */
  fun setTransferArchive(destinationDeviceId: Int, destinationDeviceCreated: Long, cdn: Int, cdnKey: String): NetworkResult<Unit> {
    val body = SetLinkedDeviceTransferArchiveRequest(
      destinationDeviceId = destinationDeviceId,
      destinationDeviceCreated = destinationDeviceCreated,
      transferArchive = SetLinkedDeviceTransferArchiveRequest.TransferArchive.CdnInfo(
        cdn = cdn,
        key = cdnKey
      )
    )
    val request = WebSocketRequestMessage.put("/v1/devices/transfer_archive", body)
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * If creating an archive has failed after linking a device, notify the linked
   * device of the failure and if you are going to try relinking or skip syncing
   *
   * PUT /v1/devices/transfer_archive
   *
   * - 204: Success.
   * - 422: Bad inputs.
   * - 429: Rate-limited.
   */
  fun setTransferArchiveError(destinationDeviceId: Int, destinationDeviceCreated: Long, error: TransferArchiveError): NetworkResult<Unit> {
    val body = SetLinkedDeviceTransferArchiveRequest(
      destinationDeviceId = destinationDeviceId,
      destinationDeviceCreated = destinationDeviceCreated,
      transferArchive = SetLinkedDeviceTransferArchiveRequest.TransferArchive.Error(error)
    )
    val request = WebSocketRequestMessage.put("/v1/devices/transfer_archive", body)
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }

  /**
   * Sets the name for a linked device
   *
   * PUT /v1/accounts/name?deviceId=[deviceId]
   *
   * - 204: Success.
   * - 403: Not authorized to change the name of the device with the given ID
   * - 404: No device found with the given ID
   */
  fun setDeviceName(encryptedDeviceName: String, deviceId: Int): NetworkResult<Unit> {
    val request = WebSocketRequestMessage.put("/v1/accounts/name?deviceId=$deviceId", SetDeviceNameRequest(encryptedDeviceName))
    return NetworkResult.fromWebSocketRequest(authWebSocket, request)
  }
}
