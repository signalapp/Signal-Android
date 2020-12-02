package org.session.libsession.messaging.messages.visible

import android.util.Size
import android.webkit.MimeTypeMap
import org.session.libsignal.service.internal.push.SignalServiceProtos
import java.io.File
import java.net.URL
import kotlin.math.absoluteValue

class Attachment : VisibleMessage<SignalServiceProtos.AttachmentPointer?>() {

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
            val kind: Kind
            if (proto.hasFlags() && (proto.flags and SignalServiceProtos.AttachmentPointer.Flags.VOICE_MESSAGE_VALUE) > 0) {
                kind = Kind.VOICEMESSAGE
            } else {
                kind = Kind.GENERIC
            }
            result.kind = kind
            result.caption = if (proto.hasCaption()) proto.caption else null
            val size: Size
            if (proto.hasWidth() && proto.width > 0 && proto.hasHeight() && proto.height > 0) {
                size = Size(proto.width, proto.height)
            } else {
                size = Size(0,0) //TODO check that it's equivalent to swift: CGSize.zero
            }
            result.size = size
            result.sizeInBytes = if (proto.size > 0) proto.size else null
            result. url = proto.url
            return result
        }
    }

    enum class Kind {
        VOICEMESSAGE,
        GENERIC
    }

    // validation
    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        // key and digest can be nil for open group attachments
        return (contentType != null && kind != null && size != null && sizeInBytes != null && url != null)
    }

    override fun toProto(transaction: String): SignalServiceProtos.AttachmentPointer? {
        TODO("Not implemented")
    }
}