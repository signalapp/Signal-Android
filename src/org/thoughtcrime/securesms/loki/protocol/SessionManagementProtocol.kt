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
import org.thoughtcrime.securesms.sms.OutgoingTextMessage
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.loki.LokiSessionResetStatus
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.loki.protocol.multidevice.MultiDeviceProtocol
import org.whispersystems.signalservice.loki.protocol.todo.LokiThreadFriendRequestStatus

object SessionManagementProtocol {

    @JvmStatic
    fun startSessionReset(context: Context, recipient: Recipient, threadID: Long) {
        if (recipient.isGroupRecipient) { return }
        val lokiThreadDB = DatabaseFactory.getLokiThreadDatabase(context)
        val smsDB = DatabaseFactory.getSmsDatabase(context)
        val devices = lokiThreadDB.getSessionRestoreDevices(threadID)
        for (device in devices) {
            val sessionRestorationRequest = EphemeralMessage.createSessionRestorationRequest(recipient.address.serialize())
            ApplicationContext.getInstance(context).jobManager.add(PushEphemeralMessageSendJob(sessionRestorationRequest))
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
    fun handlePreKeyBundleMessageIfNeeded(context: Context, content: SignalServiceContent) {
        val recipient = recipient(context, content.sender)
        if (recipient.isGroupRecipient) { return }
        val preKeyBundleMessage = content.lokiServiceMessage.orNull()?.preKeyBundleMessage ?: return
        val registrationID = TextSecurePreferences.getLocalRegistrationId(context) // TODO: It seems wrong to use the local registration ID for this?
        val lokiPreKeyBundleDatabase = DatabaseFactory.getLokiPreKeyBundleDatabase(context)
        Log.d("Loki", "Received a pre key bundle from: " + content.sender.toString() + ".")
        val preKeyBundle = preKeyBundleMessage.getPreKeyBundle(registrationID)
        lokiPreKeyBundleDatabase.setPreKeyBundle(content.sender, preKeyBundle)
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
        val lokiThreadDB = DatabaseFactory.getLokiThreadDatabase(context)
        val threadFRStatus = lokiThreadDB.getFriendRequestStatus(threadID)
        // If we received a friend request (i.e. also a new pre key bundle), but we were already friends with the other user, reset the session.
        if (content.isFriendRequest && threadFRStatus == LokiThreadFriendRequestStatus.FRIENDS) {
            val sessionStore = TextSecureSessionStore(context)
            sessionStore.archiveAllSessions(content.sender)
            val ephemeralMessage = EphemeralMessage.create(content.sender)
            ApplicationContext.getInstance(context).jobManager.add(PushEphemeralMessageSendJob(ephemeralMessage))
        }
    }

    @JvmStatic
    fun handleSessionRequestIfNeeded(context: Context, content: SignalServiceContent) {
        if (!content.dataMessage.isPresent || !content.dataMessage.get().isSessionRequest) { return }
        val sentSessionRequestTimestamp = DatabaseFactory.getLokiAPIDatabase(context).getSessionRequestTimestamp(content.sender)
        if (sentSessionRequestTimestamp != null && content.timestamp < sentSessionRequestTimestamp) {
            // We sent a session request after this one was sent
            return
        }
        // Auto-accept all session requests
        val ephemeralMessage = EphemeralMessage.create(content.sender)
        ApplicationContext.getInstance(context).jobManager.add(PushEphemeralMessageSendJob(ephemeralMessage))
    }

    @JvmStatic
    fun handleEndSessionMessageIfNeeded(context: Context, content: SignalServiceContent) {
        if (!content.dataMessage.isPresent || !content.dataMessage.get().isEndSession) { return }
        // TODO: Notify the user
        val sessionStore = TextSecureSessionStore(context)
        val lokiThreadDB = DatabaseFactory.getLokiThreadDatabase(context)
        Log.d("Loki", "Received a session reset request from: ${content.sender}; archiving the session.")
        sessionStore.archiveAllSessions(content.sender)
        lokiThreadDB.setSessionResetStatus(content.sender, LokiSessionResetStatus.REQUEST_RECEIVED)
        Log.d("Loki", "Sending an ephemeral message back to: ${content.sender}.")
        val ephemeralMessage = EphemeralMessage.create(content.sender)
        ApplicationContext.getInstance(context).jobManager.add(PushEphemeralMessageSendJob(ephemeralMessage))
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