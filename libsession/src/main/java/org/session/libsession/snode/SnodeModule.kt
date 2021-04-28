package org.session.libsession.snode

import org.session.libsignal.service.loki.database.LokiAPIDatabaseProtocol
import org.session.libsignal.service.loki.utilities.Broadcaster

class SnodeModule(val storage: LokiAPIDatabaseProtocol, val broadcaster: Broadcaster) {

    companion object {
        lateinit var shared: SnodeModule

        fun configure(storage: LokiAPIDatabaseProtocol, broadcaster: Broadcaster) {
            if (Companion::shared.isInitialized) { return }
            shared = SnodeModule(storage, broadcaster)
        }
    }
}