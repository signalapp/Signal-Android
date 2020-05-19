package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.loki.protocol.multidevice.MultiDeviceProtocol
import org.whispersystems.signalservice.loki.protocol.todo.LokiThreadFriendRequestStatus

object SessionMetaProtocol {

    private val timestamps = mutableSetOf<Long>()

    @JvmStatic
    fun shouldIgnoreMessage(content: SignalServiceContent): Boolean {
        val timestamp = content.timestamp
        return timestamps.contains(timestamp)
    }

    @JvmStatic
    fun handleProfileUpdateIfNeeded(context: Context, content: SignalServiceContent) {
        val rawDisplayName = content.senderDisplayName.orNull() ?: return
        if (rawDisplayName.isBlank()) { return }
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        val userMasterPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context)
        val sender = content.sender.toLowerCase()
        if (userMasterPublicKey == sender) {
            // Update the user's local name if the message came from their master device
            TextSecurePreferences.setProfileName(context, rawDisplayName)
        }
        // Don't overwrite if the message came from a linked device; the device name is
        // stored as a user name
        val allUserDevices = MultiDeviceProtocol.shared.getAllLinkedDevices(userPublicKey)
        if (!allUserDevices.contains(sender)) {
            val displayName = rawDisplayName + " (..." + sender.substring(sender.length - 8) + ")"
            DatabaseFactory.getLokiUserDatabase(context).setDisplayName(sender, displayName)
        }
    }

    @JvmStatic
    fun handleProfileKeyUpdateIfNeeded(context: Context, content: SignalServiceContent) {
        val userMasterPublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context)
        if (userMasterPublicKey != content.sender) { return }
        ApplicationContext.getInstance(context).updateOpenGroupProfilePicturesIfNeeded()
    }

    /**
     * Should be invoked for the recipient's master device.
     */
    @JvmStatic
    fun canUserReplyToNotification(recipient: Recipient, context: Context): Boolean {
        val isGroup = recipient.isGroupRecipient
        if (isGroup) { return !recipient.address.isRSSFeed }
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
        return DatabaseFactory.getLokiThreadDatabase(context).getFriendRequestStatus(threadID) == LokiThreadFriendRequestStatus.FRIENDS
    }

    /**
     * Should be invoked for the recipient's master device.
     */
    @JvmStatic
    fun shouldSendReadReceipt(address: Address, context: Context): Boolean {
        if (address.isGroup) { return false }
        val recipient = Recipient.from(context, address,false)
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
        return DatabaseFactory.getLokiThreadDatabase(context).getFriendRequestStatus(threadID) == LokiThreadFriendRequestStatus.FRIENDS
    }

    /**
     * Should be invoked for the recipient's master device.
     */
    @JvmStatic
    fun shouldSendTypingIndicator(recipient: Recipient, context: Context): Boolean {
        if (recipient.isGroupRecipient) { return false }
        val threadID = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient)
        return DatabaseFactory.getLokiThreadDatabase(context).getFriendRequestStatus(threadID) == LokiThreadFriendRequestStatus.FRIENDS
    }
}