package org.whispersystems.signalservice.loki.protocol.sessionmanagement

interface SessionManagementProtocolDelegate {

    fun sendSessionRequestIfNeeded(publicKey: String)
}
