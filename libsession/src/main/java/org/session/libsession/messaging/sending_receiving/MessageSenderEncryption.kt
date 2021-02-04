package org.session.libsession.messaging.sending_receiving

import org.session.libsession.messaging.MessagingConfiguration

object MessageSenderEncryption {

    internal fun encryptWithSessionProtocol(plaintext: ByteArray, recipientPublicKey: String): ByteArray{
        return MessagingConfiguration.shared.sessionProtocol.encrypt(plaintext, recipientPublicKey)
    }

}