/*
 * Copyright (C) 2014-2017 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.signalservice.api.crypto

import org.whispersystems.signalservice.internal.util.Util
import java.io.IOException
import java.io.OutputStream
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * An OutputStream for encrypting attachment data.
 * The output stream writes the IV, ciphertext, and HMAC in sequence.
 *
 * @param combinedKeyMaterial The key material used for encryption and authentication. It is expected to be a byte array
 *    containing two parts: the first half being the AES key and the second half being the HMAC key.
 * @param iv The initialization vector (IV) for the cipher, or null to generate a random one.
 * @param outputStream The underlying output stream to write the encrypted data to.
 */
class AttachmentCipherOutputStream(
  combinedKeyMaterial: ByteArray,
  iv: ByteArray?,
  outputStream: OutputStream
) : DigestingOutputStream(outputStream) {

  private val cipher: Cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
  private val mac: Mac = Mac.getInstance("HmacSHA256")

  init {
    val keyParts = Util.split(combinedKeyMaterial, 32, 32)

    if (iv == null) {
      cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyParts[0], "AES"))
    } else {
      cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyParts[0], "AES"), IvParameterSpec(iv))
    }

    mac.init(SecretKeySpec(keyParts[1], "HmacSHA256"))

    mac.update(cipher.iv)
    super.write(cipher.iv)
  }

  @Throws(IOException::class)
  override fun write(buffer: ByteArray) {
    write(buffer, 0, buffer.size)
  }

  @Throws(IOException::class)
  override fun write(buffer: ByteArray, offset: Int, length: Int) {
    val ciphertext = cipher.update(buffer, offset, length)

    if (ciphertext != null) {
      mac.update(ciphertext)
      super.write(ciphertext)
    }
  }

  @Throws(IOException::class)
  override fun write(b: Int) {
    val input = ByteArray(1)
    input[0] = b.toByte()
    write(input, 0, 1)
  }

  @Throws(IOException::class)
  override fun close() {
    try {
      val ciphertext = cipher.doFinal()
      val auth = mac.doFinal(ciphertext)

      super.write(ciphertext)
      super.write(auth)

      super.close()
    } catch (e: IllegalBlockSizeException) {
      throw AssertionError(e)
    } catch (e: BadPaddingException) {
      throw AssertionError(e)
    }
  }
}
