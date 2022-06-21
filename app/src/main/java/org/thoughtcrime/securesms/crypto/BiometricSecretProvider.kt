package org.thoughtcrime.securesms.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.Util
import java.security.InvalidKeyException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature

class BiometricSecretProvider {

    companion object {
        private const val BIOMETRIC_ASYM_KEY_ALIAS = "Session-biometric-asym"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val SIGNATURE_ALGORITHM = "SHA512withECDSA"
    }

    fun getRandomData() = Util.getSecretBytes(32)

    private fun createAsymmetricKey(context: Context) {
        val keyGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE
        )

        val builder = KeyGenParameterSpec.Builder(BIOMETRIC_ASYM_KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA512
            )
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(-1)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setInvalidatedByBiometricEnrollment(true)
        }
        keyGenerator.initialize(builder.build())
        keyGenerator.generateKeyPair()
    }

    fun getOrCreateBiometricSignature(context: Context): Signature {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        if (!ks.containsAlias(BIOMETRIC_ASYM_KEY_ALIAS)
            || !ks.entryInstanceOf(BIOMETRIC_ASYM_KEY_ALIAS, KeyStore.PrivateKeyEntry::class.java)
            || !TextSecurePreferences.getFingerprintKeyGenerated(context)
        ) {
            createAsymmetricKey(context)
            TextSecurePreferences.setFingerprintKeyGenerated(context)
        }
        val signature = try {
            val key = ks.getKey(BIOMETRIC_ASYM_KEY_ALIAS, null) as PrivateKey
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initSign(key)
            signature
        } catch (e: InvalidKeyException) {
            ks.deleteEntry(BIOMETRIC_ASYM_KEY_ALIAS)
            createAsymmetricKey(context)
            TextSecurePreferences.setFingerprintKeyGenerated(context)
            val key = ks.getKey(BIOMETRIC_ASYM_KEY_ALIAS, null) as PrivateKey
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initSign(key)
            signature
        }
        return signature
    }

    fun verifySignature(data: ByteArray, signedData: ByteArray): Boolean {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        val certificate = ks.getCertificate(BIOMETRIC_ASYM_KEY_ALIAS)
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initVerify(certificate)
        signature.update(data)
        return signature.verify(signedData)
    }
}