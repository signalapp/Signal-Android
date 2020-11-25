package org.thoughtcrime.securesms.loki.utilities

import android.content.Context
import com.goterl.lazycode.lazysodium.LazySodiumAndroid
import com.goterl.lazycode.lazysodium.SodiumAndroid
import com.goterl.lazycode.lazysodium.utils.KeyPair
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.Hex
import org.session.libsignal.libsignal.ecc.DjbECPrivateKey
import org.session.libsignal.libsignal.ecc.DjbECPublicKey
import org.session.libsignal.libsignal.ecc.ECKeyPair

object KeyPairUtilities {

    private val sodium = LazySodiumAndroid(SodiumAndroid())

    data class KeyPairGenerationResult(
        val seed: ByteArray,
        val ed25519KeyPair: KeyPair,
        val x25519KeyPair: ECKeyPair
    )

    fun generate(): KeyPairGenerationResult {
        val seed = sodium.randomBytesBuf(16)
        try {
            return generate(seed)
        } catch (exception: Exception) {
            return generate()
        }
    }

    fun generate(seed: ByteArray): KeyPairGenerationResult {
        val padding = ByteArray(16) { 0 }
        val ed25519KeyPair = sodium.cryptoSignSeedKeypair(seed + padding)
        val sodiumX25519KeyPair = sodium.convertKeyPairEd25519ToCurve25519(ed25519KeyPair)
        val x25519KeyPair = ECKeyPair(DjbECPublicKey(sodiumX25519KeyPair.publicKey.asBytes), DjbECPrivateKey(sodiumX25519KeyPair.secretKey.asBytes))
        return KeyPairGenerationResult(seed, ed25519KeyPair, x25519KeyPair)
    }

    fun store(context: Context, seed: ByteArray, ed25519KeyPair: KeyPair, x25519KeyPair: ECKeyPair) {
        IdentityKeyUtil.save(context, IdentityKeyUtil.LOKI_SEED, Hex.toStringCondensed(seed))
        IdentityKeyUtil.save(context, IdentityKeyUtil.IDENTITY_PUBLIC_KEY_PREF, Base64.encodeBytes(x25519KeyPair.publicKey.serialize()))
        IdentityKeyUtil.save(context, IdentityKeyUtil.IDENTITY_PRIVATE_KEY_PREF, Base64.encodeBytes(x25519KeyPair.privateKey.serialize()))
        IdentityKeyUtil.save(context, IdentityKeyUtil.ED25519_PUBLIC_KEY, Base64.encodeBytes(ed25519KeyPair.publicKey.asBytes))
        IdentityKeyUtil.save(context, IdentityKeyUtil.ED25519_SECRET_KEY, Base64.encodeBytes(ed25519KeyPair.secretKey.asBytes))
    }
}