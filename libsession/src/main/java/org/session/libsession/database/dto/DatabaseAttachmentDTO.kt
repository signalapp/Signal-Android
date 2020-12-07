package org.session.libsession.database.dto

import android.util.Size
import com.google.protobuf.ByteString
import org.session.libsignal.service.internal.push.SignalServiceProtos
import kotlin.math.round

class DatabaseAttachmentDTO {
    var contentType: String? = null

    var fileName: String? = null

    var url: String? = null

    var caption: String? = null

    var size: Int = 0

    var key: ByteString? = null

    var digest: ByteString? = null

    var flags: Int = 0

    var width: Int = 0

    var height: Int = 0

    val isVoiceNote: Boolean = false

    var shouldHaveImageSize: Boolean = false

    val isUploaded: Boolean = false

    fun toProto(): SignalServiceProtos.AttachmentPointer? {
        val builder = org.session.libsignal.service.internal.push.SignalServiceProtos.AttachmentPointer.newBuilder()
        builder.contentType = this.contentType

        if (!this.fileName.isNullOrEmpty()) {
            builder.fileName = this.fileName
        }
        if (!this.caption.isNullOrEmpty()) {
            builder.caption = this.caption
        }

        builder.size = this.size
        builder.key = this.key
        builder.digest = this.digest
        builder.flags = if (this.isVoiceNote) org.session.libsignal.service.internal.push.SignalServiceProtos.AttachmentPointer.Flags.VOICE_MESSAGE.number else 0

        //TODO I did copy the behavior of iOS below, not sure if that's relevant here...
        if (this.shouldHaveImageSize) {
            if (this.width < kotlin.Int.MAX_VALUE && this.height < kotlin.Int.MAX_VALUE) {
                val imageSize: Size = Size(this.width, this.height)
                val imageWidth = round(imageSize.width.toDouble())
                val imageHeight = round(imageSize.height.toDouble())
                if (imageWidth > 0 && imageHeight > 0) {
                    builder.width = imageWidth.toInt()
                    builder.height = imageHeight.toInt()
                }
            }
        }

        builder.url = this.url

        try {
            return builder.build()
        } catch (e: Exception) {
            return null
        }
    }

}