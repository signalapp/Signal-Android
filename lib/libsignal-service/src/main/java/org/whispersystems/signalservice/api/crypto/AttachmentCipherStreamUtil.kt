/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.crypto

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
}
