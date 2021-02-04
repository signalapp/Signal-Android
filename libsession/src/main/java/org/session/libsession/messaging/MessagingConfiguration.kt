package org.session.libsession.messaging

import android.content.Context
import org.session.libsession.database.MessageDataProvider
import org.session.libsignal.libsignal.loki.SessionResetProtocol
import org.session.libsignal.libsignal.state.*
import org.session.libsignal.metadata.certificate.CertificateValidator
import org.session.libsignal.service.loki.api.crypto.SessionProtocol
import org.session.libsignal.service.loki.protocol.closedgroups.SharedSenderKeysDatabaseProtocol

class MessagingConfiguration(
        val context: Context,
        val storage: StorageProtocol,
        val sskDatabase: SharedSenderKeysDatabaseProtocol,
        val messageDataProvider: MessageDataProvider,
        val sessionProtocol: SessionProtocol)
{
    companion object {
        lateinit var shared: MessagingConfiguration

        fun configure(context: Context,
                      storage: StorageProtocol,
                      sskDatabase: SharedSenderKeysDatabaseProtocol,
                      messageDataProvider: MessageDataProvider,
                      sessionProtocol: SessionProtocol
        ) {
            if (Companion::shared.isInitialized) { return }
            shared = MessagingConfiguration(context, storage, sskDatabase, messageDataProvider, sessionProtocol)
        }
    }
}