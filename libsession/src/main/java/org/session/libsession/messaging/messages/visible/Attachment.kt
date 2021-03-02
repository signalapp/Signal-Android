package org.session.libsession.messaging.messages.visible

import android.util.Size
import android.webkit.MimeTypeMap
import org.session.libsession.database.MessageDataProvider
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsignal.service.internal.push.SignalServiceProtos
import java.io.File

class Attachment {

    var fileName: String? = null
    var contentType: String? = null
    var key: ByteArray? = null
    var digest: ByteArray? = null
    var kind: Kind? = null
    var caption: String? = null
    var size: Size? = null
    var sizeInBytes: Int? = 0
    var url: String? = null

    companion object {
        fun fromProto(proto: SignalServiceProtos.AttachmentPointer): Attachment? {
            val result = Attachment()
            result.fileName = proto.fileName
            fun inferContentType(): String {
                val fileName = result.fileName ?: return "application/octet-stream" //TODO find equivalent to OWSMimeTypeApplicationOctetStream
                val fileExtension = File(fileName).extension
                val mimeTypeMap = MimeTypeMap.getSingleton()
                return mimeTypeMap.getMimeTypeFromExtension(fileExtension) ?: "application/octet-stream" //TODO check that it's correct
            }
            result.contentType = proto.contentType ?: inferContentType()
            result.key = proto.key.toByteArray()
            result.digest = proto.digest.toByteArray()
            val kind: Kind = if (proto.hasFlags() && proto.flags.and(SignalServiceProtos.AttachmentPointer.Flags.VOICE_MESSAGE_VALUE) > 0) {
                Kind.VOICE_MESSAGE
            } else {
                Kind.GENERIC
            }
            result.kind = kind
            result.caption = if (proto.hasCaption()) proto.caption else null
            val size: Size = if (proto.hasWidth() && proto.width > 0 && proto.hasHeight() && proto.height > 0) {
                Size(proto.width, proto.height)
            } else {
                Size(0,0)
            }
            result.size = size
            result.sizeInBytes = if (proto.size > 0) proto.size else null
            result. url = proto.url
            return result
        }
    }

    enum class Kind {
        VOICE_MESSAGE,
        GENERIC
    }

    // validation
    fun isValid(): Boolean {
        // key and digest can be nil for open group attachments
        return (contentType != null && kind != null && size != null && sizeInBytes != null && url != null)
    }

    fun toProto(): SignalServiceProtos.AttachmentPointer? {
        TODO("Not implemented")
    }

    fun toDatabaseAttachment(): org.session.libsession.messaging.sending_receiving.attachments.Attachment {
        return DatabaseAttachment(null, 0, true, true, contentType, 0,
                sizeInBytes?.toLong() ?: 0, fileName, null, key.toString(), null, digest, null, kind == Kind.VOICE_MESSAGE,
                size?.width ?: 0, size?.height ?: 0, false, caption, url)
    }
}