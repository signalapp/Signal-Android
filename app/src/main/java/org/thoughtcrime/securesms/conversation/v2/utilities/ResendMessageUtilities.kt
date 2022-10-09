package org.thoughtcrime.securesms.conversation.v2.utilities

import android.content.Context
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.visible.LinkPreview
import org.session.libsession.messaging.messages.visible.OpenGroupInvitation
import org.session.libsession.messaging.messages.visible.Quote
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.sending_receiving.MessageSender
import org.session.libsession.messaging.utilities.UpdateMessageData
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord

object ResendMessageUtilities {

    fun resend(context: Context, messageRecord: MessageRecord, userBlindedKey: String?) {
        val recipient: Recipient = messageRecord.recipient
        val message = VisibleMessage()
        message.id = messageRecord.getId()
        if (messageRecord.isOpenGroupInvitation) {
            val openGroupInvitation = OpenGroupInvitation()
            UpdateMessageData.fromJSON(messageRecord.body)?.let { updateMessageData ->
                val kind = updateMessageData.kind
                if (kind is UpdateMessageData.Kind.OpenGroupInvitation) {
                    openGroupInvitation.name = kind.groupName
                    openGroupInvitation.url = kind.groupUrl
                }
            }
            message.openGroupInvitation = openGroupInvitation
        } else {
            message.text = messageRecord.body
        }
        message.sentTimestamp = messageRecord.timestamp
        if (recipient.isGroupRecipient) {
            message.groupPublicKey = recipient.address.toGroupString()
        } else {
            message.recipient = messageRecord.recipient.address.serialize()
        }
        message.threadID = messageRecord.threadId
        if (messageRecord.isMms) {
            val mmsMessageRecord = messageRecord as MmsMessageRecord
            if (mmsMessageRecord.linkPreviews.isNotEmpty()) {
                message.linkPreview = LinkPreview.from(mmsMessageRecord.linkPreviews[0])
            }
            if (mmsMessageRecord.quote != null) {
                message.quote = Quote.from(mmsMessageRecord.quote!!.quoteModel)
                if (userBlindedKey != null && messageRecord.quote!!.author.serialize() == TextSecurePreferences.getLocalNumber(context)) {
                    message.quote!!.publicKey = userBlindedKey
                }
            }
            message.addSignalAttachments(mmsMessageRecord.slideDeck.asAttachments())
        }
        val sentTimestamp = message.sentTimestamp
        val sender = MessagingModuleConfiguration.shared.storage.getUserPublicKey()
        if (sentTimestamp != null && sender != null) {
            MessagingModuleConfiguration.shared.storage.markAsSending(sentTimestamp, sender)
        }
        MessageSender.send(message, recipient.address)
    }
}