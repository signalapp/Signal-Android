package org.whispersystems.signalservice.loki.protocol.closedgroups

import org.whispersystems.libsignal.ecc.DjbECPrivateKey
import org.whispersystems.libsignal.ecc.DjbECPublicKey
import org.whispersystems.libsignal.ecc.ECKeyPair
import org.whispersystems.libsignal.logging.Log
import org.whispersystems.libsignal.util.ByteUtil
import org.whispersystems.libsignal.util.Hex
import org.whispersystems.signalservice.internal.util.Util
import org.whispersystems.signalservice.loki.api.utilities.EncryptionUtilities
import org.whispersystems.signalservice.loki.utilities.removing05PrefixIfNeeded
import org.whispersystems.signalservice.loki.utilities.toHexString
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

public final class SharedSenderKeysImplementation(private val database: SharedSenderKeysDatabaseProtocol, private val delegate: SharedSenderKeysImplementationDelegate) {
    private val gcmTagSize = 128
    private val ivSize = 12

    // A quick overview of how shared sender key based closed groups work:
    //
    // • When a user creates a group, they generate a key pair for the group along with a ratchet for
    //   every member of the group. They bundle this together with some other group info such as the group
    //   name in a `ClosedGroupUpdateMessage` and send that using established channels to every member of
    //   the group. Note that because a user can only pick from their existing contacts when selecting
    //   the group members they shouldn't need to establish sessions before being able to send the
    //   `ClosedGroupUpdateMessage`.
    // • After the group is created, every user polls for the public key associated with the group.
    // • Upon receiving a `ClosedGroupUpdateMessage` of type `.new`, a user sends session requests to all
    //   other members of the group they don't yet have a session with for reasons outlined below.
    // • When a user sends a message they step their ratchet and use the resulting message key to encrypt
    //   the message.
    // • When another user receives that message, they step the ratchet associated with the sender and
    //   use the resulting message key to decrypt the message.
    // • When a user leaves or is kicked from a group, all members must generate new ratchets to ensure that
    //   removed users can't decrypt messages going forward. To this end every user deletes all ratchets
    //   associated with the group in question upon receiving a group update message that indicates that
    //   a user left. They then generate a new ratchet for themselves and send it out to all members of
    //   the group. The user should already have established sessions with all other members at this point
    //   because of the behavior outlined a few points above.
    // • When a user adds a new member to the group, they generate a ratchet for that new member and
    //   send that bundled in a `ClosedGroupUpdateMessage` to the group. They send a
    //   `ClosedGroupUpdateMessage` with the newly generated ratchet but also the existing ratchets of
    //   every other member of the group to the user that joined.

    // region Initialization
    companion object {

        public lateinit var shared: SharedSenderKeysImplementation

        public fun configureIfNeeded(database: SharedSenderKeysDatabaseProtocol, delegate: SharedSenderKeysImplementationDelegate) {
            if (::shared.isInitialized) { return; }
            shared = SharedSenderKeysImplementation(database, delegate)
        }
    }
    // endregion

    // region Error
    public class LoadingFailed(val groupPublicKey: String, val senderPublicKey: String)
        : Exception("Couldn't get ratchet for closed group with public key: $groupPublicKey, sender public key: $senderPublicKey.")
    public class MessageKeyMissing(val targetKeyIndex: Int, val groupPublicKey: String, val senderPublicKey: String)
        : Exception("Couldn't find message key for old key index: $targetKeyIndex, public key: $groupPublicKey, sender public key: $senderPublicKey.")
    public class GenericRatchetingException : Exception("An error occurred.")
    // endregion

    // region Private API
    private fun hmac(key: ByteArray, input: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(input)
    }

    private fun step(ratchet: ClosedGroupRatchet): ClosedGroupRatchet {
        val nextMessageKey = hmac(Hex.fromStringCondensed(ratchet.chainKey), ByteArray(1) { 1.toByte() })
        val nextChainKey = hmac(Hex.fromStringCondensed(ratchet.chainKey), ByteArray(1) { 2.toByte() })
        val nextKeyIndex = ratchet.keyIndex + 1
        val messageKeys = ratchet.messageKeys + listOf( nextMessageKey.toHexString() )
        return ClosedGroupRatchet(nextChainKey.toHexString(), nextKeyIndex, messageKeys)
    }

    /**
     * Sync. Don't call from the main thread.
     */
    private fun stepRatchetOnce(groupPublicKey: String, senderPublicKey: String): ClosedGroupRatchet {
        val ratchet = database.getClosedGroupRatchet(groupPublicKey, senderPublicKey, ClosedGroupRatchetCollectionType.Current)
        if (ratchet == null) {
            val exception = LoadingFailed(groupPublicKey, senderPublicKey)
            Log.d("Loki", exception.message ?: "An error occurred.")
            throw exception
        }
        try {
            val result = step(ratchet)
            database.setClosedGroupRatchet(groupPublicKey, senderPublicKey, result, ClosedGroupRatchetCollectionType.Current)
            return result
        } catch (exception: Exception) {
            Log.d("Loki", "Couldn't step ratchet due to error: $exception.")
            throw exception
        }
    }

    private fun stepRatchet(groupPublicKey: String, senderPublicKey: String, targetKeyIndex: Int, isRetry: Boolean = false): ClosedGroupRatchet {
        val collection = if (isRetry) ClosedGroupRatchetCollectionType.Old else ClosedGroupRatchetCollectionType.Current
        val ratchet = database.getClosedGroupRatchet(groupPublicKey, senderPublicKey, collection)
        if (ratchet == null) {
            val exception = LoadingFailed(groupPublicKey, senderPublicKey)
            Log.d("Loki", exception.message ?: "An error occurred.")
            throw exception
        }
        if (targetKeyIndex < ratchet.keyIndex) {
            // There's no need to advance the ratchet if this is invoked for an old key index
            if (ratchet.messageKeys.count() <= targetKeyIndex) {
                val exception = MessageKeyMissing(targetKeyIndex, groupPublicKey, senderPublicKey)
                Log.d("Loki", exception.message ?: "An error occurred.")
                throw exception
            }
            return ratchet
        } else {
            var currentKeyIndex = ratchet.keyIndex
            var result: ClosedGroupRatchet = ratchet // Explicitly typed because otherwise the compiler has trouble inferring that this can't be null
            while (currentKeyIndex < targetKeyIndex) {
                try {
                    result = step(result)
                    currentKeyIndex = result.keyIndex
                } catch (exception: Exception) {
                    Log.d("Loki", "Couldn't step ratchet due to error: $exception.")
                    throw exception
                }
            }
            val collection = if (isRetry) ClosedGroupRatchetCollectionType.Old else ClosedGroupRatchetCollectionType.Current
            database.setClosedGroupRatchet(groupPublicKey, senderPublicKey, result, collection)
            return result
        }
    }
    // endregion

    // region Public API
    public fun generateRatchet(groupPublicKey: String, senderPublicKey: String): ClosedGroupRatchet {
        val rootChainKey = Util.getSecretBytes(32).toHexString()
        val ratchet = ClosedGroupRatchet(rootChainKey, 0, listOf())
        database.setClosedGroupRatchet(groupPublicKey, senderPublicKey, ratchet, ClosedGroupRatchetCollectionType.Current)
        return ratchet
    }

    public fun encrypt(plaintext: ByteArray, groupPublicKey: String, senderPublicKey: String): Pair<ByteArray, Int> {
        val ratchet: ClosedGroupRatchet
        try {
            ratchet = stepRatchetOnce(groupPublicKey, senderPublicKey)
        } catch (exception: Exception) {
            if (exception is LoadingFailed) {
                delegate.requestSenderKey(groupPublicKey, senderPublicKey)
            }
            throw exception
        }
        val iv = Util.getSecretBytes(ivSize)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val messageKey = ratchet.messageKeys.last()
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(Hex.fromStringCondensed(messageKey), "AES"), GCMParameterSpec(gcmTagSize, iv))
        return Pair(ByteUtil.combine(iv, cipher.doFinal(plaintext)), ratchet.keyIndex)
    }

    public fun decrypt(ivAndCiphertext: ByteArray, groupPublicKey: String, senderPublicKey: String, keyIndex: Int, isRetry: Boolean = false): ByteArray {
        val ratchet: ClosedGroupRatchet
        try {
            ratchet = stepRatchet(groupPublicKey, senderPublicKey, keyIndex, isRetry)
        } catch (exception: Exception) {
            if (!isRetry) {
                return decrypt(ivAndCiphertext, groupPublicKey, senderPublicKey, keyIndex, true)
            } else {
                if (exception is LoadingFailed) {
                    delegate.requestSenderKey(groupPublicKey, senderPublicKey)
                }
                throw exception
            }
        }
        val iv = ivAndCiphertext.sliceArray(0 until ivSize)
        val ciphertext = ivAndCiphertext.sliceArray(ivSize until ivAndCiphertext.count())
        val messageKeys = ratchet.messageKeys
        val lastNMessageKeys: List<String>
        if (messageKeys.count() > 16) { // Pick an arbitrary number of message keys to try; this helps resolve issues caused by messages arriving out of order
            lastNMessageKeys = messageKeys.subList(messageKeys.lastIndex - 16, messageKeys.lastIndex)
        } else {
            lastNMessageKeys = messageKeys
        }
        if (lastNMessageKeys.isEmpty()) {
            throw MessageKeyMissing(keyIndex, groupPublicKey, senderPublicKey)
        }
        var exception: Exception? = null
        for (messageKey in lastNMessageKeys.reversed()) { // Reversed because most likely the last one is the one we need
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(Hex.fromStringCondensed(messageKey), "AES"), GCMParameterSpec(EncryptionUtilities.gcmTagSize, iv))
            try {
                return cipher.doFinal(ciphertext)
            } catch (e: Exception) {
                exception = e
            }
        }
        if (!isRetry) {
            return decrypt(ivAndCiphertext, groupPublicKey, senderPublicKey, keyIndex, true)
        } else {
            delegate.requestSenderKey(groupPublicKey, senderPublicKey)
            throw exception ?: GenericRatchetingException()
        }
    }

    public fun isClosedGroup(publicKey: String): Boolean {
        return database.getAllClosedGroupPublicKeys().contains(publicKey)
    }

    public fun getKeyPair(groupPublicKey: String): ECKeyPair? {
        val privateKey = database.getClosedGroupPrivateKey(groupPublicKey) ?: return null
        return ECKeyPair(DjbECPublicKey(Hex.fromStringCondensed(groupPublicKey.removing05PrefixIfNeeded())),
            DjbECPrivateKey(Hex.fromStringCondensed(privateKey)))
    }
    // endregion
}
