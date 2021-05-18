package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import org.thoughtcrime.securesms.ApplicationContext
import org.session.libsession.utilities.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.messages.SignalServiceContent
import org.session.libsignal.messages.SignalServiceDataMessage
import java.security.MessageDigest

object SessionMetaProtocol {

    private val timestamps = mutableSetOf<Long>()

    fun getTimestamps(): Set<Long> {
        return timestamps
    }

    fun addTimestamp(timestamp: Long) {
        timestamps.add(timestamp)
    }

    @JvmStatic
    fun clearReceivedMessages() {
        timestamps.clear()
    }

    fun removeTimestamps(timestamps: Set<Long>) {
        this.timestamps.removeAll(timestamps)
    }

    @JvmStatic
    fun shouldIgnoreMessage(timestamp: Long): Boolean {
        val shouldIgnoreMessage = timestamps.contains(timestamp)
        timestamps.add(timestamp)
        return shouldIgnoreMessage
    }

    @JvmStatic
    fun shouldIgnoreDecryptionException(context: Context, timestamp: Long): Boolean {
        val restorationTimestamp = TextSecurePreferences.getRestorationTime(context)
        return timestamp <= restorationTimestamp
    }

    @JvmStatic
    fun handleProfileUpdateIfNeeded(context: Context, content: SignalServiceContent) {
        val displayName = content.senderDisplayName.orNull() ?: return
        if (displayName.isBlank()) { return }
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        val sender = content.sender.toLowerCase()
        if (userPublicKey == sender) {
            // Update the user's local name if the message came from their master device
            TextSecurePreferences.setProfileName(context, displayName)
        }
        DatabaseFactory.getLokiUserDatabase(context).setDisplayName(sender, displayName)
    }

    @JvmStatic
    fun handleProfileKeyUpdate(context: Context, content: SignalServiceContent) {
        val message = content.dataMessage.get()
        if (!message.profileKey.isPresent) { return }
        val database = DatabaseFactory.getRecipientDatabase(context)
        val recipient = Recipient.from(context, Address.fromSerialized(content.sender), false)
        if (recipient.profileKey == null || !MessageDigest.isEqual(recipient.profileKey, message.profileKey.get())) {
            database.setProfileKey(recipient, message.profileKey.get())
            database.setUnidentifiedAccessMode(recipient, Recipient.UnidentifiedAccessMode.UNKNOWN)
            val url = content.senderProfilePictureURL.or("")
            ApplicationContext.getInstance(context).jobManager.add(RetrieveProfileAvatarJob(recipient, url))
            val userPublicKey = TextSecurePreferences.getLocalNumber(context)
            if (userPublicKey == content.sender) {
                ApplicationContext.getInstance(context).updateOpenGroupProfilePicturesIfNeeded()
            }
        }
    }

    @JvmStatic
    fun canUserReplyToNotification(recipient: Recipient): Boolean {
        // TODO return !recipient.address.isRSSFeed
        return true
    }

    @JvmStatic
    fun shouldSendDeliveryReceipt(message: SignalServiceDataMessage, address: Address): Boolean {
        if (address.isGroup) { return false }
        val hasBody = message.body.isPresent && message.body.get().isNotEmpty()
        val hasAttachment = message.attachments.isPresent && message.attachments.get().isNotEmpty()
        val hasLinkPreview = message.previews.isPresent && message.previews.get().isNotEmpty()
        return hasBody || hasAttachment || hasLinkPreview
    }

    @JvmStatic
    fun shouldSendReadReceipt(address: Address): Boolean {
        return !address.isGroup
    }

    @JvmStatic
    fun shouldSendTypingIndicator(address: Address): Boolean {
        return !address.isGroup
    }
}