package org.session.libsignal.service.loki.protocol.sessionmanagement

interface SessionManagementProtocolDelegate {

    fun sendSessionRequestIfNeeded(publicKey: String)
}
