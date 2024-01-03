/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.stream

import org.signal.libsignal.protocol.kdf.HKDF
import java.io.FilterOutputStream
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class BackupEncryptedOutputStream(key: ByteArray, backupId: ByteArray, wrapped: OutputStream) : FilterOutputStream(wrapped) {

  val cipher: Cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
  val mac: Mac = Mac.getInstance("HmacSHA256")

  var finalMac: ByteArray? = null

  init {
    if (key.size != 32) {
      throw IllegalArgumentException("Key must be 32 bytes!")
    }

    if (backupId.size != 16) {
      throw IllegalArgumentException("BackupId must be 32 bytes!")
    }

    val extendedKey = HKDF.deriveSecrets(key, backupId, "20231003_Signal_Backups_EncryptMessageBackup".toByteArray(), 80)
    val macKey = extendedKey.copyOfRange(0, 32)
    val cipherKey = extendedKey.copyOfRange(32, 64)
    val iv = extendedKey.copyOfRange(64, 80)

    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(cipherKey, "AES"), IvParameterSpec(iv))
    mac.init(SecretKeySpec(macKey, "HmacSHA256"))
  }

  override fun write(b: Int) {
    throw UnsupportedOperationException()
  }

  override fun write(data: ByteArray) {
    write(data, 0, data.size)
  }

  override fun write(data: ByteArray, off: Int, len: Int) {
    cipher.update(data, off, len)?.let { ciphertext ->
      mac.update(ciphertext)
      super.write(ciphertext)
    }
  }

  override fun flush() {
    cipher.doFinal()?.let { ciphertext ->
      mac.update(ciphertext)
      super.write(ciphertext)
    }

    finalMac = mac.doFinal()

    super.flush()
  }

  override fun close() {
    flush()
    super.close()
  }

  fun getMac(): ByteArray {
    return finalMac ?: throw IllegalStateException("Mac not yet available! You must call flush() before asking for the mac.")
  }
}
