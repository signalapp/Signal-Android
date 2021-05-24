package org.thoughtcrime.securesms.attachments

import android.content.Context
import android.text.TextUtils
import com.google.protobuf.ByteString
import org.greenrobot.eventbus.EventBus
import org.session.libsession.database.MessageDataProvider
import org.session.libsession.messaging.sending_receiving.attachments.*
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.UploadResult
import org.session.libsession.utilities.Util
import org.session.libsignal.utilities.guava.Optional
import org.session.libsignal.messages.SignalServiceAttachment
import org.session.libsignal.messages.SignalServiceAttachmentPointer
import org.session.libsignal.messages.SignalServiceAttachmentStream
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.database.AttachmentDatabase
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.events.PartProgressEvent
import org.thoughtcrime.securesms.mms.MediaConstraints
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.util.MediaUtil
import java.io.IOException
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

    override fun getScaledSignalAttachmentStream(attachmentId: Long): SignalServiceAttachmentStream? {
        val database = DatabaseFactory.getAttachmentDatabase(context)
        val databaseAttachment = database.getAttachment(AttachmentId(attachmentId, 0)) ?: return null
        val mediaConstraints = MediaConstraints.getPushMediaConstraints()
        val scaledAttachment = scaleAndStripExif(database, mediaConstraints, databaseAttachment) ?: return null
        return getAttachmentFor(scaledAttachment)
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

    override fun getMessageForQuote(timestamp: Long, author: Address): Pair<Long, Boolean>? {
        val messagingDatabase = DatabaseFactory.getMmsSmsDatabase(context)
        val message = messagingDatabase.getMessageFor(timestamp, author)
        return if (message != null) Pair(message.id, message.isMms) else null
    }

    override fun getAttachmentsAndLinkPreviewFor(mmsId: Long): List<Attachment> {
        return DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessage(mmsId)
    }

    override fun getMessageBodyFor(timestamp: Long, author: String): String {
        val messagingDatabase = DatabaseFactory.getMmsSmsDatabase(context)
        return messagingDatabase.getMessageFor(timestamp, author)!!.body
    }

    override fun getAttachmentIDsFor(messageID: Long): List<Long> {
        return DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessage(messageID).mapNotNull {
            if (it.isQuote) return@mapNotNull null
            it.attachmentId.rowId
        }
    }

    override fun getLinkPreviewAttachmentIDFor(messageID: Long): Long? {
        val message = DatabaseFactory.getMmsDatabase(context).getOutgoingMessage(messageID)
        return message.linkPreviews.firstOrNull()?.attachmentId?.rowId
    }

    override fun insertAttachment(messageId: Long, attachmentId: AttachmentId, stream: InputStream) {
        val attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context)
        attachmentDatabase.insertAttachmentsForPlaceholder(messageId, attachmentId, stream)
    }

    override fun isOutgoingMessage(timestamp: Long): Boolean {
        val smsDatabase = DatabaseFactory.getSmsDatabase(context)
        val mmsDatabase = DatabaseFactory.getMmsDatabase(context)
        return smsDatabase.isOutgoingMessage(timestamp) || mmsDatabase.isOutgoingMessage(timestamp)
    }

    override fun handleSuccessfulAttachmentUpload(attachmentId: Long, attachmentStream: SignalServiceAttachmentStream, attachmentKey: ByteArray, uploadResult: UploadResult) {
        val database = DatabaseFactory.getAttachmentDatabase(context)
        val databaseAttachment = getDatabaseAttachment(attachmentId) ?: return
        val attachmentPointer = SignalServiceAttachmentPointer(uploadResult.id,
            attachmentStream.contentType,
            attachmentKey,
            Optional.of(Util.toIntExact(attachmentStream.length)),
            attachmentStream.preview,
            attachmentStream.width, attachmentStream.height,
            Optional.fromNullable(uploadResult.digest),
            attachmentStream.fileName,
            attachmentStream.voiceNote,
            attachmentStream.caption,
            uploadResult.url);
        val attachment = PointerAttachment.forPointer(Optional.of(attachmentPointer), databaseAttachment.fastPreflightId).get()
        database.updateAttachmentAfterUploadSucceeded(databaseAttachment.attachmentId, attachment)
    }

    override fun handleFailedAttachmentUpload(attachmentId: Long) {
        val database = DatabaseFactory.getAttachmentDatabase(context)
        val databaseAttachment = getDatabaseAttachment(attachmentId) ?: return
        database.handleFailedAttachmentUpload(databaseAttachment.attachmentId)
    }

    override fun getMessageID(serverID: Long): Long? {
        val openGroupMessagingDatabase = DatabaseFactory.getLokiMessageDatabase(context)
        return openGroupMessagingDatabase.getMessageID(serverID)
    }

    override fun getMessageID(serverId: Long, threadId: Long): Pair<Long, Boolean>? {
        val messageDB = DatabaseFactory.getLokiMessageDatabase(context)
        return messageDB.getMessageID(serverId, threadId)
    }

    override fun deleteMessage(messageID: Long, isSms: Boolean) {
        if (isSms) {
            val db = DatabaseFactory.getSmsDatabase(context)
            db.deleteMessage(messageID)
        } else {
            val db = DatabaseFactory.getMmsDatabase(context)
            db.delete(messageID)
        }
        DatabaseFactory.getLokiMessageDatabase(context).deleteMessage(messageID, isSms)
    }

    override fun getDatabaseAttachment(attachmentId: Long): DatabaseAttachment? {
        val attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context)
        return attachmentDatabase.getAttachment(AttachmentId(attachmentId, 0))
    }

    private fun scaleAndStripExif(attachmentDatabase: AttachmentDatabase, constraints: MediaConstraints, attachment: Attachment): Attachment? {
        return try {
            if (constraints.isSatisfied(context, attachment)) {
                if (MediaUtil.isJpeg(attachment)) {
                    val stripped = constraints.getResizedMedia(context, attachment)
                    attachmentDatabase.updateAttachmentData(attachment, stripped)
                } else {
                    attachment
                }
            } else if (constraints.canResize(attachment)) {
                val resized = constraints.getResizedMedia(context, attachment)
                attachmentDatabase.updateAttachmentData(attachment, resized)
            } else {
                throw Exception("Size constraints could not be met!")
            }
        } catch (e: Exception) {
            return null
        }
    }

    private fun getAttachmentFor(attachment: Attachment): SignalServiceAttachmentStream? {
        try {
            if (attachment.dataUri == null || attachment.size == 0L) throw IOException("Assertion failed, outgoing attachment has no data!")
            val `is` = PartAuthority.getAttachmentStream(context, attachment.dataUri!!)
            return SignalServiceAttachment.newStreamBuilder()
                    .withStream(`is`)
                    .withContentType(attachment.contentType)
                    .withLength(attachment.size)
                    .withFileName(attachment.fileName)
                    .withVoiceNote(attachment.isVoiceNote)
                    .withWidth(attachment.width)
                    .withHeight(attachment.height)
                    .withCaption(attachment.caption)
                    .withListener { total: Long, progress: Long -> EventBus.getDefault().postSticky(PartProgressEvent(attachment, total, progress)) }
                    .build()
        } catch (ioe: IOException) {
            Log.w("Loki", "Couldn't open attachment", ioe)
        }
        return null
    }

}

fun DatabaseAttachment.toAttachmentPointer(): SessionServiceAttachmentPointer {
    return SessionServiceAttachmentPointer(attachmentId.rowId, contentType, key?.toByteArray(), Optional.fromNullable(size.toInt()), Optional.absent(), width, height, Optional.fromNullable(digest), Optional.fromNullable(fileName), isVoiceNote, Optional.fromNullable(caption), url)
}

fun SessionServiceAttachmentPointer.toSignalPointer(): SignalServiceAttachmentPointer {
    return SignalServiceAttachmentPointer(id,contentType,key?.toByteArray() ?: byteArrayOf(), size, preview, width, height, digest, fileName, voiceNote, caption, url)
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

fun DatabaseAttachment.toSignalAttachmentPointer(): SignalServiceAttachmentPointer? {
    if (TextUtils.isEmpty(location)) { return null }
    // `key` can be empty in an open group context (no encryption means no encryption key)
    return try {
        val id = location!!.toLong()
        val key = Base64.decode(key!!)
        SignalServiceAttachmentPointer(
            id,
            contentType,
            key,
            Optional.of(Util.toIntExact(size)),
            Optional.absent(),
            width,
            height,
            Optional.fromNullable(digest),
            Optional.fromNullable(fileName),
            isVoiceNote,
            Optional.fromNullable(caption),
            url
        )
    } catch (e: Exception) {
        null
    }
}

fun DatabaseAttachment.toSignalAttachmentStream(context: Context): SignalServiceAttachmentStream {
    val stream = PartAuthority.getAttachmentStream(context, this.dataUri!!)
    val listener = SignalServiceAttachment.ProgressListener { total: Long, progress: Long -> EventBus.getDefault().postSticky(PartProgressEvent(this, total, progress))}

    return SignalServiceAttachmentStream(stream, this.contentType, this.size, Optional.fromNullable(this.fileName), this.isVoiceNote, Optional.absent(), this.width, this.height, Optional.fromNullable(this.caption), listener)
}

fun DatabaseAttachment.shouldHaveImageSize(): Boolean {
    return (MediaUtil.isVideo(this) || MediaUtil.isImage(this) || MediaUtil.isGif(this));
}