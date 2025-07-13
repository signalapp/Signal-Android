package org.thoughtcrime.securesms.crypto.fips

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher

/**
 * Manages the hardware-backed storage of the FIPS private identity key.
 *
 * This singleton object is responsible for:
 * 1. Creating a hardware-backed RSA key pair within the Android Keystore. This key
 * is used exclusively for wrapping (encrypting) and unwrapping (decrypting) other keys.
 * 2. Providing methods to wrap the FIPS P-256 private identity key using the
 * hardware-backed public key.
 * 3. Providing methods to unwrap the FIPS private key using the hardware-backed
 * private key. This operation occurs within the device's Trusted Execution
 * Environment (TEE) or Secure Element (SE).
 *
 * This implementation fulfills the "Hardware-Backed Key Wrapping" requirement of the
 * architecture plan, ensuring the long-term FIPS private key is protected at rest.
 */
@RequiresApi(Build.VERSION_CODES.M)
object FipsKeyStoreManager {

    private const val TAG = "FipsKeyStoreManager"
    private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val FIPS_WRAPPING_KEY_ALIAS = "fips_signal_wrapping_key_v1"

    // The transformation used for wrapping/unwrapping keys. RSA with OAEP padding is secure.
    private const val WRAPPING_TRANSFORMATION = "${KeyProperties.KEY_ALGORITHM_RSA}/" +
                                               "${KeyProperties.BLOCK_MODE_ECB}/" +
                                               "${KeyProperties.ENCRYPTION_PADDING_RSA_OAEP}"

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply {
        load(null)
    }

    init {
        // Ensure the wrapping key exists on initialization.
        if (!keyStore.containsAlias(FIPS_WRAPPING_KEY_ALIAS)) {
            Log.i(TAG, "FIPS wrapping key not found. Generating a new one...")
            generateWrappingKeyPair()
        } else {
            Log.i(TAG, "FIPS wrapping key already exists.")
        }
    }

    /**
     * Wraps (encrypts) the given FIPS private identity key.
     *
     * @param plaintextFipsKey The raw bytes of the P-256 private identity key.
     * @return The encrypted (wrapped) key as a byte array.
     * @throws IOException if the wrapping operation fails.
     */
    fun wrapFipsIdentityKey(plaintextFipsKey: ByteArray): ByteArray {
        try {
            val publicKey = getWrappingKey()?.publicKey
                ?: throw IOException("Could not retrieve FIPS wrapping key.")

            val cipher = Cipher.getInstance(WRAPPING_TRANSFORMATION)
            cipher.init(Cipher.WRAP_MODE, publicKey)
            return cipher.wrap(FipsJniKey(plaintextFipsKey))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wrap FIPS identity key.", e)
            throw IOException("FIPS key wrapping failed.", e)
        }
    }

    /**
     * Unwraps (decrypts) a previously wrapped FIPS private identity key.
     * This operation is performed inside the device's secure hardware.
     *
     * @param wrappedFipsKey The encrypted key data.
     * @return The raw bytes of the plaintext P-256 private identity key.
     * @throws IOException if the unwrapping operation fails.
     */
    fun unwrapFipsIdentityKey(wrappedFipsKey: ByteArray): ByteArray {
        try {
            val privateKey = getWrappingKey()?.privateKey
                ?: throw IOException("Could not retrieve FIPS wrapping key.")

            val cipher = Cipher.getInstance(WRAPPING_TRANSFORMATION)
            cipher.init(Cipher.UNWRAP_MODE, privateKey)
            val unwrappedKey = cipher.unwrap(wrappedFipsKey, KeyProperties.KEY_ALGORITHM_EC, Cipher.PRIVATE_KEY) as FipsJniKey
            return unwrappedKey.encoded
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unwrap FIPS identity key.", e)
            throw IOException("FIPS key unwrapping failed.", e)
        }
    }

    /**
     * Generates a new RSA key pair in the Android Keystore for wrapping keys.
     * The key is marked as hardware-backed if the device supports it.
     */
    private fun generateWrappingKeyPair() {
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                ANDROID_KEYSTORE_PROVIDER
            )

            val spec = KeyGenParameterSpec.Builder(
                FIPS_WRAPPING_KEY_ALIAS,
                KeyProperties.PURPOSE_WRAP_KEY or KeyProperties.PURPOSE_UNWRAP_KEY
            )
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .build()

            keyPairGenerator.initialize(spec)
            keyPairGenerator.generateKeyPair()
            Log.i(TAG, "Successfully generated new FIPS wrapping key.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate FIPS wrapping key.", e)
            // This is a fatal error for the FIPS key storage system.
        }
    }

    /**
     * Retrieves the wrapping key pair from the Android Keystore.
     */
    private fun getWrappingKey(): KeyStore.PrivateKeyEntry? {
        return keyStore.getEntry(FIPS_WRAPPING_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
    }

    /**
     * A dummy java.security.Key implementation to satisfy the Cipher.wrap/unwrap API.
     * It simply holds the raw bytes of our FIPS key.
     */
    private class FipsJniKey(private val keyBytes: ByteArray) : PrivateKey {
        override fun getAlgorithm(): String = KeyProperties.KEY_ALGORITHM_EC
        override fun getFormat(): String = "RAW"
        override fun getEncoded(): ByteArray = keyBytes
    }
}
