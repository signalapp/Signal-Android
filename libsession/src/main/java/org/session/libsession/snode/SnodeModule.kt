package org.session.libsession.snode

import org.session.libsignal.database.LokiAPIDatabaseProtocol
import org.session.libsignal.utilities.Broadcaster

class SnodeModule(val storage: LokiAPIDatabaseProtocol, val broadcaster: Broadcaster) {

    companion object {
        lateinit var shared: SnodeModule

        val isInitialized: Boolean get() = Companion::shared.isInitialized

        fun configure(storage: LokiAPIDatabaseProtocol, broadcaster: Broadcaster) {
            if (isInitialized) { return }
            shared = SnodeModule(storage, broadcaster)
        }
    }
}