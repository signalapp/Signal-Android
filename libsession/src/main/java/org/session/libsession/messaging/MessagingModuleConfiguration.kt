package org.session.libsession.messaging

import android.content.Context
import org.session.libsession.database.MessageDataProvider
import org.session.libsession.database.StorageProtocol

class MessagingModuleConfiguration(
        val context: Context,
        val storage: StorageProtocol,
        val messageDataProvider: MessageDataProvider
) {

    companion object {
        lateinit var shared: MessagingModuleConfiguration

        fun configure(context: Context,
                      storage: StorageProtocol,
                      messageDataProvider: MessageDataProvider
        ) {
            if (Companion::shared.isInitialized) { return }
            shared = MessagingModuleConfiguration(context, storage, messageDataProvider)
        }
    }
}