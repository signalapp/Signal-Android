package org.session.libsession.database

import org.session.libsession.messaging.sending_receiving.attachments.AttachmentState
import org.session.libsession.messaging.sending_receiving.attachments.SessionServiceAttachmentPointer
import org.session.libsession.messaging.sending_receiving.attachments.SessionServiceAttachmentStream

interface MessageDataProvider {

    //fun getAttachment(attachmentId: Long): SignalServiceAttachmentStream?

    fun getAttachmentStream(attachmentId: Long): SessionServiceAttachmentStream?

    fun getAttachmentPointer(attachmentId: Long): SessionServiceAttachmentPointer?

    fun setAttachmentState(attachmentState: AttachmentState, attachmentId: Long, messageID: Long)

    fun isOutgoingMessage(timestamp: Long): Boolean

    @Throws(Exception::class)
    fun uploadAttachment(attachmentId: Long)

}