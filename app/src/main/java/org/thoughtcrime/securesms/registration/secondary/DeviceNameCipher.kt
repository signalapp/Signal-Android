package org.thoughtcrime.securesms.registration.secondary

import okio.ByteString.Companion.toByteString
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.thoughtcrime.securesms.devicelist.protos.DeviceName
import java.nio.charset.Charset
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Use to encrypt a secondary/linked device name.
 */
object DeviceNameCipher {

  private const val SYNTHETIC_IV_LENGTH = 16

  @JvmStatic
  fun encryptDeviceName(plaintext: ByteArray, identityKeyPair: IdentityKeyPair): ByteArray {
    val ephemeralKeyPair: ECKeyPair = Curve.generateKeyPair()
    val masterSecret: ByteArray = Curve.calculateAgreement(identityKeyPair.publicKey.publicKey, ephemeralKeyPair.privateKey)

    val syntheticIv: ByteArray = computeSyntheticIv(masterSecret, plaintext)
    val cipherKey: ByteArray = computeCipherKey(masterSecret, syntheticIv)

    val cipher = Cipher.getInstance("AES/CTR/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(cipherKey, "AES"), IvParameterSpec(ByteArray(16)))
    val cipherText = cipher.doFinal(plaintext)

    return DeviceName(
      ephemeralPublic = ephemeralKeyPair.publicKey.serialize().toByteString(),
      syntheticIv = syntheticIv.toByteString(),
      ciphertext = cipherText.toByteString()
    ).encode()
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
}
