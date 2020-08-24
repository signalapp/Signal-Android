package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.OutgoingTextMessage
import org.whispersystems.libsignal.loki.SessionResetProtocol
import org.whispersystems.libsignal.loki.SessionResetStatus
import org.whispersystems.libsignal.protocol.PreKeySignalMessage

class SessionResetImplementation(private val context: Context) : SessionResetProtocol {

    override fun getSessionResetStatus(publicKey: String): SessionResetStatus {
        return DatabaseFactory.getLokiThreadDatabase(context).getSessionResetStatus(publicKey)
    }

    override fun setSessionResetStatus(publicKey: String, sessionResetStatus: SessionResetStatus) {
        return DatabaseFactory.getLokiThreadDatabase(context).setSessionResetStatus(publicKey, sessionResetStatus)
    }

    override fun onNewSessionAdopted(publicKey: String, oldSessionResetStatus: SessionResetStatus) {
        if (oldSessionResetStatus == SessionResetStatus.IN_PROGRESS) {
            val job = NullMessageSendJob(publicKey)
            ApplicationContext.getInstance(context).jobManager.add(job)
        }
        val smsDB = DatabaseFactory.getSmsDatabase(context)
        val recipient = Recipient.from(context, Address.fromSerialized(publicKey), false)
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
        val infoMessage = OutgoingTextMessage(recipient, "", 0, 0)
        val infoMessageID = smsDB.insertMessageOutbox(threadID, infoMessage, false, System.currentTimeMillis(), null)
        if (infoMessageID > -1) {
            smsDB.markAsLokiSessionRestorationDone(infoMessageID)
        }
    }

    override fun validatePreKeySignalMessage(publicKey: String, message: PreKeySignalMessage) {
        val preKeyRecord = DatabaseFactory.getLokiPreKeyRecordDatabase(context).getPreKeyRecord(publicKey) ?: return
        // TODO: Checking that the pre key record isn't null is causing issues when it shouldn't
        check(preKeyRecord.id == (message.preKeyId ?: -1)) { "Received a background message from an unknown source." }
    }
}