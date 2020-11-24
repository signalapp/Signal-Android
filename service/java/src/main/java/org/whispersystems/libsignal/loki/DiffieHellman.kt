package org.whispersystems.libsignal.loki

import org.whispersystems.curve25519.Curve25519
import org.whispersystems.signalservice.internal.util.Util
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object DiffieHellman {
    private val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    private val curve = Curve25519.getInstance(Curve25519.BEST)
    private val ivSize = 16

    @JvmStatic @Throws
    fun encrypt(plaintext: ByteArray, symmetricKey: ByteArray): ByteArray {
        val iv = Util.getSecretBytes(ivSize)
        val ivSpec = IvParameterSpec(iv)
        val secretKeySpec = SecretKeySpec(symmetricKey, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec)
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    @JvmStatic @Throws
    fun encrypt(plaintext: ByteArray, publicKey: ByteArray, privateKey: ByteArray): ByteArray {
        val symmetricKey = curve.calculateAgreement(publicKey, privateKey)
        return encrypt(plaintext, symmetricKey)
    }

    @JvmStatic @Throws
    fun decrypt(ivAndCiphertext: ByteArray, symmetricKey: ByteArray): ByteArray {
        val iv = ivAndCiphertext.sliceArray(0 until ivSize)
        val ciphertext = ivAndCiphertext.sliceArray(ivSize until ivAndCiphertext.size)
        val ivSpec = IvParameterSpec(iv)
        val secretKeySpec = SecretKeySpec(symmetricKey, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec)
        return cipher.doFinal(ciphertext)
    }

    @JvmStatic @Throws
    fun decrypt(ivAndCiphertext: ByteArray, publicKey: ByteArray, privateKey: ByteArray): ByteArray {
        val symmetricKey = curve.calculateAgreement(publicKey, privateKey)
        return decrypt(ivAndCiphertext, symmetricKey)
    }
}
