package org.session.libsession.database

import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsession.messaging.sending_receiving.attachments.*
import org.session.libsession.utilities.Address
import org.session.libsession.messaging.utilities.DotNetAPI
import org.session.libsignal.messages.SignalServiceAttachmentPointer
import org.session.libsignal.messages.SignalServiceAttachmentStream
import java.io.InputStream

interface MessageDataProvider {

    fun getMessageID(serverID: Long): Long?
    fun getMessageID(serverId: Long, threadId: Long): Pair<Long, Boolean>?
    fun deleteMessage(messageID: Long, isSms: Boolean)

    fun getDatabaseAttachment(attachmentId: Long): DatabaseAttachment?

    fun getAttachmentStream(attachmentId: Long): SessionServiceAttachmentStream?
    fun getAttachmentPointer(attachmentId: Long): SessionServiceAttachmentPointer?

    fun getSignalAttachmentStream(attachmentId: Long): SignalServiceAttachmentStream?
    fun getScaledSignalAttachmentStream(attachmentId: Long): SignalServiceAttachmentStream?
    fun getSignalAttachmentPointer(attachmentId: Long): SignalServiceAttachmentPointer?

    fun setAttachmentState(attachmentState: AttachmentState, attachmentId: Long, messageID: Long)

    fun insertAttachment(messageId: Long, attachmentId: AttachmentId, stream : InputStream)

    fun isOutgoingMessage(timestamp: Long): Boolean

    fun handleSuccessfulAttachmentUpload(attachmentId: Long, attachmentStream: SignalServiceAttachmentStream, attachmentKey: ByteArray, uploadResult: DotNetAPI.UploadResult)
    fun handleFailedAttachmentUpload(attachmentId: Long)

    fun getMessageForQuote(timestamp: Long, author: Address): Pair<Long, Boolean>?
    fun getAttachmentsAndLinkPreviewFor(mmsId: Long): List<Attachment>
    fun getMessageBodyFor(timestamp: Long, author: String): String

    fun getAttachmentIDsFor(messageID: Long): List<Long>
    fun getLinkPreviewAttachmentIDFor(messageID: Long): Long?

    fun getOpenGroup(threadID: Long): OpenGroup?
}