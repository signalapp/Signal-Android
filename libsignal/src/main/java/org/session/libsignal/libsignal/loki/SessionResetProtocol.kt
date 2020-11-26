package org.session.libsignal.libsignal.loki

import org.session.libsignal.libsignal.protocol.PreKeySignalMessage

interface SessionResetProtocol {

    fun getSessionResetStatus(publicKey: String): SessionResetStatus
    fun setSessionResetStatus(publicKey: String, sessionResetStatus: SessionResetStatus)
    fun validatePreKeySignalMessage(publicKey: String, message: PreKeySignalMessage)
    fun onNewSessionAdopted(publicKey: String, oldSessionResetStatus: SessionResetStatus)
}
