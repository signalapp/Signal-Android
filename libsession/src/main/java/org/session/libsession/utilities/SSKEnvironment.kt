package org.session.libsession.utilities

import android.content.Context
import org.session.libsession.messaging.sending_receiving.notifications.MessageNotifier
import org.session.libsession.messaging.threads.Address
import org.session.libsession.messaging.threads.recipients.Recipient
import org.session.libsignal.service.internal.push.SignalServiceProtos

class SSKEnvironment(
        val typingIndicators: TypingIndicatorsProtocol,
        val readReceiptManager: ReadReceiptManagerProtocol,
        val profileManager: ProfileManagerProtocol,
        val notificationManager: MessageNotifier,
        val messageExpirationManager: MessageExpirationManagerProtocol
) {
    interface TypingIndicatorsProtocol {
        fun didReceiveTypingStartedMessage(context: Context, threadId: Long, author: Address, device: Int)
        fun didReceiveTypingStoppedMessage(context: Context, threadId: Long, author: Address, device: Int, isReplacedByIncomingMessage: Boolean)
        fun didReceiveIncomingMessage(context: Context, threadId: Long, author: Address, device: Int)
    }

    interface ReadReceiptManagerProtocol {
        fun processReadReceipts(context: Context, fromRecipientId: String, sentTimestamps: List<Long>, readTimestamp: Long)
    }

    interface ProfileManagerProtocol {
        fun setDisplayName(context: Context, recipient: Recipient, displayName: String)
        fun setProfilePictureURL(context: Context, recipient: Recipient, profilePictureURL: String)
        fun setProfileKey(context: Context, recipient: Recipient, profileKey: ByteArray)
        fun setUnidentifiedAccessMode(context: Context, recipient: Recipient, unidentifiedAccessMode: Recipient.UnidentifiedAccessMode)
        fun updateOpenGroupProfilePicturesIfNeeded(context: Context)
    }

    interface MessageExpirationManagerProtocol {
        fun setExpirationTimer(messageID: Long?, duration: Int, senderPublicKey: String, content: SignalServiceProtos.Content)
        fun disableExpirationTimer(messageID: Long?, senderPublicKey: String, content: SignalServiceProtos.Content)
        fun startAnyExpiration(messageID: Long)
    }

    companion object {
        lateinit var shared: SSKEnvironment

        fun configure(typingIndicators: TypingIndicatorsProtocol,
                      readReceiptManager: ReadReceiptManagerProtocol,
                      profileManager: ProfileManagerProtocol,
                      notificationManager: MessageNotifier,
                      messageExpirationManager: MessageExpirationManagerProtocol) {
            if (Companion::shared.isInitialized) { return }
            shared = SSKEnvironment(typingIndicators, readReceiptManager, profileManager, notificationManager, messageExpirationManager)
        }
    }
}