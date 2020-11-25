package org.session.libsignal.service.loki.api.shelved.p2p

interface LokiP2PAPIDelegate {

    fun ping(contactHexEncodedPublicKey: String)
}
