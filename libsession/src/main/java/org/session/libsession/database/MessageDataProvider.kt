package org.session.libsession.database

import org.session.libsession.messaging.sending_receiving.attachments.Attachment
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentState
import org.session.libsession.messaging.sending_receiving.attachments.SessionServiceAttachmentPointer
import org.session.libsession.messaging.sending_receiving.attachments.SessionServiceAttachmentStream
import org.session.libsession.messaging.threads.Address
import org.session.libsignal.service.api.messages.SignalServiceAttachmentPointer
import java.io.InputStream

interface MessageDataProvider {

    fun getMessageID(serverID: Long): Long?
    fun deleteMessage(messageID: Long)

    fun getAttachmentStream(attachmentId: Long): SessionServiceAttachmentStream?

    fun getAttachmentPointer(attachmentId: Long): SessionServiceAttachmentPointer?

    fun setAttachmentState(attachmentState: AttachmentState, attachmentId: Long, messageID: Long)

    fun insertAttachment(messageId: Long, attachmentId: Long, stream : InputStream)

    fun isOutgoingMessage(timestamp: Long): Boolean

    @Throws(Exception::class)
    fun uploadAttachment(attachmentId: Long)

    // Quotes
    fun getMessageForQuote(timestamp: Long, author: Address): Long?
    fun getAttachmentsAndLinkPreviewFor(messageID: Long): List<Attachment>
    fun getMessageBodyFor(messageID: Long): String

}