/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import org.signal.core.util.Base64.decode
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.registration.proto.RegistrationProvisionMessage
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.provisioning.RestoreMethod
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

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
  fun transferAccount(reRegisterUri: String, restoreMethodToken: String): TransferAccountResult {
    if (!isValidReRegistrationQr(reRegisterUri)) {
      Log.w(TAG, "Invalid quick re-register qr data")
      return TransferAccountResult.FAILED
    }

    val uri = Uri.parse(reRegisterUri)

    try {
      val ephemeralId: String? = uri.getQueryParameter("uuid")
      val publicKeyEncoded: String? = uri.getQueryParameter("pub_key")

      if (ephemeralId == null || publicKeyEncoded == null) {
        Log.w(TAG, "Invalid link data hasId: ${ephemeralId != null} hasKey: ${publicKeyEncoded != null}")
        return TransferAccountResult.FAILED
      }

      val publicKey = ECPublicKey(decode(publicKeyEncoded))

      SignalNetwork
        .provisioning
        .sendReRegisterDeviceProvisioningMessage(
          ephemeralId,
          publicKey,
          RegistrationProvisionMessage(
            e164 = SignalStore.account.requireE164(),
            aci = SignalStore.account.requireAci().toByteString(),
            accountEntropyPool = SignalStore.account.accountEntropyPool.value,
            pin = SignalStore.svr.pin,
            platform = RegistrationProvisionMessage.Platform.ANDROID,
            backupTimestampMs = SignalStore.backup.lastBackupTime.coerceAtLeast(0L).takeIf { it > 0 },
            tier = when (SignalStore.backup.backupTier) {
              MessageBackupTier.PAID -> RegistrationProvisionMessage.Tier.PAID
              MessageBackupTier.FREE -> RegistrationProvisionMessage.Tier.FREE
              null -> null
            },
            backupSizeBytes = if (SignalStore.backup.backupTier == MessageBackupTier.PAID) SignalDatabase.attachments.getPaidEstimatedArchiveMediaSize().takeIf { it > 0 } else null,
            restoreMethodToken = restoreMethodToken,
            aciIdentityKeyPublic = SignalStore.account.aciIdentityKey.publicKey.serialize().toByteString(),
            aciIdentityKeyPrivate = SignalStore.account.aciIdentityKey.privateKey.serialize().toByteString(),
            pniIdentityKeyPublic = SignalStore.account.pniIdentityKey.publicKey.serialize().toByteString(),
            pniIdentityKeyPrivate = SignalStore.account.pniIdentityKey.privateKey.serialize().toByteString(),
            backupVersion = SignalStore.backup.lastBackupProtoVersion
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

  /**
   * Sets the restore method enum for the old device to retrieve and update their UI with.
   */
  suspend fun setRestoreMethodForOldDevice(restoreMethod: RestoreMethod) {
    val restoreMethodToken = SignalStore.registration.restoreMethodToken

    if (restoreMethodToken != null) {
      withContext(Dispatchers.IO) {
        Log.d(TAG, "Setting restore method ***${restoreMethodToken.takeLast(4)}: $restoreMethod")
        var retries = 3
        var result: NetworkResult<Unit>? = null
        while (retries-- > 0 && result !is NetworkResult.Success) {
          Log.d(TAG, "Setting method, retries remaining: $retries")
          result = AppDependencies.registrationApi.setRestoreMethod(restoreMethodToken, restoreMethod)

          if (result !is NetworkResult.Success) {
            delay(1.seconds)
          }
        }

        if (result is NetworkResult.Success) {
          Log.i(TAG, "Restore method set successfully")
          SignalStore.registration.restoreMethodToken = null
        } else {
          Log.w(TAG, "Restore method set failed", result?.getCause())
        }
      }
    }
  }

  /**
   * Gets the restore method used by the new device to update UI with. This is a long polling operation.
   */
  suspend fun waitForRestoreMethodSelectionOnNewDevice(restoreMethodToken: String): RestoreMethod {
    var retries = 5
    var result: NetworkResult<RestoreMethod>? = null

    Log.d(TAG, "Waiting for restore method with token: ***${restoreMethodToken.takeLast(4)}")
    while (retries-- > 0 && result !is NetworkResult.Success && coroutineContext.isActive) {
      Log.d(TAG, "Waiting, remaining tries: $retries")
      result = SignalNetwork.provisioning.waitForRestoreMethod(restoreMethodToken)
      Log.d(TAG, "Result: $result")
    }

    if (result is NetworkResult.Success) {
      Log.i(TAG, "Restore method selected on new device ${result.result}")
      return result.result
    } else {
      Log.w(TAG, "Failed to determine restore method, using DECLINE")
      return RestoreMethod.DECLINE
    }
  }

  enum class TransferAccountResult {
    SUCCESS,
    FAILED
  }
}
