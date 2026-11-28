/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.crypto

import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.provisioning.Provisioning
import org.signal.libsignal.provisioning.ProvisioningPublicKey
import org.signal.registration.proto.RegistrationProvisionMessage
import org.whispersystems.signalservice.internal.push.ProvisionMessage

/**
 * Used by a primary device to encrypt a provisioning message to a secondary device.
 */
object PrimaryProvisioningCipher {

  /**
   * Returns a serialized `ProvisionEnvelope` that only the holder of [theirPublicKey] can decrypt.
   */
  @Throws(InvalidKeyException::class)
  fun encrypt(theirPublicKey: ProvisioningPublicKey, message: ProvisionMessage): ByteArray {
    return Provisioning.encryptProvisionMessage(theirPublicKey, message.encode())
  }

  /**
   * Returns a serialized `ProvisionEnvelope` that only the holder of [theirPublicKey] can decrypt.
   */
  @Throws(InvalidKeyException::class)
  fun encrypt(theirPublicKey: ProvisioningPublicKey, message: RegistrationProvisionMessage): ByteArray {
    return Provisioning.encryptProvisionMessage(theirPublicKey, message.encode())
  }
}
