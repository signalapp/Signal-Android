package org.session.libsession.messaging.sending_receiving

import com.google.protobuf.ByteString
import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.sending_receiving.MessageSender.Error
import org.session.libsession.messaging.utilities.UnidentifiedAccessUtil
import org.session.libsession.utilities.AESGCM

import org.session.libsignal.libsignal.SignalProtocolAddress
import org.session.libsignal.libsignal.loki.ClosedGroupCiphertextMessage
import org.session.libsignal.libsignal.util.Hex
import org.session.libsignal.libsignal.util.guava.Optional
import org.session.libsignal.service.api.crypto.SignalServiceCipher
import org.session.libsignal.service.api.push.SignalServiceAddress
import org.session.libsignal.service.internal.push.SignalServiceProtos
import org.session.libsignal.service.internal.util.Base64
import org.session.libsignal.service.loki.protocol.closedgroups.SharedSenderKeysImplementation
import org.session.libsignal.service.loki.utilities.removing05PrefixIfNeeded

object MessageSenderEncryption {

    /*internal fun encryptWithSignalProtocol(plaintext: ByteArray, message: Message, recipientPublicKey: String): ByteArray{
        val storage = MessagingConfiguration.shared.signalStorage
        val sskDatabase = MessagingConfiguration.shared.sskDatabase
        val sessionResetImp = MessagingConfiguration.shared.sessionResetImp
        val localAddress = SignalServiceAddress(recipientPublicKey)
        val certificateValidator = MessagingConfiguration.shared.certificateValidator
        val cipher = SignalServiceCipher(localAddress, storage, sskDatabase, sessionResetImp, certificateValidator)
        val signalProtocolAddress = SignalProtocolAddress(recipientPublicKey, 1)
        val unidentifiedAccessPair = UnidentifiedAccessUtil.getAccessFor(recipientPublicKey)
        val unidentifiedAccess = if (unidentifiedAccessPair != null) unidentifiedAccessPair.targetUnidentifiedAccess else Optional.absent()
        val encryptedMessage = cipher.encrypt(signalProtocolAddress, unidentifiedAccess, plaintext)
        return Base64.decode(encryptedMessage.content)
    }*/

    internal fun encryptWithSessionProtocol(plaintext: ByteArray, recipientPublicKey: String): ByteArray{
        return MessagingConfiguration.shared.sessionProtocol.encrypt(plaintext, recipientPublicKey)
    }

    /*internal fun encryptWithSharedSenderKeys(plaintext: ByteArray, groupPublicKey: String): ByteArray {
        // 1. ) Encrypt the data with the user's sender key
        val userPublicKey = MessagingConfiguration.shared.storage.getUserPublicKey() ?: throw Error.NoUserPublicKey
        val ciphertextAndKeyIndex = SharedSenderKeysImplementation.shared.encrypt(plaintext, groupPublicKey, userPublicKey)
        val ivAndCiphertext = ciphertextAndKeyIndex.first
        val keyIndex = ciphertextAndKeyIndex.second
        val encryptedMessage = ClosedGroupCiphertextMessage(ivAndCiphertext, Hex.fromStringCondensed(userPublicKey), keyIndex);
        // 2. ) Encrypt the result for the group's public key to hide the sender public key and key index
        val intermediate = AESGCM.encrypt(encryptedMessage.serialize(), groupPublicKey.removing05PrefixIfNeeded())
        // 3. ) Wrap the result
        return SignalServiceProtos.ClosedGroupCiphertextMessageWrapper.newBuilder()
                .setCiphertext(ByteString.copyFrom(intermediate.ciphertext))
                .setEphemeralPublicKey(ByteString.copyFrom(intermediate.ephemeralPublicKey))
                .build().toByteArray()
    }*/
}