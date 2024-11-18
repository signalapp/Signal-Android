/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.crypto

import org.signal.libsignal.protocol.kdf.HKDF

/**
 * A collection of cryptographic functions in the same namespace for easy access.
 */
object Crypto {

  fun hkdf(inputKeyMaterial: ByteArray, info: ByteArray, outputLength: Int, salt: ByteArray? = null): ByteArray {
    return HKDF.deriveSecrets(inputKeyMaterial, salt, info, outputLength)
  }
}
