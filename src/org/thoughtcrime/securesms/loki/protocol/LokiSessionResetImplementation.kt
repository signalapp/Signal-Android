package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.whispersystems.libsignal.loki.LokiSessionResetProtocol
import org.whispersystems.libsignal.loki.LokiSessionResetStatus
import org.whispersystems.libsignal.protocol.PreKeySignalMessage

class LokiSessionResetImplementation(private val context: Context) : LokiSessionResetProtocol {

    override fun getSessionResetStatus(hexEncodedPublicKey: String): LokiSessionResetStatus {
        return DatabaseFactory.getLokiThreadDatabase(context).getSessionResetStatus(hexEncodedPublicKey)
    }

    override fun setSessionResetStatus(hexEncodedPublicKey: String, sessionResetStatus: LokiSessionResetStatus) {
        return DatabaseFactory.getLokiThreadDatabase(context).setSessionResetStatus(hexEncodedPublicKey, sessionResetStatus)
    }

    override fun onNewSessionAdopted(hexEncodedPublicKey: String, oldSessionResetStatus: LokiSessionResetStatus) {
        if (oldSessionResetStatus == LokiSessionResetStatus.IN_PROGRESS) {
            SessionMetaProtocol.sendEphemeralMessage(context, hexEncodedPublicKey)
        }
        // TODO: Show session reset succeed message
    }

    override fun validatePreKeySignalMessage(sender: String, message: PreKeySignalMessage) {
        val preKeyRecord = DatabaseFactory.getLokiPreKeyRecordDatabase(context).getPreKeyRecord(sender)
        check(preKeyRecord != null) { "Received a background message from a user without an associated pre key record." }
        check(preKeyRecord.id == (message.preKeyId ?: -1)) { "Received a background message from an unknown source." }
    }
}