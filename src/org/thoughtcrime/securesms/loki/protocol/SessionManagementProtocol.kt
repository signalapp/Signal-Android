package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import android.util.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.crypto.SecurityEvent
import org.thoughtcrime.securesms.crypto.storage.TextSecureSessionStore
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.jobs.CleanPreKeysJob
import org.thoughtcrime.securesms.loki.utilities.recipient
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.sms.OutgoingEndSessionMessage
import org.thoughtcrime.securesms.sms.OutgoingTextMessage
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.loki.SessionResetStatus
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.loki.protocol.shelved.multidevice.MultiDeviceProtocol
import java.util.*

object SessionManagementProtocol {

    @JvmStatic
    fun startSessionReset(context: Context, recipient: Recipient, threadID: Long) {
        if (recipient.isGroupRecipient) { return }
        val lokiThreadDB = DatabaseFactory.getLokiThreadDatabase(context)
        val smsDB = DatabaseFactory.getSmsDatabase(context)
        val devices = lokiThreadDB.getSessionRestoreDevices(threadID)
        for (device in devices) {
            val endSessionMessage = OutgoingEndSessionMessage(OutgoingTextMessage(recipient, "TERMINATE", 0, -1))
            MessageSender.send(context, endSessionMessage, threadID, false, null)
        }
        val infoMessage = OutgoingTextMessage(recipient, "", 0, 0)
        val infoMessageID = smsDB.insertMessageOutbox(threadID, infoMessage, false, System.currentTimeMillis(), null)
        if (infoMessageID > -1) {
            smsDB.markAsSentLokiSessionRestorationRequest(infoMessageID)
        }
        lokiThreadDB.removeAllSessionRestoreDevices(threadID)
    }

    @JvmStatic
    fun refreshSignedPreKey(context: Context) {
        if (TextSecurePreferences.isSignedPreKeyRegistered(context)) {
            Log.d("Loki", "Skipping signed pre key refresh; using existing signed pre key.")
        } else {
            Log.d("Loki", "Signed pre key refreshed successfully.")
            val identityKeyPair = IdentityKeyUtil.getIdentityKeyPair(context)
            PreKeyUtil.generateSignedPreKey(context, identityKeyPair, true)
            TextSecurePreferences.setSignedPreKeyRegistered(context, true)
            ApplicationContext.getInstance(context).jobManager.add(CleanPreKeysJob())
        }
    }

    @JvmStatic
    fun shouldProcessSessionRequest(context: Context, publicKey: String, timestamp: Long): Boolean {
        val apiDB = DatabaseFactory.getLokiAPIDatabase(context)
        val sentTimestamp = apiDB.getSessionRequestSentTimestamp(publicKey) ?: 0
        val processedTimestamp = apiDB.getSessionRequestProcessedTimestamp(publicKey) ?: 0
        return timestamp > sentTimestamp && timestamp > processedTimestamp
    }

    @JvmStatic
    fun handlePreKeyBundleMessageIfNeeded(context: Context, content: SignalServiceContent) {
        val preKeyBundleMessage = content.preKeyBundleMessage.orNull() ?: return
        val publicKey = content.sender
        if (recipient(context, publicKey).isGroupRecipient) { return } // Should never occur
        Log.d("Loki", "Received a pre key bundle from: $publicKey.")
        if (!shouldProcessSessionRequest(context, publicKey, content.timestamp)) {
            Log.d("Loki", "Ignoring session request from: $publicKey.")
            return
        }
        val registrationID = TextSecurePreferences.getLocalRegistrationId(context)
        val lokiPreKeyBundleDatabase = DatabaseFactory.getLokiPreKeyBundleDatabase(context)
        val preKeyBundle = preKeyBundleMessage.getPreKeyBundle(registrationID)
        lokiPreKeyBundleDatabase.setPreKeyBundle(publicKey, preKeyBundle)
        DatabaseFactory.getLokiAPIDatabase(context).setSessionRequestProcessedTimestamp(publicKey, Date().time)
        val job = PushNullMessageSendJob(publicKey)
        ApplicationContext.getInstance(context).jobManager.add(job)
    }

    @JvmStatic
    fun handleEndSessionMessageIfNeeded(context: Context, content: SignalServiceContent) {
        if (!content.dataMessage.isPresent || !content.dataMessage.get().isEndSession) { return }
        val sessionStore = TextSecureSessionStore(context)
        val lokiThreadDB = DatabaseFactory.getLokiThreadDatabase(context)
        Log.d("Loki", "Received a session reset request from: ${content.sender}; archiving the session.")
        sessionStore.archiveAllSessions(content.sender)
        lokiThreadDB.setSessionResetStatus(content.sender, SessionResetStatus.REQUEST_RECEIVED)
        Log.d("Loki", "Sending an ephemeral message back to: ${content.sender}.")
        val job = PushNullMessageSendJob(content.sender)
        ApplicationContext.getInstance(context).jobManager.add(job)
        SecurityEvent.broadcastSecurityUpdateEvent(context)
    }

    @JvmStatic
    fun triggerSessionRestorationUI(context: Context, publicKey: String) {
        val masterDevicePublicKey = MultiDeviceProtocol.shared.getMasterDevice(publicKey) ?: publicKey
        val masterDeviceAsRecipient = recipient(context, masterDevicePublicKey)
        if (masterDeviceAsRecipient.isGroupRecipient) { return }
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(masterDeviceAsRecipient)
        DatabaseFactory.getLokiThreadDatabase(context).addSessionRestoreDevice(threadID, publicKey)
    }
}