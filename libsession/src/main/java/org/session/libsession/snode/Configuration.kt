package org.session.libsession.snode

import org.session.libsignal.service.loki.utilities.Broadcaster

class Configuration(val storage: SnodeStorageProtocol, val broadcaster: Broadcaster) {
    companion object {
        lateinit var shared: Configuration

        fun configure(storage: SnodeStorageProtocol, broadcaster: Broadcaster) {
            if (Companion::shared.isInitialized) { return }
            shared = Configuration(storage, broadcaster)
        }
    }
}