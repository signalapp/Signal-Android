package org.session.libsession.snode

import org.session.libsignal.service.loki.utilities.Broadcaster

class SnodeConfiguration(val storage: SnodeStorageProtocol, val broadcaster: Broadcaster) {
    companion object {
        lateinit var shared: SnodeConfiguration

        fun configure(storage: SnodeStorageProtocol, broadcaster: Broadcaster) {
            if (Companion::shared.isInitialized) { return }
            shared = SnodeConfiguration(storage, broadcaster)
        }
    }
}