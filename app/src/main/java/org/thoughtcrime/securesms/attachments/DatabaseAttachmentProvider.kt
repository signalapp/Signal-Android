package org.thoughtcrime.securesms.attachments

import android.content.Context
import com.google.protobuf.ByteString
import org.greenrobot.eventbus.EventBus
import org.session.libsession.database.MessageDataProvider
import org.session.libsession.messaging.sending_receiving.attachments.Attachment
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentState
import org.session.libsession.messaging.sending_receiving.attachments.SessionServiceAttachmentPointer
import org.session.libsession.messaging.sending_receiving.attachments.SessionServiceAttachmentStream
import org.session.libsession.messaging.threads.Address
import org.session.libsignal.libsignal.util.guava.Optional
import org.session.libsignal.service.api.messages.SignalServiceAttachment
import org.session.libsignal.service.api.messages.SignalServiceAttachmentPointer
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.events.PartProgressEvent
import org.thoughtcrime.securesms.jobs.AttachmentUploadJob
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.util.MediaUtil
import java.io.InputStream


class DatabaseAttachmentProvider(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), MessageDataProvider {

    override fun getAttachmentStream(attachmentId: Long): SessionServiceAttachmentStream? {
        val attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context)
        val databaseAttachment = attachmentDatabase.getAttachment(AttachmentId(attachmentId, 0)) ?: return null
        return databaseAttachment.toAttachmentStream(context)
    }

    override fun getAttachmentPointer(attachmentID: String): SignalServiceAttachmentPointer? {
        TODO("Not yet implemented")
    }

    override fun getAttachmentPointer(attachmentId: Long): SessionServiceAttachmentPointer? {
        val attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context)
        val databaseAttachment = attachmentDatabase.getAttachment(AttachmentId(attachmentId, 0)) ?: return null
        return databaseAttachment.toAttachmentPointer()
    }

    override fun setAttachmentState(attachmentState: AttachmentState, attachmentId: Long, messageID: Long) {
        val attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context)
        attachmentDatabase.setTransferState(messageID, AttachmentId(attachmentId, 0), attachmentState.value)
    }

    @Throws(Exception::class)
    override fun uploadAttachment(attachmentId: Long) {
        val attachmentUploadJob = AttachmentUploadJob(AttachmentId(attachmentId, 0), null)
        attachmentUploadJob.onRun()
    }

    override fun getMessageForQuote(timestamp: Long, author: Address): Long? {
        TODO("Not yet implemented")
    }

    override fun getAttachmentsWithLinkPreviewFor(messageID: Long): List<Attachment> {
        TODO("Not yet implemented")
    }

    override fun getMessageBodyFor(messageID: Long): String {
        TODO("Not yet implemented")
    }

    override fun insertAttachment(messageId: Long, attachmentId: Long, stream: InputStream) {
        val attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context)
        attachmentDatabase.insertAttachmentsForPlaceholder(messageId, AttachmentId(attachmentId, 0), stream)
    }

    override fun isOutgoingMessage(timestamp: Long): Boolean {
        val smsDatabase = DatabaseFactory.getSmsDatabase(context)
        return smsDatabase.isOutgoingMessage(timestamp)
    }

    override fun getMessageID(serverID: Long): Long? {
        TODO("Not yet implemented")
    }

    override fun deleteMessage(messageID: Long) {
        TODO("Not yet implemented")
        //val publicChatAPI = ApplicationContext.getInstance(context).publicChatAPI
        //publicChatAPI?.deleteMessage(messageID)
    }

}

fun DatabaseAttachment.toAttachmentPointer(): SessionServiceAttachmentPointer {
    return SessionServiceAttachmentPointer(attachmentId.rowId, contentType, key?.toByteArray(), Optional.fromNullable(size.toInt()), Optional.absent(), width, height, Optional.fromNullable(digest), Optional.fromNullable(fileName), isVoiceNote, Optional.fromNullable(caption), url)
}

fun DatabaseAttachment.toAttachmentStream(context: Context): SessionServiceAttachmentStream {
    val stream = PartAuthority.getAttachmentStream(context, this.dataUri!!)
    val listener = SignalServiceAttachment.ProgressListener { total: Long, progress: Long -> EventBus.getDefault().postSticky(PartProgressEvent(this, total, progress))}

    var attachmentStream = SessionServiceAttachmentStream(stream, this.contentType, this.size, Optional.fromNullable(this.fileName), this.isVoiceNote, Optional.absent(), this.width, this.height, Optional.fromNullable(this.caption), listener)
    attachmentStream.attachmentId = this.attachmentId.rowId
    attachmentStream.isAudio = MediaUtil.isAudio(this)
    attachmentStream.isGif = MediaUtil.isGif(this)
    attachmentStream.isVideo = MediaUtil.isVideo(this)
    attachmentStream.isImage = MediaUtil.isImage(this)

    attachmentStream.key = ByteString.copyFrom(this.key?.toByteArray())
    attachmentStream.digest = Optional.fromNullable(this.digest)

    attachmentStream.url = this.url

    return attachmentStream
}

fun DatabaseAttachment.shouldHaveImageSize(): Boolean {
    return (MediaUtil.isVideo(this) || MediaUtil.isImage(this) || MediaUtil.isGif(this));
}