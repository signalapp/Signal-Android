package org.session.libsession.database

import org.session.libsession.database.dto.AttachmentState
import org.session.libsession.database.dto.DatabaseAttachmentDTO
import org.session.libsession.messaging.messages.visible.Attachment

interface MessageDataProvider {

    fun getAttachment(attachmentId: Long): DatabaseAttachmentDTO?

    fun setAttachmentState(attachmentState: AttachmentState, attachment: DatabaseAttachmentDTO, messageID: Long)

    fun isOutgoingMessage(timestamp: Long): Boolean

    @Throws(Exception::class)
    fun uploadAttachment(attachmentId: Long)

}