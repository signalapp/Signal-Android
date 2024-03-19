package org.thoughtcrime.securesms.registration.secondary

import okio.ByteString.Companion.toByteString
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.util.ByteUtil
import org.thoughtcrime.securesms.devicelist.protos.DeviceName
import java.nio.charset.Charset
import java.security.GeneralSecurityException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Use to encrypt a secondary/linked device name.
 */
object DeviceNameCipher {

  private val TAG = Log.tag(DeviceNameCipher::class.java)

  private const val SYNTHETIC_IV_LENGTH = 16

  @JvmStatic
  fun encryptDeviceName(plaintext: ByteArray, identityKeyPair: IdentityKeyPair): ByteArray {
    val ephemeralKeyPair: ECKeyPair = Curve.generateKeyPair()
    val masterSecret: ByteArray = Curve.calculateAgreement(identityKeyPair.publicKey.publicKey, ephemeralKeyPair.privateKey)

    val syntheticIv: ByteArray = computeSyntheticIv(masterSecret, plaintext)
    val cipherKey: ByteArray = computeCipherKey(masterSecret, syntheticIv)

    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(cipherKey, "AES"), IvParameterSpec(createEmptyByteArray(16)))
    val cipherText = cipher.doFinal(plaintext)

    return DeviceName(
      ephemeralPublic = ephemeralKeyPair.publicKey.serialize().toByteString(),
      syntheticIv = syntheticIv.toByteString(),
      ciphertext = cipherText.toByteString()
    ).encode()
  }

  /**
   * Decrypts a [DeviceName]. Returns null if data is invalid/undecryptable.
   */
  @JvmStatic
  fun decryptDeviceName(deviceName: DeviceName, identityKeyPair: IdentityKeyPair): ByteArray? {
    if (deviceName.ephemeralPublic == null || deviceName.syntheticIv == null || deviceName.ciphertext == null) {
      return null
    }

    return try {
      val syntheticIv = deviceName.syntheticIv.toByteArray()
      val cipherText = deviceName.ciphertext.toByteArray()
      val identityKey: ECPrivateKey = identityKeyPair.privateKey
      val ephemeralPublic = Curve.decodePoint(deviceName.ephemeralPublic.toByteArray(), 0)
      val masterSecret = Curve.calculateAgreement(ephemeralPublic, identityKey)

      val mac = Mac.getInstance("HmacSHA256")
      mac.init(SecretKeySpec(masterSecret, "HmacSHA256"))
      val cipherKeyPart1 = mac.doFinal("cipher".toByteArray())

      mac.init(SecretKeySpec(cipherKeyPart1, "HmacSHA256"))
      val cipherKey = mac.doFinal(syntheticIv)

      val cipher = Cipher.getInstance("AES/CTR/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(cipherKey, "AES"), IvParameterSpec(ByteArray(16)))
      val plaintext = cipher.doFinal(cipherText)

      mac.init(SecretKeySpec(masterSecret, "HmacSHA256"))
      val verificationPart1 = mac.doFinal("auth".toByteArray())

      mac.init(SecretKeySpec(verificationPart1, "HmacSHA256"))
      val verificationPart2 = mac.doFinal(plaintext)
      val ourSyntheticIv = ByteUtil.trim(verificationPart2, 16)

      if (!MessageDigest.isEqual(ourSyntheticIv, syntheticIv)) {
        throw GeneralSecurityException("The computed syntheticIv didn't match the actual syntheticIv.")
      }

      plaintext
    } catch (e: GeneralSecurityException) {
      Log.w(TAG, "Failed to decrypt device name.", e)
      null
    } catch (e: InvalidKeyException) {
      Log.w(TAG, "Failed to decrypt device name.", e)
      null
    }
  }

  private fun computeCipherKey(masterSecret: ByteArray, syntheticIv: ByteArray): ByteArray {
    val input = "cipher".toByteArray(Charset.forName("UTF-8"))

    val keyMac = Mac.getInstance("HmacSHA256")
    keyMac.init(SecretKeySpec(masterSecret, "HmacSHA256"))
    val cipherKeyKey: ByteArray = keyMac.doFinal(input)

    val cipherMac = Mac.getInstance("HmacSHA256")
    cipherMac.init(SecretKeySpec(cipherKeyKey, "HmacSHA256"))
    return cipherMac.doFinal(syntheticIv)
  }

  private fun computeSyntheticIv(masterSecret: ByteArray, plaintext: ByteArray): ByteArray {
    val input = "auth".toByteArray(Charset.forName("UTF-8"))

    val keyMac = Mac.getInstance("HmacSHA256")
    keyMac.init(SecretKeySpec(masterSecret, "HmacSHA256"))
    val syntheticIvKey: ByteArray = keyMac.doFinal(input)

    val ivMac = Mac.getInstance("HmacSHA256")
    ivMac.init(SecretKeySpec(syntheticIvKey, "HmacSHA256"))
    return ivMac.doFinal(plaintext).sliceArray(0 until SYNTHETIC_IV_LENGTH)
  }

  private fun createEmptyByteArray(length: Int): ByteArray = ByteArray(length)
}
