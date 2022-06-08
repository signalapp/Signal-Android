package org.session.libsession.messaging

import android.content.Context
import com.goterl.lazysodium.utils.KeyPair
import org.session.libsession.database.MessageDataProvider
import org.session.libsession.database.StorageProtocol

class MessagingModuleConfiguration(
    val context: Context,
    val storage: StorageProtocol,
    val messageDataProvider: MessageDataProvider,
    val getUserED25519KeyPair: ()-> KeyPair?
) {

    companion object {
        @JvmStatic
        val shared: MessagingModuleConfiguration
        get() = context.getSystemService(MESSAGING_MODULE_SERVICE) as MessagingModuleConfiguration

        const val MESSAGING_MODULE_SERVICE: String = "MessagingModuleConfiguration_MESSAGING_MODULE_SERVICE"

        private lateinit var context: Context

        @JvmStatic
        fun configure(context: Context) {
            this.context = context
        }
    }
}