package org.session.libsession.database

import org.session.libsession.database.dto.DatabaseAttachmentDTO
import org.session.libsignal.service.api.messages.SignalServiceAttachmentPointer

interface MessageDataProvider {

    fun getAttachment(uniqueID: String): DatabaseAttachmentDTO?
    fun getAttachmentPointer(attachmentID: String): SignalServiceAttachmentPointer?

    fun getMessageID(serverID: Long): Long?
    fun deleteMessage(messageID: Long)

}