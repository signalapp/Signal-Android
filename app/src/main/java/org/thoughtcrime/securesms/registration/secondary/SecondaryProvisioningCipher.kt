package org.thoughtcrime.securesms.registration.secondary

import org.signal.zkgroup.profiles.ProfileKey
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.ecc.ECPublicKey
import org.whispersystems.libsignal.kdf.HKDF
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.crypto.PrimaryProvisioningCipher
import org.whispersystems.signalservice.internal.push.ProvisioningProtos
import java.security.InvalidKeyException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Used to decrypt a secondary/link device provisioning message from the primary device.
 */
class SecondaryProvisioningCipher private constructor(private val secondaryIdentityKeyPair: IdentityKeyPair) {

  val secondaryDevicePublicKey: IdentityKey = secondaryIdentityKeyPair.publicKey

  fun decrypt(envelope: ProvisioningProtos.ProvisionEnvelope): ProvisionDecryptResult {
    val primaryEphemeralPublicKey = envelope.publicKey.toByteArray()
    val body = envelope.body.toByteArray()

    val provisionMessageLength = body.size - VERSION_LENGTH - IV_LENGTH - MAC_LENGTH

    if (provisionMessageLength <= 0) {
      return ProvisionDecryptResult.Error
    }

    val version = body[0].toInt()
    if (version != 1) {
      return ProvisionDecryptResult.Error
    }

    val iv = body.sliceArray(1 until (1 + IV_LENGTH))
    val theirMac = body.sliceArray(body.size - MAC_LENGTH until body.size)
    val message = body.sliceArray(0 until body.size - MAC_LENGTH)
    val cipherText = body.sliceArray((1 + IV_LENGTH) until body.size - MAC_LENGTH)

    val sharedSecret = Curve.calculateAgreement(ECPublicKey(primaryEphemeralPublicKey), secondaryIdentityKeyPair.privateKey)
    val derivedSecret: ByteArray = HKDF.deriveSecrets(sharedSecret, PrimaryProvisioningCipher.PROVISIONING_MESSAGE.toByteArray(), 64)

    val cipherKey = derivedSecret.sliceArray(0 until 32)
    val macKey = derivedSecret.sliceArray(32 until 64)

    val ourHmac = getMac(macKey, message)

    if (!MessageDigest.isEqual(theirMac, ourHmac)) {
      return ProvisionDecryptResult.Error
    }

    val plaintext = try {
      getPlaintext(cipherKey, iv, cipherText)
    } catch (e: Exception) {
      return ProvisionDecryptResult.Error
    }

    val provisioningMessage = ProvisioningProtos.ProvisionMessage.parseFrom(plaintext)

    return ProvisionDecryptResult.Success(
      uuid = UuidUtil.parseOrThrow(provisioningMessage.uuid),
      e164 = provisioningMessage.number,
      identityKeyPair = IdentityKeyPair(IdentityKey(provisioningMessage.identityKeyPublic.toByteArray()), Curve.decodePrivatePoint(provisioningMessage.identityKeyPrivate.toByteArray())),
      profileKey = ProfileKey(provisioningMessage.profileKey.toByteArray()),
      areReadReceiptsEnabled = provisioningMessage.readReceipts,
      primaryUserAgent = provisioningMessage.userAgent,
      provisioningCode = provisioningMessage.provisioningCode,
      provisioningVersion = provisioningMessage.provisioningVersion
    )
  }

  private fun getMac(key: ByteArray, message: ByteArray): ByteArray? {
    return try {
      val mac = Mac.getInstance("HmacSHA256")
      mac.init(SecretKeySpec(key, "HmacSHA256"))
      mac.doFinal(message)
    } catch (e: NoSuchAlgorithmException) {
      throw AssertionError(e)
    } catch (e: InvalidKeyException) {
      throw AssertionError(e)
    }
  }

  private fun getPlaintext(key: ByteArray, iv: ByteArray, message: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    return cipher.doFinal(message)
  }

  companion object {
    private const val VERSION_LENGTH = 1
    private const val IV_LENGTH = 16
    private const val MAC_LENGTH = 32

    fun generate(): SecondaryProvisioningCipher {
      return SecondaryProvisioningCipher(IdentityKeyUtil.generateIdentityKeyPair())
    }
  }

  sealed class ProvisionDecryptResult {
    object Error : ProvisionDecryptResult()

    data class Success(
      val uuid: UUID,
      val e164: String,
      val identityKeyPair: IdentityKeyPair,
      val profileKey: ProfileKey,
      val areReadReceiptsEnabled: Boolean,
      val primaryUserAgent: String?,
      val provisioningCode: String,
      val provisioningVersion: Int
    ) : ProvisionDecryptResult()
  }
}
