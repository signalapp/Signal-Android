//package org.thoughtcrime.securesms.attachments
//
//import android.content.Context
//import com.google.protobuf.ByteString
//import org.session.libsession.database.dto.DatabaseAttachmentDTO
//import org.session.libsession.database.MessageDataProvider
//import org.session.libsignal.service.internal.push.SignalServiceProtos
//import org.thoughtcrime.securesms.database.Database
//import org.thoughtcrime.securesms.database.DatabaseFactory
//import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
//import org.thoughtcrime.securesms.util.MediaUtil
//
//class DatabaseAttachmentProvider(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper), MessageDataProvider {
//    override fun getAttachment(uniqueID: String): DatabaseAttachmentDTO? {
//
//        val attachmentDatabase = DatabaseFactory.getAttachmentDatabase(context)
//        val uniqueID = uniqueID.toLongOrNull() ?: return null
//        val attachmentID = AttachmentId(0, uniqueID)
//        val databaseAttachment = attachmentDatabase.getAttachment(attachmentID) ?: return null
//
//        return databaseAttachment.toDTO()
//    }
//
//}
//
//// Extension to DatabaseAttachment class
//
//fun DatabaseAttachment.toDTO(): DatabaseAttachmentDTO {
//    var databaseAttachmentDTO = DatabaseAttachmentDTO()
//    databaseAttachmentDTO.contentType = this.contentType
//    databaseAttachmentDTO.fileName = this.fileName
//    databaseAttachmentDTO.caption = this.caption
//
//    databaseAttachmentDTO.size = this.size.toInt()
//    databaseAttachmentDTO.key = ByteString.copyFrom(this.key?.toByteArray())
//    databaseAttachmentDTO.digest = ByteString.copyFrom(this.digest)
//    databaseAttachmentDTO.flags = if (this.isVoiceNote) SignalServiceProtos.AttachmentPointer.Flags.VOICE_MESSAGE.number else 0
//
//    databaseAttachmentDTO.url = this.url
//
//    if (this.shouldHaveImageSize()) {
//        databaseAttachmentDTO.shouldHaveImageSize = true
//        databaseAttachmentDTO.width = this.width
//        databaseAttachmentDTO.height = this.height
//    }
//
//    return databaseAttachmentDTO
//}
//
//fun DatabaseAttachment.shouldHaveImageSize(): Boolean {
//    return (MediaUtil.isVideo(this) || MediaUtil.isImage(this) || MediaUtil.isGif(this));
//}