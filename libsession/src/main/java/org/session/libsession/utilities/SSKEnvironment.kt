package org.session.libsession.utilities

import android.content.Context
import org.session.libsession.messaging.threads.Address
import org.session.libsession.messaging.threads.recipients.Recipient

class SSKEnvironment(
        val typingIndicators: TypingIndicatorsProtocol,
        val blockManager: BlockingManagerProtocol,
        val readReceiptManager: ReadReceiptManagerProtocol,
        val profileManager: ProfileManagerProtocol
) {
    interface TypingIndicatorsProtocol {
        fun didReceiveTypingStartedMessage(context: Context, threadId: Long, author: Address, device: Int)
        fun didReceiveTypingStoppedMessage(context: Context, threadId: Long, author: Address, device: Int, isReplacedByIncomingMessage: Boolean)
        fun didReceiveIncomingMessage(context: Context, threadId: Long, author: Address, device: Int)
    }

    interface BlockingManagerProtocol {
        fun isRecipientIdBlocked(publicKey: String): Boolean
    }

    interface ReadReceiptManagerProtocol {
        fun processReadReceipts(fromRecipientId: String, sentTimestamps: List<Long>, readTimestamp: Long)
    }

    interface ProfileManagerProtocol {
        fun setDisplayName(recipient: Recipient, displayName: String)
        fun setProfilePictureURL(recipient: Recipient, profilePictureURL: String)
        fun setProfileKey(recipient: Recipient, profileKey: ByteArray)
        fun setUnidentifiedAccessMode(recipient: Recipient, unidentifiedAccessMode: Recipient.UnidentifiedAccessMode)
        fun updateOpenGroupProfilePicturesIfNeeded()
    }

    companion object {
        lateinit var shared: SSKEnvironment

        fun configure(typingIndicators: TypingIndicatorsProtocol,
                      blockManager: BlockingManagerProtocol,
                      readReceiptManager: ReadReceiptManagerProtocol,
                      profileManager: ProfileManagerProtocol) {
            if (Companion::shared.isInitialized) { return }
            shared = SSKEnvironment(typingIndicators, blockManager, readReceiptManager, profileManager)
        }
    }
}