/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.data

import android.net.Uri
import org.signal.core.util.Base64.decode
import org.signal.core.util.Hex
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.registration.proto.RegistrationProvisionMessage
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.io.IOException

/**
 * Helpers for quickly re-registering on a new device with the old device.
 */
object QuickRegistrationRepository {
  private val TAG = Log.tag(QuickRegistrationRepository::class)

  private const val REREG_URI_HOST = "rereg"

  fun isValidReRegistrationQr(data: String): Boolean {
    val uri = Uri.parse(data)

    if (!uri.isHierarchical) {
      return false
    }

    val ephemeralId: String? = uri.getQueryParameter("uuid")
    val publicKeyEncoded: String? = uri.getQueryParameter("pub_key")
    return uri.host == REREG_URI_HOST && ephemeralId.isNotNullOrBlank() && publicKeyEncoded.isNotNullOrBlank()
  }

  /**
   * Send registration provisioning message to new device.
   */
  fun transferAccount(reRegisterUri: String): TransferAccountResult {
    if (!isValidReRegistrationQr(reRegisterUri)) {
      Log.w(TAG, "Invalid quick re-register qr data")
      return TransferAccountResult.FAILED
    }

    val uri = Uri.parse(reRegisterUri)

    try {
      val ephemeralId: String? = uri.getQueryParameter("uuid")
      val publicKeyEncoded: String? = uri.getQueryParameter("pub_key")
      val publicKey = Curve.decodePoint(publicKeyEncoded?.let { decode(it) }, 0)

      if (ephemeralId == null || publicKeyEncoded == null) {
        Log.w(TAG, "Invalid link data hasId: ${ephemeralId != null} hasKey: ${publicKeyEncoded != null}")
        return TransferAccountResult.FAILED
      }

      val pin = SignalStore.svr.pin ?: run {
        Log.w(TAG, "No pin")
        return TransferAccountResult.FAILED
      }

      AppDependencies
        .signalServiceAccountManager
        .registrationApi
        .sendReRegisterDeviceProvisioningMessage(
          ephemeralId,
          publicKey,
          RegistrationProvisionMessage(
            e164 = SignalStore.account.requireE164(),
            aci = SignalStore.account.requireAci().toByteString(),
            accountEntropyPool = Hex.toStringCondensed(SignalStore.svr.masterKey.serialize()),
            pin = pin,
            platform = RegistrationProvisionMessage.Platform.ANDROID,
            backupTimestampMs = SignalStore.backup.lastBackupTime.coerceAtLeast(0L),
            tier = when (SignalStore.backup.backupTier) {
              MessageBackupTier.PAID -> RegistrationProvisionMessage.Tier.PAID
              MessageBackupTier.FREE,
              null -> RegistrationProvisionMessage.Tier.FREE
            }
          )
        )
        .successOrThrow()

      Log.i(TAG, "Re-registration provisioning message sent")
    } catch (e: IOException) {
      Log.w(TAG, "Exception re-registering new device", e)
      return TransferAccountResult.FAILED
    } catch (e: InvalidKeyException) {
      Log.w(TAG, "Exception re-registering new device", e)
      return TransferAccountResult.FAILED
    }

    return TransferAccountResult.SUCCESS
  }

  enum class TransferAccountResult {
    SUCCESS,
    FAILED
  }
}
