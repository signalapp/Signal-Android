package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.whispersystems.libsignal.loki.SessionResetProtocol
import org.whispersystems.libsignal.loki.SessionResetStatus
import org.whispersystems.libsignal.protocol.PreKeySignalMessage

class LokiSessionResetImplementation(private val context: Context) : SessionResetProtocol {

    override fun getSessionResetStatus(publicKey: String): SessionResetStatus {
        return DatabaseFactory.getLokiThreadDatabase(context).getSessionResetStatus(publicKey)
    }

    override fun setSessionResetStatus(publicKey: String, sessionResetStatus: SessionResetStatus) {
        return DatabaseFactory.getLokiThreadDatabase(context).setSessionResetStatus(publicKey, sessionResetStatus)
    }

    override fun onNewSessionAdopted(publicKey: String, oldSessionResetStatus: SessionResetStatus) {
        if (oldSessionResetStatus == SessionResetStatus.IN_PROGRESS) {
            val ephemeralMessage = EphemeralMessage.create(publicKey)
            ApplicationContext.getInstance(context).jobManager.add(PushEphemeralMessageSendJob(ephemeralMessage))
        }
        // TODO: Show session reset succeed message
    }

    override fun validatePreKeySignalMessage(publicKey: String, message: PreKeySignalMessage) {
        val preKeyRecord = DatabaseFactory.getLokiPreKeyRecordDatabase(context).getPreKeyRecord(publicKey) ?: return
        // TODO: Checking that the pre key record isn't null is causing issues when it shouldn't
        check(preKeyRecord.id == (message.preKeyId ?: -1)) { "Received a background message from an unknown source." }
    }
}