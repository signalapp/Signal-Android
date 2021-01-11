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
        val signalStorage: SignalProtocolStore,
        val sskDatabase: SharedSenderKeysDatabaseProtocol,
        val messageDataProvider: MessageDataProvider,
        val sessionResetImp: SessionResetProtocol,
        val sessionProtocol: SessionProtocol,
        val certificateValidator: CertificateValidator)
{
    companion object {
        lateinit var shared: MessagingConfiguration

        fun configure(context: Context,
                      storage: StorageProtocol,
                      signalStorage: SignalProtocolStore,
                      sskDatabase: SharedSenderKeysDatabaseProtocol,
                      messageDataProvider: MessageDataProvider,
                      sessionResetImp: SessionResetProtocol,
                      sessionProtocol: SessionProtocol,
                      certificateValidator: CertificateValidator
        ) {
            if (Companion::shared.isInitialized) { return }
            shared = MessagingConfiguration(context, storage, signalStorage, sskDatabase, messageDataProvider, sessionResetImp, sessionProtocol, certificateValidator)
        }
    }
}