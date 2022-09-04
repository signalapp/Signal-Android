package org.session.libsession.database

import org.session.libsession.messaging.sending_receiving.attachments.Attachment
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentId
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentState
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.messaging.sending_receiving.attachments.SessionServiceAttachmentPointer
import org.session.libsession.messaging.sending_receiving.attachments.SessionServiceAttachmentStream
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.UploadResult
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.messages.SignalServiceAttachmentPointer
import org.session.libsignal.messages.SignalServiceAttachmentStream
import java.io.InputStream

interface MessageDataProvider {

    fun getMessageID(serverID: Long): Long?
    /**
     * @return pair of sms or mms table-specific ID and whether it is in SMS table
     */
    fun getMessageID(serverId: Long, threadId: Long): Pair<Long, Boolean>?
    fun deleteMessage(messageID: Long, isSms: Boolean)
    fun updateMessageAsDeleted(timestamp: Long, author: String)
    fun getServerHashForMessage(messageID: Long): String?
    fun getDatabaseAttachment(attachmentId: Long): DatabaseAttachment?
    fun getAttachmentStream(attachmentId: Long): SessionServiceAttachmentStream?
    fun getAttachmentPointer(attachmentId: Long): SessionServiceAttachmentPointer?
    fun getSignalAttachmentStream(attachmentId: Long): SignalServiceAttachmentStream?
    fun getScaledSignalAttachmentStream(attachmentId: Long): SignalServiceAttachmentStream?
    fun getSignalAttachmentPointer(attachmentId: Long): SignalServiceAttachmentPointer?
    fun setAttachmentState(attachmentState: AttachmentState, attachmentId: AttachmentId, messageID: Long)
    fun insertAttachment(messageId: Long, attachmentId: AttachmentId, stream : InputStream)
    fun updateAudioAttachmentDuration(attachmentId: AttachmentId, durationMs: Long, threadId: Long)
    fun isMmsOutgoing(mmsMessageId: Long): Boolean
    fun isOutgoingMessage(timestamp: Long): Boolean
    fun handleSuccessfulAttachmentUpload(attachmentId: Long, attachmentStream: SignalServiceAttachmentStream, attachmentKey: ByteArray, uploadResult: UploadResult)
    fun handleFailedAttachmentUpload(attachmentId: Long)
    fun getMessageForQuote(timestamp: Long, author: Address): Pair<Long, Boolean>?
    fun getAttachmentsAndLinkPreviewFor(mmsId: Long): List<Attachment>
    fun getMessageBodyFor(timestamp: Long, author: String): String
    fun getAttachmentIDsFor(messageID: Long): List<Long>
    fun getLinkPreviewAttachmentIDFor(messageID: Long): Long?
    fun getIndividualRecipientForMms(mmsId: Long): Recipient?
}