/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.crypto

import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.provisioning.ProvisioningPublicKey
import org.signal.libsignal.provisioning.RecvProvisionCiphertextState
import org.signal.registration.proto.RegistrationProvisionMessage
import org.whispersystems.signalservice.internal.push.ProvisionMessage

/**
 * Used to decrypt a secondary/link device provisioning message from the primary device.
 */
class SecondaryProvisioningCipher private constructor(private val state: RecvProvisionCiphertextState) {

  companion object {
    private val TAG = Log.tag(SecondaryProvisioningCipher::class)

    fun generate(): SecondaryProvisioningCipher {
      return SecondaryProvisioningCipher(RecvProvisionCiphertextState.generate())
    }
  }

  /**
   * The public key that goes in the provisioning QR code.
   */
  val secondaryDevicePublicKey: ProvisioningPublicKey = state.publicKey

  /**
   * Decrypts a serialized `ProvisionEnvelope` containing a [ProvisionMessage].
   */
  fun decryptProvisionEnvelope(envelope: ByteArray): ProvisioningDecryptResult<ProvisionMessage> {
    val plaintext = decrypt(envelope) ?: return ProvisioningDecryptResult.Error()
    return ProvisioningDecryptResult.Success(ProvisionMessage.ADAPTER.decode(plaintext))
  }

  /**
   * Decrypts a serialized `ProvisionEnvelope` containing a [RegistrationProvisionMessage].
   */
  fun decryptRegistrationProvisionEnvelope(envelope: ByteArray): ProvisioningDecryptResult<RegistrationProvisionMessage> {
    val plaintext = decrypt(envelope) ?: return ProvisioningDecryptResult.Error()
    return ProvisioningDecryptResult.Success(RegistrationProvisionMessage.ADAPTER.decode(plaintext))
  }

  private fun decrypt(envelope: ByteArray): ByteArray? {
    return try {
      state.decryptEnvelope(envelope)
    } catch (e: InvalidMessageException) {
      Log.w(TAG, "Unable to decrypt provisioning envelope", e)
      null
    }
  }

  sealed interface ProvisioningDecryptResult<T> {
    data class Success<T>(val message: T) : ProvisioningDecryptResult<T>
    class Error<T>() : ProvisioningDecryptResult<T>
  }
}
