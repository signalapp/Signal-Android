package org.session.libsession.snode

import org.session.libsignal.service.loki.database.LokiAPIDatabaseProtocol
import org.session.libsignal.service.loki.utilities.Broadcaster

class SnodeConfiguration(val storage: LokiAPIDatabaseProtocol, val broadcaster: Broadcaster) {
    companion object {
        lateinit var shared: SnodeConfiguration

        fun configure(storage: LokiAPIDatabaseProtocol, broadcaster: Broadcaster) {
            if (Companion::shared.isInitialized) { return }
            shared = SnodeConfiguration(storage, broadcaster)
        }
    }
}