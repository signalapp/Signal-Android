package network.loki.messenger

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.session.libsession.messaging.utilities.SodiumUtilities
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.toHexString

@RunWith(AndroidJUnit4::class)
class SodiumUtilitiesTest {

    private val publicKey: String = "88672ccb97f40bb57238989226cf429b575ba355443f47bc76c5ab144a96c65b"
    private val privateKey: String = "30d796c1ddb4dc455fd998a98aa275c247494a9a7bde9c1fee86ae45cd585241"
    private val edKeySeed: String = "c010d89eccbaf5d1c6d19df766c6eedf965d4a28a56f87c9fc819edb59896dd9"
    private val edPublicKey: String = "bac6e71efd7dfa4a83c98ed24f254ab2c267f9ccdb172a5280a0444ad24e89cc"
    private val edSecretKey: String = "c010d89eccbaf5d1c6d19df766c6eedf965d4a28a56f87c9fc819edb59896dd9bac6e71efd7dfa4a83c98ed24f254ab2c267f9ccdb172a5280a0444ad24e89cc"
    private val blindedPublicKey: String = "98932d4bccbe595a8789d7eb1629cefc483a0eaddc7e20e8fe5c771efafd9af5"
    private val serverPublicKey: String = "c3b3c6f32f0ab5a57f853cc4f30f5da7fda5624b0c77b3fb0829de562ada081d"
    
    private val edKeyPair = KeyPair(Key.fromHexString(edPublicKey), Key.fromHexString(edSecretKey))

    @Test
    fun generateBlindingFactorSuccess() {
        val result = SodiumUtilities.generateBlindingFactor(serverPublicKey)

        assertThat(result?.toHexString(), equalTo("84e3eb75028a9b73fec031b7448e322a68ca6485fad81ab1bead56f759ebeb0f"))
    }

    @Test
    fun generateBlindingFactorFailure() {
        val result = SodiumUtilities.generateBlindingFactor("Test")

        assertNull(result?.toHexString())
    }

    @Test
    fun blindedKeyPairSuccess() {
        val result = SodiumUtilities.blindedKeyPair(serverPublicKey, edKeyPair)!!

        assertThat(result.publicKey.asHexString.lowercase(), equalTo(blindedPublicKey))
        assertThat(result.secretKey.asHexString.take(64).lowercase(), equalTo("16663322d6b684e1c9dcc02b9e8642c3affd3bc431a9ea9e63dbbac88ce7a305"))
    }

    @Test
    fun blindedKeyPairFailurePublicKeyLength() {
        val result = SodiumUtilities.blindedKeyPair(
            serverPublicKey,
            KeyPair(Key.fromHexString(edPublicKey.take(4)), Key.fromHexString(edKeySeed))
        )

        assertNull(result)
    }

    @Test
    fun blindedKeyPairFailureSecretKeyLength() {
        val result = SodiumUtilities.blindedKeyPair(
            serverPublicKey,
            KeyPair(Key.fromHexString(edPublicKey), Key.fromHexString(edSecretKey.take(4)))
        )

        assertNull(result)
    }

    @Test
    fun blindedKeyPairFailureBlindingFactor() {
        val result = SodiumUtilities.blindedKeyPair("Test", edKeyPair)

        assertNull(result)
    }

    @Test
    fun sogsSignature() {
        val expectedSignature = "dcc086abdd2a740d9260b008fb37e12aa0ff47bd2bd9e177bbbec37fd46705a9072ce747bda66c788c3775cdd7ad60ad15a478e0886779aad5d795fd7bf8350d"

        val result = SodiumUtilities.sogsSignature(
            "TestMessage".toByteArray(),
            Hex.fromStringCondensed(edSecretKey),
            Hex.fromStringCondensed("44d82cc15c0a5056825cae7520b6b52d000a23eb0c5ed94c4be2d9dc41d2d409"),
            Hex.fromStringCondensed("0bb7815abb6ba5142865895f3e5286c0527ba4d31dbb75c53ce95e91ffe025a2")
        )

        assertThat(result?.toHexString(), equalTo(expectedSignature))
    }

    @Test
    fun combineKeysSuccess() {
        val result = SodiumUtilities.combineKeys(
            Hex.fromStringCondensed(edSecretKey),
            Hex.fromStringCondensed(edPublicKey)
        )

        assertThat(result?.toHexString(), equalTo("1159b5d0fcfba21228eb2121a0f59712fa8276fc6e5547ff519685a40b9819e6"))
    }

    @Test
    fun combineKeysFailure() {
        val result = SodiumUtilities.combineKeys(
            SodiumUtilities.generatePrivateKeyScalar(Hex.fromStringCondensed(edSecretKey))!!,
            Hex.fromStringCondensed(publicKey)
        )

        assertNull(result?.toHexString())
    }

    @Test
    fun sharedBlindedEncryptionKeySuccess() {
        val result = SodiumUtilities.sharedBlindedEncryptionKey(
            Hex.fromStringCondensed(edSecretKey),
            Hex.fromStringCondensed(blindedPublicKey),
            Hex.fromStringCondensed(publicKey),
            Hex.fromStringCondensed(blindedPublicKey)
        )

        assertThat(result?.toHexString(), equalTo("388ee09e4c356b91f1cce5cc0aa0cf59e8e8cade69af61685d09c2d2731bc99e"))
    }

    @Test
    fun sharedBlindedEncryptionKeyFailure() {
        val result = SodiumUtilities.sharedBlindedEncryptionKey(
            Hex.fromStringCondensed(edSecretKey),
            Hex.fromStringCondensed(publicKey),
            Hex.fromStringCondensed(edPublicKey),
            Hex.fromStringCondensed(publicKey)
        )

        assertNull(result?.toHexString())
    }

    @Test
    fun sessionIdSuccess() {
        val result = SodiumUtilities.sessionId("05$publicKey", "15$blindedPublicKey", serverPublicKey)

        assertTrue(result)
    }

    @Test
    fun sessionIdFailureInvalidSessionId() {
        val result = SodiumUtilities.sessionId("AB$publicKey", "15$blindedPublicKey", serverPublicKey)

        assertFalse(result)
    }

    @Test
    fun sessionIdFailureInvalidBlindedId() {
        val result = SodiumUtilities.sessionId("05$publicKey", "AB$blindedPublicKey", serverPublicKey)

        assertFalse(result)
    }

    @Test
    fun sessionIdFailureBlindingFactor() {
        val result = SodiumUtilities.sessionId("05$publicKey", "15$blindedPublicKey", "Test")

        assertFalse(result)
    }

}