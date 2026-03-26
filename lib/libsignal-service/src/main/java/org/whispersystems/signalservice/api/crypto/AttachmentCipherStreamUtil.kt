/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.crypto

import org.signal.core.util.stream.NullOutputStream
import java.io.InputStream

object AttachmentCipherStreamUtil {

  /**
   * Given the size of the plaintext, this will return the length of ciphertext output.
   * @param inputSize Size of the plaintext fed into the stream. This does *not* automatically include padding. Add that yourself before calling if needed.
   */
  @JvmStatic
  fun getCiphertextLength(plaintextLength: Long): Long {
    val ivLength: Long = 16
    val macLength: Long = 32
    val blockLength: Long = (plaintextLength / 16 + 1) * 16
    return ivLength + macLength + blockLength
  }

  @JvmStatic
  fun getPlaintextLength(ciphertextLength: Long): Long {
    return ((ciphertextLength - 16 - 32) / 16 - 1) * 16
  }

  /**
   * Computes the SHA-256 digest of the ciphertext that would result from encrypting [plaintextStream] with the given [key] and [iv].
   * This includes the IV prefix and HMAC suffix that are part of the encrypted attachment format.
   * The stream is encrypted to /dev/null -- only the digest is retained.
   */
  @JvmStatic
  fun computeCiphertextSha256(key: ByteArray, iv: ByteArray, plaintextStream: InputStream): ByteArray {
    val cipherOutputStream = AttachmentCipherOutputStream(key, iv, NullOutputStream)
    val buffer = ByteArray(16 * 1024)
    var read: Int
    while (plaintextStream.read(buffer).also { read = it } != -1) {
      cipherOutputStream.write(buffer, 0, read)
    }
    cipherOutputStream.close()
    return cipherOutputStream.transmittedDigest
  }
}
