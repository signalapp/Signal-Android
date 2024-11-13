package org.whispersystems.signalservice.api.storage

import org.whispersystems.signalservice.internal.util.Util
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts and decrypts data from the storage service.
 */
object SignalStorageCipher {
  private const val IV_LENGTH = 12

  @JvmStatic
  fun encrypt(key: StorageCipherKey, data: ByteArray): ByteArray {
    try {
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      val iv = Util.getSecretBytes(IV_LENGTH)

      cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.serialize(), "AES"), GCMParameterSpec(128, iv))
      val ciphertext = cipher.doFinal(data)

      return iv + ciphertext
    } catch (e: NoSuchAlgorithmException) {
      throw AssertionError(e)
    } catch (e: InvalidKeyException) {
      throw AssertionError(e)
    } catch (e: InvalidAlgorithmParameterException) {
      throw AssertionError(e)
    } catch (e: NoSuchPaddingException) {
      throw AssertionError(e)
    } catch (e: BadPaddingException) {
      throw AssertionError(e)
    } catch (e: IllegalBlockSizeException) {
      throw AssertionError(e)
    }
  }

  @JvmStatic
  @Throws(org.signal.libsignal.protocol.InvalidKeyException::class)
  fun decrypt(key: StorageCipherKey, data: ByteArray): ByteArray {
    try {
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      val iv = data.copyOfRange(0, IV_LENGTH)
      val cipherText = data.copyOfRange(IV_LENGTH, data.size)

      cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.serialize(), "AES"), GCMParameterSpec(128, iv))
      return cipher.doFinal(cipherText)
    } catch (e: InvalidKeyException) {
      throw org.signal.libsignal.protocol.InvalidKeyException(e)
    } catch (e: BadPaddingException) {
      throw org.signal.libsignal.protocol.InvalidKeyException(e)
    } catch (e: IllegalBlockSizeException) {
      throw org.signal.libsignal.protocol.InvalidKeyException(e)
    } catch (e: NoSuchAlgorithmException) {
      throw AssertionError(e)
    } catch (e: NoSuchPaddingException) {
      throw AssertionError(e)
    } catch (e: InvalidAlgorithmParameterException) {
      throw AssertionError(e)
    }
  }
}
