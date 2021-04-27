package org.session.libsession.messaging.sending_receiving

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsignal.libsignal.ecc.ECKeyPair

object MessageReceiverDecryption {

    internal fun decryptWithSessionProtocol(ciphertext: ByteArray, x25519KeyPair: ECKeyPair): Pair<ByteArray, String> {
        return MessagingModuleConfiguration.shared.sessionProtocol.decrypt(ciphertext, x25519KeyPair)
    }
}