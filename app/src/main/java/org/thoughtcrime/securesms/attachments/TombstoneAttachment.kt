package org.thoughtcrime.securesms.attachments

import android.net.Uri
import android.os.Parcel
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.database.AttachmentTable
import java.util.UUID

/**
 * An attachment that represents where an attachment used to be. Useful when you need to know that
 * a message had an attachment and some metadata about it (like the contentType), even though the
 * underlying media no longer exists. An example usecase would be view-once messages, so that we can
 * quote them and know their contentType even though the media has been deleted.
 */
class TombstoneAttachment : Attachment {
  constructor(contentType: String, quote: Boolean) : super(
    contentType = contentType,
    quote = quote,
    transferState = AttachmentTable.TRANSFER_PROGRESS_DONE,
    size = 0,
    fileName = null,
    cdn = Cdn.CDN_0,
    remoteLocation = null,
    remoteKey = null,
    remoteDigest = null,
    incrementalDigest = null,
    fastPreflightId = null,
    voiceNote = false,
    borderless = false,
    videoGif = false,
    width = 0,
    height = 0,
    incrementalMacChunkSize = 0,
    uploadTimestamp = 0,
    caption = null,
    stickerLocator = null,
    blurHash = null,
    audioHash = null,
    transformProperties = null,
    uuid = null
  )

  constructor(
    contentType: String?,
    incrementalMac: ByteArray?,
    incrementalMacChunkSize: Int?,
    width: Int?,
    height: Int?,
    caption: String?,
    blurHash: String?,
    voiceNote: Boolean = false,
    borderless: Boolean = false,
    gif: Boolean = false,
    quote: Boolean,
    uuid: UUID?
  ) : super(
    contentType = contentType ?: "",
    quote = quote,
    transferState = AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE,
    size = 0,
    fileName = null,
    cdn = Cdn.CDN_0,
    remoteLocation = null,
    remoteKey = null,
    remoteDigest = null,
    incrementalDigest = incrementalMac,
    fastPreflightId = null,
    voiceNote = voiceNote,
    borderless = borderless,
    videoGif = gif,
    width = width ?: 0,
    height = height ?: 0,
    incrementalMacChunkSize = incrementalMacChunkSize ?: 0,
    uploadTimestamp = 0,
    caption = caption,
    stickerLocator = null,
    blurHash = BlurHash.parseOrNull(blurHash),
    audioHash = null,
    transformProperties = null,
    uuid = uuid
  )

  constructor(parcel: Parcel) : super(parcel)

  override val uri: Uri? = null
  override val publicUri: Uri? = null
  override val thumbnailUri: Uri? = null
}
