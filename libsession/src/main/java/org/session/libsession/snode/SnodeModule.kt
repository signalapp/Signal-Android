package org.session.libsession.snode

import org.session.libsignal.database.LokiAPIDatabaseProtocol
import org.session.libsignal.utilities.Broadcaster

class SnodeModule(val storage: LokiAPIDatabaseProtocol, val broadcaster: Broadcaster) {

    companion object {
        lateinit var shared: SnodeModule

        fun configure(storage: LokiAPIDatabaseProtocol, broadcaster: Broadcaster) {
            if (Companion::shared.isInitialized) { return }
            shared = SnodeModule(storage, broadcaster)
        }
    }
}