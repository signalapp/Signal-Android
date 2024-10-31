/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.link

import okio.ByteString.Companion.toByteString
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.backup.MessageBackupKey
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.internal.crypto.PrimaryProvisioningCipher
import org.whispersystems.signalservice.internal.push.ProvisionMessage
import org.whispersystems.signalservice.internal.push.ProvisioningVersion
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import kotlin.math.min

/**
 * Class to interact with device-linking endpoints.
 */
class LinkDeviceApi(private val pushServiceSocket: PushServiceSocket) {

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
    return NetworkResult.fromFetch {
      pushServiceSocket.getLinkedDeviceVerificationCode()
    }
  }

  /**
   * Links a new device to the account.
   *
   * PUT /v1/devices/link
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
    code: String,
    ephemeralMessageBackupKey: MessageBackupKey?
  ): NetworkResult<Unit> {
    return NetworkResult.fromFetch {
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
        ephemeralBackupKey = ephemeralMessageBackupKey?.value?.toByteString()
      )
      val ciphertext = cipher.encrypt(message)

      pushServiceSocket.sendProvisioningMessage(deviceIdentifier, ciphertext)
    }
  }

  /**
   * A "long-polling" endpoint that will return once the device has successfully been linked.
   *
   * @param timeoutSeconds The max amount of time to wait. Capped at 30 seconds.
   *
   * GET /v1/devices/wait_for_linked_device/{token}
   *
   * - 200: Success, a new device was linked associated with the provided token.
   * - 204: No device was linked before the max waiting time elapsed.
   * - 400: Invalid token/timeout.
   * - 429: Rate-limited.
   */
  fun waitForLinkedDevice(token: String, timeoutSeconds: Int = 30): NetworkResult<WaitForLinkedDeviceResponse> {
    return NetworkResult.fromFetch {
      pushServiceSocket.waitForLinkedDevice(token, min(timeoutSeconds, 30))
    }
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
    return NetworkResult.fromFetch {
      pushServiceSocket.setLinkedDeviceTransferArchive(
        SetLinkedDeviceTransferArchiveRequest(
          destinationDeviceId = destinationDeviceId,
          destinationDeviceCreated = destinationDeviceCreated,
          transferArchive = SetLinkedDeviceTransferArchiveRequest.CdnInfo(
            cdn = cdn,
            key = cdnKey
          )
        )
      )
    }
  }
}
