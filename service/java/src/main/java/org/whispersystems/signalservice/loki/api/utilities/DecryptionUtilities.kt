package org.whispersystems.signalservice.loki.api.utilities

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object DecryptionUtilities {

    /**
     * Sync. Don't call from the main thread.
     */
    internal fun decryptUsingAESGCM(ivAndCiphertext: ByteArray, symmetricKey: ByteArray): ByteArray {
        val iv = ivAndCiphertext.sliceArray(0 until EncryptionUtilities.ivSize)
        val ciphertext = ivAndCiphertext.sliceArray(EncryptionUtilities.ivSize until ivAndCiphertext.count())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(symmetricKey, "AES"), GCMParameterSpec(EncryptionUtilities.gcmTagSize, iv))
        return cipher.doFinal(ciphertext)
    }
}
