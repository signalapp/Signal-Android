package org.session.libsession.messaging.utilities

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.AEAD
import com.goterl.lazysodium.interfaces.GenericHash
import com.goterl.lazysodium.interfaces.Hash
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.toHexString
import org.whispersystems.curve25519.Curve25519
import kotlin.experimental.xor

object SodiumUtilities {
    private val sodium by lazy { LazySodiumAndroid(SodiumAndroid()) }
    private val curve by lazy { Curve25519.getInstance(Curve25519.BEST) }

    private const val SCALAR_LENGTH: Int = 32 // crypto_core_ed25519_scalarbytes
    private const val NO_CLAMP_LENGTH: Int = 32 // crypto_scalarmult_ed25519_bytes
    private const val SCALAR_MULT_LENGTH: Int = 32 // crypto_scalarmult_bytes
    private const val PUBLIC_KEY_LENGTH: Int = 32 // crypto_scalarmult_bytes
    private const val SECRET_KEY_LENGTH: Int = 64 //crypto_sign_secretkeybytes

    /* 64-byte blake2b hash then reduce to get the blinding factor */
    fun generateBlindingFactor(serverPublicKey: String): ByteArray? {
        // k = salt.crypto_core_ed25519_scalar_reduce(blake2b(server_pk, digest_size=64).digest())
        val serverPubKeyData = Hex.fromStringCondensed(serverPublicKey)
        if (serverPubKeyData.size != PUBLIC_KEY_LENGTH) return null
        val serverPubKeyHash = ByteArray(GenericHash.BLAKE2B_BYTES_MAX)
        if (!sodium.cryptoGenericHash(serverPubKeyHash, serverPubKeyHash.size, serverPubKeyData, serverPubKeyData.size.toLong())) {
            return null
        }
        // Reduce the server public key into an ed25519 scalar (`k`)
        val x25519PublicKey = ByteArray(SCALAR_LENGTH)
        sodium.cryptoCoreEd25519ScalarReduce(x25519PublicKey, serverPubKeyHash)
        return if (x25519PublicKey.any { it.toInt() != 0 }) {
            x25519PublicKey
        } else  null
    }

    /*
     Calculate k*a. To get 'a' (the Ed25519 private key scalar) we call the sodium function to
     convert to an *x* secret key, which seems wrong--but isn't because converted keys use the
     same secret scalar secret (and so this is just the most convenient way to get 'a' out of
     a sodium Ed25519 secret key)
    */
    fun generatePrivateKeyScalar(secretKey: ByteArray): ByteArray? {
        // a = s.to_curve25519_private_key().encode()
        val aBytes = ByteArray(SCALAR_MULT_LENGTH)
        return if (sodium.convertSecretKeyEd25519ToCurve25519(aBytes, secretKey)) {
            aBytes
        } else null
    }

    /* Constructs a "blinded" key pair (`ka, kA`) based on an open group server `publicKey` and an ed25519 `keyPair` */
    @JvmStatic
    fun blindedKeyPair(serverPublicKey: String, edKeyPair: KeyPair): KeyPair?  {
        if (edKeyPair.publicKey.asBytes.size != PUBLIC_KEY_LENGTH || edKeyPair.secretKey.asBytes.size != SECRET_KEY_LENGTH) return null
        val kBytes = generateBlindingFactor(serverPublicKey) ?: return null
        val aBytes = generatePrivateKeyScalar(edKeyPair.secretKey.asBytes) ?: return null
        // Generate the blinded key pair `ka`, `kA`
        val kaBytes = ByteArray(SECRET_KEY_LENGTH)
        sodium.cryptoCoreEd25519ScalarMul(kaBytes, kBytes, aBytes)
        if (kaBytes.all { it.toInt() == 0 }) return null

        val kABytes = ByteArray(PUBLIC_KEY_LENGTH)
        return if (sodium.cryptoScalarMultEd25519BaseNoClamp(kABytes, kaBytes)) {
            KeyPair(Key.fromBytes(kABytes), Key.fromBytes(kaBytes))
        } else {
            null
        }
    }

    /*
     Constructs an Ed25519 signature from a root Ed25519 key and a blinded scalar/pubkey pair, with one tweak to the
     construction: we add kA into the hashed value that yields r so that we have domain separation for different blinded
     pubkeys (this doesn't affect verification at all)
    */
    fun sogsSignature(
        message: ByteArray,
        secretKey: ByteArray,
        blindedSecretKey: ByteArray, /*ka*/
        blindedPublicKey: ByteArray /*kA*/
    ): ByteArray? {
        // H_rh = sha512(s.encode()).digest()[32:]
        val digest = ByteArray(Hash.SHA512_BYTES)
        val h_rh = if (sodium.cryptoHashSha512(digest, secretKey, secretKey.size.toLong())) {
            digest.takeLast(32).toByteArray()
        } else return null

        // r = salt.crypto_core_ed25519_scalar_reduce(sha512_multipart(H_rh, kA, message_parts))
        val rHash = sha512Multipart(listOf(h_rh, blindedPublicKey, message)) ?: return null
        val r = ByteArray(SCALAR_LENGTH)
        sodium.cryptoCoreEd25519ScalarReduce(r, rHash)
        if (r.all { it.toInt() == 0 }) return null

        // sig_R = salt.crypto_scalarmult_ed25519_base_noclamp(r)
        val sig_R = ByteArray(NO_CLAMP_LENGTH)
        if (!sodium.cryptoScalarMultEd25519BaseNoClamp(sig_R, r)) return null

        // HRAM = salt.crypto_core_ed25519_scalar_reduce(sha512_multipart(sig_R, kA, message_parts))
        val hRamHash = sha512Multipart(listOf(sig_R, blindedPublicKey, message)) ?: return null
        val hRam = ByteArray(SCALAR_LENGTH)
        sodium.cryptoCoreEd25519ScalarReduce(hRam, hRamHash)
        if (hRam.all { it.toInt() == 0 }) return null

        // sig_s = salt.crypto_core_ed25519_scalar_add(r, salt.crypto_core_ed25519_scalar_mul(HRAM, ka))
        val sig_sMul = ByteArray(SCALAR_LENGTH)
        val sig_s = ByteArray(SCALAR_LENGTH)
        sodium.cryptoCoreEd25519ScalarMul(sig_sMul, hRam, blindedSecretKey)
        if (sig_sMul.any { it.toInt() != 0 }) {
            sodium.cryptoCoreEd25519ScalarAdd(sig_s,  r, sig_sMul)
            if (sig_s.all { it.toInt() == 0 }) return null
        } else return null

        return sig_R + sig_s
    }

    private fun sha512Multipart(parts: List<ByteArray>): ByteArray? {
        val state = Hash.State512()
        sodium.cryptoHashSha512Init(state)
        parts.forEach {
            sodium.cryptoHashSha512Update(state, it, it.size.toLong())
        }
        val finalHash = ByteArray(Hash.SHA512_BYTES)
        return if (sodium.cryptoHashSha512Final(state, finalHash)) {
            finalHash
        } else null
    }

    /* Combines two keys (`kA`) */
    fun combineKeys(lhsKey: ByteArray, rhsKey: ByteArray): ByteArray? {
        val kA = ByteArray(NO_CLAMP_LENGTH)
        return if (sodium.cryptoScalarMultEd25519NoClamp(kA, lhsKey, rhsKey)) {
            kA
        } else null
    }

    /*
     Calculate a shared secret for a message from A to B:
     BLAKE2b(a kB || kA || kB)
     The receiver can calculate the same value via:
     BLAKE2b(b kA || kA || kB)
    */
    fun sharedBlindedEncryptionKey(
        secretKey: ByteArray,
        otherBlindedPublicKey: ByteArray,
        kA: ByteArray, /*fromBlindedPublicKey*/
        kB: ByteArray  /*toBlindedPublicKey*/
    ): ByteArray? {
        val aBytes = generatePrivateKeyScalar(secretKey) ?: return null
        val combinedKeyBytes = combineKeys(aBytes, otherBlindedPublicKey) ?: return null
        val outputHash = ByteArray(GenericHash.KEYBYTES)
        val inputBytes = combinedKeyBytes + kA + kB
        return if (sodium.cryptoGenericHash(outputHash, outputHash.size, inputBytes, inputBytes.size.toLong())) {
            outputHash
        } else null
    }

    /* This method should be used to check if a users standard sessionId matches a blinded one */
    fun sessionId(
        standardSessionId: String,
        blindedSessionId: String,
        serverPublicKey: String
    ): Boolean {
        // Only support generating blinded keys for standard session ids
        val sessionId = SessionId(standardSessionId)
        if (sessionId.prefix != IdPrefix.STANDARD) return false
        val blindedId = SessionId(blindedSessionId)
        if (blindedId.prefix != IdPrefix.BLINDED) return false
        val k = generateBlindingFactor(serverPublicKey) ?: return false

        // From the session id (ignoring 05 prefix) we have two possible ed25519 pubkeys;
        // the first is the positive (which is what Signal's XEd25519 conversion always uses)
        val xEd25519Key = curve.convertToEd25519PublicKey(Key.fromHexString(sessionId.publicKey).asBytes)

        // Blind the positive public key
        val pk1 = combineKeys(k, xEd25519Key) ?: return false

        // For the negative, what we're going to get out of the above is simply the negative of pk1, so flip the sign bit to get pk2
        //     pk2 = pk1[0:31] + bytes([pk1[31] ^ 0b1000_0000])
        val pk2 = pk1.take(31).toByteArray() + listOf(pk1.last().xor(128.toByte())).toByteArray()
        return SessionId(IdPrefix.BLINDED, pk1).publicKey == blindedId.publicKey ||
                SessionId(IdPrefix.BLINDED, pk2).publicKey == blindedId.publicKey
    }

    fun encrypt(message: ByteArray, secretKey: ByteArray, nonce: ByteArray, additionalData: ByteArray? = null): ByteArray? {
        val authenticatedCipherText = ByteArray(message.size + AEAD.CHACHA20POLY1305_ABYTES)
        return if (sodium.cryptoAeadXChaCha20Poly1305IetfEncrypt(
                authenticatedCipherText,
                longArrayOf(0),
                message,
                message.size.toLong(),
                additionalData,
                (additionalData?.size ?: 0).toLong(),
                null,
                nonce,
                secretKey
            )
        ) {
            authenticatedCipherText
        } else null
    }

    fun decrypt(ciphertext: ByteArray, decryptionKey: ByteArray, nonce: ByteArray): ByteArray? {
        val plaintextSize = ciphertext.size - AEAD.CHACHA20POLY1305_ABYTES
        val plaintext = ByteArray(plaintextSize)
        return if (sodium.cryptoAeadXChaCha20Poly1305IetfDecrypt(
                plaintext,
                longArrayOf(plaintextSize.toLong()),
                null,
                ciphertext,
                ciphertext.size.toLong(),
                null,
                0L,
                nonce,
                decryptionKey
            )
        ) {
            plaintext
        } else null
    }

    fun toX25519(ed25519PublicKey: ByteArray): ByteArray? {
        val x25519PublicKey = ByteArray(PUBLIC_KEY_LENGTH)
        return if (sodium.convertPublicKeyEd25519ToCurve25519(x25519PublicKey, ed25519PublicKey)) {
            x25519PublicKey
        } else null
    }

}

class SessionId {
    var prefix: IdPrefix?
    var publicKey: String

    constructor(id: String) {
        prefix = IdPrefix.fromValue(id)
        publicKey = id.drop(2)
    }

    constructor(prefix: IdPrefix, publicKey: ByteArray) {
        this.prefix = prefix
        this.publicKey = publicKey.toHexString()
    }

    val hexString
        get() = prefix?.value + publicKey
}

