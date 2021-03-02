package org.thoughtcrime.securesms.attachments

import android.content.Context
import com.google.protobuf.ByteString
import org.greenrobot.eventbus.EventBus
import org.session.libsession.database.MessageDataProvider
import org.session.libsession.messaging.sending_receiving.attachments.*
import org.session.libsession.messaging.threads.Address
import org.session.libsession.messaging.utilities.DotNetAPI
import org.session.libsignal.libsignal.util.guava.Optional
import org.session.libsignal.service.api.messages.SignalServiceAttachment
import org.session.libsignal.service.api.messages.SignalServiceAttachmentPointer
import org.session.libsignal.service.api.messages.SignalServiceAttachmentStream
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

    override fun getAttachmentPointer(attachmentId: Long): SessionServiceAttachmentPointer? {
        val attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context)
        val databaseAttachment = attachmentDatabase.getAttachment(AttachmentId(attachmentId, 0)) ?: return null
        return databaseAttachment.toAttachmentPointer()
    }

    override fun getSignalAttachmentStream(attachmentId: Long): SignalServiceAttachmentStream? {
        val attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context)
        val databaseAttachment = attachmentDatabase.getAttachment(AttachmentId(attachmentId, 0)) ?: return null
        return databaseAttachment.toSignalAttachmentStream(context)
    }

    override fun getSignalAttachmentPointer(attachmentId: Long): SignalServiceAttachmentPointer? {
        val attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context)
        val databaseAttachment = attachmentDatabase.getAttachment(AttachmentId(attachmentId, 0)) ?: return null
        return databaseAttachment.toSignalAttachmentPointer()
    }

    override fun setAttachmentState(attachmentState: AttachmentState, attachmentId: Long, messageID: Long) {
        val attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context)
        attachmentDatabase.setTransferState(messageID, AttachmentId(attachmentId, 0), attachmentState.value)
    }

    override fun getMessageForQuote(timestamp: Long, author: Address): Long? {
        val messagingDatabase = DatabaseFactory.getMmsSmsDatabase(context)
        return messagingDatabase.getMessageFor(timestamp, author)?.id
    }

    override fun getAttachmentsAndLinkPreviewFor(messageID: Long): List<Attachment> {
        val attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context)
        return attachmentDatabase.getAttachmentsForMessage(messageID)
    }

    override fun getMessageBodyFor(messageID: Long): String {
        val messagingDatabase = DatabaseFactory.getSmsDatabase(context)
        return messagingDatabase.getMessage(messageID).body
    }

    override fun getAttachmentIDsFor(messageID: Long): List<Long> {
        return DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessage(messageID).map {
            it.attachmentId.rowId
        }
    }

    override fun insertAttachment(messageId: Long, attachmentId: Long, stream: InputStream) {
        val attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context)
        attachmentDatabase.insertAttachmentsForPlaceholder(messageId, AttachmentId(attachmentId, 0), stream)
    }

    override fun isOutgoingMessage(timestamp: Long): Boolean {
        val smsDatabase = DatabaseFactory.getSmsDatabase(context)
        val mmsDatabase = DatabaseFactory.getMmsDatabase(context)
        return smsDatabase.isOutgoingMessage(timestamp) || mmsDatabase.isOutgoingMessage(timestamp)
    }

    override fun updateAttachmentAfterUploadSucceeded(attachmentId: Long, uploadResult: DotNetAPI.UploadResult) {
        TODO("Not yet implemented")
    }

    override fun updateAttachmentAfterUploadFailed(attachmentId: Long) {
        TODO("Not yet implemented")
    }

    override fun getMessageID(serverID: Long): Long? {
        val openGroupMessagingDatabase = DatabaseFactory.getLokiMessageDatabase(context)
        return openGroupMessagingDatabase.getMessageID(serverID)
    }

    override fun deleteMessage(messageID: Long) {
        val messagingDatabase = DatabaseFactory.getSmsDatabase(context)
        messagingDatabase.deleteMessage(messageID)
    }

    override fun getDatabaseAttachment(attachmentId: Long): DatabaseAttachment? {
        val attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context)
        return attachmentDatabase.getAttachment(AttachmentId(attachmentId, 0))
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

fun DatabaseAttachment.toSignalAttachmentPointer(): SignalServiceAttachmentPointer {
    return SignalServiceAttachmentPointer(attachmentId.rowId, contentType, key?.toByteArray(), Optional.fromNullable(size.toInt()), Optional.absent(), width, height, Optional.fromNullable(digest), Optional.fromNullable(fileName), isVoiceNote, Optional.fromNullable(caption), url)
}

fun DatabaseAttachment.toSignalAttachmentStream(context: Context): SignalServiceAttachmentStream {
    val stream = PartAuthority.getAttachmentStream(context, this.dataUri!!)
    val listener = SignalServiceAttachment.ProgressListener { total: Long, progress: Long -> EventBus.getDefault().postSticky(PartProgressEvent(this, total, progress))}

    return SignalServiceAttachmentStream(stream, this.contentType, this.size, Optional.fromNullable(this.fileName), this.isVoiceNote, Optional.absent(), this.width, this.height, Optional.fromNullable(this.caption), listener)
}

fun DatabaseAttachment.shouldHaveImageSize(): Boolean {
    return (MediaUtil.isVideo(this) || MediaUtil.isImage(this) || MediaUtil.isGif(this));
}