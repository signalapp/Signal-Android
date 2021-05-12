package org.session.libsession.messaging

import android.content.Context
import org.session.libsession.database.MessageDataProvider
import org.session.libsignal.service.loki.api.crypto.SessionProtocol

class MessagingModuleConfiguration(
    val context: Context,
    val storage: StorageProtocol,
    val messageDataProvider: MessageDataProvider,
    val sessionProtocol: SessionProtocol
) {

    companion object {
        lateinit var shared: MessagingModuleConfiguration

        fun configure(context: Context,
            storage: StorageProtocol,
            messageDataProvider: MessageDataProvider,
            sessionProtocol: SessionProtocol
        ) {
            if (Companion::shared.isInitialized) { return }
            shared = MessagingModuleConfiguration(context, storage, messageDataProvider, sessionProtocol)
        }
    }
}