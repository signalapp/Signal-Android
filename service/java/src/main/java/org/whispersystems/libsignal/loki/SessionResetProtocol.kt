package org.whispersystems.libsignal.loki

import org.whispersystems.libsignal.protocol.PreKeySignalMessage

interface SessionResetProtocol {

    fun getSessionResetStatus(publicKey: String): SessionResetStatus
    fun setSessionResetStatus(publicKey: String, sessionResetStatus: SessionResetStatus)
    fun validatePreKeySignalMessage(publicKey: String, message: PreKeySignalMessage)
    fun onNewSessionAdopted(publicKey: String, oldSessionResetStatus: SessionResetStatus)
}
